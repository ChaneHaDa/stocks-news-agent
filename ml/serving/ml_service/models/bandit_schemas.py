"""
Multi-Armed Bandit API Schemas for F5 Implementation
"""
from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field
from enum import Enum


class BanditAlgorithm(str, Enum):
    EPSILON_GREEDY = "EPSILON_GREEDY"
    UCB1 = "UCB1"
    THOMPSON_SAMPLING = "THOMPSON_SAMPLING"


class ArmType(str, Enum):
    PERSONALIZED = "PERSONALIZED"
    POPULAR = "POPULAR"
    DIVERSE = "DIVERSE"
    RECENT = "RECENT"


class RewardType(str, Enum):
    CLICK = "CLICK"
    DWELL_TIME = "DWELL_TIME"
    ENGAGEMENT = "ENGAGEMENT"


class SelectionReason(str, Enum):
    EXPLORATION = "EXPLORATION"
    EXPLOITATION = "EXPLOITATION"
    RANDOM = "RANDOM"


class BanditContext(BaseModel):
    user_id: Optional[str] = None
    time_slot: Optional[int] = Field(None, description="Hour of day (0-23)")
    category: Optional[str] = None
    user_preferences: Optional[Dict[str, Any]] = None


class ArmState(BaseModel):
    arm_id: int
    arm_name: str
    arm_type: ArmType
    pulls: int = 0
    total_reward: float = 0.0
    sum_reward_squared: float = 0.0
    
    @property
    def average_reward(self) -> float:
        return self.total_reward / self.pulls if self.pulls > 0 else 0.0
    
    @property
    def variance(self) -> float:
        if self.pulls <= 1:
            return 0.0
        mean = self.average_reward
        return (self.sum_reward_squared / self.pulls) - (mean * mean)


class BanditDecisionRequest(BaseModel):
    experiment_id: int = 1
    context: BanditContext
    available_arms: Optional[List[int]] = None
    algorithm: BanditAlgorithm = BanditAlgorithm.EPSILON_GREEDY
    epsilon: float = Field(0.1, ge=0.0, le=1.0)
    alpha: float = Field(1.0, gt=0.0)  # Beta distribution parameter
    beta: float = Field(1.0, gt=0.0)   # Beta distribution parameter


class BanditDecisionResponse(BaseModel):
    selected_arm_id: int
    selected_arm_name: str
    selected_arm_type: ArmType
    decision_value: float
    selection_reason: SelectionReason
    algorithm_used: BanditAlgorithm
    confidence: float = Field(0.0, ge=0.0, le=1.0)
    exploration_rate: float = Field(0.0, ge=0.0, le=1.0)


class RewardEvent(BaseModel):
    decision_id: int
    reward_type: RewardType
    reward_value: float = Field(ge=0.0)
    news_id: Optional[int] = None
    user_id: Optional[str] = None


class BanditStateRequest(BaseModel):
    experiment_id: int = 1
    context: Optional[BanditContext] = None


class BanditStateResponse(BaseModel):
    experiment_id: int
    algorithm: BanditAlgorithm
    total_pulls: int
    total_rewards: float
    arms: List[ArmState]
    context_hash: Optional[str] = None


class BanditPerformanceMetrics(BaseModel):
    experiment_id: int
    time_window_hours: int = 24
    total_decisions: int
    total_rewards: float
    average_reward: float
    regret_estimate: float
    exploration_rate: float
    best_arm_id: int
    best_arm_confidence: float
    arm_performance: Dict[int, Dict[str, float]]


class BanditOptimizationRequest(BaseModel):
    experiment_id: int = 1
    optimization_target: str = "average_reward"  # "average_reward", "minimize_regret"
    time_window_hours: int = 24
    min_pulls_per_arm: int = 10