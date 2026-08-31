# State Machine Design — History Versions

> 历史版本归档，用于追踪设计演进。当前最新版本为 [version5](../event-driven-orchestration-design.md)。

## 版本索引

| 版本 | 文件 | 日期 | 核心变更 |
|------|------|------|----------|
| **version5（当前）** | [../event-driven-orchestration-design.md](../event-driven-orchestration-design.md) | 2026-08-31 | 新增 Multi-Market 配置支持（market 级别差异化超时/开关/策略，配置热更新）；新增 TraceId 全链路追踪（贯穿事件/状态迁移/Action/外部调用，MDC 集成）；Conversation 新增 market/traceId/conversationId 字段；Monitor 超时从 marketConfig 动态获取 |
| version4 | [event-driven-orchestration-design-v4.md](./event-driven-orchestration-design-v4.md) | 2026-08-31 | Transfer 失败直接回 INITIATED（不执行 rollback）；transferOutcome 简化为 NONE/CONNECTED/FAILED/TIMEOUT；endReason 统一 CUSTOMER_IDLE 为首选；Interaction TRANSFERRED 不要求回滚 |
| version3 | [event-driven-orchestration-design-v3.md](./event-driven-orchestration-design-v3.md) | 2026-08-31 | 双层状态机模型；标准事件语义（Request/Command/Fact/Result）；Conversation 7 状态 + Interaction 8 状态；ENDING 强治理；跨渠道转接含 rollback；Customer Idle 全覆盖；9 大场景验证 |

## version4 → version5 关键变更

| 变更点 | version4 | version5 |
|--------|----------|----------|
| Multi-Market 支持 | 无 | 新增 Market 配置管理，支持 market 级别差异化配置 |
| TraceId 追踪 | 无 | 新增全链路 TraceId，贯穿事件/状态迁移/Action/外部调用 |
| Conversation 标识字段 | 无 | 新增 conversationId、market、traceId、tenantId |
| Monitor 超时配置 | 硬编码默认值 | 从 marketConfig 动态获取，支持热更新 |
| Action 数据模型 | 无 traceId | 新增 traceId、parentSpanId、market 字段 |
| 配置热更新 | 无 | 支持配置中心推送、缓存失效、灰度发布、版本回滚 |
| 场景验证 | 6 个场景 | 新增 Multi-Market 差异化配置验证 + TraceId 全链路追踪验证（共 8 个） |

## version3 → version4 关键变更

| 变更点 | version3 | version4 |
|--------|----------|----------|
| Transfer 失败处理 | 执行 RollbackToSourceCmd，回 ACTIVE | 直接回 INITIATED（重新分配/兜底） |
| transferOutcome | NONE/CONNECTED/CONNECT_FAILED/ROLLBACK_OK/ROLLBACK_FAILED/TIMEOUT | NONE/CONNECTED/FAILED/TIMEOUT |
| endReason 顺序 | CUSTOMER_ENDED/AGENT_ENDED/BOT_ENDED/SYSTEM_ERROR/CUSTOMER_IDLE | CUSTOMER_IDLE/CUSTOMER_ENDED/AGENT_ENDED/BOT_ENDED/SYSTEM_ERROR |
| Interaction TRANSFERRED | 回滚到 IN_PROGRESS | 不要求回滚，由 CloseInteractions 或通道侧回收 |
| ROLLBACK_TO_SOURCE_* Facts | 存在 | 移除（Conversation 不再消费） |

---

*History versions — last updated 2026-08-31*
