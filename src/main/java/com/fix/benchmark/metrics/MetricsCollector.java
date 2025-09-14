package com.fix.benchmark.metrics;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.TimeUnit;

public class MetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);
    
    private final Timer responseTimer;
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final LongAdder totalLatency = new LongAdder();
    
    public MetricsCollector() {
        this.responseTimer = Timer.builder("fix.response.time")
                .description("Time taken for FIX response")
                .register(Metrics.globalRegistry);
    }
    
    public void recordMessageSent() {
        messagesSent.incrementAndGet();
    }
    
    public void recordResponse(long latencyNanos) {
        messagesReceived.incrementAndGet();
        totalLatency.add(latencyNanos);
        responseTimer.record(latencyNanos, TimeUnit.NANOSECONDS);
    }
    
    public void logSummary() {
        long sent = messagesSent.get();
        long received = messagesReceived.get();
        double avgLatencyMs = totalLatency.sum() / (double) received / 1_000_000.0;
        
        logger.info("=== Performance Summary ===");
        logger.info("Messages Sent: {}", sent);
        logger.info("Messages Received: {}", received);
        logger.info("Average Latency: {} ms", String.format("%.3f", avgLatencyMs));
        logger.info("Success Rate: {}%", String.format("%.2f", (received * 100.0 / sent)));
    }
    
    public void shutdown() {
        logSummary();
        Metrics.globalRegistry.close();
    }
}