import asyncio

from fastapi.testclient import TestClient

from intelli_agent.graph import build_graph
from intelli_agent.main import app
from intelli_agent.schemas import WeatherInfo


class ForbiddenWeather:
    async def fetch(self):
        raise AssertionError("Safety branch must skip weather")


class ForbiddenLlm:
    async def ainvoke(self, messages):
        raise AssertionError("Safety branch must skip LLM")


class UnavailableWeather:
    async def fetch(self):
        return WeatherInfo.unavailable()


class FailedLlm:
    async def ainvoke(self, messages):
        raise TimeoutError("test failure")


def test_safety_branch_skips_both_external_calls():
    graph = build_graph(ForbiddenWeather(), ForbiddenLlm())
    result = asyncio.run(graph.ainvoke({"device_key": "indoor", "safety_alerts": [
        {"title": "火灾隐患", "content": "请确认现场安全情况"}
    ]}))
    assert result["scenario"] == "FIRE"
    assert result["recommendation"] == "请确认现场安全情况"
    assert result["degraded"] is False


def test_llm_failure_preserves_weather_missing_reason():
    graph = build_graph(UnavailableWeather(), FailedLlm())
    result = asyncio.run(graph.ainvoke({"device_key": "balcony", "location": "BALCONY",
                                      "properties": {"temperature": 25, "humidity": 40}}))
    assert result["degraded"] is True
    assert result["degradation_reason"] == "LLM_UNAVAILABLE"
    assert "天气数据不可用" in result["recommendation"]


def test_no_key_api_matches_java_contract(monkeypatch):
    monkeypatch.setenv("LLM_API_KEY", "")
    monkeypatch.setenv("WEATHER_API_KEY", "")
    from intelli_agent.config import get_settings
    get_settings.cache_clear()
    try:
        with TestClient(app) as client:
            response = client.post("/agent/recommend", json={"version": 1, "requestId": "req-1",
                "deviceKey": "indoor", "location": "INDOOR", "properties": {},
                "states": {"INDOOR": {"temperature": 30, "humidity": 50},
                           "BALCONY": {"temperature": 20, "humidity": 50}}, "safetyAlerts": []})
            assert response.status_code == 200
            body = response.json()
            assert body["degraded"] is True
            assert body["degradationReason"] == "LLM_NOT_CONFIGURED"
            assert "recommendation" in body and "analysis" in body
            assert client.post("/agent/recommend", json={"version": 2, "deviceKey": "indoor"}).status_code == 422
    finally:
        get_settings.cache_clear()
