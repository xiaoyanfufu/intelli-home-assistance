from fastapi.testclient import TestClient
from intelli_agent.main import app
from intelli_agent.schemas import WeatherInfo


def test_weather_preserves_observation_time_and_unknown_probability(monkeypatch):
    async def fetch(self):
        return WeatherInfo(available=True, raining=True, observed_at=1700000000000)
    monkeypatch.setattr("intelli_agent.main.WeatherService.fetch", fetch)
    response = TestClient(app).get("/agent/weather")
    assert response.status_code == 200
    body = response.json()
    assert body["observedAt"] == 1700000000000
    assert body["validUntil"] == 1700000600000
    assert body["raining"] is True
    assert body["rainProbability"] is None


def test_missing_observation_is_unavailable(monkeypatch):
    async def fetch(self):
        return WeatherInfo(available=True, raining=False)
    monkeypatch.setattr("intelli_agent.main.WeatherService.fetch", fetch)
    body = TestClient(app).get("/agent/weather").json()
    assert body["available"] is False
    assert body["reason"] == "WEATHER_UNAVAILABLE"
    assert body["validUntil"] == body["observedAt"]
