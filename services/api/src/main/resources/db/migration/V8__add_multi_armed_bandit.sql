-- V8: Multi-Armed Bandit System Tables
-- Supports contextual bandits with real-time optimization

-- Bandit Arms (recommendation algorithms)
CREATE TABLE bandit_arm (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    algorithm_type VARCHAR(50) NOT NULL, -- 'PERSONALIZED', 'POPULAR', 'DIVERSE', 'RECENT'
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bandit Experiment Configuration
CREATE TABLE bandit_experiment (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    bandit_algorithm VARCHAR(50) NOT NULL, -- 'EPSILON_GREEDY', 'UCB1', 'THOMPSON_SAMPLING'
    epsilon DOUBLE PRECISION DEFAULT 0.1, -- for ε-greedy
    alpha DOUBLE PRECISION DEFAULT 1.0, -- for Beta distribution (Thompson Sampling)
    beta DOUBLE PRECISION DEFAULT 1.0,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bandit Context (user profile, time, category)
CREATE TABLE bandit_context (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(100),
    context_type VARCHAR(50) NOT NULL, -- 'USER_PROFILE', 'TIME_SLOT', 'CATEGORY'
    context_value VARCHAR(200) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bandit State (arm statistics per context)
CREATE TABLE bandit_state (
    id BIGINT PRIMARY KEY,
    experiment_id BIGINT NOT NULL REFERENCES bandit_experiment(id),
    arm_id BIGINT NOT NULL REFERENCES bandit_arm(id),
    context_id BIGINT REFERENCES bandit_context(id),
    pulls INTEGER DEFAULT 0,
    total_reward DOUBLE PRECISION DEFAULT 0.0,
    sum_reward_squared DOUBLE PRECISION DEFAULT 0.0, -- for variance calculation
    last_pull_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(experiment_id, arm_id, context_id)
);

-- Bandit Decision Log
CREATE TABLE bandit_decision (
    id BIGINT PRIMARY KEY,
    experiment_id BIGINT NOT NULL REFERENCES bandit_experiment(id),
    arm_id BIGINT NOT NULL REFERENCES bandit_arm(id),
    context_id BIGINT REFERENCES bandit_context(id),
    user_id VARCHAR(100),
    decision_value DOUBLE PRECISION, -- UCB/Thompson sampling value
    selection_reason VARCHAR(100), -- 'EXPLORATION', 'EXPLOITATION', 'RANDOM'
    news_ids TEXT, -- JSON array of recommended news IDs
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bandit Reward Events
CREATE TABLE bandit_reward (
    id BIGINT PRIMARY KEY,
    decision_id BIGINT NOT NULL REFERENCES bandit_decision(id),
    reward_type VARCHAR(50) NOT NULL, -- 'CLICK', 'DWELL_TIME', 'ENGAGEMENT'
    reward_value DOUBLE PRECISION NOT NULL,
    news_id BIGINT,
    user_id VARCHAR(100),
    collected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_bandit_state_experiment_arm ON bandit_state(experiment_id, arm_id);
CREATE INDEX idx_bandit_state_context ON bandit_state(context_id);
CREATE INDEX idx_bandit_decision_experiment ON bandit_decision(experiment_id);
CREATE INDEX idx_bandit_decision_user_time ON bandit_decision(user_id, created_at);
CREATE INDEX idx_bandit_reward_decision ON bandit_reward(decision_id);
CREATE INDEX idx_bandit_reward_type_time ON bandit_reward(reward_type, collected_at);

-- Insert default bandit arms
INSERT INTO bandit_arm (id, name, description, algorithm_type) VALUES
(1, 'personalized', '개인화 추천 (사용자 선호도 기반)', 'PERSONALIZED'),
(2, 'popular', '인기도 추천 (전체 사용자 기준)', 'POPULAR'),
(3, 'diverse', '다양성 추천 (MMR 알고리즘)', 'DIVERSE'),
(4, 'recent', '최신성 추천 (시간 기준)', 'RECENT');

-- Insert default bandit experiment
INSERT INTO bandit_experiment (id, name, description, bandit_algorithm, epsilon) VALUES
(1, 'news_recommendation_bandit', '뉴스 추천 Multi-Armed Bandit 실험', 'EPSILON_GREEDY', 0.1);