package com.scheduler.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Machine {
    private String id;
    private String status;
    private long lastHeartbeat;
    private boolean heartbeatBlocked;
    private boolean degraded;
    private String riskLevel;   // "HIGH" | "MEDIUM" | null
    private String riskReason;

    public Machine() {}

    public Machine(String id, String status, long lastHeartbeat) {
        this.id = id;
        this.status = status;
        this.lastHeartbeat = lastHeartbeat;
        this.heartbeatBlocked = false;
        this.degraded = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    @JsonIgnore
    public boolean isHeartbeatBlocked() { return heartbeatBlocked; }
    public void setHeartbeatBlocked(boolean heartbeatBlocked) { this.heartbeatBlocked = heartbeatBlocked; }

    @JsonIgnore
    public boolean isDegraded() { return degraded; }
    public void setDegraded(boolean degraded) { this.degraded = degraded; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getRiskReason() { return riskReason; }
    public void setRiskReason(String riskReason) { this.riskReason = riskReason; }
}
