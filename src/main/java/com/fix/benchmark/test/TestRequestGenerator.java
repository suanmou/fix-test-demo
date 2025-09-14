package com.fix.benchmark.test;

import java.util.concurrent.atomic.AtomicLong;

public class TestRequestGenerator {
    private static final AtomicLong counter = new AtomicLong(0);
    
    public static String generateTestReqId() {
        return "BENCH-" + System.currentTimeMillis() + "-" + counter.incrementAndGet();
    }
}