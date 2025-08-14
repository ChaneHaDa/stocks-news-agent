-- V5: F0 Analytics and Experiment Infrastructure
-- Anonymous user identification, experiment management, and behavioral logging

-- Anonymous User table for session-based identification
CREATE TABLE anonymous_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    anon_id VARCHAR(36) NOT NULL UNIQUE,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    session_count INTEGER DEFAULT 1,
    user_agent VARCHAR(500),
    ip_address VARCHAR(45),
    country_code VARCHAR(2) DEFAULT 'KR',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_anon_user_anon_id ON anonymous_user(anon_id);
CREATE INDEX idx_anon_user_created ON anonymous_user(created_at);
CREATE INDEX idx_anon_user_active ON anonymous_user(is_active);

-- Experiment configuration for A/B testing
CREATE TABLE experiment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    variants VARCHAR(500) NOT NULL, -- JSON array: ["control", "treatment"]
    traffic_allocation VARCHAR(500) NOT NULL, -- JSON object: {"control": 50, "treatment": 50}
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN DEFAULT FALSE,
    auto_stop_enabled BOOLEAN DEFAULT TRUE,
    auto_stop_threshold DOUBLE PRECISION DEFAULT -0.05,
    minimum_sample_size INTEGER DEFAULT 5000,
    created_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_experiment_key ON experiment(experiment_key);
CREATE INDEX idx_experiment_active ON experiment(is_active);
CREATE INDEX idx_experiment_dates ON experiment(start_date, end_date);

-- Impression logging for tracking news views
CREATE TABLE impression_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    anon_id VARCHAR(36) NOT NULL,
    news_id BIGINT NOT NULL,
    session_id VARCHAR(100),
    position INTEGER NOT NULL, -- Position in ranked list (1-based)
    page_type VARCHAR(50) DEFAULT 'top',
    experiment_key VARCHAR(100),
    variant VARCHAR(50),
    importance_score DOUBLE PRECISION,
    rank_score DOUBLE PRECISION,
    personalized BOOLEAN DEFAULT FALSE,
    diversity_applied BOOLEAN DEFAULT TRUE,
    user_agent VARCHAR(500),
    ip_address VARCHAR(45),
    referer VARCHAR(500),
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    date_partition VARCHAR(10) NOT NULL -- YYYY-MM-DD for partitioning
);

CREATE INDEX idx_impression_anon_id ON impression_log(anon_id);
CREATE INDEX idx_impression_news_id ON impression_log(news_id);
CREATE INDEX idx_impression_timestamp ON impression_log(timestamp);
CREATE INDEX idx_impression_experiment ON impression_log(experiment_key, variant);
CREATE INDEX idx_impression_date_anon ON impression_log(date_partition, anon_id);

-- Feature Flag system for runtime configuration
CREATE TABLE feature_flag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flag_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    category VARCHAR(50) DEFAULT 'general',
    value_type VARCHAR(20) DEFAULT 'boolean',
    flag_value VARCHAR(500) NOT NULL,
    default_value VARCHAR(500),
    is_enabled BOOLEAN DEFAULT TRUE,
    environment VARCHAR(20) DEFAULT 'all',
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_feature_flag_key ON feature_flag(flag_key);
CREATE INDEX idx_feature_flag_enabled ON feature_flag(is_enabled);
CREATE INDEX idx_feature_flag_category ON feature_flag(category);

-- Enhance existing click_log table with F0 fields
ALTER TABLE click_log ADD COLUMN anon_id VARCHAR(36);
ALTER TABLE click_log ADD COLUMN experiment_key VARCHAR(100);
ALTER TABLE click_log ADD COLUMN variant VARCHAR(50);
ALTER TABLE click_log ADD COLUMN dwell_time_ms BIGINT;
ALTER TABLE click_log ADD COLUMN click_source VARCHAR(50) DEFAULT 'news_list';
ALTER TABLE click_log ADD COLUMN personalized BOOLEAN DEFAULT FALSE;
ALTER TABLE click_log ADD COLUMN referer VARCHAR(500);
ALTER TABLE click_log ADD COLUMN date_partition VARCHAR(10);

-- Update existing click_log records with date_partition (H2 compatible)
UPDATE click_log SET date_partition = FORMATDATETIME(clicked_at, 'yyyy-MM-dd') WHERE date_partition IS NULL;

-- Make date_partition NOT NULL after setting values (skip anon_id for now - will be set by application)
ALTER TABLE click_log ALTER COLUMN date_partition SET NOT NULL;

-- Add new indexes for click_log
CREATE INDEX idx_click_log_anon_id ON click_log(anon_id);
CREATE INDEX idx_click_experiment ON click_log(experiment_key, variant);
CREATE INDEX idx_click_date_anon ON click_log(date_partition, anon_id);

-- Insert default feature flags for F0
INSERT INTO feature_flag (flag_key, name, description, category, value_type, flag_value, default_value, created_by) VALUES
('experiment.rank_ab.enabled', 'EXPERIMENT RANK AB ENABLED', 'Enable A/B testing for ranking algorithm', 'experiment', 'boolean', 'false', 'false', 'system'),
('feature.personalization.enabled', 'FEATURE PERSONALIZATION ENABLED', 'Enable personalization features', 'feature', 'boolean', 'true', 'true', 'system'),
('feature.diversity_filter.enabled', 'FEATURE DIVERSITY FILTER ENABLED', 'Enable MMR diversity filtering', 'feature', 'boolean', 'true', 'true', 'system'),
('analytics.impression_logging.enabled', 'ANALYTICS IMPRESSION LOGGING ENABLED', 'Enable impression logging', 'analytics', 'boolean', 'true', 'true', 'system'),
('analytics.click_logging.enabled', 'ANALYTICS CLICK LOGGING ENABLED', 'Enable click logging', 'analytics', 'boolean', 'true', 'true', 'system'),
('config.mmr_lambda', 'CONFIG MMR LAMBDA', 'MMR lambda parameter for diversity', 'config', 'double', '0.7', '0.7', 'system');