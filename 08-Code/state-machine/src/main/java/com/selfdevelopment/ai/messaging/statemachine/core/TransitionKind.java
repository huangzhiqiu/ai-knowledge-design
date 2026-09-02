package com.selfdevelopment.ai.messaging.statemachine.core;

import com.selfdevelopment.ai.messaging.statemachine.api.StateMachine;

import com.selfdevelopment.ai.messaging.statemachine.api.Action;

/**
 * The kind of a transition.
 * <p>
 * Mirrors Spring StateMachine's TransitionKind:
 * <ul>
 *   <li>{@link #EXTERNAL} — source state exits, target state enters (default)</li>
 *   <li>{@link #INTERNAL} — source state does not exit; action executes but state remains</li>
 * </ul>
 * <p>
 * LOCAL transitions (for hierarchical states) are not supported in this lightweight implementation.
 */
public enum TransitionKind {
    /**
     * Standard transition: exit source, enter target.
     */
    EXTERNAL,

    /**
     * Internal transition: action executes, state remains unchanged.
     * No entry/exit actions are triggered.
     */
    INTERNAL
}
