"""FastAPI 入口 —— 对 Java 主服务暴露 Agent 能力。

对外接口：
  GET  /health              健康检查
  POST /agent/recommend     按需建议
  POST /agent/daily-advice  生活建议日报
  GET  /agent/weather       结构化天气
"""

from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI

from . import __version__
from .config import get_settings
from .graph import build_graph
from .daily import build_daily_graph
from .schemas import DailyAdviceRequest, DailyAdviceResponse
from .schemas import RecommendRequest, RecommendResponse
from .weather import WeatherService
import time

_app_graph = None
_daily_graph = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """启动时编译一次图，避免每次请求重复构建。"""
    global _app_graph, _daily_graph
    _app_graph = build_graph()
    _daily_graph = build_daily_graph()
    settings = get_settings()
    print(
        f"[intelli-home-agent] 启动完成 version={__version__} "
        f"llm_configured={settings.llm_configured} "
        f"weather_configured={settings.weather_configured}"
    )
    yield
    _app_graph = None
    _daily_graph = None


app = FastAPI(
    title="intelli-home-agent",
    description="个人智能居家助手 - Agent 服务",
    version=__version__,
    lifespan=lifespan,
)


@app.get("/health")
async def health() -> dict[str, object]:
    settings = get_settings()
    return {
        "status": "UP",
        "version": __version__,
        "llm_configured": settings.llm_configured,
        "weather_configured": settings.weather_configured,
    }


@app.post("/agent/recommend", response_model=RecommendResponse)
async def recommend(request: RecommendRequest) -> RecommendResponse:
    """根据设备事件与环境状态生成生活建议。"""
    if _app_graph is None:  # pragma: no cover - 仅在未走 lifespan 时触发
        raise RuntimeError("Agent 工作流尚未初始化")

    result = await _app_graph.ainvoke(
        {
            "device_key": request.device_key,
            "location": request.location,
            "properties": request.properties,
            "states": request.states,
            "safety_alerts": request.safety_alerts,
        }
    )

    return RecommendResponse(
        recommendation=result.get("recommendation", ""),
        scenario=result.get("scenario", "UNKNOWN"),
        analysis=result.get("analysis", {}),
        degraded=result.get("degraded", False),
        degradation_reason=result.get("degradation_reason"),
    )


@app.get("/agent/weather")
async def weather_snapshot() -> dict[str, object]:
    """结构化天气桥接，不调用 LLM；保留供应商观测时间，不把缓存数据伪装成新观测。"""
    weather = await WeatherService().fetch()
    now = int(time.time() * 1000)
    observed = weather.observed_at if weather.observed_at is not None else now
    available = weather.available and weather.observed_at is not None
    return {
        "location": get_settings().weather_location,
        "source": "qweather",
        "observedAt": observed,
        "validUntil": observed + 600_000 if available else observed,
        "available": available,
        "raining": weather.raining,
        "rainProbability": None,
        "reason": None if available else "WEATHER_UNAVAILABLE",
    }


@app.post("/agent/daily-advice", response_model=DailyAdviceResponse)
async def daily_advice(request: DailyAdviceRequest) -> DailyAdviceResponse:
    if _daily_graph is None:
        raise RuntimeError("Daily advice workflow is not initialized")
    result = await _daily_graph.ainvoke({"request": request.model_dump(by_alias=True, mode="json")})
    return DailyAdviceResponse(advice=result["advice"], analysis=result["analysis"],
                               degraded=result["degraded"], degradation_reason=result["degradation_reason"])


def main() -> None:
    """本地启动：uv run serve"""
    import uvicorn

    settings = get_settings()
    uvicorn.run(
        "intelli_agent.main:app",
        host=settings.server_host,
        port=settings.server_port,
        reload=True,
    )


if __name__ == "__main__":
    main()
