package com.fix.benchmark.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class PreciseRequestTracker {
    private static final Logger logger = LoggerFactory.getLogger(PreciseRequestTracker.class);
    
    // 请求跟踪映射
    private final ConcurrentHashMap<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    
    // 统计信息
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalResponses = new AtomicLong(0);
    private final AtomicLong totalTimeouts = new AtomicLong(0);
    private final LongAdder totalLatencyNanos = new LongAdder();
    
    // 延迟分布统计
    private final ConcurrentSkipListMap<Long, AtomicLong> latencyDistribution = new ConcurrentSkipListMap<>();
    
    // 超时配置
    private final long timeoutMillis;
    
    // 性能边界
    private volatile long minLatencyNanos = Long.MAX_VALUE;
    private volatile long maxLatencyNanos = Long.MIN_VALUE;
    
    public PreciseRequestTracker(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }
    
    /**
     * 记录发送的请求
     */
    public void recordRequest(String testReqId, long sendTimeNanos) {
        PendingRequest request = new PendingRequest(testReqId, sendTimeNanos);
        pendingRequests.put(testReqId, request);
        totalRequests.incrementAndGet();
    }
    
    /**
     * 记录响应到达
     */
    public void recordResponse(String testReqId, long receiveTimeNanos) {
        PendingRequest request = pendingRequests.remove(testReqId);
        if (request != null) {
            long latency = receiveTimeNanos - request.sendTimeNanos;
            totalResponses.incrementAndGet();
            totalLatencyNanos.add(latency);
            
            // 更新边界值
            updateLatencyBounds(latency);
            
            // 记录延迟分布
            recordLatencyDistribution(latency);
            
            request.markCompleted(latency);
        }
    }
    
    /**
     * 检查并处理超时请求
     */
    public void checkTimeouts(long currentTimeNanos) {
        long timeoutNanos = timeoutMillis * 1_000_000L;
        
        pendingRequests.entrySet().removeIf(entry -> {
            PendingRequest request = entry.getValue();
            if (currentTimeNanos - request.sendTimeNanos > timeoutNanos) {
                totalTimeouts.incrementAndGet();
                request.markTimeout();
                logger.debug("Request {} timed out after {} ms", 
                        request.testReqId, timeoutMillis);
                return true;
            }
            return false;
        });
    }
    
    /**
     * 获取统计摘要
     */
    public StatsSnapshot getStats() {
        long total = totalRequests.get();
        long responses = totalResponses.get();
        long timeouts = totalTimeouts.get();
        
        double avgLatency = total > 0 ? totalLatencyNanos.sum() / (double) responses : 0.0;
        
        return new StatsSnapshot(
                total,
                responses,
                timeouts,
                minLatencyNanos,
                maxLatencyNanos,
                avgLatency,
                calculatePercentile(0.95),
                calculatePercentile(0.99),
                total > 0 ? (responses * 100.0 / total) : 0.0,
                total > 0 ? (timeouts * 100.0 / total) : 0.0
        );
    }
    
    private void updateLatencyBounds(long latency) {
        minLatencyNanos = Math.min(minLatencyNanos, latency);
        maxLatencyNanos = Math.max(maxLatencyNanos, latency);
    }
    
    private void recordLatencyDistribution(long latency) {
        // 将延迟按微秒分组
        long bucket = latency / 1000L; // 转换为微秒
        latencyDistribution.computeIfAbsent(bucket, k -> new AtomicLong()).incrementAndGet();
    }
    
    private long calculatePercentile(double percentile) {
        if (latencyDistribution.isEmpty()) return 0;
        
        long totalSamples = latencyDistribution.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
        
        long targetCount = (long) (totalSamples * percentile);
        long cumulative = 0;
        
        for (Map.Entry<Long, AtomicLong> entry : latencyDistribution.entrySet()) {
            cumulative += entry.getValue().get();
            if (cumulative >= targetCount) {
                return entry.getKey() * 1000L; // 转换回纳秒
            }
        }
        
        return latencyDistribution.lastKey() * 1000L;
    }
    
    public int getPendingCount() {
        return pendingRequests.size();
    }
    
    public void reset() {
        pendingRequests.clear();
        latencyDistribution.clear();
        totalRequests.set(0);
        totalResponses.set(0);
        totalTimeouts.set(0);
        totalLatencyNanos.reset();
        minLatencyNanos = Long.MAX_VALUE;
        maxLatencyNanos = Long.MIN_VALUE;
    }
    
    // 内部类
    private static class PendingRequest {
        final String testReqId;
        final long sendTimeNanos;
        volatile long responseTimeNanos = -1;
        volatile boolean completed = false;
        volatile boolean timeout = false;
        
        PendingRequest(String testReqId, long sendTimeNanos) {
            this.testReqId = testReqId;
            this.sendTimeNanos = sendTimeNanos;
        }
        
        void markCompleted(long latencyNanos) {
            this.responseTimeNanos = latencyNanos;
            this.completed = true;
        }
        
        void markTimeout() {
            this.timeout = true;
        }
    }
    
    public static class StatsSnapshot {
        public final long totalRequests;
        public final long totalResponses;
        public final long totalTimeouts;
        public final long minLatencyNanos;
        public final long maxLatencyNanos;
        public final double avgLatencyNanos;
        public final long p95LatencyNanos;
        public final long p99LatencyNanos;
        public final double responseRate;
        public final double timeoutRate;
        
        public StatsSnapshot(long totalRequests, long totalResponses, long totalTimeouts,
                           long minLatencyNanos, long maxLatencyNanos, double avgLatencyNanos,
                           long p95LatencyNanos, long p99LatencyNanos,
                           double responseRate, double timeoutRate) {
            this.totalRequests = totalRequests;
            this.totalResponses = totalResponses;
            this.totalTimeouts = totalTimeouts;
            this.minLatencyNanos = minLatencyNanos;
            this.maxLatencyNanos = maxLatencyNanos;
            this.avgLatencyNanos = avgLatencyNanos;
            this.p95LatencyNanos = p95LatencyNanos;
            this.p99LatencyNanos = p99LatencyNanos;
            this.responseRate = responseRate;
            this.timeoutRate = timeoutRate;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Stats{total=%d, responses=%d, timeouts=%d, min=%.2fms, max=%.2fms, avg=%.2fms, p95=%.2fms, p99=%.2fms, responseRate=%.2f%%, timeoutRate=%.2f%%}",
                totalRequests, totalResponses, totalTimeouts,
                minLatencyNanos / 1_000_000.0,
                maxLatencyNanos / 1_000_000.0,
                avgLatencyNanos / 1_000_000.0,
                p95LatencyNanos / 1_000_000.0,
                p99LatencyNanos / 1_000_000.0,
                responseRate, timeoutRate
            );
        }
    }
}