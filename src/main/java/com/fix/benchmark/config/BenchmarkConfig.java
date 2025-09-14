package com.fix.benchmark.config;

import com.typesafe.config.Config;

public class BenchmarkConfig {
    private final Config config;
    
    // FIX配置
    private final String configFile;
    private final String senderCompId;
    private final String targetCompId;
    
    // 测试配置
    private final int threads;
    private final int messagesPerSecond;
    private final int testDurationSeconds;
    private final int warmupSeconds;
    
    // GCP/网络配置
    private final boolean enableGcpOptimization;
    private final String gcpRegion;
    private final int socketBufferSize;
    
    public BenchmarkConfig(Config config) {
        this.config = config;
        
        // FIX配置
        this.configFile = config.getString("fix.config-file");
        this.senderCompId = config.getString("fix.sender-comp-id");
        this.targetCompId = config.getString("fix.target-comp-id");
        
        // 测试配置
        this.threads = config.getInt("test.threads");
        this.messagesPerSecond = config.getInt("test.messages-per-second");
        this.testDurationSeconds = config.getInt("test.duration-seconds");
        this.warmupSeconds = config.getInt("test.warmup-seconds");
        
        // GCP优化
        this.enableGcpOptimization = config.getBoolean("gcp.optimization-enabled");
        this.gcpRegion = config.getString("gcp.region");
        this.socketBufferSize = config.getInt("network.socket-buffer-size");
    }
    
    // Getters
    public String getConfigFile() { return configFile; }
    public String getSenderCompId() { return senderCompId; }
    public String getTargetCompId() { return targetCompId; }
    public int getThreads() { return threads; }
    public int getMessagesPerSecond() { return messagesPerSecond; }
    public int getTestDurationSeconds() { return testDurationSeconds; }
    public int getWarmupSeconds() { return warmupSeconds; }
    public boolean isEnableGcpOptimization() { return enableGcpOptimization; }
    public String getGcpRegion() { return gcpRegion; }
    public int getSocketBufferSize() { return socketBufferSize; }
}