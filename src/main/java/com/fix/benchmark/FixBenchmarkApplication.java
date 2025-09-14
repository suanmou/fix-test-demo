package com.fix.benchmark;

import com.fix.benchmark.config.BenchmarkConfig;
import com.fix.benchmark.engine.FixEngineManager;
import com.fix.benchmark.metrics.MetricsCollector;
import com.fix.benchmark.test.LoadTester;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public class FixBenchmarkApplication {
    private static final Logger logger = LoggerFactory.getLogger(FixBenchmarkApplication.class);
    
    public static void main(String[] args) {
        try {
            // 加载配置
            Config config = ConfigFactory.load();
            BenchmarkConfig benchmarkConfig = new BenchmarkConfig(config);
            
            // 初始化指标收集器
            MetricsCollector metricsCollector = new MetricsCollector();
            
            // 初始化FIX引擎管理器
            FixEngineManager engineManager = new FixEngineManager(benchmarkConfig, metricsCollector);
            
            // 启动引擎
            engineManager.start();
            
            // 等待连接建立
            Thread.sleep(5000);
            
            // 启动压力测试
            LoadTester loadTester = new LoadTester(engineManager, benchmarkConfig, metricsCollector);
            
            // 添加关闭钩子
            CountDownLatch shutdownLatch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.info("Shutting down benchmark...");
                    loadTester.stop();
                    engineManager.stop();
                    metricsCollector.shutdown();
                    shutdownLatch.countDown();
                } catch (Exception e) {
                    logger.error("Error during shutdown", e);
                }
            }));
            
            // 开始测试
            loadTester.start();
            
            // 等待测试完成或手动停止
            shutdownLatch.await();
            
        } catch (Exception e) {
            logger.error("Error in benchmark application", e);
            System.exit(1);
        }
    }
}