package com.selfdevelopment.chatengine.action.exception;

/**
 * Exception thrown when a downstream connection fails.
 * <p>
 * This exception is used when an Action cannot connect to a downstream system,
 * such as a database, message queue, external API, or Genesys/Aibot connector.
 * <p>
 * Handlers can implement custom logic for this exception type, such as:
 * <ul>
 *   <li>Alerting on-call teams</li>
 *   <li>Triggering circuit breakers</li>
 *   <li>Scheduling retries with backoff</li>
 *   <li>Falling back to alternative systems</li>
 * </ul>
 *
 * @see DownstreamConnectionExceptionHandler
 */
public class DownstreamConnectionException extends RuntimeException {

    private final String downstreamSystem;
    private final String operation;

    /**
     * Creates a new downstream connection exception.
     *
     * @param message          the detail message
     * @param downstreamSystem the name of the downstream system that failed
     * @param operation        the operation that was being performed
     */
    public DownstreamConnectionException(String message, String downstreamSystem, String operation) {
        super(message);
        this.downstreamSystem = downstreamSystem;
        this.operation = operation;
    }

    /**
     * Creates a new downstream connection exception with a cause.
     *
     * @param message          the detail message
     * @param cause            the cause
     * @param downstreamSystem the name of the downstream system that failed
     * @param operation        the operation that was being performed
     */
    public DownstreamConnectionException(String message, Throwable cause, String downstreamSystem, String operation) {
        super(message, cause);
        this.downstreamSystem = downstreamSystem;
        this.operation = operation;
    }

    /**
     * Returns the name of the downstream system that failed.
     *
     * @return the downstream system name
     */
    public String getDownstreamSystem() {
        return downstreamSystem;
    }

    /**
     * Returns the operation that was being performed when the exception occurred.
     *
     * @return the operation name
     */
    public String getOperation() {
        return operation;
    }
}
