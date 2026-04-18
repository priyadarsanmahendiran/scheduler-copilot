package com.scheduler.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Machine {
    private String id;
    private String status;
    private long lastHeartbeat;
    private boolean heartbeatBlocked;

    public Machine() {}

    public Machine(String id, String status, long lastHeartbeat) {
        this.id = id;
        this.status = status;
        this.lastHeartbeat = lastHeartbeat;
        this.heartbeatBlocked = false;
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
}
