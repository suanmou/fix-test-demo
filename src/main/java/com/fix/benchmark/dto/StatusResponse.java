package com.fix.benchmark.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class StatusResponse {
    private boolean success;
    private TaskStatus data;
    private String error;
    
    public StatusResponse(boolean success, TaskStatus data, String error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }
}

@Data
public class ReportResponse {
    private boolean success;
    private BenchmarkReport data;
    private String error;
    
    public ReportResponse(boolean success, BenchmarkReport data, String error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }
}

@Data
public class TaskListResponse {
    private Map<String, TaskStatus> tasks;
    
    public TaskListResponse(Map<String, TaskStatus> tasks) {
        this.tasks = tasks;
    }
}

@Data
public class TaskStatus {
    private String taskId;
    private String status; // PENDING, RUNNING, COMPLETED, FAILED, STOPPED
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int activeSessions;
    private int totalSessions;
    private long messagesSent;
    private long messagesReceived;
    private long pendingRequests;
    private double connectionSuccessRate;
}

@Data
public class BenchmarkReport {
    private String taskId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private TestSummary summary;
    private LatencyStats latency;
    private ConnectionStats connections;
    private SessionDetails sessionDetails;
}

@Data
public class TestSummary {
    private long totalRequests;
    private long successfulResponses;
    private long timeouts;
    private double responseRate;
    private double timeoutRate;
}

@Data
public class LatencyStats {
    private double minMs;
    private double maxMs;
    private double avgMs;
    private double p95Ms;
    private double p99Ms;
}

@Data
public class ConnectionStats {
    private int totalConnections;
    private int successfulConnections;
    private int failedConnections;
    private double successRate;
}

@Data
public class SessionDetails {
    private Map<String, SessionReport> sessions;
}

@Data
public class SessionReport {
    private String sessionId;
    private boolean connected;
    private long messagesSent;
    private long messagesReceived;
    private double responseRate;
    private double avgLatencyMs;
}