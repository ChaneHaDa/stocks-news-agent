package com.newsagent.api.dto;

public class HealthResponse {
    private boolean ok;

    public HealthResponse() {}

    public HealthResponse(boolean ok) {
        this.ok = ok;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }
}