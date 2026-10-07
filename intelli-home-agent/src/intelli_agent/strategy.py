"""根据设备状态和天气阈值生成结构化开窗、晾晒建议。

模型仅负责建议的文字表达；安全告警由 Java 后端判断。
"""

from __future__ import annotations

from typing import Any

# 阈值集中定义，便于调参与单测
TEMP_DIFF_THRESHOLD = 3.0
HUMIDITY_DIFF_THRESHOLD = 10.0
BALCONY_DRYING_HUMIDITY = 60.0
BALCONY_DRYING_TEMP = 20.0


def _num(state: dict[str, Any] | None, key: str) -> float | None:
    """从状态表里取数值，缺失或非数字返回 None。"""
    if not state:
        return None
    value = state.get(key)
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        try:
            return float(value)
        except ValueError:
            return None
    return None


def analyze(states: dict[str, dict[str, Any]], weather: dict[str, Any]) -> dict[str, Any]:
    """产出结构化分析结论。

    Args:
        states: {"INDOOR": {...}, "BALCONY": {...}}
        weather: WeatherInfo 序列化后的字典

    Returns:
        含 conclusion、window_advice、drying_advice 等字段；安全告警由 Java 判定。
    """
    indoor = states.get("INDOOR") or {}
    balcony = states.get("BALCONY") or {}

    indoor_temp = _num(indoor, "temperature")
    indoor_humidity = _num(indoor, "humidity")
    indoor_smoke = _num(indoor, "smoke")
    balcony_temp = _num(balcony, "temperature")
    balcony_humidity = _num(balcony, "humidity")

    analysis: dict[str, Any] = {
        "indoor": {"temperature": indoor_temp, "humidity": indoor_humidity, "smoke": indoor_smoke},
        "balcony": {"temperature": balcony_temp, "humidity": balcony_humidity},
        "weather": weather,
    }

    # ── 开窗建议：室内外温差 + 湿度差 ────────────────
    if indoor_temp is not None and balcony_temp is not None:
        temp_diff = round(indoor_temp - balcony_temp, 1)
        analysis["temp_diff"] = temp_diff
        if temp_diff >= TEMP_DIFF_THRESHOLD:
            analysis["window_advice"] = "OPEN"
            analysis["window_reason"] = f"室内比阳台高 {temp_diff}℃，开窗可快速降温"
        elif temp_diff <= -TEMP_DIFF_THRESHOLD:
            analysis["window_advice"] = "CLOSE"
            analysis["window_reason"] = f"阳台比室内高 {abs(temp_diff)}℃，开窗会引入热空气"
        else:
            analysis["window_advice"] = "HOLD"
            analysis["window_reason"] = f"室内外温差仅 {abs(temp_diff)}℃，开窗收益有限"

    if indoor_humidity is not None and balcony_humidity is not None:
        humidity_diff = round(indoor_humidity - balcony_humidity, 1)
        analysis["humidity_diff"] = humidity_diff
        if humidity_diff >= HUMIDITY_DIFF_THRESHOLD:
            analysis["window_advice"] = "OPEN"
            analysis["window_reason"] = (
                f"室内湿度比阳台高 {humidity_diff}%，开窗通风可除湿"
            )

    # ── 晾晒建议：阳台湿度 + 天气 ────────────────────
    if balcony_humidity is not None:
        raining = "雨" in str(weather.get("text", ""))
        can_dry = (
            balcony_humidity <= BALCONY_DRYING_HUMIDITY
            and (balcony_temp is None or balcony_temp >= BALCONY_DRYING_TEMP)
            and not raining
            and weather.get("available", False)
        )
        analysis["drying_advice"] = "DRY" if can_dry else "WAIT"
        if can_dry:
            analysis["drying_reason"] = (
                f"阳台湿度 {balcony_humidity}%、天气{weather.get('text')}，适合晾晒"
            )
        elif not weather.get("available", False):
            analysis["drying_reason"] = "天气数据不可用，暂无法确认是否适合晾晒"
        elif raining:
            analysis["drying_reason"] = f"天气{weather.get('text')}，晾晒会返潮"
        else:
            analysis["drying_reason"] = f"阳台湿度 {balcony_humidity}%，暂不建议晾晒"

    analysis.setdefault("scenario", "NORMAL" if indoor_temp is not None or balcony_temp is not None else "UNKNOWN")
    if "conclusion" not in analysis:
        analysis["conclusion"] = "环境数据不足，无法判断。" if analysis["scenario"] == "UNKNOWN" else "已根据现有环境数据生成建议。"
    return analysis
