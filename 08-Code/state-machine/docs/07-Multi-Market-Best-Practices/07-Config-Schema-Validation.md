# 07. Config Schema Validation

> Version: 1.0 | Last Updated: 2026-09-01
> Priority: P0 | Estimated Effort: 1-2 days

## 1. Problem Statement

### 1.1 Current Issue
Market configuration is currently defined as Java records with no formal validation. Invalid configs can be created at runtime, causing:
- NullPointerExceptions when required fields are missing
- Illegal argument errors when values are out of range
- Logical inconsistencies (e.g., survey enabled but no survey type)
- Typos in enum values (e.g., "GENESY" instead of "GENESYS")
- Config errors discovered only in production (after deployment)

### 1.2 Symptoms
- **Runtime crashes**: Config with null `genesysOrgId` causes NPE when Genesys action executes
- **Silent misbehavior**: `customerIdleSeconds: -1` causes timer to fire immediately
- **Typo bugs**: `fallbackRoutingStrategy: "REQUE"` silently falls back to default
- **No early feedback**: Developer doesn't know config is invalid until it causes a bug
- **Environment-specific**: Config works in dev (default values) but fails in prod (overridden values)

### 1.3 Goals
- Validate config format (schema) before it reaches runtime
- Validate config semantics (logical consistency) beyond format
- Catch errors at PR time (CI), not production
- Provide clear, actionable error messages
- Support both JSON Schema (format) and custom Java validators (semantics)

---

## 2. Design Overview

### 2.1 Three-Layer Validation

```
┌─────────────────────────────────────────────────────────────┐
│                   Config Validation Layers                     │
├─────────────────────────────────────────────────────────────┤
│                                                                 │
│  Layer 1: JSON Schema Validation (Format)                      │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ • Required fields present                                 │  │
│  │ • Field types correct (string, number, boolean, array)   │  │
│  │ • Enum values valid (from allowed list)                   │  │
│  │ • Numeric ranges (min/max)                                │  │
│  │ • String patterns (regex, e.g., market code format)       │  │
│  │ • Nested object structure                                  │  │
│  └─────────────────────────────────────────────────────────┘  │
│                              │                                  │
│                              ▼                                  │
│  Layer 2: Semantic Validation (Logic)                          │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ • Cross-field dependencies (if A then B required)        │  │
│  │ • Cross-field consistency (A and B must be compatible)   │  │
│  │ • Business rule validation (timeout ordering, retry limits)│  │
│  │ • Extension existence (referenced extensions must exist)  │  │
│  │ • Connector config completeness (if enabled, endpoint set) │  │
│  └─────────────────────────────────────────────────────────┘  │
│                              │                                  │
│                              ▼                                  │
│  Layer 3: State Machine Reachability (Runtime)                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ • Every non-terminal state has at least one outgoing     │  │
│  │   transition (with this config's guards)                  │  │
│  │ • Every state is reachable from initial state             │  │
│  │ • No dead states (states that can never be entered)       │  │
│  │ • Terminal states have no outgoing transitions             │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Validation Result

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
    String field,             // e.g., "timeouts.customerIdleSeconds"
    String message,           // human-readable message
    String code,              // machine-readable code, e.g., "REQUIRED_FIELD_MISSING"
    Object actualValue,       // the invalid value (for debugging)
    String suggestion         // suggested fix
) {}

public enum Severity { ERROR, WARNING, INFO }
```

---

## 3. Detailed Design

### 3.1 Layer 1: JSON Schema

#### 3.1.1 Schema Definition

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

#### 3.1.2 Schema Validator

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

### 3.2 Layer 2: Semantic Validator

```java
public class SemanticConfigValidator {

    private final ExtensionRegistry extensionRegistry;

    public ValidationResult validate(MergedMarketConfig config) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Rule 1: surveyEnabled → surveyType required
        if (config.surveyEnabled() && config.surveyType() == null) {
            issues.add(error("features.surveyEnabled",
                    "surveyEnabled is true but surveyType is not set",
                    "SURVEY_TYPE_REQUIRED",
                    "Set surveyType to one of: CSAT, NPS, CES"));
        }

        // Rule 2: genesysEnabled → genesysOrgId required
        if (config.genesysEnabled() && isBlank(config.genesysOrgId())) {
            issues.add(error("features.genesysEnabled",
                    "genesysEnabled is true but genesysOrgId is not set",
                    "GENESYS_ORG_REQUIRED",
                    "Set connectors.genesysOrgId to your Genesys organization ID"));
        }

        // Rule 3: transferEnabled=false but fallbackRoutingStrategy=REQUEUE (warning)
        if (!config.transferEnabled() && "REQUEUE".equals(config.fallbackRoutingStrategy())) {
            issues.add(warning("businessRules.fallbackRoutingStrategy",
                    "transferEnabled=false but fallbackRoutingStrategy=REQUEUE (no transfers to requeue)",
                    "INCONSISTENT_FALLBACK_STRATEGY",
                    "Consider changing fallbackRoutingStrategy to DROP"));
        }

        // Rule 4: Timeout logical ordering
        if (config.transferTimeoutSeconds() >= config.customerIdleSeconds()) {
            issues.add(warning("timeouts.transferTimeoutSeconds",
                    String.format("transferTimeout (%ds) >= customerIdle (%ds) — transfer may timeout before idle detection",
                            config.transferTimeoutSeconds(), config.customerIdleSeconds()),
                    "TIMEOUT_ORDERING",
                    "Ensure transferTimeout < customerIdle"));
        }

        // Rule 5: surveyTimeout > endingGrace (if survey enabled)
        if (config.surveyEnabled() && config.surveyTimeoutSeconds() != null
                && config.endingGraceSeconds() > config.surveyTimeoutSeconds()) {
            issues.add(warning("timeouts.endingGraceSeconds",
                    String.format("endingGrace (%ds) > surveyTimeout (%ds) — conversation may close before survey completes",
                            config.endingGraceSeconds(), config.surveyTimeoutSeconds()),
                    "SURVEY_TIMEOUT_TOO_SHORT",
                    "Ensure surveyTimeout > endingGrace"));
        }

        // Rule 6: maxTransferRetries reasonable
        if (config.maxTransferRetries() > 5) {
            issues.add(warning("businessRules.maxTransferRetries",
                    "maxTransferRetries=" + config.maxTransferRetries() + " is unusually high (>5)",
                    "HIGH_RETRY_COUNT",
                    "Consider reducing to 3 or fewer"));
        }

        // Rule 7: Extension names must exist in registry
        for (String ext : config.enabledExtensions()) {
            if (!extensionRegistry.exists(ext)) {
                issues.add(error("extensions",
                        "Unknown extension: " + ext,
                        "UNKNOWN_EXTENSION",
                        "Available extensions: " + extensionRegistry.listNames()));
            }
        }

        // Rule 8: aibotEnabled → aibotEndpoint required
        if (config.aibotEnabled() && isBlank(config.aibotEndpoint())) {
            issues.add(error("features.aibotEnabled",
                    "aibotEnabled is true but aibotEndpoint is not set",
                    "AIBOT_ENDPOINT_REQUIRED",
                    "Set connectors.aibotEndpoint to your AIBot API URL"));
        }

        // Rule 9: transferTarget=GENESYS but genesysEnabled=false
        if ("GENESYS".equals(config.transferTarget()) && !config.genesysEnabled()) {
            issues.add(error("businessRules.transferTarget",
                    "transferTarget=GENESYS but genesysEnabled=false",
                    "INCONSISTENT_TRANSFER_TARGET",
                    "Either set genesysEnabled=true or change transferTarget"));
        }

        // Rule 10: market code matches filename
        // (validated at load time, not here)

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

### 3.3 Layer 3: Reachability Checker

```java
public class StateMachineReachabilityChecker {

    public List<ValidationIssue> check(StateMachine<...> machine, StateMachineMarketConfig config) {
        List<ValidationIssue> issues = new ArrayList<>();
        TransitionResolver resolver = new TransitionResolver();
        Map<TransitionKey, ConversationState> effective = resolver.resolve(machine, config);

        // 1. Build adjacency graph
        Map<ConversationState, Set<ConversationState>> adjacency = new HashMap<>();
        for (ConversationState state : ConversationState.values()) {
            adjacency.put(state, new HashSet<>());
        }
        for (Map.Entry<TransitionKey, ConversationState> entry : effective.entrySet()) {
            adjacency.get(entry.getKey().state()).add(entry.getValue());
        }

        // 2. Check every non-terminal state has outgoing transitions
        for (ConversationState state : ConversationState.values()) {
            if (isTerminal(state)) continue;
            if (adjacency.get(state).isEmpty()) {
                issues.add(error("states." + state,
                        "State " + state + " has no outgoing transitions with current config",
                        "DEAD_END_STATE",
                        "Add a transition from " + state + " or check guard conditions"));
            }
        }

        // 3. BFS from initial state to find reachable states
        Set<ConversationState> reachable = bfs(ConversationState.INITIATED, adjacency);

        // 4. Check for unreachable states
        for (ConversationState state : ConversationState.values()) {
            if (!reachable.contains(state)) {
                issues.add(warning("states." + state,
                        "State " + state + " is not reachable from INITIATED with current config",
                        "UNREACHABLE_STATE",
                        "Add a transition leading to " + state + " or remove it"));
            }
        }

        // 5. Check terminal states have no outgoing transitions
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

### 3.4 Unified Validator

```java
public class UnifiedConfigValidator {

    private final JsonSchemaValidator schemaValidator;
    private final SemanticConfigValidator semanticValidator;
    private final StateMachineReachabilityChecker reachabilityChecker;

    public ValidationResult validateAll(JsonNode rawConfig, MergedMarketConfig mergedConfig,
                                          StateMachine<...> machine) {
        List<ValidationIssue> allIssues = new ArrayList<>();

        // Layer 1: Schema
        allIssues.addAll(schemaValidator.validate(rawConfig));

        // If schema has errors, skip deeper validation (config is malformed)
        if (allIssues.stream().anyMatch(i -> i.severity() == Severity.ERROR)) {
            return new ValidationResult(false, allIssues);
        }

        // Layer 2: Semantic
        ValidationResult semanticResult = semanticValidator.validate(mergedConfig);
        allIssues.addAll(semanticResult.issues());

        // Layer 3: Reachability
        allIssues.addAll(reachabilityChecker.check(machine, mergedConfig));

        boolean valid = allIssues.stream().noneMatch(i -> i.severity() == Severity.ERROR);
        return new ValidationResult(valid, allIssues);
    }
}
```

### 3.5 Example Validation Report

```
=== Config Validation Report: markets/sg.yaml ===
Status: ❌ FAILED (2 errors, 1 warning)

─── Errors ───

  1. [REQUIRED_FIELD_MISSING] features.surveyEnabled
     Message: surveyEnabled is true but surveyType is not set
     Suggestion: Set surveyType to one of: CSAT, NPS, CES

  2. [UNKNOWN_EXTENSION] extensions
     Message: Unknown extension: SGSpecialAudit
     Available extensions: HKRegulatoryAudit, UKGdprRetention, DefaultAudit
     Suggestion: Remove SGSpecialAudit or register it in ExtensionRegistry

─── Warnings ───

  1. [TIMEOUT_ORDERING] timeouts.transferTimeoutSeconds
     Message: transferTimeout (300s) >= customerIdle (300s) — transfer may timeout before idle detection
     Suggestion: Ensure transferTimeout < customerIdle

─── Reachability ───
  ✓ All non-terminal states have outgoing transitions
  ⚠ State SURVEY_IN_PROGRESS is not reachable (surveyEnabled=false, but state is defined)
  ✓ Terminal state CLOSED has no outgoing transitions
```

---

## 4. Implementation Roadmap

### Phase 1: JSON Schema (0.5 day)
- [ ] Write JSON Schema for market config (all fields, enums, ranges, patterns)
- [ ] Implement `JsonSchemaValidator` using networknt/json-schema-validator
- [ ] Add schema validation to CI pipeline
- [ ] Unit tests for each validation rule

### Phase 2: Semantic Validator (0.5 day)
- [ ] Implement `SemanticConfigValidator` with 10+ rules
- [ ] Implement `ValidationResult` and `ValidationIssue` data structures
- [ ] Implement `ExtensionRegistry` for extension existence check
- [ ] Unit tests for each semantic rule

### Phase 3: Reachability Checker (0.5 day)
- [ ] Implement `StateMachineReachabilityChecker` (BFS, dead-end, unreachable checks)
- [ ] Integrate with `TransitionResolver` (from doc 02)
- [ ] Unit tests for reachability scenarios

### Phase 4: Unified Validator & CI (0.5 day)
- [ ] Implement `UnifiedConfigValidator` (3-layer orchestration)
- [ ] Implement validation report generator (markdown/JSON)
- [ ] Integrate into CI: validate all configs on every PR
- [ ] Post validation report as PR comment
- [ ] Add `validate-config` Maven goal for local use

---

## 5. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Schema too strict (blocks valid configs) | Medium | Medium | Start with lenient schema (warnings not errors); tighten over time; allow `additionalProperties` for extensibility |
| Semantic rules have false positives | Medium | Medium | Warnings don't block; errors only for clear violations; allow `@SuppressWarnings` in config comments |
| Reachability checker false positives (state intentionally unreachable) | Low | Low | Report as warning, not error; document which states are intentionally unreachable per market |
| Validation slows down CI | Low | Low | Validate only changed configs; cache validation results; schema validation is fast (<100ms) |
| Schema and Java config drift (schema says one thing, Java code another) | Medium | Medium | Generate Java config classes from schema (or vice versa); single source of truth |
| Validator itself has bugs (misses invalid configs) | Low | Medium | Test validator with known-invalid configs; mutation testing; regular review of validation rules |

---

## 6. Success Criteria

- [ ] 100% of config fields covered by JSON Schema (types, enums, ranges, patterns)
- [ ] At least 10 semantic validation rules implemented
- [ ] Reachability checker catches dead-end and unreachable states
- [ ] Config PRs automatically include validation report as comment
- [ ] Invalid config (with errors) cannot be merged (CI blocks)
- [ ] Validation report includes actionable suggestions for every error
- [ ] Local validation possible via `./mvnw validate-config`
- [ ] Validation runs in < 1 second per config
- [ ] All existing configs pass validation (no false positives)
