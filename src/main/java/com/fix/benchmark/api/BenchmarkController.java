package com.fix.benchmark.api;

import com.fix.benchmark.dto.*;
import com.fix.benchmark.service.BenchmarkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/benchmark")
@CrossOrigin(origins = "*")
public class BenchmarkController {

    @Autowired
    private BenchmarkService benchmarkService;

    /**
     * 启动压测
     */
    @PostMapping("/start")
    public ResponseEntity<StartResponse> startBenchmark(@RequestBody StartRequest request) {
        try {
            String taskId = benchmarkService.startBenchmark(request);
            return ResponseEntity.ok(new StartResponse(true, taskId, "Benchmark started successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new StartResponse(false, null, "Failed to start benchmark: " + e.getMessage()));
        }
    }

    /**
     * 停止压测
     */
    @PostMapping("/stop/{taskId}")
    public ResponseEntity<StopResponse> stopBenchmark(@PathVariable String taskId) {
        try {
            boolean stopped = benchmarkService.stopBenchmark(taskId);
            return ResponseEntity.ok(new StopResponse(stopped, stopped ? "Benchmark stopped" : "Task not found"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new StopResponse(false, "Failed to stop benchmark: " + e.getMessage()));
        }
    }

    /**
     * 获取压测状态
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<StatusResponse> getStatus(@PathVariable String taskId) {
        try {
            StatusResponse status = benchmarkService.getStatus(taskId);
            if (status != null) {
                return ResponseEntity.ok(status);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new StatusResponse(false, null, "Error getting status: " + e.getMessage()));
        }
    }

    /**
     * 获取压测报告
     */
    @GetMapping("/report/{taskId}")
    public ResponseEntity<ReportResponse> getReport(@PathVariable String taskId) {
        try {
            ReportResponse report = benchmarkService.getReport(taskId);
            if (report != null) {
                return ResponseEntity.ok(report);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ReportResponse(false, null, "Error getting report: " + e.getMessage()));
        }
    }

    /**
     * 获取所有任务
     */
    @GetMapping("/tasks")
    public ResponseEntity<TaskListResponse> getAllTasks() {
        return ResponseEntity.ok(benchmarkService.getAllTasks());
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> healthCheck() {
        return ResponseEntity.ok(new HealthResponse(true, "Service is running"));
    }
}