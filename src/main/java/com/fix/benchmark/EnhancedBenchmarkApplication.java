package com.fix.benchmark;

import com.fix.benchmark.engine.MultiSessionEngineManager;
import com.fix.benchmark.metrics.PreciseRequestTracker;
import com.fix.benchmark.test.EnhancedLoadTester;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class EnhancedBenchmarkApplication {
    public static void main(String[] args) {
        try {
            Config config = ConfigFactory.load();
            
            // 创建请求跟踪器
            int timeoutMillis = config.getInt("test.timeout-millis");
            PreciseRequestTracker requestTracker = new PreciseRequestTracker(timeoutMillis);
            
            // 创建引擎管理器
            MultiSessionEngineManager engineManager = new MultiSessionEngineManager(config, requestTracker);
            
            // 启动测试
            double messagesPerSecond = config.getDouble("test.messages-per-second");
            int durationSeconds = config.getInt("test.duration-seconds");
            
            EnhancedLoadTester tester = new EnhancedLoadTester(engineManager, requestTracker, timeoutMillis, messagesPerSecond);
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                tester.stop();
                requestTracker.shutdown();
            }));
            
            // 开始测试
            tester.startTest(durationSeconds);
            
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}