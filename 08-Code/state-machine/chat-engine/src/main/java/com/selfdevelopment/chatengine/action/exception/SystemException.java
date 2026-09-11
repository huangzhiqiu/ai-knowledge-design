package com.selfdevelopment.chatengine.action.exception;

/**
 * Exception thrown for unexpected system errors.
 * <p>
 * This exception is used when an Action encounters an unexpected system error,
 * such as null pointer exceptions, out of memory errors, or other runtime
 * exceptions that are not business-related and not downstream connection issues.
 * <p>
 * Handlers can implement custom logic for this exception type, such as:
 * <ul>
 *   <li>Alerting on-call engineering teams</li>
 *   <li>Recording detailed error logs with stack traces</li>
 *   <li>Triggering incident management workflows</li>
 *   <li>Collecting system metrics for monitoring</li>
 * </ul>
 *
 * @see SystemExceptionHandler
 */
public class SystemException extends RuntimeException {

    private final String systemComponent;
    private final String errorCategory;

    /**
     * Creates a new system exception.
     *
     * @param message         the detail message
     * @param systemComponent the system component where the error occurred
     * @param errorCategory   the category of the error
     */
    public SystemException(String message, String systemComponent, String errorCategory) {
        super(message);
        this.systemComponent = systemComponent;
        this.errorCategory = errorCategory;
    }

    /**
     * Creates a new system exception with a cause.
     *
     * @param message         the detail message
     * @param cause           the cause
     * @param systemComponent the system component where the error occurred
     * @param errorCategory   the category of the error
     */
    public SystemException(String message, Throwable cause, String systemComponent, String errorCategory) {
        super(message, cause);
        this.systemComponent = systemComponent;
        this.errorCategory = errorCategory;
    }

    /**
     * Returns the system component where the error occurred.
     *
     * @return the system component name
     */
    public String getSystemComponent() {
        return systemComponent;
    }

    /**
     * Returns the category of the error.
     *
     * @return the error category
     */
    public String getErrorCategory() {
        return errorCategory;
    }
}
