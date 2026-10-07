import asyncio
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from intelli_agent.config import get_settings
from intelli_agent.daily import build_daily_graph
from intelli_agent.main import app
from intelli_agent.schemas import WeatherInfo


class Weather:
    async def fetch(self):
        return WeatherInfo(text="晴", temp=22, humidity=50, available=True)


class MissingWeather:
    async def fetch(self):
        return WeatherInfo.unavailable()


class FailedLlm:
    async def ainvoke(self, messages):
        raise TimeoutError()


class Llm:
    async def ainvoke(self, messages):
        return SimpleNamespace(content="请结合当前环境安排通风。")


@pytest.fixture(autouse=True)
def no_credentials(monkeypatch):
    monkeypatch.setenv("LLM_API_KEY", "")
    monkeypatch.setenv("WEATHER_API_KEY", "")
    get_settings.cache_clear()
    yield
    get_settings.cache_clear()


def request():
    return {"version": 1, "requestId": "daily-advice:2026-10-08", "date": "2026-10-08",
            "windowHours": 24, "states": {"INDOOR": {"temperature": 26.5, "humidity": 48}},
            "history": [{"deviceKey": "indoor", "location": "INDOOR", "from": 1, "to": 100,
                         "sampleCount": 2, "truncated": False, "properties": {
                             "temperature": {"min": 24, "max": 29, "avg": 26.5, "last": 29, "count": 2}}}],
            "alertSummary": {"total": 1, "byLevel": {"WARN": 1}, "items": [
                {"title": "设备离线", "level": "WARN", "content": "历史离线记录", "occurredAt": 30}]}}


def invoke(payload, weather=None, llm=None):
    return asyncio.run(build_daily_graph(weather or Weather(), llm).ainvoke({"request": payload}))


def test_template_contains_real_data_and_four_sections():
    result = invoke(request())
    assert result["degraded"] is True
    assert result["degradation_reason"] == "LLM_NOT_CONFIGURED"
    for text in ("26.5", "最小 24", "设备离线", "当前状态", "历史回顾", "告警回顾", "生活建议", "晴"):
        assert text in result["advice"]
    assert result["analysis"]["indoor"]["temperature"] == 26.5


def test_llm_failure_and_missing_weather_use_template():
    result = invoke(request(), MissingWeather(), FailedLlm())
    assert result["degraded"] is True
    assert result["degradation_reason"] == "LLM_UNAVAILABLE"
    assert "天气数据不可用" in result["advice"]


def test_missing_data_is_explicit():
    payload = request()
    payload.update(states={}, history=[], alertSummary={"total": 0})
    result = invoke(payload, MissingWeather())
    assert "当前设备数据缺失" in result["advice"]
    assert "历史数据缺失" in result["advice"]
    assert "不等于确认当前环境安全" in result["advice"]


def test_llm_cannot_omit_truncation_warnings():
    payload = request()
    payload["history"][0]["truncated"] = True
    payload["alertSummary"]["truncated"] = True
    payload["coverage"] = {"devicesTruncated": True}
    result = invoke(payload, llm=Llm())
    assert result["degraded"] is False
    for text in ("样本已截断", "部分设备", "100 条上限", "请结合当前环境"):
        assert text in result["advice"]


def test_blank_llm_response_falls_back():
    class Empty:
        async def ainvoke(self, messages):
            return SimpleNamespace(content="  ")
    result = invoke(request(), llm=Empty())
    assert result["degradation_reason"] == "LLM_UNAVAILABLE"


def test_api_contract_and_version_validation():
    with TestClient(app) as client:
        response = client.post("/agent/daily-advice", json=request())
        assert response.status_code == 200
        assert response.json()["degradationReason"] == "LLM_NOT_CONFIGURED"
        for field, value in (("version", 2), ("date", "invalid"), ("windowHours", 721)):
            payload = request()
            payload[field] = value
            assert client.post("/agent/daily-advice", json=payload).status_code == 422
