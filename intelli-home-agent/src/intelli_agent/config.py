"""配置管理：从环境变量 / .env 读取，不把密钥写进代码。"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Agent 服务的运行时配置。"""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ── LLM ────────────────────────────────
    llm_base_url: str = "https://api.openai.com/v1"
    llm_api_key: str = ""
    llm_model: str = "gpt-4o-mini"
    llm_temperature: float = 0.3

    # ── 天气 ───────────────────────────────
    weather_api_key: str = ""
    weather_api_host: str = "https://devapi.qweather.com"
    weather_location: str = "101280601"

    # ── 服务 ───────────────────────────────
    server_host: str = "0.0.0.0"
    server_port: int = 8000

    @property
    def llm_configured(self) -> bool:
        """LLM 是否已配置。未配置时降级为纯规则建议，便于本地无密钥演示。"""
        return bool(self.llm_api_key)

    @property
    def weather_configured(self) -> bool:
        return bool(self.weather_api_key)


@lru_cache
def get_settings() -> Settings:
    return Settings()
