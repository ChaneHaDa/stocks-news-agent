"""F4: Deep Learning Embedding Service with Ko-BERT and RoBERTa support."""

import asyncio
import time
import os
import hashlib
from typing import List, Dict, Any, Optional, Union
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from enum import Enum

import torch
import numpy as np
from transformers import AutoTokenizer, AutoModel, RobertaTokenizer, RobertaModel
from sentence_transformers import SentenceTransformer

from ..core.config import Settings
from ..core.logging import get_logger
from ..core.metrics import MODEL_INFERENCE_DURATION, MODEL_INFERENCE_COUNT

logger = get_logger(__name__)


class ModelType(Enum):
    """Available embedding model types."""
    MOCK = "mock"
    KOBERT = "kobert"
    ROBERTA_KOREAN = "roberta-korean"
    SENTENCE_TRANSFORMER = "sentence-transformer"


@dataclass
class EmbeddingResult:
    """Embedding result container."""
    id: str
    vector: List[float]
    norm: float
    model_type: str
    dimension: int
    processing_time_ms: float


class DeepEmbedService:
    """Advanced embedding service with multiple deep learning models."""
    
    def __init__(self, settings: Settings):
        self.settings = settings
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.models = {}
        self.tokenizers = {}
        self.model_configs = {
            ModelType.KOBERT: {
                "model_name": "skt/kobert-base-v1",
                "dimension": 768,
                "max_length": 512
            },
            ModelType.ROBERTA_KOREAN: {
                "model_name": "klue/roberta-large",
                "dimension": 1024,
                "max_length": 512
            },
            ModelType.SENTENCE_TRANSFORMER: {
                "model_name": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
                "dimension": 384,
                "max_length": 256
            }
        }
        
        # Performance optimization
        self.executor = ThreadPoolExecutor(max_workers=4)
        self.batch_size = 16
        self.last_loaded = {}
        self.embedding_counts = {}
        
        # Load default model
        self.default_model = ModelType.KOBERT
        if settings.enable_embed:
            asyncio.create_task(self._load_default_models())
    
    async def _load_default_models(self):
        """Load default models asynchronously."""
        try:
            await self.load_model(self.default_model)
            logger.info("Default embedding models loaded successfully")
        except Exception as e:
            logger.error("Failed to load default embedding models", exc_info=e)
    
    async def load_model(self, model_type: ModelType) -> bool:
        """Load a specific embedding model."""
        try:
            model_name = self.model_configs[model_type]["model_name"]
            logger.info(f"Loading {model_type.value} model", model_name=model_name)
            
            start_time = time.time()
            
            if model_type == ModelType.KOBERT:
                await self._load_kobert_model()
            elif model_type == ModelType.ROBERTA_KOREAN:
                await self._load_roberta_model()
            elif model_type == ModelType.SENTENCE_TRANSFORMER:
                await self._load_sentence_transformer_model()
            else:
                raise ValueError(f"Unsupported model type: {model_type}")
            
            load_time = time.time() - start_time
            self.last_loaded[model_type] = time.time()
            self.embedding_counts[model_type] = 0
            
            logger.info(f"{model_type.value} model loaded", 
                       load_time=load_time, device=str(self.device))
            return True
            
        except Exception as e:
            logger.error(f"Failed to load {model_type.value} model", exc_info=e)
            return False
    
    async def _load_kobert_model(self):
        """Load Ko-BERT model for Korean financial text."""
        def _load():
            try:
                # Use KoBERT from SKT
                model_name = "skt/kobert-base-v1"
                
                tokenizer = AutoTokenizer.from_pretrained(model_name)
                model = AutoModel.from_pretrained(model_name)
                model.to(self.device)
                model.eval()
                
                self.tokenizers[ModelType.KOBERT] = tokenizer
                self.models[ModelType.KOBERT] = model
                
                logger.info("Ko-BERT model loaded successfully",
                           model_name=model_name,
                           dimension=self.model_configs[ModelType.KOBERT]["dimension"])
                
            except Exception as e:
                logger.error("Ko-BERT model loading failed", exc_info=e)
                raise
        
        # Run in thread pool to avoid blocking
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(self.executor, _load)
    
    async def _load_roberta_model(self):
        """Load Korean RoBERTa model."""
        def _load():
            try:
                model_name = "klue/roberta-large"
                
                tokenizer = AutoTokenizer.from_pretrained(model_name)
                model = AutoModel.from_pretrained(model_name)
                model.to(self.device)
                model.eval()
                
                self.tokenizers[ModelType.ROBERTA_KOREAN] = tokenizer
                self.models[ModelType.ROBERTA_KOREAN] = model
                
                logger.info("Korean RoBERTa model loaded successfully",
                           model_name=model_name,
                           dimension=self.model_configs[ModelType.ROBERTA_KOREAN]["dimension"])
                
            except Exception as e:
                logger.error("Korean RoBERTa model loading failed", exc_info=e)
                raise
        
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(self.executor, _load)
    
    async def _load_sentence_transformer_model(self):
        """Load Sentence Transformer model for comparison."""
        def _load():
            try:
                model_name = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
                
                model = SentenceTransformer(model_name, device=str(self.device))
                self.models[ModelType.SENTENCE_TRANSFORMER] = model
                
                logger.info("Sentence Transformer model loaded successfully",
                           model_name=model_name,
                           dimension=self.model_configs[ModelType.SENTENCE_TRANSFORMER]["dimension"])
                
            except Exception as e:
                logger.error("Sentence Transformer model loading failed", exc_info=e)
                raise
        
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(self.executor, _load)
    
    async def embed_batch(self, text_items: List[Any], 
                         model_type: Optional[ModelType] = None) -> List[EmbeddingResult]:
        """Generate embeddings for a batch of text items using specified model."""
        if model_type is None:
            model_type = self.default_model
        
        if model_type not in self.models:
            await self.load_model(model_type)
        
        if model_type not in self.models:
            raise RuntimeError(f"{model_type.value} model not loaded")
        
        start_time = time.time()
        
        try:
            if model_type == ModelType.SENTENCE_TRANSFORMER:
                results = await self._embed_with_sentence_transformer(text_items, model_type)
            else:
                results = await self._embed_with_transformer(text_items, model_type)
            
            duration = time.time() - start_time
            
            # Update metrics
            MODEL_INFERENCE_DURATION.labels(
                model_type=f"embed_{model_type.value}",
                model_version=self.model_configs[model_type]["model_name"]
            ).observe(duration)
            
            MODEL_INFERENCE_COUNT.labels(
                model_type=f"embed_{model_type.value}",
                model_version=self.model_configs[model_type]["model_name"],
                status="success"
            ).inc(len(text_items))
            
            self.embedding_counts[model_type] += len(text_items)
            
            logger.info(
                "Embeddings generated successfully",
                model_type=model_type.value,
                item_count=len(text_items),
                duration=duration,
                dimension=self.model_configs[model_type]["dimension"],
                device=str(self.device)
            )
            
            return results
            
        except Exception as e:
            MODEL_INFERENCE_COUNT.labels(
                model_type=f"embed_{model_type.value}",
                model_version=self.model_configs[model_type]["model_name"],
                status="error"
            ).inc()
            
            logger.error(f"Embedding generation failed for {model_type.value}", exc_info=e)
            raise
    
    async def _embed_with_transformer(self, text_items: List[Any], 
                                    model_type: ModelType) -> List[EmbeddingResult]:
        """Generate embeddings using BERT/RoBERTa models."""
        def _process_batch():
            model = self.models[model_type]
            tokenizer = self.tokenizers[model_type]
            config = self.model_configs[model_type]
            
            texts = [item.text for item in text_items]
            
            # Tokenize batch
            encoded = tokenizer(
                texts,
                padding=True,
                truncation=True,
                max_length=config["max_length"],
                return_tensors="pt"
            )
            
            # Move to device
            encoded = {k: v.to(self.device) for k, v in encoded.items()}
            
            # Generate embeddings
            with torch.no_grad():
                outputs = model(**encoded)
                # Use CLS token embedding (first token)
                embeddings = outputs.last_hidden_state[:, 0, :].cpu().numpy()
            
            results = []
            for i, item in enumerate(text_items):
                embedding = embeddings[i].tolist()
                norm = float(np.linalg.norm(embedding))
                
                results.append(EmbeddingResult(
                    id=item.id,
                    vector=embedding,
                    norm=norm,
                    model_type=model_type.value,
                    dimension=len(embedding),
                    processing_time_ms=0.0  # Set later
                ))
            
            return results
        
        loop = asyncio.get_event_loop()
        return await loop.run_in_executor(self.executor, _process_batch)
    
    async def _embed_with_sentence_transformer(self, text_items: List[Any], 
                                             model_type: ModelType) -> List[EmbeddingResult]:
        """Generate embeddings using Sentence Transformer."""
        def _process_batch():
            model = self.models[model_type]
            texts = [item.text for item in text_items]
            
            # Generate embeddings
            embeddings = model.encode(texts, convert_to_numpy=True, show_progress_bar=False)
            
            results = []
            for i, item in enumerate(text_items):
                embedding = embeddings[i].tolist()
                norm = float(np.linalg.norm(embedding))
                
                results.append(EmbeddingResult(
                    id=item.id,
                    vector=embedding,
                    norm=norm,
                    model_type=model_type.value,
                    dimension=len(embedding),
                    processing_time_ms=0.0
                ))
            
            return results
        
        loop = asyncio.get_event_loop()
        return await loop.run_in_executor(self.executor, _process_batch)
    
    async def get_model_info(self) -> Dict[str, Any]:
        """Get information about loaded models."""
        info = {
            "loaded_models": list(self.models.keys()),
            "default_model": self.default_model.value,
            "device": str(self.device),
            "cuda_available": torch.cuda.is_available(),
            "model_configs": {k.value: v for k, v in self.model_configs.items()},
            "embedding_counts": {k.value: v for k, v in self.embedding_counts.items()},
            "last_loaded": {k.value: v for k, v in self.last_loaded.items()}
        }
        
        if torch.cuda.is_available():
            info["gpu_info"] = {
                "device_name": torch.cuda.get_device_name(0),
                "memory_allocated": torch.cuda.memory_allocated(0),
                "memory_reserved": torch.cuda.memory_reserved(0)
            }
        
        return info
    
    async def cleanup(self):
        """Cleanup resources."""
        logger.info("Cleaning up DeepEmbedService")
        
        # Clear models and tokenizers
        for model_type in list(self.models.keys()):
            if model_type in self.models:
                del self.models[model_type]
            if model_type in self.tokenizers:
                del self.tokenizers[model_type]
        
        # Clear CUDA cache
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
        
        # Shutdown executor
        self.executor.shutdown(wait=True)
        
        logger.info("DeepEmbedService cleanup completed")


# Global instance
deep_embed_service = None


def get_deep_embed_service(settings: Settings) -> DeepEmbedService:
    """Get or create the deep embedding service instance."""
    global deep_embed_service
    if deep_embed_service is None:
        deep_embed_service = DeepEmbedService(settings)
    return deep_embed_service