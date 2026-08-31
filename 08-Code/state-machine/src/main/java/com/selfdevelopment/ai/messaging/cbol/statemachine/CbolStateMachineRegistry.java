package com.selfdevelopment.ai.messaging.cbol.statemachine;

import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;
import com.selfdevelopment.ai.messaging.statemachine.registry.StateMachineRegistry;

/**
 * CBOL 业务层状态机注册中心（单例 holder）
 * 持有通用 StateMachineRegistry 实例，供各 Factory 和 Service 共享
 */
public final class CbolStateMachineRegistry {

    private static final StateMachineRegistry INSTANCE = new StateMachineRegistry();

    private CbolStateMachineRegistry() {
    }

    public static StateMachineRegistry getInstance() {
        return INSTANCE;
    }

    public static <S, E, C> void register(StateMachine<S, E, C> machine) {
        INSTANCE.register(machine);
    }

    public static <S, E, C> StateMachine<S, E, C> get(String machineId) {
        return INSTANCE.get(machineId);
    }

    /** 清空所有已注册的状态机（主要用于测试隔离） */
    public static void clear() {
        // 通用清空：尝试移除常见的 machine ID
        try { INSTANCE.unregister("conversation"); } catch (Exception ignored) {}
        try { INSTANCE.unregister("interaction"); } catch (Exception ignored) {}
    }
}
