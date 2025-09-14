package com.fix.benchmark.test;

import com.fix.benchmark.engine.EnhancedMultiSessionApplication;
import com.fix.benchmark.engine.MultiSessionEngineManager;
import com.fix.benchmark.metrics.PreciseRequestTracker;
import com.fix.benchmark.metrics.PreciseRequestTracker.StatsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class EnhancedLoadTester {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedLoadTester.class);
    
    private final MultiSessionEngineManager engineManager;
    private final PreciseRequestTracker requestTracker;
    private final long timeoutMillis;
    
    private final ExecutorService testExecutor;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private final PreciseRateLimiter rateLimiter;
    
    public EnhancedLoadTester(MultiSessionEngineManager engineManager, PreciseRequestTracker requestTracker, 
                            int timeoutMillis, double messagesPerSecond) {
        this.engineManager = engineManager;
        this.requestTracker = requestTracker;
        this.timeoutMillis = timeoutMillis;
        this.rateLimiter = new PreciseRateLimiter(messagesPerSecond);
        
        this.testExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }
    
    public void startTest(int durationSeconds) {
        logger.info("Starting enhanced test with timeout={}ms, rate={}msg/s", timeoutMillis, rateLimiter.permitsPerSecond);
        
        running.set(true);
        
        // 启动超时检查
        scheduler.scheduleAtFixedRate(this::checkTimeouts, 1, 1, TimeUnit.SECONDS);
        
        // 启动实时报告
        scheduler.scheduleAtFixedRate(this::reportStatus, 5, 5, TimeUnit.SECONDS);
        
        // 启动测试
        testExecutor.submit(() -> runTest(durationSeconds));
    }
    
    private void runTest(int durationSeconds) {
        long endTime = System.currentTimeMillis() + durationSeconds * 1000L;
        
        while (running.get() && System.currentTimeMillis() < endTime) {
            // 控制发送速率
            if (rateLimiter.tryAcquire()) {
                sendTestRequest();
            }
            
            // 短暂休眠避免CPU占用过高
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // 测试结束，等待响应
        logger.info("Test phase completed, waiting for responses...");
        waitForRemainingResponses();
        
        // 最终报告
        generateFinalReport();
    }
    
    private void sendTestRequest() {
        // 随机选择一个活跃会话
        String sessionId = selectActiveSession();
        if (sessionId == null) {
            return;
        }
        
        EnhancedMultiSessionApplication app = engineManager.getApplication(sessionId);
        if (app != null && app.isConnected()) {
            app.sendTestRequest();
        }
    }
    
    private String selectActiveSession() {
        // 简化实现，实际应该从engineManager获取活跃会话
        return "BENCHMARK_CLIENT_0001"; // 示例
    }
    
    private void checkTimeouts() {
        requestTracker.checkTimeouts(System.nanoTime());
    }
    
    private void reportStatus() {
        StatsSnapshot stats = requestTracker.getStats();
        logger.info("Live Stats: {}", stats);
    }
    
    private void waitForRemainingResponses() {
        long waitStart = System.currentTimeMillis();
        long maxWait = timeoutMillis;
        
        while (requestTracker.getPendingCount() > 0 && 
               System.currentTimeMillis() - waitStart < maxWait) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // 最后检查一次超时
        requestTracker.checkTimeouts(System.nanoTime());
    }
    
    private void generateFinalReport() {
        StatsSnapshot stats = requestTracker.getStats();
        
        logger.info("=== FINAL PERFORMANCE REPORT ===");
        logger.info("Total Requests: {}", stats.totalRequests);
        logger.info("Successful Responses: {}", stats.totalResponses);
        logger.info("Timeouts: {}", stats.totalTimeouts);
        logger.info("");
        logger.info("Response Rate: {}%", String.format("%.2f", stats.responseRate));
        logger.info("Timeout Rate: {}%", String.format("%.2f", stats.timeoutRate));
        logger.info("");
        logger.info("Latency Statistics:");
        logger.info("  Min: {} ms", String.format("%.2f", stats.minLatencyNanos / 1_000_000.0));
        logger.info("  Max: {} ms", String.format("%.2f", stats.maxLatencyNanos / 1_000_000.0));
        logger.info("  Avg: {} ms", String.format("%.2f", stats.avgLatencyNanos / 1_000_000.0));
        logger.info("  P95: {} ms", String.format("%.2f", stats.p95LatencyNanos / 1_000_000.0));
        logger.info("  P99: {} ms", String.format("%.2f", stats.p99LatencyNanos / 1_000_000.0));
    }
    
    public void stop() {
        running.set(false);
        testExecutor.shutdown();
        scheduler.shutdown();
        
        try {
            if (!testExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                testExecutor.shutdownNow();
            }
            if (!scheduler.awaitTermination(5, Time TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            testExecutor.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}