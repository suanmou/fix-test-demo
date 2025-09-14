package com.fix.benchmark.test;

import com.fix.benchmark.config.BenchmarkConfig;
import com.fix.benchmark.engine.FixEngineManager;
import com.fix.benchmark.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoadTester {
    private static final Logger logger = LoggerFactory.getLogger(LoadTester.class);
    
    private final FixEngineManager engineManager;
    private final BenchmarkConfig config;
    private final MetricsCollector metricsCollector;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ExecutorService workerPool;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    public LoadTester(FixEngineManager engineManager, BenchmarkConfig config, MetricsCollector metricsCollector) {
        this.engineManager = engineManager;
        this.config = config;
        this.metricsCollector = metricsCollector;
        this.workerPool = Executors.newFixedThreadPool(config.getThreads());
    }
    
    public void start() {
        if (!engineManager.isConnected()) {
            logger.error("FIX engine not connected, cannot start test");
            return;
        }
        
        running.set(true);
        logger.info("Starting load test with {} threads, {} msg/s for {} seconds", 
                config.getThreads(), config.getMessagesPerSecond(), config.getTestDurationSeconds());
        
        // 预热阶段
        if (config.getWarmupSeconds() > 0) {
            logger.info("Starting warmup for {} seconds...", config.getWarmupSeconds());
            runTest(true);
        }
        
        // 正式测试
        logger.info("Starting actual test...");
        runTest(false);
    }
    
    private void runTest(boolean isWarmup) {
        int duration = isWarmup ? config.getWarmupSeconds() : config.getTestDurationSeconds();
        int messagesPerSecond = config.getMessagesPerSecond();
        int messagesPerThread = Math.max(1, messagesPerSecond / config.getThreads());
        
        long endTime = System.currentTimeMillis() + duration * 1000L;
        
        CountDownLatch latch = new CountDownLatch(config.getThreads());
        
        for (int i = 0; i < config.getThreads(); i++) {
            final int threadId = i;
            workerPool.submit(() -> {
                try {
                    while (running.get() && System.currentTimeMillis() < endTime) {
                        long batchStart = System.currentTimeMillis();
                        
                        // 发送一批消息
                        for (int j = 0; j < messagesPerThread; j++) {
                            if (!running.get()) break;
                            
                            String testReqId = TestRequestGenerator.generateTestReqId();
                            try {
                                engineManager.sendTestRequest(testReqId);
                            } catch (Exception e) {
                                logger.error("Error sending test request", e);
                            }
                        }
                        
                        // 控制发送速率
                        long batchDuration = System.currentTimeMillis() - batchStart;
                        long sleepTime = 1000L - batchDuration;
                        if (sleepTime > 0) {
                            Thread.sleep(sleepTime);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
            if (!isWarmup) {
                logger.info("Test completed");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void stop() {
        running.set(false);
        workerPool.shutdown();
        scheduler.shutdown();
        
        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}