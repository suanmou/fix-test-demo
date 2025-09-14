package com.fix.benchmark.test;

import com.fix.benchmark.config.MultiSessionConfig;
import com.fix.benchmark.engine.MultiSessionEngineManager;
import com.fix.benchmark.engine.MultiSessionEngineManager.SessionInstance;
import com.fix.benchmark.metrics.MultiSessionMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MultiSessionLoadTester {
    private static final Logger logger = LoggerFactory.getLogger(MultiSessionLoadTester.class);
    
    private final MultiSessionEngineManager engineManager;
    private final MultiSessionConfig config;
    private final MultiSessionMetrics metrics;
    
    private final ExecutorService testExecutor;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private final List<String> activeSessions = new CopyOnWriteArrayList<>();
    private final Random random = new Random();
    
    public MultiSessionLoadTester(MultiSessionEngineManager engineManager, MultiSessionConfig config, MultiSessionMetrics metrics) {
        this.engineManager = engineManager;
        this.config = config;
        this.metrics = metrics;
        this.testExecutor = Executors.newCachedThreadPool();
    }
    
    public void startMultiSessionTest(String baseSenderCompId, String targetCompId, int sessionCount, int totalMessagesPerSecond) {
        logger.info("Starting multi-session test with {} sessions, {} msg/s total", sessionCount, totalMessagesPerSecond);
        
        // 创建多个会话
        engineManager.createMultipleSessions(baseSenderCompId, targetCompId, sessionCount);
        
        // 等待所有会话建立
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        // 收集活跃会话
        activeSessions.clear();
        activeSessions.addAll(getActiveSessions());
        
        if (activeSessions.isEmpty()) {
            logger.error("No active sessions available for testing");
            return;
        }
        
        logger.info("Active sessions for testing: {}", activeSessions.size());
        
        running.set(true);
        
        // 计算每个会话的消息速率
        int messagesPerSession = totalMessagesPerSecond / activeSessions.size();
        if (messagesPerSession == 0) messagesPerSession = 1;
        
        // 启动测试
        for (String sessionId : activeSessions) {
            SessionInstance instance = engineManager.getSession(sessionId);
            if (instance != null && instance.isConnected()) {
                testExecutor.submit(() -> runSessionTest(instance, sessionId, messagesPerSession));
            }
        }
        
        // 定期报告状态
        scheduler.scheduleAtFixedRate(this::reportStatus, 10, 10, TimeUnit.SECONDS);
    }
    
    private void runSessionTest(SessionInstance instance, String sessionId, int messagesPerSecond) {
        logger.info("Starting test for session {} at {} msg/s", sessionId, messagesPerSecond);
        
        long testDuration = 60; // 60秒测试
        long endTime = System.currentTimeMillis() + testDuration * 1000L;
        
        while (running.get() && System.currentTimeMillis() < endTime && instance.isConnected()) {
            long batchStart = System.currentTimeMillis();
            
            // 发送一批消息
            for (int i = 0; i < messagesPerSecond; i++) {
                if (!running.get() || !instance.isConnected()) break;
                
                String testReqId = generateTestReqId(sessionId);
                instance.sendTestRequest(testReqId);
            }
            
            // 控制发送速率
            long batchDuration = System.currentTimeMillis() - batchStart;
            long sleepTime = 1000 - batchDuration;
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        logger.info("Test completed for session {}", sessionId);
    }
    
    private List<String> getActiveSessions() {
        List<String> active = new ArrayList<>();
        // 这里应该返回实际连接的会话列表
        // 简化实现，实际应该从engineManager获取
        return active;
    }
    
    private String generateTestReqId(String sessionId) {
        return "BENCH-" + sessionId + "-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
    
    private void reportStatus() {
        logger.info("=== Live Status Report ===");
        logger.info("Active Sessions: {}", engineManager.getActiveConnectionCount());
        logger.info("Connection Success Rate: {}%", String.format("%.2f", engineManager.getConnectionSuccessRate()));
        logger.info("Global Response Rate: {}%", String.format("%.2f", metrics.getGlobalResponseRate()));
        logger.info("Global Average Latency: {} ms", String.format("%.2f", metrics.getGlobalAverageLatencyMs()));
    }
    
    public void stop() {
        running.set(false);
        testExecutor.shutdown();
        scheduler.shutdown();
        
        try {
            if (!testExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                testExecutor.shutdownNow();
            }
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            testExecutor.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        metrics.shutdown();
    }
}