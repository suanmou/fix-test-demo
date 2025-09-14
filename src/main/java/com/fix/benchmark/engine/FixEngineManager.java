package com.fix.benchmark.engine;

import com.fix.benchmark.config.BenchmarkConfig;
import com.fix.benchmark.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.field.TestReqID;
import quickfix.fix44.TestRequest;

import java.io.FileInputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class FixEngineManager {
    private static final Logger logger = LoggerFactory.getLogger(FixEngineManager.class);
    
    private final BenchmarkConfig config;
    private final MetricsCollector metricsCollector;
    private final SocketInitiator initiator;
    private final FixClientApplication application;
    
    private final AtomicLong messageCounter = new AtomicLong(0);
    private final ConcurrentHashMap<String, Long> pendingRequests = new ConcurrentHashMap<>();
    
    public FixEngineManager(BenchmarkConfig config, MetricsCollector metricsCollector) throws Exception {
        this.config = config;
        this.metricsCollector = metricsCollector;
        
        // 加载FIX配置
        SessionSettings settings = new SessionSettings(new FileInputStream(config.getConfigFile()));
        
        // 配置GCP优化
        if (config.isEnableGcpOptimization()) {
            configureGcpOptimization(settings);
        }
        
        // 创建应用
        this.application = new FixClientApplication(metricsCollector, pendingRequests);
        
        // 创建消息存储
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new ScreenLogFactory(true, true, true);
        MessageFactory messageFactory = new quickfix.fix44.MessageFactory();
        
        // 创建初始化器
        this.initiator = new SocketInitiator(application, storeFactory, settings, logFactory, messageFactory);
    }
    
    public void start() throws Exception {
        logger.info("Starting FIX engine...");
        initiator.start();
        
        // 等待会话建立
        Thread.sleep(2000);
    }
    
    public void stop() {
        logger.info("Stopping FIX engine...");
        initiator.stop();
    }
    
    public void sendTestRequest(String testReqId) throws SessionNotFound {
        TestRequest testRequest = new TestRequest();
        testRequest.set(new TestReqID(testReqId));
        
        SessionID sessionId = getSessionId();
        if (sessionId != null) {
            long startTime = System.nanoTime();
            pendingRequests.put(testReqId, startTime);
            
            Session.sendToTarget(testRequest, sessionId);
            messageCounter.incrementAndGet();
            
            metricsCollector.recordMessageSent();
        }
    }
    
    private SessionID getSessionId() {
        for (SessionID sessionId : initiator.getSessions()) {
            Session session = Session.lookupSession(sessionId);
            if (session != null && session.isLoggedOn()) {
                return sessionId;
            }
        }
        return null;
    }
    
    private void configureGcpOptimization(SessionSettings settings) {
        // GCP网络优化配置
        settings.setString("SocketUseSSL", "Y");
        settings.setString("SocketKeyStore", "gcp-keystore.jks");
        settings.setString("SocketKeyStorePassword", "changeit");
        settings.setString("SocketTcpNoDelay", "Y");
        settings.setString("SocketSendBufferSize", String.valueOf(config.getSocketBufferSize()));
        settings.setString("SocketReceiveBufferSize", String.valueOf(config.getSocketBufferSize()));
        
        // EDMZ网络隔离配置
        settings.setString("ReconnectInterval", "5");
        settings.setString("HeartBtInt", "30");
        settings.setString("SocketConnectPort", "9876");
        settings.setString("SocketConnectHost", config.getGcpRegion() + "-fix.yourdomain.com");
    }
    
    public boolean isConnected() {
        SessionID sessionId = getSessionId();
        return sessionId != null && Session.lookupSession(sessionId).isLoggedOn();
    }
}