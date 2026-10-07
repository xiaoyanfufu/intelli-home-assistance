"""策略分析单测 —— 不依赖 LLM 和网络，验证确定性结论是否算对。"""

from intelli_agent.strategy import analyze


def test_python_does_not_reclassify_safety_thresholds():
    analysis = analyze(
        {"INDOOR": {"temperature": 46.2, "humidity": 30, "smoke": 320}},
        {"text": "晴", "available": True},
    )
    # Java is the only safety authority; graph receives its explicit safetyAlerts.
    assert analysis["scenario"] != "FIRE"


def test_window_advice_opens_when_indoor_hotter():
    analysis = analyze(
        {
            "INDOOR": {"temperature": 30.0, "humidity": 50},
            "BALCONY": {"temperature": 20.0, "humidity": 50},
        },
        {"text": "晴", "available": True},
    )
    assert analysis["window_advice"] == "OPEN"
    assert analysis["temp_diff"] == 10.0


def test_drying_advice_waits_when_raining():
    analysis = analyze(
        {
            "INDOOR": {"temperature": 24.0, "humidity": 50},
            "BALCONY": {"temperature": 25.0, "humidity": 40},
        },
        {"text": "中雨", "available": True},
    )
    assert analysis["drying_advice"] == "WAIT"
    assert "雨" in analysis["drying_reason"]


def test_drying_advice_dries_when_dry_and_clear():
    analysis = analyze(
        {
            "INDOOR": {"temperature": 24.0, "humidity": 50},
            "BALCONY": {"temperature": 25.0, "humidity": 40},
        },
        {"text": "晴", "available": True},
    )
    assert analysis["drying_advice"] == "DRY"


def test_missing_data_does_not_crash():
    analysis = analyze({}, {})
    assert analysis["scenario"] == "UNKNOWN"
