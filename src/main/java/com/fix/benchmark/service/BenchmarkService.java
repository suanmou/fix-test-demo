package com.fix.benchmark.service;

import com.fix.benchmark.dto.*;
import com.fix.benchmark.engine.MultiSessionEngineManager;
import com.fix.benchmark.metrics.PreciseRequestTracker;
import com.fix.benchmark.test.EnhancedLoadTester;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
public class BenchmarkService {

    private final Map<String, BenchmarkTask> activeTasks = new ConcurrentHashMap<>();
    private final ExecutorService taskExecutor = Executors.newCachedThreadPool();
    private final Config config = ConfigFactory.load();

    public String startBenchmark(StartRequest request) {
        String taskId = UUID.randomUUID().toString();
        
        BenchmarkTask task = new BenchmarkTask(taskId, request);
        activeTasks.put(taskId, task);
        
        taskExecutor.submit(() -> executeBenchmark(task));
        
        return taskId;
    }

    public boolean stopBenchmark(String taskId) {
        BenchmarkTask task = activeTasks.get(taskId);
        if (task != null) {
            task.stop();
            return true;
        }
        return false;
    }

    public StatusResponse getStatus(String taskId) {
        BenchmarkTask task = activeTasks.get(taskId);
        if (task == null) {
            return null;
        }
        
        return new StatusResponse(true, task.getStatus(), null);
    }

    public ReportResponse getReport(String taskId) {
        BenchmarkTask task = activeTasks.get(taskId);
        if (task == null) {
            return null;
        }
        
        return new ReportResponse(true, task.generateReport(), null);
    }

    public TaskListResponse getAllTasks() {
        Map<String, TaskStatus> taskMap = new HashMap<>();
        activeTasks.forEach((id, task) -> taskMap.put(id, task.getStatus()));
        return new TaskListResponse(taskMap);
    }

    private void executeBenchmark(BenchmarkTask task) {
        try {
            task.setStatus(TaskStatus.builder()
                .taskId(task.getTaskId())
                .status("RUNNING")
                .startTime(LocalDateTime.now())
                .totalSessions(task.getRequest().getSessionCount())
                .build());

            // 初始化引擎和测试器
            PreciseRequestTracker tracker = new PreciseRequestTracker(task.getRequest().getTimeoutMillis());
            MultiSessionEngineManager engineManager = new MultiSessionEngineManager(config, tracker);
            
            // 创建会话
            engineManager.createMultipleSessions(
                task.getRequest().getBaseSenderCompId(),
                task.getRequest().getTargetCompId(),
                task.getRequest().getSessionCount()
            );

            // 启动测试
            EnhancedLoadTester tester = new EnhancedLoadTester(
                engineManager,
                tracker,
                task.getRequest().getTimeoutMillis(),
                task.getRequest().getMessagesPerSecond()
            );

            task.setTester(tester);
            tester.startTest(task.getRequest().getDurationSeconds());

            // 测试完成
            task.complete();
            
        } catch (Exception e) {
            task.fail(e.getMessage());
        }
    }

    private static class BenchmarkTask {
        private final String taskId;
        private final StartRequest request;
        private EnhancedLoadTester tester;
        private volatile TaskStatus status;
        private volatile BenchmarkReport report;

        public BenchmarkTask(String taskId, StartRequest request) {
            this.taskId = taskId;
            this.request = request;
            this.status = TaskStatus.builder()
                .taskId(taskId)
                .status("PENDING")
                .startTime(LocalDateTime.now())
                .build();
        }

        public void setTester(EnhancedLoadTester tester) {
            this.tester = tester;
        }

        public void complete() {
            status.setStatus("COMPLETED");
            status.setEndTime(LocalDateTime.now());
            report = generateReport();
        }

        public void fail(String error) {
            status.setStatus("FAILED");
            status.setEndTime(LocalDateTime.now());
        }

        public void stop() {
            if (tester != null) {
                tester.stop();
                status.setStatus("STOPPED");
                status.setEndTime(LocalDateTime.now());
            }
        }

        public TaskStatus getStatus() {
            return status;
        }

        public BenchmarkReport generateReport() {
            if (report != null) return report;
            
            // 生成报告逻辑
            return new BenchmarkReport();
        }

        public String getTaskId() {
            return taskId;
        }

        public StartRequest getRequest() {
            return request;
        }
    }
}package com.fix.benchmark.service;

import com.fix.benchmark.dto.*;
import com.fix.benchmark.engine.MultiSessionEngineManager;
import com.fix.benchmark.metrics.PreciseRequestTracker;
import com.fix.benchmark.test.EnhancedLoadTester;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
public class BenchmarkService {

    private final Map<String, BenchmarkTask> activeTasks = new ConcurrentHashMap<>();
    private final ExecutorService taskExecutor = Executors.newCachedThreadPool();
    private final Config config = ConfigFactory.load();

    public String startBenchmark(StartRequest request) {
        String taskId = UUID.randomUUID().toString();
        
        BenchmarkTask task = new BenchmarkTask(taskId, request);
        activeTasks.put(taskId, task);
        
        taskExecutor.submit(() -> executeBenchmark(task));
        
        return taskId;
    }

    public boolean stopBenchmark(String taskId) {
        BenchmarkTask task = activeTasks.get(taskId);
        if (task != null) {
            task.stop();
            return true;
        }
        return false;
    }

    public StatusResponse getStatus(String taskId) {
        BenchmarkTask task = activeTasks.get(taskId);
        if (task == null) {
            return null;
        }
        
        return new StatusResponse(true, task.getStatus(), null);
    }

    public ReportResponse getReport(String taskId) {
        BenchmarkTask task = activeTasks.get(taskId);
        if (task == null) {
            return null;
        }
        
        return new ReportResponse(true, task.generateReport(), null);
    }

    public TaskListResponse getAllTasks() {
        Map<String, TaskStatus> taskMap = new HashMap<>();
        activeTasks.forEach((id, task) -> taskMap.put(id, task.getStatus()));
        return new TaskListResponse(taskMap);
    }

    private void executeBenchmark(BenchmarkTask task) {
        try {
            task.setStatus(TaskStatus.builder()
                .taskId(task.getTaskId())
                .status("RUNNING")
                .startTime(LocalDateTime.now())
                .totalSessions(task.getRequest().getSessionCount())
                .build());

            // 初始化引擎和测试器
            PreciseRequestTracker tracker = new PreciseRequestTracker(task.getRequest().getTimeoutMillis());
            MultiSessionEngineManager engineManager = new MultiSessionEngineManager(config, tracker);
            
            // 创建会话
            engineManager.createMultipleSessions(
                task.getRequest().getBaseSenderCompId(),
                task.getRequest().getTargetCompId(),
                task.getRequest().getSessionCount()
            );

            // 启动测试
            EnhancedLoadTester tester = new EnhancedLoadTester(
                engineManager,
                tracker,
                task.getRequest().getTimeoutMillis(),
                task.getRequest().getMessagesPerSecond()
            );

            task.setTester(tester);
            tester.startTest(task.getRequest().getDurationSeconds());

            // 测试完成
            task.complete();
            
        } catch (Exception e) {
            task.fail(e.getMessage());
        }
    }

    private static class BenchmarkTask {
        private final String taskId;
        private final StartRequest request;
        private EnhancedLoadTester tester;
        private volatile TaskStatus status;
        private volatile BenchmarkReport report;

        public BenchmarkTask(String taskId, StartRequest request) {
            this.taskId = taskId;
            this.request = request;
            this.status = TaskStatus.builder()
                .taskId(taskId)
                .status("PENDING")
                .startTime(LocalDateTime.now())
                .build();
        }

        public void setTester(EnhancedLoadTester tester) {
            this.tester = tester;
        }

        public void complete() {
            status.setStatus("COMPLETED");
            status.setEndTime(LocalDateTime.now());
            report = generateReport();
        }

        public void fail(String error) {
            status.setStatus("FAILED");
            status.setEndTime(LocalDateTime.now());
        }

        public void stop() {
            if (tester != null) {
                tester.stop();
                status.setStatus("STOPPED");
                status.setEndTime(LocalDateTime.now());
            }
        }

        public TaskStatus getStatus() {
            return status;
        }

        public BenchmarkReport generateReport() {
            if (report != null) return report;
            
            // 生成报告逻辑
            return new BenchmarkReport();
        }

        public String getTaskId() {
            return taskId;
        }

        public StartRequest getRequest() {
            return request;
        }
    }
}