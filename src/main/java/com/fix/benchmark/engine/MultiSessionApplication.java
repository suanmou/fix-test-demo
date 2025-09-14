package com.fix.benchmark.engine;

import com.fix.benchmark.metrics.MultiSessionMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.field.TestReqID;
import quickfix.fix44.Heartbeat;
import quickfix.fix44.TestRequest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class MultiSessionApplication extends MessageCracker implements Application {
    private static final Logger logger = LoggerFactory.getLogger(MultiSessionApplication.class);
    
    private final String sessionId;
    private final MultiSessionMetrics metrics;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    
    // 跟踪待处理的请求
    private final ConcurrentHashMap<String, Long> pendingRequests = new ConcurrentHashMap<>();
    
    public MultiSessionApplication(String sessionId, MultiSessionMetrics metrics) {
        this.sessionId = sessionId;
        this.metrics = metrics;
    }
    
    @Override
    public void onCreate(SessionID sessionId) {
        logger.debug("Session {} created: {}", this.sessionId, sessionId);
    }
    
    @Override
    public void onLogon(SessionID sessionId) {
        connected.set(true);
        metrics.recordSessionLogin(sessionId.toString());
        logger.info("Session {} logged on: {}", this.sessionId, sessionId);
    }
    
    @Override
    public void onLogout(SessionID sessionId) {
        connected.set(false);
        metrics.recordSessionLogout(sessionId.toString());
        logger.info("Session {} logged out: {}", this.sessionId, sessionId);
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
        // 消息发送时的处理
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
                metrics.recordResponse(sessionId, latency);
                metrics.recordResponseReceived(sessionId);
            }
        }
    }
    
    public boolean sendTestRequest(String testReqId) {
        if (!connected.get()) {
            metrics.recordSendFailure(sessionId, "Not connected");
            return false;
        }
        
        try {
            TestRequest testRequest = new TestRequest();
            testRequest.set(new TestReqID(testReqId));
            
            SessionID sessionID = getSessionID();
            if (sessionID != null) {
                long startTime = System.nanoTime();
                pendingRequests.put(testReqId, startTime);
                
                boolean sent = Session.sendToTarget(testRequest, sessionID);
                if (sent) {
                    metrics.recordMessageSent(sessionId);
                    metrics.recordRequestSent(sessionId);
                } else {
                    pendingRequests.remove(testReqId);
                    metrics.recordSendFailure(sessionId, "Send failed");
                }
                return sent;
            }
        } catch (Exception e) {
            metrics.recordSendFailure(sessionId, e.getMessage());
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