# 01. 三层配置继承

> 版本：1.0 | 最后更新：2026-09-01
> 优先级：P0 | 预估工作量：2-3 天

## 1. 问题陈述

### 1.1 当前问题

每个市场有独立的 `StateMachineMarketConfig`，没有继承关系。当全局默认值变化时（例如空闲超时从 300 秒改为 600 秒），每个市场配置都必须手动更新。被遗漏的市场会静默地偏离预期基线。

### 1.2 症状

- **配置重复**：80% 的配置值在跨市场相同，但在每个文件中重复
- **漂移风险**：全局变更需要 N 次手动更新；被遗漏的市场行为异常
- **无审计追踪**：难以回答"为什么 HK 有这个值？是有意的还是继承的？"
- **变更影响未知**：修改基础配置需要手动检查每个市场

### 1.3 目标

- 在适当的层级（基础/区域/市场）定义一次配置
- 允许较低层级显式覆盖较高层级
- 每个最终配置值都可追溯到其来源（基础 / 区域 / 市场覆盖）
- 全局变更自动传播，除非被显式覆盖

---

## 2. 设计概述

### 2.1 三层模型

```
┌─────────────────────────────────────────────────────┐
│  层 1: base-profile.yaml                          │
│  所有市场的全局默认值                      │
│  （超时、功能开关、回退策略）    │
└──────────────────────┬──────────────────────────────┘
                       │ 继承
┌──────────────────────▼──────────────────────────────┐
│  层 2: regional-profiles/                         │
│    apac.yaml  (HK, SG, JP, AU)                      │
│    emea.yaml  (UK, DE, FR)                           │
│    amer.yaml  (US, CA, BR)                           │
│  区域覆盖：监管、时区、语言  │
└──────────────────────┬──────────────────────────────┘
                       │ 继承
┌──────────────────────▼──────────────────────────────┐
│  层 3: market-profiles/                           │
│    hk.yaml  sg.yaml  uk.yaml  us.yaml  ...          │
│  市场特定覆盖：阈值、连接器   │
└─────────────────────────────────────────────────────┘
```

### 2.2 合并优先级
```
最终值 = 市场覆盖 > 区域覆盖 > 基础默认值
```

- 如果一个键在所有三个层级都存在，**市场**值获胜
- 如果一个键在基础和区域存在但不在市场，**区域**值获胜
- 如果一个键仅在基础存在，使用**基础**值

### 2.3 按类型的合并规则

| 类型 | 合并策略 | 示例 |
|------|---------------|---------|
| 标量（字符串、数字、布尔） | 直接覆盖 | `customerIdleSeconds: 300` → `600` |
| Map / 对象 | 深度合并（键级覆盖） | `connectors: {aibot: {...}}` 按键合并 |
| 列表（默认） | 替换 | `enabledExtensions: [X]` → `[Y]`（不是 `[X,Y]`） |
| 列表（追加模式） | 连接 | `enabledExtensions: [X]` + `[Y]` → `[X,Y]` |
| Null | 显式重置为 null（用"未设置"覆盖） | `genesysOrgId: null` 移除继承值 |

---

## 3. 详细设计

### 3.1 配置文件格式

#### base-profile.yaml
```yaml
# 全局默认值 — 所有市场继承这些值
profile: base
version: "2.0"

timeouts:
  customerIdleSeconds: 300
  transferTimeoutSeconds: 180
  endingGraceSeconds: 120
  surveyTimeoutSeconds: 300

features:
  surveyEnabled: false
  transferEnabled: true
  genesysEnabled: false
  aibotEnabled: true
  regulatoryAuditEnabled: false

businessRules:
  fallbackRoutingStrategy: DROP
  surveyType: CSAT
  transferTarget: AIBOT
  maxTransferRetries: 1

connectors:
  aibotEndpoint: https://aibot.default.example.com
  websocketEndpoint: wss://ws.default.example.com

extensions: []
```

#### regional-profiles/apac.yaml
```yaml
profile: apac
inherits: base
version: "2.0"

# APAC 监管：金融市场需要审计
features:
  regulatoryAuditEnabled: true

# APAC 通常由于流量更高而超时更短
timeouts:
  customerIdleSeconds: 240

businessRules:
  fallbackRoutingStrategy: REQUEUE
```

#### market-profiles/hk.yaml
```yaml
market: HK
inherits: [base, apac]    # 先从 base 继承，然后 apac 覆盖
version: "2.0"

# HK 特定：Genesys 集成，CSAT 满意度调查
features:
  genesysEnabled: true
  surveyEnabled: true

businessRules:
  transferTarget: GENESYS
  maxTransferRetries: 3

connectors:
  aibotEndpoint: https://aibot.hk.example.com
  genesysOrgId: hk-org-001

extensions:
  - HKRegulatoryAudit
```

### 3.2 配置合并引擎

```java
public class ConfigMergeEngine {

    /**
     * 从 base → regional → market 合并配置层。
     * 返回完全解析的、不可变的 StateMachineMarketConfig。
     */
    public MergedMarketConfig merge(String market) {
        MarketProfile marketProfile = loader.loadMarket(market);
        List<String> inheritanceChain = resolveInheritanceChain(marketProfile);

        // 从空配置开始，按顺序应用每一层（base 先，market 后）
        Map<String, Object> merged = new HashMap<>();
        for (String layer : inheritanceChain) {
            Map<String, Object> layerConfig = loader.loadLayer(layer);
            deepMerge(merged, layerConfig);
        }

        // 校验并转换为类型化配置
        return validateAndConvert(merged, market);
    }

    /**
     * 按应用顺序解析继承链。
     * 示例：HK 继承 [base, apac] → ["base", "apac", "hk"]
     */
    private List<String> resolveInheritanceChain(MarketProfile profile) {
        List<String> chain = new ArrayList<>();
        collectInherited(profile.inherits(), chain, new HashSet<>());
        chain.add(profile.name());
        return chain;
    }

    /**
     * 深度合并：标量用 source 覆盖 target，
     * 递归合并 map，替换列表（除非追加模式）。
     */
    private void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                target.remove(key);  // 显式 null = 重置
            } else if (value instanceof Map && target.get(key) instanceof Map) {
                deepMerge((Map<String, Object>) target.get(key), (Map<String, Object>) value);
            } else {
                target.put(key, value);  // 标量或列表 = 替换
            }
        }
    }
}
```

### 3.3 带来源追踪的合并配置

最终配置中的每个值都应可追溯到其来源层：

```java
public record MergedMarketConfig(
    String market,
    List<String> inheritanceChain,    // ["base", "apac", "hk"]
    ConfigValue<Long> customerIdleSeconds,
    ConfigValue<Boolean> surveyEnabled,
    ConfigValue<String> transferTarget,
    // ... 所有其他字段
) {
    // ConfigValue 用其来源层包装值
    public record ConfigValue<T>(T value, String sourceLayer, boolean isOverride) {}
}

// 使用
MergedMarketConfig config = mergeEngine.merge("HK");
config.customerIdleSeconds().value();      // 240
config.customerIdleSeconds().sourceLayer(); // "apac"（继承，不是 HK 覆盖）
config.surveyEnabled().value();             // true
config.surveyEnabled().sourceLayer();       // "hk"（显式覆盖）
```

### 3.4 配置快照导出

为了审计和调试，导出带来源注解的最终合并配置：

```yaml
# HK 的导出快照（自动生成）
market: HK
inheritanceChain: [base, apac, hk]

timeouts:
  customerIdleSeconds:
    value: 240
    source: apac           # 从 apac 继承，未被 HK 覆盖
  transferTimeoutSeconds:
    value: 180
    source: base           # 从 base 继承
  endingGraceSeconds:
    value: 120
    source: base

features:
  surveyEnabled:
    value: true
    source: hk             # HK 显式覆盖
    overrides: base(false) # 显示它覆盖了什么
  genesysEnabled:
    value: true
    source: hk
    overrides: base(false)
```

---

## 4. 变更影响分析

### 4.1 影响分析工具

当基础或区域配置变化时，自动确定受影响的市场：

```java
public class ConfigImpactAnalyzer {

    /**
     * 给定变更的配置键及其新值，
     * 返回最终配置会变化的市场列表。
     */
    public ImpactReport analyzeImpact(String changedLayer, String key, Object newValue) {
        List<String> allMarkets = loader.listAllMarkets();
        List<MarketImpact> affected = new ArrayList<>();

        for (String market : allMarkets) {
            MergedMarketConfig before = mergeEngine.merge(market);
            // 模拟变更
            MergedMarketConfig after = simulateChange(before, changedLayer, key, newValue);

            if (!before.equals(after)) {
                affected.add(new MarketImpact(market, before, after, findDifference(before, after)));
            }
        }
        return new ImpactReport(changedLayer, key, newValue, affected);
    }
}
```

### 4.2 影响报告示例

```
=== 配置变更影响报告 ===
变更: base.features.surveyEnabled: false → true
受影响市场: 5 个中的 3 个

  市场 | 变更前 | 变更后  | 来源变更
  -------|--------|--------|--------------
  SG     | false  | true   | base（之前继承，现在变化）
  UK     | false  | true   | base（之前继承，现在变化）
  US     | false  | true   | base（之前继承，现在变化）

不受影响:
  HK     | true   | true   | 已覆盖 base（显式 true）
  JP     | false  | false  | 显式覆盖 base 为 false
```

---

## 5. 实施路线图

### 阶段 1：核心合并引擎（1 天）
- [ ] 定义带 `inherits` 字段的 `MarketProfile` record
- [ ] 实现带深度合并逻辑的 `ConfigMergeEngine`
- [ ] 实现继承链解析（带循环检测）
- [ ] 合并场景的单元测试（覆盖、深度合并、null 重置、列表替换）

### 阶段 2：来源追踪与快照（0.5 天）
- [ ] 实现带来源层追踪的 `ConfigValue<T>` 包装器
- [ ] 实现配置快照导出（带来源注解的 YAML）
- [ ] 来源追踪准确性的单元测试

### 阶段 3：影响分析工具（0.5 天）
- [ ] 实现 `ConfigImpactAnalyzer`
- [ ] 实现差异报告生成
- [ ] 与 CI 集成：在每个配置 PR 上运行影响分析

### 阶段 4：迁移（1 天）
- [ ] 将现有市场配置转换为三层格式
- [ ] 将公共值提取到 base-profile.yaml
- [ ] 将市场分组到区域配置文件（apac/emea/amer）
- [ ] 验证合并配置与当前行为匹配（回归测试）

---

## 6. 风险评估

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|-----------|--------|------------|
| 继承循环（A 继承 B，B 继承 A） | 低 | 高 | 链解析中的循环检测，启动时快速失败 |
| 深度合并对嵌套 map 产生意外结果 | 中 | 中 | 清晰的合并规则文档，快照导出用于验证 |
| 市场覆盖意外移除预期的基础值 | 中 | 高 | Null = 显式重置已文档化；影响分析显示移除的值 |
| 列表合并混淆（替换 vs 追加） | 中 | 中 | 默认 = 替换；追加模式需要显式 `__append: true` 标记 |
| 迁移破坏现有行为 | 中 | 高 | 回归测试：所有市场的合并配置必须等于当前配置 |
| 性能：每次请求都合并 | 低 | 低 | 启动/配置刷新时合并一次，缓存不可变结果 |

---

## 7. 成功标准

- [ ] 每个市场配置值都可追溯到其来源层（基础/区域/市场）
- [ ] 更改基础值自动传播到所有未覆盖的市场
- [ ] 配置 PR 自动包含影响分析报告
- [ ] 没有市场配置包含与基础相同的值（标记冗余覆盖）
- [ ] 继承循环在启动时被检测和拒绝
- [ ] 迁移：所有现有市场的合并配置与当前行为完全匹配
