package com.fix.benchmark.engine;

import com.fix.benchmark.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.field.TestReqID;
import quickfix.fix44.Heartbeat;
import quickfix.fix44.TestRequest;

import java.util.concurrent.ConcurrentHashMap;

public class FixClientApplication extends MessageCracker implements Application {
    private static final Logger logger = LoggerFactory.getLogger(FixClientApplication.class);
    
    private final MetricsCollector metricsCollector;
    private final ConcurrentHashMap<String, Long> pendingRequests;
    
    public FixClientApplication(MetricsCollector metricsCollector, ConcurrentHashMap<String, Long> pendingRequests) {
        this.metricsCollector = metricsCollector;
        this.pendingRequests = pendingRequests;
    }
    
    @Override
    public void onCreate(SessionID sessionId) {
        logger.info("Session created: {}", sessionId);
    }
    
    @Override
    public void onLogon(SessionID sessionId) {
        logger.info("Logged on: {}", sessionId);
    }
    
    @Override
    public void onLogout(SessionID sessionId) {
        logger.info("Logged out: {}", sessionId);
    }
    
    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        // 处理发送的管理消息
    }
    
    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        // 处理接收的管理消息
    }
    
    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        // 处理发送的应用消息
    }
    
    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        crack(message, sessionId);
    }
    
    @Override
    public void onMessage(Heartbeat heartbeat, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        // 处理心跳消息
    }
    
    @Override
    public void onMessage(TestRequest testRequest, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        TestReqID testReqID = new TestReqID();
        if (testRequest.isSetField(testReqID)) {
            testRequest.get(testReqID);
            String reqId = testReqID.getValue();
            
            Long startTime = pendingRequests.remove(reqId);
            if (startTime != null) {
                long latency = System.nanoTime() - startTime;
                metricsCollector.recordResponse(latency);
            }
        }
    }
}