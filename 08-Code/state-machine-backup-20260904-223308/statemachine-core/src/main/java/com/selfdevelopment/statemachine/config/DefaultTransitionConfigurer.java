package com.selfdevelopment.statemachine.config;

import com.selfdevelopment.statemachine.api.StateMachine;

import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.api.Guard;
import com.selfdevelopment.statemachine.core.Transition;
import com.selfdevelopment.statemachine.core.TransitionKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Default implementation of {@link TransitionConfigurer}.
 */
public final class DefaultTransitionConfigurer<S, E, C> implements TransitionConfigurer<S, E, C> {

    private final List<Transition<S, E, C>> transitions = new ArrayList<>();

    // Current transition being built
    private S currentSource;
    private S currentTarget;
    private E currentEvent;
    private Guard<S, E, C> currentGuard;
    private Action<S, E, C> currentAction;
    private TransitionKind currentKind = TransitionKind.EXTERNAL;

    @Override
    public TransitionConfigurer<S, E, C> withExternal() {
        this.currentKind = TransitionKind.EXTERNAL;
        return this;
    }

    @Override
    public TransitionConfigurer<S, E, C> withInternal() {
        this.currentKind = TransitionKind.INTERNAL;
        return this;
    }

    @Override
    public TransitionConfigurer<S, E, C> source(S source) {
        this.currentSource = Objects.requireNonNull(source, "source must not be null");
        return this;
    }

    @Override
    public TransitionConfigurer<S, E, C> target(S target) {
        this.currentTarget = Objects.requireNonNull(target, "target must not be null");
        return this;
    }

    @Override
    public TransitionConfigurer<S, E, C> event(E event) {
        this.currentEvent = Objects.requireNonNull(event, "event must not be null");
        return this;
    }

    @Override
    public TransitionConfigurer<S, E, C> guard(Guard<S, E, C> guard) {
        this.currentGuard = Objects.requireNonNull(guard, "guard must not be null");
        return this;
    }

    @Override
    public TransitionConfigurer<S, E, C> action(Action<S, E, C> action) {
        this.currentAction = Objects.requireNonNull(action, "action must not be null");
        return this;
    }

    @Override
    public TransitionConfigurer<S, E, C> and() {
        if (currentSource == null || currentEvent == null || currentTarget == null) {
            throw new IllegalStateException(
                    "Transition must have source, event, and target set before calling and()");
        }
        transitions.add(new Transition<>(
                currentSource, currentEvent, currentTarget,
                currentGuard, currentAction, currentKind));
        resetCurrent();
        return this;
    }

    @Override
    public List<Transition<S, E, C>> getTransitions() {
        // Auto-complete any in-progress transition
        if (currentSource != null && currentEvent != null && currentTarget != null) {
            transitions.add(new Transition<>(
                    currentSource, currentEvent, currentTarget,
                    currentGuard, currentAction, currentKind));
            resetCurrent();
        }
        return Collections.unmodifiableList(transitions);
    }

    private void resetCurrent() {
        currentSource = null;
        currentTarget = null;
        currentEvent = null;
        currentGuard = null;
        currentAction = null;
        currentKind = TransitionKind.EXTERNAL;
    }
}
