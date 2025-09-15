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
import java.util.concurrent.atomic.AtomicBoolean;

public class EnhancedMultiSessionApplication extends MessageCracker implements Application {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedMultiSessionApplication.class);
    
    private final String sessionId;
    private final PreciseRequestTracker requestTracker;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    
    // 会话级别的统计
    private final ConcurrentHashMap<String, Long> pendingRequests = new ConcurrentHashMap<>();
    private SessionID targetSessionId;
    
    public EnhancedMultiSessionApplication(String sessionId, PreciseRequestTracker requestTracker) {
        this.sessionId = sessionId;
        this.requestTracker = requestTracker;
    }
    
    @Override
    public void onCreate(SessionID sessionId) {
        logger.debug("Session {} created: {}", this.sessionId, sessionId);
        // 保存会话ID用于后续使用
        if (sessionId.toString().contains(this.sessionId)) {
            this.targetSessionId = sessionId;
        }
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
        // 使用MessageCracker来处理消息
        crack(message, sessionId);
    }
    
    // 使用MessageCracker的onMessage方法来处理TestRequest响应
    @Override
    public void onMessage(TestRequest testRequest, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        TestReqID testReqID = new TestReqID();
        if (testRequest.isSetField(testReqID)) {
            testRequest.get(testReqID);
            String testReqId = testReqID.getValue();
            
            Long sendTime = pendingRequests.remove(testReqId);
            if (sendTime != null) {
                long receiveTime = System.nanoTime();
                requestTracker.recordResponse(testReqId, receiveTime);
            }
        }
    }
    
    // 处理心跳消息 - 注意：Heartbeat通常不会包含TestReqID
    public void onMessage(Heartbeat heartbeat, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        // Heartbeat消息通常不包含TestReqID，所以我们不需要在这里处理响应
        // 响应处理应该在TestRequest的onMessage中处理
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
        // 直接使用存储的targetSessionId
        if (targetSessionId != null) {
            Session session = Session.lookupSession(targetSessionId);
            if (session != null && session.isLoggedOn()) {
                return targetSessionId;
            }
        }
        
        // 如果没有存储的会话ID，返回null
        return null;
    }
    
    public boolean isConnected() {
        return connected.get();
    }
    
    public String getSessionId() {
        return sessionId;
    }
}