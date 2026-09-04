# 07. 配置模式校验

> 版本：1.0 | 最后更新：2026-09-01
> 优先级：P0 | 预估工作量：1-2 天

## 1. 问题陈述

### 1.1 当前问题

市场配置目前定义为没有正式校验的 Java records。无效配置可以在运行时创建，导致：
- 缺少必填字段时出现 NullPointerExceptions
- 值超出范围时出现非法参数错误
- 逻辑不一致（例如启用满意度调查但没有满意度调查类型）
- 枚举值中的拼写错误（例如 "GENESY" 而不是 "GENESYS"）
- 配置错误仅在生产环境中（部署后）才被发现

### 1.2 症状

- **运行时崩溃**：带 null `genesysOrgId` 的配置在 Genesys 动作执行时导致 NPE
- **静默错误行为**：`customerIdleSeconds: -1` 导致计时器立即触发
- **拼写错误 bug**：`fallbackRoutingStrategy: "REQUE"` 静默回退到默认值
- **无早期反馈**：开发人员在配置导致 bug 之前不知道配置无效
- **环境特定**：配置在 dev（默认值）中工作但在 prod（覆盖值）中失败

### 1.3 目标

- 在配置到达运行时之前校验配置格式（模式）
- 校验配置语义（逻辑一致性），超越格式
- 在 PR 时（CI）捕获错误，而不是生产环境
- 提供清晰、可操作的错误消息
- 同时支持 JSON Schema（格式）和自定义 Java 校验器（语义）

---

## 2. 设计概述

### 2.1 三层校验

```
┌─────────────────────────────────────────────────────────────┐
│                   配置校验层                     │
├─────────────────────────────────────────────────────────────┤
│                                                                 │
│  层 1: JSON 模式校验（格式）                      │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ • 必填字段存在                                 │  │
│  │ • 字段类型正确（字符串、数字、布尔、数组）   │  │
│  │ • 枚举值有效（来自允许列表）                   │  │
│  │ • 数字范围（最小/最大）                                │  │
│  │ • 字符串模式（正则表达式，例如市场代码格式）       │  │
│  │ • 嵌套对象结构                                  │  │
│  └─────────────────────────────────────────────────────────┘  │
│                              │                                  │
│                              ▼                                  │
│  层 2: 语义校验（逻辑）                          │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ • 跨字段依赖（如果 A 则需要 B）        │  │
│  │ • 跨字段一致性（A 和 B 必须兼容）   │  │
│  │ • 业务规则校验（超时排序、重试限制）│  │
│  │ • 扩展存在性（引用的扩展必须存在）  │  │
│  │ • 连接器配置完整性（如果启用，端点已设置） │  │
│  └─────────────────────────────────────────────────────────┘  │
│                              │                                  │
│                              ▼                                  │
│  层 3: 状态机可达性（运行时）                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ • 每个非终态至少有一个出向     │  │
│  │   迁移（使用此配置的 guard）                  │  │
│  │ • 每个状态都可从初始状态到达             │  │
│  │ • 无死状态（永远无法进入的状态）       │  │
│  │ • 终态无出向迁移             │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 校验结果

```java
public record ValidationResult(
    boolean valid,
    List<ValidationIssue> issues
) {
    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
    }

    public boolean hasWarnings() {
        return issues.stream().anyMatch(i -> i.severity() == Severity.WARNING);
    }

    public static ValidationResult pass() {
        return new ValidationResult(true, List.of());
    }
}

public record ValidationIssue(
    Severity severity,        // ERROR, WARNING, INFO
    String field,             // 例如 "timeouts.customerIdleSeconds"
    String message,           // 人类可读的消息
    String code,              // 机器可读的代码，例如 "REQUIRED_FIELD_MISSING"
    Object actualValue,       // 无效值（用于调试）
    String suggestion         // 建议的修复
) {}

public enum Severity { ERROR, WARNING, INFO }
```

---

## 3. 详细设计

### 3.1 层 1：JSON 模式

#### 3.1.1 模式定义

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "CBOL Market Config",
  "type": "object",
  "required": ["market", "version", "timeouts", "features", "businessRules"],
  "additionalProperties": false,
  "properties": {
    "market": {
      "type": "string",
      "enum": ["HK", "SG", "UK", "US", "JP", "AU", "DE", "FR", "DEFAULT"],
      "description": "Market identifier"
    },
    "version": {
      "type": "string",
      "pattern": "^\\d+\\.\\d+\\.\\d+$",
      "description": "Semantic version (MAJOR.MINOR.PATCH)"
    },
    "inherits": {
      "type": "array",
      "items": { "type": "string" },
      "maxItems": 5,
      "description": "Parent config profiles to inherit from"
    },
    "timeouts": {
      "type": "object",
      "required": ["customerIdleSeconds", "transferTimeoutSeconds", "endingGraceSeconds"],
      "additionalProperties": false,
      "properties": {
        "customerIdleSeconds": {
          "type": "integer", "minimum": 30, "maximum": 3600,
          "description": "Customer idle timeout (30s - 1h)"
        },
        "transferTimeoutSeconds": {
          "type": "integer", "minimum": 10, "maximum": 600,
          "description": "Transfer timeout (10s - 10min)"
        },
        "endingGraceSeconds": {
          "type": "integer", "minimum": 5, "maximum": 300,
          "description": "Ending grace period (5s - 5min)"
        },
        "surveyTimeoutSeconds": {
          "type": "integer", "minimum": 30, "maximum": 1800,
          "description": "Survey timeout (30s - 30min)"
        }
      }
    },
    "features": {
      "type": "object",
      "required": ["surveyEnabled", "transferEnabled"],
      "additionalProperties": false,
      "properties": {
        "surveyEnabled": { "type": "boolean" },
        "transferEnabled": { "type": "boolean" },
        "genesysEnabled": { "type": "boolean" },
        "aibotEnabled": { "type": "boolean" },
        "regulatoryAuditEnabled": { "type": "boolean" }
      }
    },
    "businessRules": {
      "type": "object",
      "required": ["fallbackRoutingStrategy", "transferTarget"],
      "additionalProperties": false,
      "properties": {
        "fallbackRoutingStrategy": {
          "type": "string",
          "enum": ["DROP", "REQUEUE", "FALLBACK_QUEUE", "RETRY_THEN_DROP"]
        },
        "surveyType": {
          "type": "string",
          "enum": ["CSAT", "NPS", "CES"]
        },
        "transferTarget": {
          "type": "string",
          "enum": ["GENESYS", "INTERNAL_QUEUE", "AIBOT"]
        },
        "maxTransferRetries": {
          "type": "integer", "minimum": 0, "maximum": 10
        }
      }
    },
    "connectors": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "aibotEndpoint": {
          "type": "string",
          "pattern": "^https?://.*",
          "description": "AIBot API endpoint URL"
        },
        "genesysOrgId": {
          "type": "string",
          "pattern": "^[a-zA-Z0-9-]+$"
        },
        "websocketEndpoint": {
          "type": "string",
          "pattern": "^wss?://.*"
        }
      }
    },
    "extensions": {
      "type": "array",
      "items": { "type": "string" },
      "maxItems": 10,
      "description": "Enabled market extension names"
    }
  }
}
```

#### 3.1.2 模式校验器

```java
public class JsonSchemaValidator {

    private final JsonSchema schema;

    public JsonSchemaValidator(URL schemaUrl) {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        this.schema = factory.getSchema(schemaUrl);
    }

    public List<ValidationIssue> validate(JsonNode config) {
        Set<ValidationMessage> messages = schema.validate(config);
        return messages.stream()
                .map(this::toIssue)
                .collect(Collectors.toList());
    }

    private ValidationIssue toIssue(ValidationMessage msg) {
        return new ValidationIssue(
                Severity.ERROR,
                msg.getProperty(),
                msg.getMessage(),
                mapErrorCode(msg),
                null,
                suggestFix(msg)
        );
    }

    private String mapErrorCode(ValidationMessage msg) {
        if (msg.getMessage().contains("required")) return "REQUIRED_FIELD_MISSING";
        if (msg.getMessage().contains("enum")) return "INVALID_ENUM_VALUE";
        if (msg.getMessage().contains("minimum") || msg.getMessage().contains("maximum"))
            return "VALUE_OUT_OF_RANGE";
        if (msg.getMessage().contains("pattern")) return "INVALID_FORMAT";
        if (msg.getMessage().contains("type")) return "INVALID_TYPE";
        return "SCHEMA_VALIDATION_ERROR";
    }
}
```

### 3.2 层 2：语义校验器

```java
public class SemanticConfigValidator {

    private final ExtensionRegistry extensionRegistry;

    public ValidationResult validate(MergedMarketConfig config) {
        List<ValidationIssue> issues = new ArrayList<>();

        // 规则 1: surveyEnabled → 需要 surveyType
        if (config.surveyEnabled() && config.surveyType() == null) {
            issues.add(error("features.surveyEnabled",
                    "surveyEnabled is true but surveyType is not set",
                    "SURVEY_TYPE_REQUIRED",
                    "Set surveyType to one of: CSAT, NPS, CES"));
        }

        // 规则 2: genesysEnabled → 需要 genesysOrgId
        if (config.genesysEnabled() && isBlank(config.genesysOrgId())) {
            issues.add(error("features.genesysEnabled",
                    "genesysEnabled is true but genesysOrgId is not set",
                    "GENESYS_ORG_REQUIRED",
                    "Set connectors.genesysOrgId to your Genesys organization ID"));
        }

        // 规则 3: transferEnabled=false 但 fallbackRoutingStrategy=REQUEUE（警告）
        if (!config.transferEnabled() && "REQUEUE".equals(config.fallbackRoutingStrategy())) {
            issues.add(warning("businessRules.fallbackRoutingStrategy",
                    "transferEnabled=false but fallbackRoutingStrategy=REQUEUE (no transfers to requeue)",
                    "INCONSISTENT_FALLBACK_STRATEGY",
                    "Consider changing fallbackRoutingStrategy to DROP"));
        }

        // 规则 4: 超时逻辑排序
        if (config.transferTimeoutSeconds() >= config.customerIdleSeconds()) {
            issues.add(warning("timeouts.transferTimeoutSeconds",
                    String.format("transferTimeout (%ds) >= customerIdle (%ds) — transfer may timeout before idle detection",
                            config.transferTimeoutSeconds(), config.customerIdleSeconds()),
                    "TIMEOUT_ORDERING",
                    "Ensure transferTimeout < customerIdle"));
        }

        // 规则 5: surveyTimeout > endingGrace（如果启用满意度调查）
        if (config.surveyEnabled() && config.surveyTimeoutSeconds() != null
                && config.endingGraceSeconds() > config.surveyTimeoutSeconds()) {
            issues.add(warning("timeouts.endingGraceSeconds",
                    String.format("endingGrace (%ds) > surveyTimeout (%ds) — conversation may close before survey completes",
                            config.endingGraceSeconds(), config.surveyTimeoutSeconds()),
                    "SURVEY_TIMEOUT_TOO_SHORT",
                    "Ensure surveyTimeout > endingGrace"));
        }

        // 规则 6: maxTransferRetries 合理
        if (config.maxTransferRetries() > 5) {
            issues.add(warning("businessRules.maxTransferRetries",
                    "maxTransferRetries=" + config.maxTransferRetries() + " is unusually high (>5)",
                    "HIGH_RETRY_COUNT",
                    "Consider reducing to 3 or fewer"));
        }

        // 规则 7: 扩展名称必须存在于注册表中
        for (String ext : config.enabledExtensions()) {
            if (!extensionRegistry.exists(ext)) {
                issues.add(error("extensions",
                        "Unknown extension: " + ext,
                        "UNKNOWN_EXTENSION",
                        "Available extensions: " + extensionRegistry.listNames()));
            }
        }

        // 规则 8: aibotEnabled → 需要 aibotEndpoint
        if (config.aibotEnabled() && isBlank(config.aibotEndpoint())) {
            issues.add(error("features.aibotEnabled",
                    "aibotEnabled is true but aibotEndpoint is not set",
                    "AIBOT_ENDPOINT_REQUIRED",
                    "Set connectors.aibotEndpoint to your AIBot API URL"));
        }

        // 规则 9: transferTarget=GENESYS 但 genesysEnabled=false
        if ("GENESYS".equals(config.transferTarget()) && !config.genesysEnabled()) {
            issues.add(error("businessRules.transferTarget",
                    "transferTarget=GENESYS but genesysEnabled=false",
                    "INCONSISTENT_TRANSFER_TARGET",
                    "Either set genesysEnabled=true or change transferTarget"));
        }

        // 规则 10: 市场代码匹配文件名
        // （在加载时校验，不在此处）

        return new ValidationResult(issues.stream().noneMatch(i -> i.severity() == Severity.ERROR), issues);
    }

    private ValidationIssue error(String field, String message, String code, String suggestion) {
        return new ValidationIssue(Severity.ERROR, field, message, code, null, suggestion);
    }

    private ValidationIssue warning(String field, String message, String code, String suggestion) {
        return new Issue(Severity.WARNING, field, message, code, null, suggestion);
    }
}
```

### 3.3 层 3：可达性检查器

```java
public class StateMachineReachabilityChecker {

    public List<ValidationIssue> check(StateMachine<...> machine, StateMachineMarketConfig config) {
        List<ValidationIssue> issues = new ArrayList<>();
        TransitionResolver resolver = new TransitionResolver();
        Map<TransitionKey, ConversationState> effective = resolver.resolve(machine, config);

        // 1. 构建邻接图
        Map<ConversationState, Set<ConversationState>> adjacency = new HashMap<>();
        for (ConversationState state : ConversationState.values()) {
            adjacency.put(state, new HashSet<>());
        }
        for (Map.Entry<TransitionKey, ConversationState> entry : effective.entrySet()) {
            adjacency.get(entry.getKey().state()).add(entry.getValue());
        }

        // 2. 检查每个非终态都有出向迁移
        for (ConversationState state : ConversationState.values()) {
            if (isTerminal(state)) continue;
            if (adjacency.get(state).isEmpty()) {
                issues.add(error("states." + state,
                        "State " + state + " has no outgoing transitions with current config",
                        "DEAD_END_STATE",
                        "Add a transition from " + state + " or check guard conditions"));
            }
        }

        // 3. 从初始状态进行 BFS 以查找可达状态
        Set<ConversationState> reachable = bfs(ConversationState.INITIATED, adjacency);

        // 4. 检查不可达状态
        for (ConversationState state : ConversationState.values()) {
            if (!reachable.contains(state)) {
                issues.add(warning("states." + state,
                        "State " + state + " is not reachable from INITIATED with current config",
                        "UNREACHABLE_STATE",
                        "Add a transition leading to " + state + " or remove it"));
            }
        }

        // 5. 检查终态无出向迁移
        for (ConversationState state : ConversationState.values()) {
            if (isTerminal(state) && !adjacency.get(state).isEmpty()) {
                issues.add(warning("states." + state,
                        "Terminal state " + state + " has outgoing transitions",
                        "TERMINAL_STATE_WITH_TRANSITIONS",
                        "Terminal states should have no outgoing transitions"));
            }
        }

        return issues;
    }

    private boolean isTerminal(ConversationState state) {
        return state == ConversationState.CLOSED;
    }

    private Set<ConversationState> bfs(ConversationState start,
                                         Map<ConversationState, Set<ConversationState>> adjacency) {
        Set<ConversationState> visited = new HashSet<>();
        Queue<ConversationState> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            ConversationState current = queue.poll();
            for (ConversationState next : adjacency.get(current)) {
                if (!visited.contains(next)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return visited;
    }
}
```

### 3.4 统一校验器

```java
public class UnifiedConfigValidator {

    private final JsonSchemaValidator schemaValidator;
    private final SemanticConfigValidator semanticValidator;
    private final StateMachineReachabilityChecker reachabilityChecker;

    public ValidationResult validateAll(JsonNode rawConfig, MergedMarketConfig mergedConfig,
                                          StateMachine<...> machine) {
        List<ValidationIssue> allIssues = new ArrayList<>();

        // 层 1: 模式
        allIssues.addAll(schemaValidator.validate(rawConfig));

        // 如果模式有错误，跳过更深层校验（配置格式错误）
        if (allIssues.stream().anyMatch(i -> i.severity() == Severity.ERROR)) {
            return new ValidationResult(false, allIssues);
        }

        // 层 2: 语义
        ValidationResult semanticResult = semanticValidator.validate(mergedConfig);
        allIssues.addAll(semanticResult.issues());

        // 层 3: 可达性
        allIssues.addAll(reachabilityChecker.check(machine, mergedConfig));

        boolean valid = allIssues.stream().noneMatch(i -> i.severity() == Severity.ERROR);
        return new ValidationResult(valid, allIssues);
    }
}
```

### 3.5 示例校验报告

```
=== 配置校验报告: markets/sg.yaml ===
状态: ❌ 失败（2 个错误，1 个警告）

─── 错误 ───

  1. [REQUIRED_FIELD_MISSING] features.surveyEnabled
     消息: surveyEnabled is true but surveyType is not set
     建议: Set surveyType to one of: CSAT, NPS, CES

  2. [UNKNOWN_EXTENSION] extensions
     消息: Unknown extension: SGSpecialAudit
     可用扩展: HKRegulatoryAudit, UKGdprRetention, DefaultAudit
     建议: Remove SGSpecialAudit or register it in ExtensionRegistry

─── 警告 ───

  1. [TIMEOUT_ORDERING] timeouts.transferTimeoutSeconds
     消息: transferTimeout (300s) >= customerIdle (300s) — transfer may timeout before idle detection
     建议: Ensure transferTimeout < customerIdle

─── 可达性 ───
  ✓ 所有非终态都有出向迁移
  ⚠ 状态 IN_PROGRESS 不可达（surveyEnabled=false，但状态已定义）
  ✓ 终态 CLOSED 无出向迁移
```

---

## 4. 实施路线图

### 阶段 1：JSON 模式（0.5 天）
- [ ] 为市场配置编写 JSON Schema（所有字段、枚举、范围、模式）
- [ ] 使用 networknt/json-schema-validator 实现 `JsonSchemaValidator`
- [ ] 将模式校验添加到 CI 管道
- [ ] 每条校验规则的单元测试

### 阶段 2：语义校验器（0.5 天）
- [ ] 实现带 10+ 条规则的 `SemanticConfigValidator`
- [ ] 实现 `ValidationResult` 和 `ValidationIssue` 数据结构
- [ ] 实现用于扩展存在性检查的 `ExtensionRegistry`
- [ ] 每条语义规则的单元测试

### 阶段 3：可达性检查器（0.5 天）
- [ ] 实现 `StateMachineReachabilityChecker`（BFS、死端、不可达检查）
- [ ] 与 `TransitionResolver` 集成（来自文档 02）
- [ ] 可达性场景的单元测试

### 阶段 4：统一校验器与 CI（0.5 天）
- [ ] 实现 `UnifiedConfigValidator`（三层编排）
- [ ] 实现校验报告生成器（markdown/JSON）
- [ ] 集成到 CI：在每个 PR 上校验所有配置
- [ ] 将校验报告作为 PR 评论发布
- [ ] 添加用于本地使用的 `validate-config` Maven 目标

---

## 5. 风险评估

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|-----------|--------|------------|
| 模式过于严格（阻止有效配置） | 中 | 中 | 从宽松模式开始（警告而非错误）；随时间收紧；允许 `additionalProperties` 以实现可扩展性 |
| 语义规则有误报 | 中 | 中 | 警告不阻止；错误仅用于明显违规；允许在配置注释中使用 `@SuppressWarnings` |
| 可达性检查器误报（状态有意不可达） | 低 | 低 | 报告为警告，不是错误；记录哪些状态按市场有意不可达 |
| 校验减慢 CI | 低 | 低 | 仅校验变更的配置；缓存校验结果；模式校验快速（<100ms） |
| 模式和 Java 配置漂移（模式说一件事，Java 代码说另一件事） | 中 | 中 | 从模式生成 Java 配置类（反之亦然）；单一真相源 |
| 校验器本身有 bug（遗漏无效配置） | 低 | 中 | 用已知无效配置测试校验器；变异测试；定期审查校验规则 |

---

## 6. 成功标准

- [ ] 100% 的配置字段被 JSON Schema 覆盖（类型、枚举、范围、模式）
- [ ] 至少实现 10 条语义校验规则
- [ ] 可达性检查器捕获死端和不可达状态
- [ ] 配置 PR 自动包含校验报告作为评论
- [ ] 无效配置（带错误）无法合并（CI 阻止）
- [ ] 校验报告包含每个错误的可操作建议
- [ ] 可通过 `./mvnw validate-config` 进行本地校验
- [ ] 每个配置的校验运行时间 < 1 秒
- [ ] 所有现有配置通过校验（无误报）
