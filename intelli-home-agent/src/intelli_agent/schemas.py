"""Agent 的输入输出数据结构。

Java 主服务通过 HTTP 把设备事件与当前状态传进来，字段名与 Java 侧
DeviceEvent / DeviceStateService 保持一致。
"""

from typing import Any, Literal
from datetime import date as Date

from pydantic import BaseModel, Field


class RecommendRequest(BaseModel):
    """Java 侧发来的建议请求。"""

    version: int = Field(default=1, ge=1, le=1)
    request_id: str = Field(default="", alias="requestId")
    safety_alerts: list[dict[str, Any]] = Field(default_factory=list, alias="safetyAlerts")
    device_key: str = Field(alias="deviceKey")
    location: str | None = None
    properties: dict[str, Any] = Field(default_factory=dict)
    # key 为 INDOOR / BALCONY，value 为该位置设备的属性表
    states: dict[str, dict[str, Any]] = Field(default_factory=dict)

    model_config = {"populate_by_name": True}


class RecommendResponse(BaseModel):
    """返回给 Java 侧的建议。"""

    recommendation: str
    scenario: str = "UNKNOWN"
    # 关键中间结果一并返回，便于排查"为什么给出这个建议"
    analysis: dict[str, Any] = Field(default_factory=dict)
    degraded: bool = False
    degradation_reason: str | None = Field(default=None, alias="degradationReason")

    model_config = {"populate_by_name": True}


class WeatherInfo(BaseModel):
    """室外天气，作为室内的外部上下文。"""

    text: str = "未知"
    temp: float | None = None
    humidity: float | None = None
    wind_scale: str | None = None
    available: bool = False
    raining: bool | None = None
    observed_at: int | None = None

    @classmethod
    def unavailable(cls) -> "WeatherInfo":
        """天气接口失败时的占位值 —— 明确标记不可用，而不是编造数据。"""
        return cls(text="天气数据不可用", available=False)


class PropertyStatistics(BaseModel):
    min: float
    max: float
    avg: float
    last: float
    count: int = Field(default=0, ge=0)
    model_config = {"allow_inf_nan": False}


class DailyHistory(BaseModel):
    device_key: str = Field(alias="deviceKey", max_length=128)
    location: str
    from_time: int = Field(alias="from")
    to: int
    sample_count: int = Field(alias="sampleCount", ge=0)
    truncated: bool = False
    covered_from: int | None = Field(default=None, alias="coveredFrom")
    covered_to: int | None = Field(default=None, alias="coveredTo")
    properties: dict[str, PropertyStatistics] = Field(default_factory=dict)
    model_config = {"populate_by_name": True}


class DailyAlertSummary(BaseModel):
    total: int = Field(default=0, ge=0)
    by_level: dict[str, int] = Field(default_factory=dict, alias="byLevel")
    items: list[dict[str, Any]] = Field(default_factory=list, max_length=100)
    truncated: bool = False
    model_config = {"populate_by_name": True}


class DailyAdviceRequest(BaseModel):
    version: Literal[1] = 1
    request_id: str = Field(alias="requestId", min_length=1, max_length=64)
    date: Date
    window_hours: int = Field(default=24, alias="windowHours", ge=1, le=720)
    states: dict[str, dict[str, Any]] = Field(default_factory=dict)
    history: list[DailyHistory] = Field(default_factory=list, max_length=100)
    alert_summary: DailyAlertSummary = Field(default_factory=DailyAlertSummary, alias="alertSummary")
    coverage: dict[str, Any] = Field(default_factory=dict)
    model_config = {"populate_by_name": True}


class DailyAdviceResponse(BaseModel):
    advice: str
    analysis: dict[str, Any] = Field(default_factory=dict)
    degraded: bool = False
    degradation_reason: str | None = Field(default=None, alias="degradationReason")
    model_config = {"populate_by_name": True}
