package com.selfdevelopment.agentconnector.spring.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

/**
 * Aspect for monitoring agent connector state machine performance metrics.
 * <p>
 * This aspect collects and logs performance metrics for state machine operations,
 * including execution time, success/failure counts, and throughput.
 * <p>
 * Features:
 * <ul>
 *   <li>Track execution time per method</li>
 *   <li>Track success/failure counts</li>
 *   <li>Calculate average execution time</li>
 *   <li>Log periodic performance summaries</li>
 *   <li>Alert on slow operations</li>
 * </ul>
 * <p>
 * Note: For production use, consider integrating with Micrometer/Prometheus
 * for more robust metrics collection and visualization.
 */
@Slf4j
@Aspect
@Component
public class StateMachinePerformanceAspect {

    // Performance metrics storage
    private final Map<String, MethodMetrics> metricsMap = new ConcurrentHashMap<>();

    // Alert thresholds
    private static final long SLOW_OPERATION_THRESHOLD_MS = 50;
    private static final long VERY_SLOW_OPERATION_THRESHOLD_MS = 200;

    /**
     * Pointcut for all state machine fire methods.
     */
    @Pointcut("execution(* com.selfdevelopment.agentconnector.service.AgentConnectorStateMachineService.fire(..))")
    public void stateMachineFireMethods() {
        // Pointcut definition - no body needed
    }

    /**
     * Around advice for monitoring state machine fire method performance.
     *
     * @param joinPoint the join point
     * @return the method return value
     * @throws Throwable if the method throws an exception
     */
    @Around("stateMachineFireMethods()")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        long start = System.nanoTime();

        MethodMetrics metrics = metricsMap.computeIfAbsent(methodName, k -> new MethodMetrics());

        try {
            Object result = joinPoint.proceed();
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            // Record success
            metrics.recordSuccess(durationMs);

            // Alert on slow operations
            if (durationMs > VERY_SLOW_OPERATION_THRESHOLD_MS) {
                log.error("VERY SLOW agent connector state machine operation: {} took {}ms (threshold: {}ms)",
                        methodName, durationMs, VERY_SLOW_OPERATION_THRESHOLD_MS);
            } else if (durationMs > SLOW_OPERATION_THRESHOLD_MS) {
                log.warn("Slow agent connector state machine operation: {} took {}ms (threshold: {}ms)",
                        methodName, durationMs, SLOW_OPERATION_THRESHOLD_MS);
            }

            // Log periodic summary (every 100 calls)
            if (metrics.getTotalCount() % 100 == 0) {
                logPerformanceSummary(methodName, metrics);
            }

            return result;
        } catch (Throwable ex) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            // Record failure
            metrics.recordFailure(durationMs);

            log.error("Agent connector state machine operation failed: {} took {}ms, error: {}",
                    methodName, durationMs, ex.getMessage());

            throw ex;
        }
    }

    /**
     * Logs a performance summary for a method.
     *
     * @param methodName the method name
     * @param metrics    the metrics
     */
    private void logPerformanceSummary(String methodName, MethodMetrics metrics) {
        log.info("Agent connector performance summary for {}: total={}, success={}, failures={}, " +
                        "avgTime={}ms, maxTime={}ms, successRate={}%",
                methodName,
                metrics.getTotalCount(),
                metrics.getSuccessCount(),
                metrics.getFailureCount(),
                metrics.getAverageTimeMs(),
                metrics.getMaxTimeMs(),
                metrics.getSuccessRate());
    }

    /**
     * Gets a copy of all performance metrics.
     * Useful for exposing metrics via REST endpoint or actuator.
     *
     * @return map of method name to metrics snapshot
     */
    public Map<String, MethodMetrics> getMetrics() {
        return new ConcurrentHashMap<>(metricsMap);
    }

    /**
     * Resets all performance metrics.
     */
    public void resetMetrics() {
        metricsMap.clear();
        log.info("Agent connector performance metrics reset");
    }

    /**
     * Inner class to hold method performance metrics.
     */
    public static class MethodMetrics {
        private final AtomicLong totalCount = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failureCount = new AtomicLong(0);
        private final AtomicLong totalTimeMs = new AtomicLong(0);
        private final AtomicLong maxTimeMs = new AtomicLong(0);

        public void recordSuccess(long durationMs) {
            totalCount.incrementAndGet();
            successCount.incrementAndGet();
            totalTimeMs.addAndGet(durationMs);
            maxTimeMs.updateAndGet(current -> Math.max(current, durationMs));
        }

        public void recordFailure(long durationMs) {
            totalCount.incrementAndGet();
            failureCount.incrementAndGet();
            totalTimeMs.addAndGet(durationMs);
            maxTimeMs.updateAndGet(current -> Math.max(current, durationMs));
        }

        public long getTotalCount() {
            return totalCount.get();
        }

        public long getSuccessCount() {
            return successCount.get();
        }

        public long getFailureCount() {
            return failureCount.get();
        }

        public double getAverageTimeMs() {
            long count = totalCount.get();
            return count > 0 ? (double) totalTimeMs.get() / count : 0.0;
        }

        public long getMaxTimeMs() {
            return maxTimeMs.get();
        }

        public double getSuccessRate() {
            long total = totalCount.get();
            return total > 0 ? (double) successCount.get() / total * 100 : 0.0;
        }
    }
}
