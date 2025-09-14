package com.fix.benchmark.dto;

import lombok.Data;

@Data
public class StartRequest {
    private String baseSenderCompId = "BENCHMARK_CLIENT";
    private String targetCompId = "FIX_SERVER";
    private int sessionCount = 10;
    private int messagesPerSecond = 1000;
    private int durationSeconds = 60;
    private int timeoutMillis = 5000;
    private int warmupSeconds = 10;
}

@Data
public class StartResponse {
    private boolean success;
    private String taskId;
    private String message;
    
    public StartResponse(boolean success, String taskId, String message) {
        this.success = success;
        this.taskId = taskId;
        this.message = message;
    }
}

@Data
public class StopResponse {
    private boolean stopped;
    private String message;
    
    public StopResponse(boolean stopped, String message) {
        this.stopped = stopped;
        this.message = message;
    }
}

@Data
public class HealthResponse {
    private boolean healthy;
    private String status;
    
    public HealthResponse(boolean healthy, String status) {
        this.healthy = healthy;
        this.status = status;
    }
}