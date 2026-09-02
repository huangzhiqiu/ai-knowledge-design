package com.selfdevelopment.agentconnector.enums;

/**
 * Events that drive the Interaction (channel/connection) state machine.
 * <p>
 * These are channel-level events (connection lifecycle, hold, transfer)
 * as opposed to {@link ConversationFact} which are business-level events.
 */
public enum InteractionFact {

    // CONNECTION LIFECYCLE
    CONNECTION_ESTABLISHED,   // Channel connection successfully established
    CONNECTION_FAILED,        // Channel connection failed (network, auth, etc.)
    CONNECTION_DROPPED,       // Active connection dropped unexpectedly
    RECONNECT_SUCCESS,        // Reconnection attempt succeeded
    RECONNECT_FAILED,         // Reconnection attempt failed
    RECONNECT_EXHAUSTED,      // Max reconnection retries reached
    CLOSE_REQUEST,            // Explicit close request (customer or agent)

    // HOLD
    HOLD_REQUEST,             // Agent puts customer on hold
    HOLD_RESUME,              // Customer retrieved from hold

    // TRANSFER (channel-level)
    TRANSFER_START,           // Channel transfer initiated (e.g., WebSocket handoff)
    TRANSFER_COMPLETE,        // Channel transfer completed
    TRANSFER_FAILED           // Channel transfer failed
}
