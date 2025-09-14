package com.fix.benchmark.config;

import com.typesafe.config.Config;
import quickfix.SessionSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiSessionConfig {
    private final Config config;
    private final AtomicInteger sessionCounter = new AtomicInteger(0);
    
    public MultiSessionConfig(Config config) {
        this.config = config;
    }
    
    public String generateSessionConfig(String baseSenderCompId, String targetCompId, int sessionId) {
        String senderCompId = baseSenderCompId + "_" + String.format("%04d", sessionId);
        
        Properties props = new Properties();
        props.setProperty("ConnectionType", "initiator");
        props.setProperty("ReconnectInterval", "5");
        props.setProperty("FileStorePath", "./data/" + senderCompId);
        props.setProperty("FileLogPath", "./log/" + senderCompId);
        props.setProperty("StartTime", "00:00:00");
        props.setProperty("EndTime", "23:59:59");
        props.setProperty("HeartBtInt", "30");
        props.setProperty("SocketConnectHost", config.getString("fix.server.host"));
        props.setProperty("SocketConnectPort", config.getString("fix.server.port"));
        props.setProperty("SocketTcpNoDelay", "Y");
        props.setProperty("SocketSendBufferSize", "65536");
        props.setProperty("SocketReceiveBufferSize", "65536");
        
        // 会话特定配置
        props.setProperty("BeginString", "FIX.4.4");
        props.setProperty("SenderCompID", senderCompId);
        props.setProperty("TargetCompID", targetCompId);
        
        return saveSessionConfig(senderCompId, props);
    }
    
    private String saveSessionConfig(String senderCompId, Properties props) {
        try {
            File configDir = new File("./config/sessions");
            configDir.mkdirs();
            
            File configFile = new File(configDir, senderCompId + ".cfg");
            try (OutputStream output = new FileOutputStream(configFile)) {
                props.store(output, "Auto-generated session config for " + senderCompId);
            }
            
            return configFile.getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create session config", e);
        }
    }
}