"""
Multi-Armed Bandit Service for F5 Implementation
Implements ε-greedy, UCB1, and Thompson Sampling algorithms
"""
import math
import random
import hashlib
from typing import List, Dict, Optional
from dataclasses import dataclass
import numpy as np
from scipy.stats import beta

from ..models.bandit_schemas import (
    BanditAlgorithm, ArmType, SelectionReason, ArmState, 
    BanditContext, BanditDecisionRequest, BanditDecisionResponse
)


@dataclass
class BanditArm:
    id: int
    name: str
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
    
    @property
    def standard_deviation(self) -> float:
        return math.sqrt(self.variance)


class BanditService:
    """Multi-Armed Bandit decision engine with contextual support"""
    
    def __init__(self):
        # Default arms configuration
        self.default_arms = [
            BanditArm(1, "personalized", ArmType.PERSONALIZED),
            BanditArm(2, "popular", ArmType.POPULAR),
            BanditArm(3, "diverse", ArmType.DIVERSE),
            BanditArm(4, "recent", ArmType.RECENT)
        ]
        
        # Context-based arm states (context_hash -> arm_id -> arm_state)
        self.contextual_states: Dict[str, Dict[int, BanditArm]] = {}
        
        # Global arm states (fallback when no context)
        self.global_states: Dict[int, BanditArm] = {
            arm.id: arm for arm in self.default_arms.copy()
        }
    
    def _get_context_hash(self, context: Optional[BanditContext]) -> str:
        """Generate hash for contextual bandit state"""
        if not context:
            return "global"
        
        context_str = f"{context.user_id or 'anonymous'}_{context.time_slot or 'any'}_{context.category or 'all'}"
        return hashlib.md5(context_str.encode()).hexdigest()[:12]
    
    def _get_arm_states(self, context: Optional[BanditContext]) -> Dict[int, BanditArm]:
        """Get arm states for given context"""
        context_hash = self._get_context_hash(context)
        
        if context_hash not in self.contextual_states:
            # Initialize context with copy of default arms
            self.contextual_states[context_hash] = {
                arm.id: BanditArm(arm.id, arm.name, arm.arm_type)
                for arm in self.default_arms
            }
        
        return self.contextual_states[context_hash]
    
    def make_decision(self, request: BanditDecisionRequest) -> BanditDecisionResponse:
        """Make bandit decision based on algorithm and context"""
        arm_states = self._get_arm_states(request.context)
        available_arms = request.available_arms or list(arm_states.keys())
        
        if request.algorithm == BanditAlgorithm.EPSILON_GREEDY:
            return self._epsilon_greedy_decision(arm_states, available_arms, request.epsilon)
        elif request.algorithm == BanditAlgorithm.UCB1:
            return self._ucb1_decision(arm_states, available_arms)
        elif request.algorithm == BanditAlgorithm.THOMPSON_SAMPLING:
            return self._thompson_sampling_decision(arm_states, available_arms, request.alpha, request.beta)
        else:
            raise ValueError(f"Unsupported algorithm: {request.algorithm}")
    
    def _epsilon_greedy_decision(self, arm_states: Dict[int, BanditArm], 
                               available_arms: List[int], epsilon: float) -> BanditDecisionResponse:
        """ε-greedy algorithm implementation"""
        total_pulls = sum(arm_states[arm_id].pulls for arm_id in available_arms)
        
        # Exploration vs Exploitation
        if random.random() < epsilon or total_pulls == 0:
            # Exploration: random selection
            selected_arm_id = random.choice(available_arms)
            reason = SelectionReason.EXPLORATION
            decision_value = epsilon
        else:
            # Exploitation: select best performing arm
            best_arm_id = max(available_arms, 
                            key=lambda arm_id: arm_states[arm_id].average_reward)
            selected_arm_id = best_arm_id
            reason = SelectionReason.EXPLOITATION
            decision_value = arm_states[selected_arm_id].average_reward
        
        selected_arm = arm_states[selected_arm_id]
        confidence = 1.0 - epsilon if reason == SelectionReason.EXPLOITATION else epsilon
        
        return BanditDecisionResponse(
            selected_arm_id=selected_arm_id,
            selected_arm_name=selected_arm.name,
            selected_arm_type=selected_arm.arm_type,
            decision_value=decision_value,
            selection_reason=reason,
            algorithm_used=BanditAlgorithm.EPSILON_GREEDY,
            confidence=confidence,
            exploration_rate=epsilon
        )
    
    def _ucb1_decision(self, arm_states: Dict[int, BanditArm], 
                      available_arms: List[int]) -> BanditDecisionResponse:
        """Upper Confidence Bound (UCB1) algorithm implementation"""
        total_pulls = sum(arm_states[arm_id].pulls for arm_id in available_arms)
        
        if total_pulls == 0:
            # No pulls yet, select randomly
            selected_arm_id = random.choice(available_arms)
            reason = SelectionReason.EXPLORATION
            decision_value = float('inf')
        else:
            # Calculate UCB values for each arm
            ucb_values = {}
            for arm_id in available_arms:
                arm = arm_states[arm_id]
                if arm.pulls == 0:
                    ucb_values[arm_id] = float('inf')
                else:
                    confidence_width = math.sqrt(2 * math.log(total_pulls) / arm.pulls)
                    ucb_values[arm_id] = arm.average_reward + confidence_width
            
            # Select arm with highest UCB value
            selected_arm_id = max(ucb_values.keys(), key=lambda k: ucb_values[k])
            decision_value = ucb_values[selected_arm_id]
            
            # Determine if this is exploration or exploitation
            selected_arm = arm_states[selected_arm_id]
            best_avg_reward = max(arm_states[arm_id].average_reward for arm_id in available_arms)
            reason = (SelectionReason.EXPLORATION 
                     if selected_arm.average_reward < best_avg_reward 
                     else SelectionReason.EXPLOITATION)
        
        selected_arm = arm_states[selected_arm_id]
        confidence = min(decision_value / (1.0 + decision_value), 1.0) if decision_value != float('inf') else 0.0
        
        return BanditDecisionResponse(
            selected_arm_id=selected_arm_id,
            selected_arm_name=selected_arm.name,
            selected_arm_type=selected_arm.arm_type,
            decision_value=decision_value,
            selection_reason=reason,
            algorithm_used=BanditAlgorithm.UCB1,
            confidence=confidence,
            exploration_rate=1.0 / math.sqrt(total_pulls) if total_pulls > 0 else 1.0
        )
    
    def _thompson_sampling_decision(self, arm_states: Dict[int, BanditArm], 
                                  available_arms: List[int], 
                                  alpha: float, beta_param: float) -> BanditDecisionResponse:
        """Thompson Sampling (Bayesian) algorithm implementation"""
        samples = {}
        
        for arm_id in available_arms:
            arm = arm_states[arm_id]
            # Beta distribution parameters
            # Success = total_reward, Failure = pulls - total_reward (simplified)
            successes = max(0, arm.total_reward) + alpha
            failures = max(0, arm.pulls - arm.total_reward) + beta_param
            
            # Sample from Beta distribution
            samples[arm_id] = np.random.beta(successes, failures)
        
        # Select arm with highest sample
        selected_arm_id = max(samples.keys(), key=lambda k: samples[k])
        decision_value = samples[selected_arm_id]
        
        # Determine exploration vs exploitation based on uncertainty
        selected_arm = arm_states[selected_arm_id]
        uncertainty = selected_arm.standard_deviation / (selected_arm.average_reward + 1e-6)
        reason = (SelectionReason.EXPLORATION if uncertainty > 0.1 
                 else SelectionReason.EXPLOITATION)
        
        confidence = decision_value
        exploration_rate = np.mean([arm_states[arm_id].standard_deviation 
                                  for arm_id in available_arms]) / len(available_arms)
        
        return BanditDecisionResponse(
            selected_arm_id=selected_arm_id,
            selected_arm_name=selected_arm.name,
            selected_arm_type=selected_arm.arm_type,
            decision_value=decision_value,
            selection_reason=reason,
            algorithm_used=BanditAlgorithm.THOMPSON_SAMPLING,
            confidence=confidence,
            exploration_rate=exploration_rate
        )
    
    def update_reward(self, context: Optional[BanditContext], arm_id: int, reward: float):
        """Update arm statistics with observed reward"""
        arm_states = self._get_arm_states(context)
        
        if arm_id in arm_states:
            arm = arm_states[arm_id]
            arm.pulls += 1
            arm.total_reward += reward
            arm.sum_reward_squared += reward * reward
    
    def get_arm_states(self, context: Optional[BanditContext]) -> List[ArmState]:
        """Get current arm states for given context"""
        arm_states = self._get_arm_states(context)
        
        return [
            ArmState(
                arm_id=arm.id,
                arm_name=arm.name,
                arm_type=arm.arm_type,
                pulls=arm.pulls,
                total_reward=arm.total_reward,
                sum_reward_squared=arm.sum_reward_squared
            )
            for arm in arm_states.values()
        ]
    
    def reset_experiment(self, context: Optional[BanditContext] = None):
        """Reset bandit states (for testing or new experiments)"""
        if context:
            context_hash = self._get_context_hash(context)
            if context_hash in self.contextual_states:
                del self.contextual_states[context_hash]
        else:
            # Reset all states
            self.contextual_states.clear()
            self.global_states = {
                arm.id: BanditArm(arm.id, arm.name, arm.arm_type)
                for arm in self.default_arms
            }