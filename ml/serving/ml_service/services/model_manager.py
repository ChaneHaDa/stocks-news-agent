"""Model manager for ML services."""

import time
from typing import Dict, Any, Optional

from ..core.config import Settings
from ..core.logging import get_logger
from .importance_service import ImportanceService
from .summarize_service import SummarizeService
from .embed_service import EmbedService

logger = get_logger(__name__)


class ModelManager:
    """Manages all ML models and services."""
    
    def __init__(self, settings: Settings):
        self.settings = settings
        self.start_time = time.time()
        
        # Initialize services
        self.importance_service = ImportanceService(settings)
        self.summarize_service = SummarizeService(settings)
        self.embed_service = EmbedService(settings)
        
        logger.info("ModelManager initialized")
    
    async def reload_models(self, version: Optional[str] = None) -> Dict[str, Any]:
        """Reload all models."""
        results = {}
        
        # Reload importance model
        if self.settings.enable_importance:
            try:
                await self.importance_service.reload(version)
                results["importance"] = {"status": "success", "version": version}
            except Exception as e:
                logger.error("Failed to reload importance model", exc_info=e)
                results["importance"] = {"status": "error", "error": str(e)}
        
        # Reload summarize service
        if self.settings.enable_summarize:
            try:
                await self.summarize_service.reload(version)
                results["summarize"] = {"status": "success", "version": version}
            except Exception as e:
                logger.error("Failed to reload summarize service", exc_info=e)
                results["summarize"] = {"status": "error", "error": str(e)}
        
        # Reload embed model
        if self.settings.enable_embed:
            try:
                await self.embed_service.reload(version)
                results["embed"] = {"status": "success", "version": version}
            except Exception as e:
                logger.error("Failed to reload embed model", exc_info=e)
                results["embed"] = {"status": "error", "error": str(e)}
        
        return results
    
    async def cleanup(self):
        """Cleanup resources."""
        logger.info("Cleaning up ModelManager")
        
        await self.importance_service.cleanup()
        await self.summarize_service.cleanup()
        await self.embed_service.cleanup()
        
        logger.info("ModelManager cleanup completed")