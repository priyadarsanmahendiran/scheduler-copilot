package com.scheduler.model;

public class MachineFailureRequest {
    private String machineId;

    public MachineFailureRequest() {}

    public MachineFailureRequest(String machineId) {
        this.machineId = machineId;
    }

    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }
}
