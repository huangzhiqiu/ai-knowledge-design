# State Machine Design — History Versions

> 历史版本归档，用于追踪设计演进。当前最新版本为 [version6](../event-driven-orchestration-design.md)（重构简化版）。

## 版本索引

| 版本 | 文件 | 日期 | 核心变更 |
|------|------|------|----------|
| **version6（当前）** | [../event-driven-orchestration-design.md](../event-driven-orchestration-design.md) | 2026-08-31 | 结构重构：14章→9章，参考 COLA 设计哲学；合并状态模型/事件模型/核心能力/关键流程/运行时机制；减少冗余，突出核心决策；保持 Multi-Market + TraceId + Transfer不回滚 + ENDING强治理全部功能 |
| version5 | [event-driven-orchestration-design-v5.md](./event-driven-orchestration-design-v5.md) | 2026-08-31 | 新增 Multi-Market 配置支持（market 级别差异化超时/开关/策略，配置热更新）；新增 TraceId 全链路追踪（贯穿事件/状态迁移/Action/外部调用，MDC 集成） |
| version4 | [event-driven-orchestration-design-v4.md](./event-driven-orchestration-design-v4.md) | 2026-08-31 | Transfer 失败直接回 INITIATED（不执行 rollback）；transferOutcome 简化为 NONE/CONNECTED/FAILED/TIMEOUT；endReason 统一 CUSTOMER_IDLE 为首选 |
| version3 | [event-driven-orchestration-design-v3.md](./event-driven-orchestration-design-v3.md) | 2026-08-31 | 双层状态机模型；标准事件语义（Request/Command/Fact/Result）；Conversation 7 状态 + Interaction 8 状态；ENDING 强治理；跨渠道转接含 rollback；Customer Idle 全覆盖 |

## version5 → version6 关键变更

| 变更点 | version5 | version6 |
|--------|----------|----------|
| 章节数量 | 14 章（含 4.5/4.6 插入章节） | 9 章（结构清晰） |
| 设计哲学 | 未明确 | 明确参考 COLA：无状态、表驱动、DSL、零依赖、极简核心 |
| 状态模型 | Conversation + Interaction 分两章 + 字段独立 | 合并为一章（2.1/2.2/2.3） |
| 事件模型 | 标准事件模型 + Facts 分两章 | 合并为一章（3.1/3.2） |
| 核心能力 | Market 配置(4.5) + TraceId(4.6) 独立 | 合并为"核心能力"一章（4.1/4.2） |
| 关键流程 | Transfer Flow(6) + ENDING(7) 分两章 | 合并为"关键流程"一章（6.1/6.2） |
| 运行时 | Monitor(9) + Action(10) 分两章 | 合并为"运行时机制"一章（7.1/7.2） |
| 场景验证 | 每个场景独立小节 | 合并为表格，更紧凑 |
| 功能完整性 | 完整 | 完整（无功能删减，仅结构重组） |

## version4 → version5 关键变更

| 变更点 | version4 | version5 |
|--------|----------|----------|
| Multi-Market 支持 | 无 | 新增 Market 配置管理，支持 market 级别差异化配置 |
| TraceId 追踪 | 无 | 新增全链路 TraceId，贯穿事件/状态迁移/Action/外部调用 |
| Conversation 标识字段 | 无 | 新增 conversationId、market、traceId、tenantId |
| Monitor 超时配置 | 硬编码默认值 | 从 marketConfig 动态获取，支持热更新 |
| Action 数据模型 | 无 traceId | 新增 traceId、parentSpanId、market 字段 |

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
