package com.fix.benchmark.utils;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class EnhancedTestReqIdGenerator {
    private static final AtomicLong sequence = new AtomicLong(0);
    private static final String PREFIX = "BENCH";
    
    /**
     * 生成全局唯一的TestReqID，格式: BENCH-[timestamp]-[sessionId]-[sequence]
     */
    public static String generate(String sessionId) {
        long timestamp = Instant.now().toEpochMilli();
        long seq = sequence.incrementAndGet();
        return String.format("%s-%d-%s-%06d", PREFIX, timestamp, sessionId, seq);
    }
    
    /**
     * 解析TestReqID获取会话ID
     */
    public static String extractSessionId(String testReqId) {
        if (testReqId == null || !testReqId.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = testReqId.split("-");
        return parts.length >= 3 ? parts[2] : null;
    }
}