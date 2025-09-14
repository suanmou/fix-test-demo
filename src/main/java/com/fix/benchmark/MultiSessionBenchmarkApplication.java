package com.fix.benchmark;

import com.fix.benchmark.config.MultiSessionConfig;
import com.fix.benchmark.engine.MultiSessionEngineManager;
import com.fix.benchmark.metrics.MultiSessionMetrics;
import com.fix.benchmark.test.MultiSessionLoadTester;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class MultiSessionBenchmarkApplication {
    public static void main(String[] args) {
        try {
            Config config = ConfigFactory.load();
            MultiSessionConfig multiConfig = new MultiSessionConfig(config);
            MultiSessionMetrics metrics = new MultiSessionMetrics();
            
            MultiSessionEngineManager engineManager = new MultiSessionEngineManager(multiConfig, metrics);
            
            // 获取配置
            String baseSenderCompId = config.getString("fix.sessions.base-sender-comp-id");
            String targetCompId = config.getString("fix.sessions.target-comp-id");
            int sessionCount = config.getInt("fix.sessions.count");
            int totalMessagesPerSecond = config.getInt("test.total-messages-per-second");
            
            // 启动测试
            MultiSessionLoadTester tester = new MultiSessionLoadTester(engineManager, multiConfig, metrics);
            tester.startMultiSessionTest(baseSenderCompId, targetCompId, sessionCount, totalMessagesPerSecond);
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(tester::stop));
            
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}