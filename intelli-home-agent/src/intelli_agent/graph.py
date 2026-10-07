"""按需建议工作流。

收到 Java 安全告警时直接返回固定提示，跳过天气查询和模型调用。
普通请求依次整理上下文、获取天气、分析策略和生成建议；
模型不可用时使用结构化结论生成模板，并标记 degraded=True。
"""

from __future__ import annotations

from typing import Any, TypedDict

from langchain_core.language_models import BaseChatModel
from langgraph.graph import END, StateGraph

from .config import get_settings
from .schemas import WeatherInfo
from .strategy import analyze
from .weather import WeatherService

SYSTEM_PROMPT = """你是智能居家助手，负责把环境分析结论转述成简洁、可执行的中文生活建议。

要求：
1. 只依据给定的分析结论，不要编造未提供的传感器数据；
2. 用 1-3 句话，先给结论再给理由，语气自然，像家人提醒；
3. 安全告警由 Java 固定提示分支处理，普通建议不要自行判定火灾；
4. 如果天气数据不可用，不要基于天气给建议。"""


class AgentState(TypedDict, total=False):
    """在图各节点间流转的状态。"""

    safety_alerts: list[dict[str, Any]]
    degradation_reason: str | None
    device_key: str
    location: str | None
    properties: dict[str, Any]
    states: dict[str, dict[str, Any]]
    weather: dict[str, Any]
    analysis: dict[str, Any]
    recommendation: str
    scenario: str
    degraded: bool


def build_graph(weather_service: WeatherService | None = None,
                llm: BaseChatModel | None = None):
    """构建并编译 Agent 工作流。

    Args:
        weather_service: 天气服务，测试时可注入假实现。
        llm: 聊天模型，测试时可注入假实现；为 None 且未配置密钥时走降级路径。
    """
    service = weather_service or WeatherService()
    settings = get_settings()

    if llm is None and settings.llm_configured:
        from langchain_openai import ChatOpenAI

        llm = ChatOpenAI(
            model=settings.llm_model,
            api_key=settings.llm_api_key,
            base_url=settings.llm_base_url,
            temperature=settings.llm_temperature,
            timeout=8,
            max_retries=0,
        )

    async def collect_context(state: AgentState) -> AgentState:
        """整理入参。状态表由 Java 侧传入，这里只做兜底。"""
        states = state.get("states") or {}
        # 若 Java 侧没给位置状态，用事件自身属性兜底，保证至少有一份数据可用
        if not states and state.get("location"):
            states = {state["location"]: state.get("properties") or {}}
        return {"states": states}

    async def fetch_weather(state: AgentState) -> AgentState:
        """拉取室外天气。失败时降级为不可用，不中断流程。"""
        weather = await service.fetch()
        return {"weather": weather.model_dump()}

    async def analyze_strategy(state: AgentState) -> AgentState:
        """确定性分析，产出结构化结论。"""
        result = analyze(state.get("states") or {}, state.get("weather") or {})
        return {"analysis": result, "scenario": result.get("scenario", "UNKNOWN")}

    async def generate_recommendation(state: AgentState) -> AgentState:
        """用 LLM 把结构化结论转成自然语言；不可用时降级为模板拼接。"""
        analysis = state.get("analysis") or {}

        if llm is None:
            return {
                "recommendation": _fallback_text(analysis),
                "degraded": True,
                "degradation_reason": "LLM_NOT_CONFIGURED",
            }

        user_prompt = (
            f"设备：{state.get('device_key')}\n"
            f"本次上报：{state.get('properties')}\n"
            f"室内外状态：{state.get('states')}\n"
            f"天气：{state.get('weather')}\n"
            f"分析结论：{analysis}\n\n"
            "请据此给出建议。"
        )

        try:
            response = await llm.ainvoke(
                [("system", SYSTEM_PROMPT), ("human", user_prompt)]
            )
            text = response.content if isinstance(response.content, str) else str(response.content)
            return {"recommendation": text.strip(), "degraded": False, "degradation_reason": None}
        except Exception:
            # LLM 不可用不能让接口 500，降级返回结构化结论
            return {"recommendation": _fallback_text(analysis), "degraded": True, "degradation_reason": "LLM_UNAVAILABLE"}

    def _fallback_text(analysis: dict[str, Any]) -> str:
        parts: list[str] = []
        if analysis.get("scenario") == "FIRE":
            parts.append(f"⚠️ 火灾隐患：{analysis.get('conclusion', '')}")
        if analysis.get("window_reason"):
            parts.append(f"开窗建议：{analysis['window_reason']}")
        if analysis.get("drying_reason"):
            parts.append(f"晾晒建议：{analysis['drying_reason']}")
        if not parts:
            parts.append(analysis.get("conclusion", "环境数据正常，暂无特别建议。"))
        return " ".join(parts)

    async def safety_notice(state: AgentState) -> AgentState:
        alerts = state.get("safety_alerts") or []
        text = " ".join(str(alert.get("content") or alert.get("title") or "请确认现场安全情况") for alert in alerts)
        return {"scenario": "FIRE", "recommendation": text, "analysis": {"safetyAlerts": alerts},
                "degraded": False, "degradation_reason": None}

    def route(state: AgentState) -> str:
        return "safety_notice" if state.get("safety_alerts") else "fetch_weather"

    graph = StateGraph(AgentState)
    graph.add_node("collect_context", collect_context)
    graph.add_node("safety_notice", safety_notice)
    graph.add_node("fetch_weather", fetch_weather)
    graph.add_node("analyze_strategy", analyze_strategy)
    graph.add_node("generate_recommendation", generate_recommendation)

    graph.set_entry_point("collect_context")
    graph.add_conditional_edges("collect_context", route, {"safety_notice": "safety_notice", "fetch_weather": "fetch_weather"})
    graph.add_edge("safety_notice", END)
    graph.add_edge("fetch_weather", "analyze_strategy")
    graph.add_edge("analyze_strategy", "generate_recommendation")
    graph.add_edge("generate_recommendation", END)

    return graph.compile()


__all__ = ["AgentState", "build_graph", "WeatherInfo"]
