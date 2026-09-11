package com.selfdevelopment.chatengine.action;

import com.selfdevelopment.chatengine.enums.ConversationFact;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark an Action class as handling a specific ConversationFact event.
 * <p>
 * This enables automatic Action-to-event binding without manual registration in the factory.
 * The ActionRegistry scans all Spring-managed Action beans and indexes them by the fact
 * declared in this annotation.
 * <p>
 * Usage:
 * <pre>{@code
 * @Component
 * @HandlesFact(ConversationFact.SESSION_STARTED)
 * public class SessionStartedAction implements Action<CbolStateContext> {
 *     // ...
 * }
 * }</pre>
 *
 * @see ConversationActionRegistry
 * @see com.selfdevelopment.chatengine.action.Action
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HandlesFact {

    /**
     * The ConversationFact event that this Action handles.
     *
     * @return the conversation fact
     */
    ConversationFact value();
}
