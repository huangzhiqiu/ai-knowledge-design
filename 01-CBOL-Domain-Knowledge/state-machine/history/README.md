# State Machine Design — History Versions

> 历史版本归档，用于追踪设计演进。当前最新版本为 [version4](../event-driven-orchestration-design.md)。

## 版本索引

| 版本 | 文件 | 日期 | 核心变更 |
|------|------|------|----------|
| **version4（当前）** | [../event-driven-orchestration-design.md](../event-driven-orchestration-design.md) | 2026-08-31 | Transfer 失败直接回 INITIATED（不执行 rollback）；transferOutcome 简化为 NONE/CONNECTED/FAILED/TIMEOUT；endReason 统一 CUSTOMER_IDLE 为首选；Interaction TRANSFERRED 不要求回滚 |
| version3 | [event-driven-orchestration-design-v3.md](./event-driven-orchestration-design-v3.md) | 2026-08-31 | 双层状态机模型；标准事件语义（Request/Command/Fact/Result）；Conversation 7 状态 + Interaction 8 状态；ENDING 强治理；跨渠道转接含 rollback；Customer Idle 全覆盖；9 大场景验证 |

## version3 → version4 关键变更

| 变更点 | version3 | version4 |
|--------|----------|----------|
| Transfer 失败处理 | 执行 RollbackToSourceCmd，回 ACTIVE | 直接回 INITIATED（重新分配/兜底） |
| transferOutcome | NONE/CONNECTED/CONNECT_FAILED/ROLLBACK_OK/ROLLBACK_FAILED/TIMEOUT | NONE/CONNECTED/FAILED/TIMEOUT |
| endReason 顺序 | CUSTOMER_ENDED/AGENT_ENDED/BOT_ENDED/SYSTEM_ERROR/CUSTOMER_IDLE | CUSTOMER_IDLE/CUSTOMER_ENDED/AGENT_ENDED/BOT_ENDED/SYSTEM_ERROR |
| Interaction TRANSFERRED | 回滚到 IN_PROGRESS | 不要求回滚，由 CloseInteractions 或通道侧回收 |
| ROLLBACK_TO_SOURCE_* Facts | 存在 | 移除（Conversation 不再消费） |
| 文档标题 | 状态机管理与事件驱动编排详细设计 | 状态机管理与事件驱动编排详细设计（简化版·整篇最终稿） |

---

*History versions — last updated 2026-08-31*
