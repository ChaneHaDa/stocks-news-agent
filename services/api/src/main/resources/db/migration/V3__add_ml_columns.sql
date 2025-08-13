-- Add ML service integration columns to news_score table

ALTER TABLE news_score ADD COLUMN importance_p REAL;
ALTER TABLE news_score ADD COLUMN model_version VARCHAR(50);
ALTER TABLE news_score ADD COLUMN summary VARCHAR(1000);

-- Add indexes for new columns
CREATE INDEX idx_news_score_importance_p ON news_score(importance_p DESC);
CREATE INDEX idx_news_score_model_version ON news_score(model_version);

-- Update the view to include new columns
DROP VIEW IF EXISTS news_with_score;
CREATE VIEW news_with_score AS 
SELECT 
    n.id, n.source, n.url, n.published_at, n.title, n.body, n.dedup_key, n.lang, n.created_at,
    ns.importance, ns.reason_json, ns.rank_score, ns.importance_p, ns.model_version, ns.summary,
    ns.updated_at as score_updated_at
FROM news n
LEFT JOIN news_score ns ON n.id = ns.news_id;