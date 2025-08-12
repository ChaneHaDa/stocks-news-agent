package com.newsagent.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ImportanceReason {
    @JsonProperty("source_weight")
    private double sourceWeight;
    
    @JsonProperty("tickers_hit")
    private double tickersHit;
    
    @JsonProperty("keywords_hit") 
    private double keywordsHit;
    
    private double freshness;

    public ImportanceReason() {}

    public ImportanceReason(double sourceWeight, double tickersHit, double keywordsHit, double freshness) {
        this.sourceWeight = sourceWeight;
        this.tickersHit = tickersHit;
        this.keywordsHit = keywordsHit;
        this.freshness = freshness;
    }

    public double getSourceWeight() {
        return sourceWeight;
    }

    public void setSourceWeight(double sourceWeight) {
        this.sourceWeight = sourceWeight;
    }

    public double getTickersHit() {
        return tickersHit;
    }

    public void setTickersHit(double tickersHit) {
        this.tickersHit = tickersHit;
    }

    public double getKeywordsHit() {
        return keywordsHit;
    }

    public void setKeywordsHit(double keywordsHit) {
        this.keywordsHit = keywordsHit;
    }

    public double getFreshness() {
        return freshness;
    }

    public void setFreshness(double freshness) {
        this.freshness = freshness;
    }
}