# 04. Config as Code & GitOps

> Version: 1.0 | Last Updated: 2026-09-01
> Priority: P1 | Estimated Effort: 2-3 days

## 1. Problem Statement

### 1.1 Current Issue
Market configuration is currently hardcoded in Java (`StateMachineMarketConfig.defaultConfig()`) or stored in a database without version control. Changes are made directly in production via admin consoles or database updates, with no review, no audit trail, and no rollback path.

### 1.2 Symptoms
- **No audit trail**: "Who changed HK's transfer timeout last Tuesday?" — unanswerable
- **No review process**: Config changes go straight to production, no peer review
- **No rollback**: Bad config can only be fixed by manually changing it back (if you remember the old value)
- **Environment drift**: Dev/Staging/Prod configs diverge silently
- **Accidental changes**: Fat-finger errors in admin console cause outages
- **No reproducibility**: Can't recreate a specific config state from a point in time

### 1.3 Goals
- All market configs stored as version-controlled files in Git
- Every change goes through PR review and automated validation
- Full audit trail: who changed what, when, why, and who approved
- One-click rollback to any previous config version
- Config changes are testable before deployment
- Config version is traceable in every state transition log

---

## 2. Design Overview

### 2.1 GitOps Workflow

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Developer   │────▶│  Git PR      │────▶│  CI Pipeline  │
│  edits config │     │  (review)    │     │  (validate)   │
└──────────────┘     └──────────────┘     └──────┬───────┘
                                                     │
                                          ┌──────────▼──────────┐
                                          │  Validation Gates:    │
                                          │  1. Schema validate   │
                                          │  2. Semantic validate │
                                          │  3. Impact analysis   │
                                          │  4. Unit tests        │
                                          └──────────┬──────────┘
                                                     │ pass
                                          ┌──────────▼──────────┐
                                          │  Merge to main       │
                                          └──────────┬──────────┘
                                                     │
                                          ┌──────────▼──────────┐
                                          │  Config Service      │
                                          │  (pulls from Git)    │
                                          └──────────┬──────────┘
                                                     │
                                          ┌──────────▼──────────┐
                                          │  Canary Release      │
                                          │  (5% → 20% → 100%)  │
                                          └──────────┬──────────┘
                                                     │
                                          ┌──────────▼──────────┐
                                          │  Production config   │
                                          │  (immutable snapshot)│
                                          └─────────────────────┘
```

### 2.2 Repository Structure

```
config-repo/
├── README.md                    # Config documentation and change process
├── schema/
│   └── market-config.schema.json   # JSON Schema for validation
├── base/
│   └── profile.yaml             # Global defaults (layer 1)
├── regional/
│   ├── apac.yaml                # APAC regional overrides (layer 2)
│   ├── emea.yaml
│   └── amer.yaml
├── markets/
│   ├── hk.yaml                  # HK market config (layer 3)
│   ├── sg.yaml
│   ├── uk.yaml
│   ├── us.yaml
│   └── jp.yaml
├── environments/
│   ├── dev.yaml                 # Environment-specific overrides
│   ├── staging.yaml
│   └── prod.yaml
└── CHANGELOG.md                 # Auto-generated changelog of config changes
```

---

## 3. Detailed Design

### 3.1 Config File Standards

#### 3.1.1 Required Metadata
Every config file must include metadata headers:

```yaml
# config/markets/hk.yaml
# Last modified: 2026-09-01 by @zhiqiu
# Approved by: @reviewer1, @reviewer2
# Related ticket: CBOL-1234
# Change reason: Increase transfer timeout for HK due to Genesys latency

market: HK
inherits: [base, apac]
version: "2.1.0"                    # Semantic version of this config
configVersion: "2026.09.01-hk-3"  # Unique version identifier

timeouts:
  customerIdleSeconds: 240
  transferTimeoutSeconds: 180
  # ...
```

#### 3.1.2 Versioning Strategy
- **Config semantic version**: `MAJOR.MINOR.PATCH`
  - MAJOR: breaking change (state machine definition changed)
  - MINOR: new feature / new market
  - PATCH: threshold / toggle change
- **Unique version ID**: `YYYY.MM.DD-{market}-{sequence}` for traceability
- Every state transition log includes `configVersion` for debugging

### 3.2 PR Template

```markdown
## Config Change Summary

**Markets affected**: HK, SG
**Config version**: 2.1.0 → 2.2.0
**Related ticket**: CBOL-1234

## Changes Made

| File | Key | Old Value | New Value | Reason |
|------|-----|-----------|-----------|--------|
| markets/hk.yaml | timeouts.transferTimeoutSeconds | 180 | 240 | Genesys latency increased |
| markets/sg.yaml | features.surveyEnabled | false | true | Launch CSAT survey in SG |

## Impact Analysis (auto-generated)

- HK: 1 config value changed, 0 transitions affected
- SG: 1 feature toggle changed, 2 transitions now active (SURVEY_START from ACTIVE/TRANSFERRED)
- No other markets affected

## Validation Results (auto-generated)

- [x] Schema validation passed
- [x] Semantic validation passed
- [x] State machine reachability check passed
- [x] Unit tests passed (327/327)
- [x] Compatibility matrix generated

## Rollback Plan

Revert this PR to restore previous config.
Estimated rollback time: < 2 minutes (config service auto-pulls from Git).

## Checklist

- [ ] I have tested this config in staging
- [ ] I have notified the market owner(s)
- [ ] I have considered the impact on monitoring/alerts
- [ ] I have updated the CHANGELOG
```

### 3.3 CI Validation Pipeline

#### 3.3.1 Pipeline Stages

```yaml
# .github/workflows/config-validation.yml
name: Config Validation

on:
  pull_request:
    paths:
      - 'config/**/*.yaml'
      - 'config/**/*.yml'

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: 1. Schema Validation
        run: |
          for f in config/markets/*.yaml; do
            ajv validate -s config/schema/market-config.schema.json -d "$f"
          done

      - name: 2. Semantic Validation
        run: ./mvnw exec:java -Dexec.mainClass="com.example.ConfigSemanticValidator"

      - name: 3. State Machine Reachability Check
        run: ./mvnw exec:java -Dexec.mainClass="com.example.ReachabilityChecker"

      - name: 4. Impact Analysis
        run: ./mvnw exec:java -Dexec.mainClass="com.example.ConfigImpactAnalyzer" --args="--pr"

      - name: 5. Generate Compatibility Matrix
        run: ./mvnw exec:java -Dexec.mainClass="com.example.MatrixGenerator"

      - name: 6. Run Unit Tests
        run: ./mvnw test

      - name: 7. Post Results as PR Comment
        uses: actions/github-script@v7
        with:
          script: |
            const results = require('./validation-results.json');
            const impact = require('./impact-report.md');
            const matrix = require('./compatibility-matrix.md');
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              body: `## Config Validation Results\n\n${results}\n\n## Impact Analysis\n\n${impact}\n\n<details><summary>Compatibility Matrix</summary>\n\n${matrix}\n</details>`
            });
```

#### 3.3.2 Semantic Validator

```java
public class ConfigSemanticValidator {

    public List<ValidationIssue> validate(MergedMarketConfig config) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Rule 1: surveyEnabled=true requires surveyType
        if (config.surveyEnabled() && config.surveyType() == null) {
            issues.add(ValidationIssue.error("surveyEnabled is true but surveyType is not set"));
        }

        // Rule 2: genesysEnabled=true requires genesysOrgId
        if (config.genesysEnabled() && config.genesysOrgId() == null) {
            issues.add(ValidationIssue.error("genesysEnabled is true but genesysOrgId is not set"));
        }

        // Rule 3: transferEnabled=false but fallbackRoutingStrategy=REQUEUE (warning)
        if (!config.transferEnabled() && "REQUEUE".equals(config.fallbackRoutingStrategy())) {
            issues.add(ValidationIssue.warning("transferEnabled=false but fallbackRoutingStrategy=REQUEUE (no transfer to requeue)"));
        }

        // Rule 4: Timeout logical ordering
        if (config.transferTimeoutSeconds() >= config.customerIdleSeconds()) {
            issues.add(ValidationIssue.warning(
                    "transferTimeout (" + config.transferTimeoutSeconds() +
                    "s) >= customerIdle (" + config.customerIdleSeconds() +
                    "s) — transfer may timeout before idle detection"));
        }

        // Rule 5: Ending grace should be shorter than survey timeout
        if (config.surveyEnabled() && config.endingGraceSeconds() > config.surveyTimeoutSeconds()) {
            issues.add(ValidationIssue.warning(
                    "endingGrace (" + config.endingGraceSeconds() +
                    "s) > surveyTimeout (" + config.surveyTimeoutSeconds() +
                    "s) — conversation may close before survey completes"));
        }

        // Rule 6: Max transfer retries reasonable
        if (config.maxTransferRetries() > 5) {
            issues.add(ValidationIssue.warning("maxTransferRetries=" + config.maxTransferRetries() + " is unusually high (>5)"));
        }

        // Rule 7: Extension names must exist in registry
        for (String ext : config.enabledExtensions()) {
            if (!extensionRegistry.exists(ext)) {
                issues.add(ValidationIssue.error("Unknown extension: " + ext));
            }
        }

        return issues;
    }
}
```

### 3.4 Config Service (Git Pull & Hot Reload)

```java
public class GitConfigService implements ConfigProvider {

    private final GitRepository gitRepo;
    private final ConfigMergeEngine mergeEngine;
    private final Map<String, MergedMarketConfig> cache = new ConcurrentHashMap<>();
    private volatile String currentCommit;
    private final ScheduledExecutorService watcher = Executors.newSingleThreadScheduledExecutor();

    public GitConfigService(String repoUrl, String branch, String localPath) {
        this.gitRepo = new GitRepository(repoUrl, branch, localPath);
        this.mergeEngine = new ConfigMergeEngine();
        this.currentCommit = gitRepo.getCurrentCommit();
        loadAllConfigs();

        // Poll for changes every 30 seconds
        watcher.scheduleAtFixedRate(this::checkForUpdates, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public MergedMarketConfig getConfig(String market) {
        return cache.get(market);
    }

    private void checkForUpdates() {
        try {
            String latestCommit = gitRepo.pullLatest();
            if (!latestCommit.equals(currentCommit)) {
                log.info("Config updated: {} → {}", currentCommit, latestCommit);
                loadAllConfigs();
                currentCommit = latestCommit;
                // Notify listeners (state machine, monitors, etc.)
                notifyConfigChanged(latestCommit);
            }
        } catch (Exception e) {
            log.error("Failed to check for config updates", e);
            // Don't crash — keep serving cached config
        }
    }

    private void loadAllConfigs() {
        List<String> markets = gitRepo.listMarkets();
        for (String market : markets) {
            try {
                MergedMarketConfig config = mergeEngine.merge(market);
                // Validate before caching
                List<ValidationIssue> issues = semanticValidator.validate(config);
                if (issues.stream().anyMatch(ValidationIssue::isError)) {
                    log.error("Config for market {} has validation errors, keeping old config", market);
                    continue;
                }
                cache.put(market, config);
            } catch (Exception e) {
                log.error("Failed to load config for market {}", market, e);
            }
        }
    }

    /**
     * Returns the current config version (Git commit hash) for audit logging.
     */
    public String getConfigVersion() {
        return currentCommit;
    }

    /**
     * Forces an immediate reload (for admin-triggered refresh).
     */
    public void forceReload() {
        checkForUpdates();
    }
}
```

### 3.5 Config Version in Audit Logs

Every state transition record includes the config version:

```java
StateTransitionRecord record = StateTransitionRecord.builder()
        .businessId(conversationId)
        .fromState(from.name())
        .toState(result.getTargetState().name())
        .fact(fact.name())
        .market(market)
        .configVersion(configService.getConfigVersion())  // Git commit hash
        .timestampMs(System.currentTimeMillis())
        .traceId(traceContext.traceId())
        .build();
```

This enables: "At 14:30, conversation X transitioned from ACTIVE to TRANSFERRED using config version abc1234 (which had transferTimeout=180s)."

### 3.6 Rollback Process

```
Scenario: Bad config deployed to production

1. Identify the bad commit: git log --oneline config/
2. Revert the PR: git revert <bad-commit-hash>
3. Push revert: git push origin main
4. Config service detects change within 30 seconds
5. New config loaded and validated
6. If validation passes, cache updated
7. All new events use reverted config
8. In-flight conversations continue with their current state
   (config changes don't retroactively affect running conversations)

Total rollback time: < 2 minutes
```

---

## 4. Implementation Roadmap

### Phase 1: Config Repository & Schema (0.5 day)
- [ ] Create config Git repository structure
- [ ] Write JSON Schema for market config
- [ ] Migrate existing configs to YAML files
- [ ] Add config metadata standards (version, change reason, ticket)

### Phase 2: CI Validation Pipeline (1 day)
- [ ] Implement schema validation in CI
- [ ] Implement semantic validator (7+ rules)
- [ ] Implement state machine reachability checker
- [ ] Implement PR template with checklist
- [ ] Implement auto-generated PR comment with validation results

### Phase 3: Config Service (1 day)
- [ ] Implement GitConfigService with pull-based refresh
- [ ] Implement config caching (immutable snapshots)
- [ ] Implement validation-before-cache (bad config doesn't replace good config)
- [ ] Implement config change notification (listeners)
- [ ] Implement force-reload endpoint (admin)

### Phase 4: Audit & Rollback (0.5 day)
- [ ] Add configVersion to StateTransitionRecord
- [ ] Implement rollback runbook
- [ ] Add config version to health endpoint
- [ ] Add CHANGELOG auto-generation

---

## 5. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Config service can't reach Git (network issue) | Medium | Medium | Serve cached config; alert on failed pull; cache never expires (last-known-good) |
| Bad config passes validation but causes runtime issues | Medium | High | Canary release before full rollout; monitoring with auto-rollback on error rate spike |
| Config hot-reload causes inconsistent state for in-flight conversations | Low | Medium | Config is immutable snapshot; conversations use config loaded at conversation start (or event time) |
| Git repo becomes single point of failure | Low | High | Mirror repo to secondary Git provider; config service has local clone (survives Git outage) |
| PR review bottleneck slows config changes | Medium | Low | Define emergency change process (bypass review for critical fixes, with post-hoc review) |
| Config version in logs increases log volume | Low | Low | Config version only in transition records (not every log line); sampled in regular logs |
| Semantic validator has false positives (blocks valid config) | Medium | Medium | Warnings don't block; errors can be overridden with `@SuppressWarnings` annotation in config comments |

---

## 6. Success Criteria

- [ ] Every config change is a Git commit with author, timestamp, and review approval
- [ ] Config PRs automatically include validation results and impact analysis
- [ ] Bad config (validation errors) never reaches production cache
- [ ] Rollback to any previous config takes < 2 minutes
- [ ] Every state transition log includes the config version (Git commit)
- [ ] Config service continues serving during Git outage (last-known-good cache)
- [ ] No config changes are possible outside of Git PR process
- [ ] Config changelog is auto-generated and human-readable
