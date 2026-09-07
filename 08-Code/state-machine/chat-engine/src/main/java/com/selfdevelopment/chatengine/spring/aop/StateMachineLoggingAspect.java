package com.selfdevelopment.chatengine.spring.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * Aspect for logging state machine execution.
 * <p>
 * This aspect logs entry, exit, and exceptions for state machine service methods,
 * providing detailed execution traces for debugging and monitoring.
 * <p>
 * Features:
 * <ul>
 *   <li>Log method entry with parameters</li>
 *   <li>Log method exit with return value and duration</li>
 *   <li>Log exceptions with stack trace</li>
 *   <li>Performance monitoring (execution time)</li>
 * </ul>
 * <p>
 * Enable with: {@code @EnableAspectJAutoProxy} and include this aspect in Spring context.
 */
@Slf4j
@Aspect
@Component
public class StateMachineLoggingAspect {

    /**
     * Pointcut for all methods in ChatEngineStateMachineService and its subclasses.
     */
    @Pointcut("execution(* com.selfdevelopment.chatengine.service.ChatEngineStateMachineService.*(..))")
    public void stateMachineServiceMethods() {
        // Pointcut definition - no body needed
    }

    /**
     * Pointcut for all methods in SpringChatEngineStateMachineService.
     */
    @Pointcut("execution(* com.selfdevelopment.chatengine.spring.service.SpringChatEngineStateMachineService.*(..))")
    public void springStateMachineServiceMethods() {
        // Pointcut definition - no body needed
    }

    /**
     * Around advice for logging state machine service method execution.
     *
     * @param joinPoint the join point
     * @return the method return value
     * @throws Throwable if the method throws an exception
     */
    @Around("stateMachineServiceMethods() || springStateMachineServiceMethods()")
    public Object logStateMachineExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        long start = System.currentTimeMillis();

        // Log entry
        if (log.isDebugEnabled()) {
            log.debug("Entering state machine method: {} with {} arguments",
                    methodName, args != null ? args.length : 0);
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    log.debug("  arg[{}]: {}", i, args[i] != null ? args[i].getClass().getSimpleName() : "null");
                }
            }
        }

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;

            // Log exit with result
            if (log.isDebugEnabled()) {
                log.debug("Exiting state machine method: {} returned {} in {}ms",
                        methodName,
                        result != null ? result.getClass().getSimpleName() : "null",
                        duration);
            }

            // Log slow operations at WARN level
            if (duration > 100) {
                log.warn("Slow state machine operation: {} took {}ms (threshold: 100ms)",
                        methodName, duration);
            }

            return result;
        } catch (Throwable ex) {
            long duration = System.currentTimeMillis() - start;

            // Log exception
            log.error("Exception in state machine method: {} after {}ms: {}",
                    methodName, duration, ex.getMessage(), ex);

            throw ex;
        }
    }
}
