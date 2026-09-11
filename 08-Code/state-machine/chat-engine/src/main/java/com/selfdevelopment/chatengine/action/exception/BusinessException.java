package com.selfdevelopment.chatengine.action.exception;

/**
 * Exception thrown for business rule violations or expected business errors.
 * <p>
 * This exception is used when an Action encounters a business rule violation,
 * such as invalid state transitions, missing required data, or business logic
 * validation failures. Unlike system exceptions, business exceptions are expected
 * and should be handled gracefully.
 * <p>
 * Handlers can implement custom logic for this exception type, such as:
 * <ul>
 *   <li>Logging with business context</li>
 *   <li>Notifying business stakeholders</li>
 *   <li>Triggering business workflows</li>
 *   <li>Recording business metrics</li>
 * </ul>
 *
 * @see BusinessExceptionHandler
 */
public class BusinessException extends RuntimeException {

    private final String businessCode;
    private final String businessContext;

    /**
     * Creates a new business exception.
     *
     * @param message         the detail message
     * @param businessCode    the business error code
     * @param businessContext additional business context
     */
    public BusinessException(String message, String businessCode, String businessContext) {
        super(message);
        this.businessCode = businessCode;
        this.businessContext = businessContext;
    }

    /**
     * Creates a new business exception with a cause.
     *
     * @param message         the detail message
     * @param cause           the cause
     * @param businessCode    the business error code
     * @param businessContext additional business context
     */
    public BusinessException(String message, Throwable cause, String businessCode, String businessContext) {
        super(message, cause);
        this.businessCode = businessCode;
        this.businessContext = businessContext;
    }

    /**
     * Returns the business error code.
     *
     * @return the business error code
     */
    public String getBusinessCode() {
        return businessCode;
    }

    /**
     * Returns additional business context.
     *
     * @return the business context
     */
    public String getBusinessContext() {
        return businessContext;
    }
}
