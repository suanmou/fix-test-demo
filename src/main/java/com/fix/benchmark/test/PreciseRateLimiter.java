package com.fix.benchmark.test;

import java.util.concurrent.atomic.AtomicLong;

public class PreciseRateLimiter {
    private final double permitsPerSecond;
    private final AtomicLong nextFreeTicketMicros = new AtomicLong(0);
    
    public PreciseRateLimiter(double permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond;
    }
    
    public boolean tryAcquire() {
        long nowMicros = System.nanoTime() / 1000L;
        long nextFree = nextFreeTicketMicros.get();
        
        if (nowMicros < nextFree) {
            return false;
        }
        
        long next = nextFree + (long) (1_000_000.0 / permitsPerSecond);
        return nextFreeTicketMicros.compareAndSet(nextFree, next);
    }
    
    public void reset() {
        nextFreeTicketMicros.set(0);
    }
}