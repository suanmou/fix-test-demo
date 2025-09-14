package com.fix.benchmark.engine;

import com.fix.benchmark.metrics.PreciseRequestTracker;
import com.fix.benchmark.utils.EnhancedTestReqIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.field.TestReqID;
import quickfix.fix44.Heartbeat;
import quickfix.fix44.TestRequest;

import java.util.concurrent.ConcurrentHashMap;

public class EnhancedMultiSessionApplication extends MessageCracker implements Application {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedMultiSessionApplication.class);
    
    private final String sessionId;
    private final PreciseRequestTracker requestTracker;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    
    // 会话级别的统计
    private final ConcurrentHashMap<String, Long> pendingRequests = new ConcurrentHashMap<>();
    
    public EnhancedMultiSessionApplication(String sessionId, PreciseRequestTracker requestTracker) {
        this.sessionId = sessionId;
        this.requestTracker = requestTracker;
    }
    
    @Override
    public void onCreate(SessionID sessionId) {
        logger.debug("Session {} created: {}", this.sessionId, sessionId);
    }
    
    @Override
    public void onLogon(SessionID sessionId) {
        connected.set(true);
        logger.info("Session {} logged on: {}", this.sessionId, sessionId);
    }
    
    @Override
    public void onLogout(SessionID sessionId) {
        connected.set(false);
        logger.info("Session {} logged out: {}", this.sessionId, sessionId);
    }
    
    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        // 记录发送的管理消息
    }
    
    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        // 处理接收的管理消息
    }
    
    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        // 处理发送的应用消息
        if (message instanceof TestRequest) {
            TestRequest testRequest = (TestRequest) message;
            TestReqID testReqID = new TestReqID();
            if (testRequest.isSetField(testReqID)) {
                testRequest.get(testReqID);
                String testReqId = testReqID.getValue();
                
                long sendTime = System.nanoTime();
                pendingRequests.put(testReqId, sendTime);
                requestTracker.recordRequest(testReqId, sendTime);
            }
        }
    }
    
    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        crack(message, sessionId);
    }
    
    @Override
    public void onMessage(Heartbeat heartbeat, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        // 检查是否是TestRequest的响应
        TestReqID testReqID = new TestReqID();
        if (heartbeat.isSetField(testReqID)) {
            heartbeat.get(testReqID);
            String testReqId = testReqID.getValue();
            
            Long sendTime = pendingRequests.remove(testReqId);
            if (sendTime != null) {
                long receiveTime = System.nanoTime();
                requestTracker.recordResponse(testReqId, receiveTime);
            }
        }
    }
    
    public boolean sendTestRequest() {
        if (!connected.get()) {
            return false;
        }
        
        try {
            String testReqId = EnhancedTestReqIdGenerator.generate(sessionId);
            TestRequest testRequest = new TestRequest();
            testRequest.set(new TestReqID(testReqId));
            
            SessionID sessionID = getSessionID();
            if (sessionID != null) {
                return Session.sendToTarget(testRequest, sessionID);
            }
        } catch (Exception e) {
            logger.error("Error sending test request for session {}", sessionId, e);
        }
        
        return false;
    }
    
    private SessionID getSessionID() {
        for (SessionID sessionID : Session.getSessions()) {
            Session session = Session.lookupSession(sessionID);
            if (session != null && session.isLoggedOn()) {
                return sessionID;
            }
        }
        return null;
    }
    
    public boolean isConnected() {
        return connected.get();
    }
    
    public String getSessionId() {
        return sessionId;
    }
}