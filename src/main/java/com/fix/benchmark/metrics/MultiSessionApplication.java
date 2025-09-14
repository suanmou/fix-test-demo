package com.fix.benchmark.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class MultiSessionMetrics {
    private static final Logger logger = LoggerFactory.getLogger(MultiSessionMetrics.class);
    
    // 会话级别指标
    private final ConcurrentHashMap<String, SessionMetrics> sessionMetrics = new ConcurrentHashMap<>();
    
    // 全局指标
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong successfulConnections = new AtomicLong(0);
    private final AtomicLong failedConnections = new AtomicLong(0);
    
    private final LongAdder totalMessagesSent = new LongAdder();
    private final LongAdder totalMessagesReceived = new LongAdder();
    private final LongAdder totalRequestsSent = new LongAdder();
    private final LongAdder totalResponsesReceived = new LongAdder();
    
    private final Timer globalResponseTimer;
    
    public MultiSessionMetrics() {
        this.globalResponseTimer = Timer.builder("fix.global.response.time")
                .description("Global FIX response time across all sessions")
                .register(Metrics.globalRegistry);
        
        // 注册全局指标
        Gauge.builder("fix.connections.total", totalConnections, AtomicLong::get)
                .register(Metrics.globalRegistry);
        Gauge.builder("fix.connections.successful", successfulConnections, AtomicLong::get)
                .register(Metrics.globalRegistry);
        Gauge.builder("fix.connections.failed", failedConnections, AtomicLong::get)
                .register(Metrics.globalRegistry);
    }
    
    public void recordConnectionSuccess(String sessionId) {
        successfulConnections.incrementAndGet();
        totalConnections.incrementAndGet();
        getSessionMetrics(sessionId).recordConnectionSuccess();
    }
    
    public void recordConnectionFailure(String sessionId) {
        failedConnections.incrementAndGet();
        totalConnections.incrementAndGet();
        getSessionMetrics(sessionId).recordConnectionFailure();
    }
    
    public void recordSessionLogin(String sessionId) {
        getSessionMetrics(sessionId).recordLogin();
    }
    
    public void recordSessionLogout(String sessionId) {
        getSessionMetrics(sessionId).recordLogout();
    }
    
    public void recordMessageSent(String sessionId) {
        totalMessagesSent.increment();
        getSessionMetrics(sessionId).recordMessageSent();
    }
    
    public void recordResponse(String sessionId, long latencyNanos) {
        globalResponseTimer.record(latencyNanos, TimeUnit.NANOSECONDS);
        getSessionMetrics(sessionId).recordResponse(latencyNanos);
    }
    
    public void recordRequestSent(String sessionId) {
        totalRequestsSent.increment();
        getSessionMetrics(sessionId).recordRequestSent();
    }
    
    public void recordResponseReceived(String sessionId) {
        totalResponsesReceived.increment();
        getSessionMetrics(sessionId).recordResponseReceived();
    }
    
    public void recordSendFailure(String sessionId, String reason) {
        getSessionMetrics(sessionId).recordSendFailure(reason);
    }
    
    public void recordResponseReceived(String sessionId) {
        totalResponsesReceived.increment();
        getSessionMetrics(sessionId).recordResponseReceived();
    }
    
    private SessionMetrics getSessionMetrics(String sessionId) {
        return sessionMetrics.computeIfAbsent(sessionId, k -> new SessionMetrics(k));
    }
    
    public void logSummary() {
        logger.info("=== Multi-Session Performance Summary ===");
        logger.info("Total Connections: {}", totalConnections.get());
        logger.info("Successful Connections: {} ({}%)", 
                successfulConnections.get(), 
                getConnectionSuccessRate());
        logger.info("Failed Connections: {}", failedConnections.get());
        
        logger.info("Total Messages Sent: {}", totalMessagesSent.sum());
        logger.info("Total Messages Received: {}", totalMessagesReceived.sum());
        logger.info("Total Requests Sent: {}", totalRequestsSent.sum());
        logger.info("Total Responses Received: {}", totalResponsesReceived.sum());
        
        logger.info("Global Response Rate: {}%", getGlobalResponseRate());
        logger.info("Global Average Latency: {} ms", getGlobalAverageLatencyMs());
        
        // 会话详细统计
        sessionMetrics.forEach((sessionId, metrics) -> {
            logger.info("Session {}: Connected={}, Messages={}, Responses={}, AvgLatency={}ms, SuccessRate={}%",
                    sessionId,
                    metrics.isConnected(),
                    metrics.getMessagesSent(),
                    metrics.getResponsesReceived(),
                    String.format("%.2f", metrics.getAverageLatencyMs()),
                    String.format("%.2f", metrics.getResponseRate()));
        });
    }
    
    public double getConnectionSuccessRate() {
        if (totalConnections.get() == 0) return 0.0;
        return (double) successfulConnections.get() / totalConnections.get() * 100.0;
    }
    
    public double getGlobalResponseRate() {
        long requests = totalRequestsSent.sum();
        long responses = totalResponsesReceived.sum();
        if (requests == 0) return 0.0;
        return (double) responses / requests * 100.0;
    }
    
    public double getGlobalAverageLatencyMs() {
        return globalResponseTimer.mean(TimeUnit.MILLISECONDS);
    }
    
    public void shutdown() {
        logSummary();
        Metrics.globalRegistry.close();
    }
    
    // 内部会话指标类
    private static class SessionMetrics {
        private final String sessionId;
        private final AtomicLong connectionSuccess = new AtomicLong(0);
        private final AtomicLong connectionFailure = new AtomicLong(0);
        private final AtomicLong loginCount = new AtomicLong(0);
        private final AtomicLong logoutCount = new AtomicLong(0);
        private final AtomicLong messagesSent = new AtomicLong(0);
        private final AtomicLong requestsSent = new AtomicLong(0);
        private final AtomicLong responsesReceived = new AtomicLong(0);
        private final LongAdder totalLatency = new LongAdder();
        
        private final Timer sessionResponseTimer;
        
        public SessionMetrics(String sessionId) {
            this.sessionId = sessionId;
            this.sessionResponseTimer = Timer.builder("fix.session." + sessionId + ".response.time")
                    .description("Response time for session " + sessionId)
                    .register(Metrics.globalRegistry);
        }
        
        public void recordConnectionSuccess() {
            connectionSuccess.incrementAndGet();
        }
        
        public void recordConnectionFailure() {
            connectionFailure.incrementAndGet();
        }
        
        public void recordLogin() {
            loginCount.incrementAndGet();
        }
        
        public void recordLogout() {
            logoutCount.incrementAndGet();
        }
        
        public void recordMessageSent() {
            messagesSent.incrementAndGet();
        }
        
        public void recordRequestSent() {
            requestsSent.incrementAndGet();
        }
        
        public void recordResponseReceived() {
            responsesReceived.incrementAndGet();
        }
        
        public void recordResponse(long latencyNanos) {
            totalLatency.add(latencyNanos);
            sessionResponseTimer.record(latencyNanos, TimeUnit.NANOSECONDS);
        }
        
        public void recordSendFailure(String reason) {
            // 可以记录失败原因
        }
        
        public boolean isConnected() {
            return loginCount.get() > logoutCount.get();
        }
        
        public long getMessagesSent() {
            return messagesSent.get();
        }
        
        public long getResponsesReceived() {
            return responsesReceived.get();
        }
        
        public double getAverageLatencyMs() {
            long responses = responsesReceived.get();
            if (responses == 0) return 0.0;
            return totalLatency.sum() / (double) responses / 1_000_000.0;
        }
        
        public double getResponseRate() {
            long requests = requestsSent.get();
            long responses = responsesReceived.get();
            if (requests == 0) return 0.0;
            return (double) responses / requests * 100.0;
        }
    }
}