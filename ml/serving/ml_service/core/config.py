"""Configuration settings for ML service."""

from functools import lru_cache
from typing import List, Optional

from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Application settings."""
    
    # Service settings
    host: str = Field(default="0.0.0.0", env="ML_HOST")
    port: int = Field(default=8001, env="ML_PORT")
    debug: bool = Field(default=False, env="ML_DEBUG")
    
    # Model settings
    models_dir: str = Field(default="./models", env="ML_MODELS_DIR")
    importance_model_path: str = Field(default="importance.pkl", env="ML_IMPORTANCE_MODEL")
    embed_model_name: str = Field(default="sentence-transformers/distiluse-base-multilingual-cased", env="ML_EMBED_MODEL")
    
    # Cache settings
    redis_url: str = Field(default="redis://localhost:6379/0", env="REDIS_URL")
    cache_ttl_summary: int = Field(default=604800, env="CACHE_TTL_SUMMARY")  # 7 days
    cache_ttl_importance: int = Field(default=300, env="CACHE_TTL_IMPORTANCE")  # 5 minutes
    
    # LLM API settings
    llm_api_url: Optional[str] = Field(default=None, env="LLM_API_URL")
    llm_api_key: Optional[str] = Field(default=None, env="LLM_API_KEY")
    llm_timeout: int = Field(default=30, env="LLM_TIMEOUT")
    llm_max_retries: int = Field(default=3, env="LLM_MAX_RETRIES")
    
    # Feature flags
    enable_importance: bool = Field(default=True, env="ENABLE_IMPORTANCE")
    enable_summarize: bool = Field(default=True, env="ENABLE_SUMMARIZE")
    enable_embed: bool = Field(default=True, env="ENABLE_EMBED")
    
    # Performance settings
    max_batch_size: int = Field(default=100, env="MAX_BATCH_SIZE")
    request_timeout: int = Field(default=60, env="REQUEST_TIMEOUT")
    
    # Logging
    log_level: str = Field(default="INFO", env="LOG_LEVEL")
    log_format: str = Field(default="json", env="LOG_FORMAT")
    
    # Korean text processing
    korean_stopwords: List[str] = Field(
        default=[
            "의", "가", "이", "은", "는", "을", "를", "에", "와", "과", "로", "으로",
            "에서", "부터", "까지", "한", "그", "저", "이런", "그런", "저런"
        ]
    )
    
    # Investment advice compliance
    investment_disclaimer: str = Field(
        default="본 정보는 투자 참고용이며 투자 권유가 아닙니다. 투자 결정은 본인 책임하에 하시기 바랍니다."
    )
    
    class Config:
        env_file = ".env"
        case_sensitive = False


@lru_cache()
def get_settings() -> Settings:
    """Get cached settings instance."""
    return Settings()