"""天气服务 —— 为 Agent 提供室外上下文。

设计要点：天气是**可选**的外部依赖，失败必须降级而不是让整条建议链路崩掉。
所以这里任何异常都返回不可用占位值，由策略层与 LLM 层自己判断要不要用。
"""

from __future__ import annotations

import httpx
from datetime import datetime

from .config import get_settings
from .schemas import WeatherInfo


class WeatherService:
    """和风天气（QWeather）客户端。换其他服务商只需改本类。"""

    def __init__(self, timeout: float = 5.0) -> None:
        self._timeout = timeout
        self._settings = get_settings()

    async def fetch(self) -> WeatherInfo:
        """获取当前天气；未配置密钥或请求失败时返回不可用占位值。"""
        if not self._settings.weather_configured:
            return WeatherInfo.unavailable()

        url = f"{self._settings.weather_api_host}/v7/weather/now"
        params = {
            "location": self._settings.weather_location,
            "key": self._settings.weather_api_key,
        }

        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                response = await client.get(url, params=params)
                response.raise_for_status()
                payload = response.json()
        except Exception:
            # 天气接口失败不影响建议生成，只是少了一个上下文
            return WeatherInfo.unavailable()

        if payload.get("code") != "200":
            return WeatherInfo.unavailable()

        now = payload.get("now", {})
        observed_at = None
        try:
            observed_at = int(datetime.fromisoformat(now["obsTime"]).timestamp() * 1000)
        except (KeyError, TypeError, ValueError):
            pass
        icon = str(now.get("icon", ""))
        raining = None
        if icon.isdigit():
            code = int(icon)
            if 300 <= code <= 399:
                raining = True
            elif 100 <= code <= 299 or 500 <= code <= 599:
                raining = False
        return WeatherInfo(
            text=now.get("text", "未知"),
            temp=_to_float(now.get("temp")),
            humidity=_to_float(now.get("humidity")),
            wind_scale=now.get("windScale"),
            available=True,
            raining=raining,
            observed_at=observed_at,
        )


def _to_float(value: object) -> float | None:
    """和风接口把数值也返回成字符串，统一转 float。"""
    if value is None:
        return None
    try:
        return float(value)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return None
