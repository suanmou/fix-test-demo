package com.fix.benchmark.engine;

import com.fix.benchmark.config.MultiSessionConfig;
import com.fix.benchmark.metrics.MultiSessionMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiSessionEngineManager {
    private static final Logger logger = LoggerFactory.getLogger(MultiSessionEngineManager.class);
    
    private final MultiSessionConfig config;
    private final MultiSessionMetrics metrics;
    private final ExecutorService connectionPool;
    private final ConcurrentHashMap<String, SessionInstance> sessions = new ConcurrentHashMap<>();
    
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger failedConnections = new AtomicInteger(0);
    
    public MultiSessionEngineManager(MultiSessionConfig config, MultiSessionMetrics metrics) {
        this.config = config;
        this.metrics = metrics;
        this.connectionPool = Executors.newFixedThreadPool(50);
    }
    
    public CompletableFuture<Boolean> createSession(String baseSenderCompId, String targetCompId, int sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sessionConfigPath = config.generateSessionConfig(baseSenderCompId, targetCompId, sessionId);
                String senderCompId = baseSenderCompId + "_" + String.format("%04d", sessionId);
                
                SessionSettings settings = new SessionSettings(sessionConfigPath);
                MultiSessionApplication application = new MultiSessionApplication(senderCompId, metrics);
                
                MessageStoreFactory storeFactory = new FileStoreFactory(settings);
                LogFactory logFactory = new ScreenLogFactory(false, false, false);
                MessageFactory messageFactory = new quickfix.fix44.MessageFactory();
                
                SocketInitiator initiator = new SocketInitiator(
                    application, storeFactory, settings, logFactory, messageFactory);
                
                SessionInstance instance = new SessionInstance(senderCompId, initiator, application);
                sessions.put(senderCompId, instance);
                
                // 启动连接
                initiator.start();
                
                // 等待连接建立
                boolean connected = waitForConnection(instance, 30);
                
                if (connected) {
                    activeConnections.incrementAndGet();
                    metrics.recordConnectionSuccess(senderCompId);
                    logger.info("Session {} connected successfully", senderCompId);
                } else {
                    failedConnections.incrementAndGet();
                    metrics.recordConnectionFailure(senderCompId);
                    logger.warn("Session {} failed to connect", senderCompId);
                }
                
                totalConnections.incrementAndGet();
                return connected;
                
            } catch (Exception e) {
                failedConnections.incrementAndGet();
                logger.error("Error creating session {}_{}", baseSenderCompId, sessionId, e);
                return false;
            }
        }, connectionPool);
    }
    
    private boolean waitForConnection(SessionInstance instance, int timeoutSeconds) {
        long endTime = System.currentTimeMillis() + timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() < endTime) {
            if (instance.isConnected()) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return false;
    }
    
    public void createMultipleSessions(String baseSenderCompId, String targetCompId, int count) {
        logger.info("Creating {} concurrent sessions...", count);
        
        CompletableFuture<Boolean>[] futures = new CompletableFuture[count];
        for (int i = 0; i < count; i++) {
            futures[i] = createSession(baseSenderCompId, targetCompId, i + 1);
        }
        
        // 等待所有连接完成
        CompletableFuture.allOf(futures).join();
        
        logger.info("Connection summary: {} successful, {} failed, {} total",
                activeConnections.get(), failedConnections.get(), totalConnections.get());
    }
    
    public SessionInstance getSession(String sessionId) {
        return sessions.get(sessionId);
    }
    
    public int getActiveConnectionCount() {
        return activeConnections.get();
    }
    
    public double getConnectionSuccessRate() {
        if (totalConnections.get() == 0) return 0.0;
        return (double) activeConnections.get() / totalConnections.get() * 100.0;
    }
    
    public void shutdown() {
        logger.info("Shutting down all sessions...");
        
        sessions.values().forEach(instance -> {
            try {
                instance.getInitiator().stop();
            } catch (Exception e) {
                logger.error("Error stopping session {}", instance.getSessionId(), e);
            }
        });
        
        connectionPool.shutdown();
        try {
            if (!connectionPool.awaitTermination(10, TimeUnit.SECONDS)) {
                connectionPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            connectionPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    public static class SessionInstance {
        private final String sessionId;
        private final SocketInitiator initiator;
        private final MultiSessionApplication application;
        
        public SessionInstance(String sessionId, SocketInitiator initiator, MultiSessionApplication application) {
            this.sessionId = sessionId;
            this.initiator = initiator;
            this.application = application;
        }
        
        public boolean isConnected() {
            return application.isConnected();
        }
        
        public boolean sendTestRequest(String testReqId) {
            return application.sendTestRequest(testReqId);
        }
        
        public String getSessionId() { return sessionId; }
        public SocketInitiator getInitiator() { return initiator; }
        public MultiSessionApplication getApplication() { return application; }
    }
}