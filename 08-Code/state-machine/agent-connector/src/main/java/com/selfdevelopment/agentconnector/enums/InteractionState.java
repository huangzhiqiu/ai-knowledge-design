package com.selfdevelopment.agentconnector.enums;

/**
 * Interaction (channel/connection layer) states.
 * <p>
 * Based on Event-Driven Orchestration Design (v4.0).
 * <p>
 * States:
 * <ul>
 *   <li>INITIATED - connection initiated, waiting for connection result</li>
 *   <li>CONNECTED - connection established, ready for messaging</li>
 *   <li>IN_PROGRESS - active messaging (first inbound received)</li>
 *   <li>DEGRADED - connection degraded (heartbeat miss, temporary issues)</li>
 *   <li>RECONNECTING - reconnection in progress</li>
 *   <li>CONSULT_TRANSFER - Genesys consult transfer (GENESYS ONLY)</li>
 *   <li>TRANSFERRED - cross-channel source detached marker</li>
 *   <li>CLOSED - terminal state</li>
 * </ul>
 */
public enum InteractionState {
    INITIATED,          // 连接已发起，等待连接结果
    CONNECTED,          // 连接已建立，准备消息收发
    IN_PROGRESS,        // 消息进行中（已收到第一条入站消息）
    DEGRADED,           // 连接降级（心跳丢失、临时问题）
    RECONNECTING,       // 重连中
    CONSULT_TRANSFER,   // GENESYS ONLY：咨询转接
    TRANSFERRED,        // 跨渠道 source detach 标记
    CLOSED              // 终态
}
