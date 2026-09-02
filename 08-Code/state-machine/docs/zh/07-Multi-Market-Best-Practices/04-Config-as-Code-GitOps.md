# 04. 配置即代码与 GitOps

> 版本：1.0 | 最后更新：2026-09-01
> 优先级：P1 | 预估工作量：2-3 天

## 1. 问题陈述

### 1.1 当前问题

市场配置目前硬编码在 Java 中（`StateMachineMarketConfig.defaultConfig()`）或存储在没有版本控制的数据库中。变更通过管理控制台或数据库更新直接在生产环境中进行，没有评审、没有审计追踪、没有回滚路径。

### 1.2 症状

- **无审计追踪**："上周二谁改了 HK 的转接超时？" — 无法回答
- **无评审流程**：配置变更直接到生产环境，无同行评审
- **无回滚**：坏配置只能通过手动改回来修复（如果你还记得旧值）
- **环境漂移**：Dev/Staging/Prod 配置静默偏离
- **意外变更**：管理控制台中的误操作导致中断
- **无可重现性**：无法从某个时间点重现特定的配置状态

### 1.3 目标

- 所有市场配置存储为 Git 中的版本控制文件
- 每个变更都经过 PR 评审和自动校验
- 完整审计追踪：谁改了什么、何时、为什么、谁批准的
- 一键回滚到任何先前的配置版本
- 配置变更在部署前可测试
- 配置版本在每个状态迁移日志中可追溯

---

## 2. 设计概述

### 2.1 GitOps 工作流

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  开发者   │────▶│  Git PR      │────▶│  CI 管道  │
│  编辑配置 │     │  （评审）    │     │  （校验）   │
└──────────────┘     └──────────────┘     └──────┬───────┘
                                                     │
                                          ┌──────────▼──────────┐
                                          │  校验门禁:    │
                                          │  1. 模式校验   │
                                          │  2. 语义校验 │
                                          │  3. 影响分析   │
                                          │  4. 单元测试        │
                                          └──────────┬──────────┘
                                                     │ 通过
                                          ┌──────────▼──────────┐
                                          │  合并到 main       │
                                          └──────────┬──────────┘
                                                     │
                                          ┌──────────▼──────────┐
                                          │  配置服务      │
                                          │  （从 Git 拉取）    │
                                          └──────────┬──────────┘
                                                     │
                                          ┌──────────▼──────────┐
                                          │  金丝雀发布      │
                                          │  （5% → 20% → 100%）  │
                                          └──────────┬──────────┘
                                                     │
                                          ┌──────────▼──────────┐
                                          │  生产配置   │
                                          │  （不可变快照）│
                                          └─────────────────────┘
```

### 2.2 仓库结构

```
config-repo/
├── README.md                    # 配置文档和变更流程
├── schema/
│   └── market-config.schema.json   # 用于校验的 JSON Schema
├── base/
│   └── profile.yaml             # 全局默认值（层 1）
├── regional/
│   ├── apac.yaml                # APAC 区域覆盖（层 2）
│   ├── emea.yaml
│   └── amer.yaml
├── markets/
│   ├── hk.yaml                  # HK 市场配置（层 3）
│   ├── sg.yaml
│   ├── uk.yaml
│   ├── us.yaml
│   └── jp.yaml
├── environments/
│   ├── dev.yaml                 # 环境特定覆盖
│   ├── staging.yaml
│   └── prod.yaml
└── CHANGELOG.md                 # 自动生成的配置变更日志
```

---

## 3. 详细设计

### 3.1 配置文件标准

#### 3.1.1 必需元数据

每个配置文件必须包含元数据头：

```yaml
# config/markets/hk.yaml
# Last modified: 2026-09-01 by @zhiqiu
# Approved by: @reviewer1, @reviewer2
# Related ticket: CBOL-1234
# Change reason: Increase transfer timeout for HK due to Genesys latency

market: HK
inherits: [base, apac]
version: "2.1.0"                    # 此配置的语义版本
configVersion: "2026.09.01-hk-3"  # 唯一版本标识符

timeouts:
  customerIdleSeconds: 240
  transferTimeoutSeconds: 180
  # ...
```

#### 3.1.2 版本策略

- **配置语义版本**：`MAJOR.MINOR.PATCH`
  - MAJOR：破坏性变更（状态机定义变更）
  - MINOR：新功能 / 新市场
  - PATCH：阈值 / 开关变更
- **唯一版本 ID**：`YYYY.MM.DD-{market}-{sequence}` 用于可追溯性
- 每个状态迁移日志包含 `configVersion` 用于调试

### 3.2 PR 模板

```markdown
## 配置变更摘要

**受影响市场**: HK, SG
**配置版本**: 2.1.0 → 2.2.0
**相关工单**: CBOL-1234

## 所做变更

| 文件 | 键 | 旧值 | 新值 | 原因 |
|------|-----|-----------|-----------|--------|
| markets/hk.yaml | timeouts.transferTimeoutSeconds | 180 | 240 | Genesys 延迟增加 |
| markets/sg.yaml | features.surveyEnabled | false | true | 在 SG 推出 CSAT 满意度调查 |

## 影响分析（自动生成）

- HK: 1 个配置值变更，0 个迁移受影响
- SG: 1 个功能开关变更，2 个迁移现在处于活动状态（来自 IN_PROGRESS/TRANSFERRED 的 SURVEY_START）
- 无其他市场受影响

## 校验结果（自动生成）

- [x] 模式校验通过
- [x] 语义校验通过
- [x] 状态机可达性检查通过
- [x] 单元测试通过（327/327）
- [x] 兼容性矩阵已生成

## 回滚计划

还原此 PR 以恢复先前配置。
预估回滚时间：< 2 分钟（配置服务自动从 Git 拉取）。

## 检查清单

- [ ] 我已在 staging 中测试此配置
- [ ] 我已通知市场所有者
- [ ] 我已考虑对监控/告警的影响
- [ ] 我已更新 CHANGELOG
```

### 3.3 CI 校验管道

#### 3.3.1 管道阶段

```yaml
# .github/workflows/config-validation.yml
name: 配置校验

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

      - name: 1. 模式校验
        run: |
          for f in config/markets/*.yaml; do
            ajv validate -s config/schema/market-config.schema.json -d "$f"
          done

      - name: 2. 语义校验
        run: ./mvnw exec:java -Dexec.mainClass="com.example.ConfigSemanticValidator"

      - name: 3. 状态机可达性检查
        run: ./mvnw exec:java -Dexec.mainClass="com.example.ReachabilityChecker"

      - name: 4. 影响分析
        run: ./mvnw exec:java -Dexec.mainClass="com.example.ConfigImpactAnalyzer" --args="--pr"

      - name: 5. 生成兼容性矩阵
        run: ./mvnw exec:java -Dexec.mainClass="com.example.MatrixGenerator"

      - name: 6. 运行单元测试
        run: ./mvnw test

      - name: 7. 将结果作为 PR 评论发布
        uses: actions/github-script@v7
        with:
          script: |
            const results = require('./validation-results.json');
            const impact = require('./impact-report.md');
            const matrix = require('./compatibility-matrix.md');
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              body: `## 配置校验结果\n\n${results}\n\n## 影响分析\n\n${impact}\n\n<details><summary>兼容性矩阵</summary>\n\n${matrix}\n</details>`
            });
```

#### 3.3.2 语义校验器

```java
public class ConfigSemanticValidator {

    public List<ValidationIssue> validate(MergedMarketConfig config) {
        List<ValidationIssue> issues = new ArrayList<>();

        // 规则 1: surveyEnabled=true 需要 surveyType
        if (config.surveyEnabled() && config.surveyType() == null) {
            issues.add(ValidationIssue.error("surveyEnabled is true but surveyType is not set"));
        }

        // 规则 2: genesysEnabled=true 需要 genesysOrgId
        if (config.genesysEnabled() && config.genesysOrgId() == null) {
            issues.add(ValidationIssue.error("genesysEnabled is true but genesysOrgId is not set"));
        }

        // 规则 3: transferEnabled=false 但 fallbackRoutingStrategy=REQUEUE（警告）
        if (!config.transferEnabled() && "REQUEUE".equals(config.fallbackRoutingStrategy())) {
            issues.add(ValidationIssue.warning("transferEnabled=false but fallbackRoutingStrategy=REQUEUE (no transfer to requeue)"));
        }

        // 规则 4: 超时逻辑排序
        if (config.transferTimeoutSeconds() >= config.customerIdleSeconds()) {
            issues.add(ValidationIssue.warning(
                    "transferTimeout (" + config.transferTimeoutSeconds() +
                    "s) >= customerIdle (" + config.customerIdleSeconds() +
                    "s) — transfer may timeout before idle detection"));
        }

        // 规则 5: 结束宽限应短于满意度调查超时
        if (config.surveyEnabled() && config.endingGraceSeconds() > config.surveyTimeoutSeconds()) {
            issues.add(ValidationIssue.warning(
                    "endingGrace (" + config.endingGraceSeconds() +
                    "s) > surveyTimeout (" + config.surveyTimeoutSeconds() +
                    "s) — conversation may close before survey completes"));
        }

        // 规则 6: 最大转接重试合理
        if (config.maxTransferRetries() > 5) {
            issues.add(ValidationIssue.warning("maxTransferRetries=" + config.maxTransferRetries() + " is unusually high (>5)"));
        }

        // 规则 7: 扩展名称必须存在于注册表中
        for (String ext : config.enabledExtensions()) {
            if (!extensionRegistry.exists(ext)) {
                issues.add(ValidationIssue.error("Unknown extension: " + ext));
            }
        }

        return issues;
    }
}
```

### 3.4 配置服务（Git 拉取与热重载）

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

        // 每 30 秒轮询变更
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
                // 通知监听器（状态机、监控器等）
                notifyConfigChanged(latestCommit);
            }
        } catch (Exception e) {
            log.error("Failed to check for config updates", e);
            // 不要崩溃 — 继续提供缓存配置
        }
    }

    private void loadAllConfigs() {
        List<String> markets = gitRepo.listMarkets();
        for (String market : markets) {
            try {
                MergedMarketConfig config = mergeEngine.merge(market);
                // 缓存前校验
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
     * 返回当前配置版本（Git 提交哈希）用于审计日志。
     */
    public String getConfigVersion() {
        return currentCommit;
    }

    /**
     * 强制立即重新加载（用于管理员触发的刷新）。
     */
    public void forceReload() {
        checkForUpdates();
    }
}
```

### 3.5 审计日志中的配置版本

每个状态迁移记录包含配置版本：

```java
StateTransitionRecord record = StateTransitionRecord.builder()
        .businessId(conversationId)
        .fromState(from.name())
        .toState(result.getTargetState().name())
        .fact(fact.name())
        .market(market)
        .configVersion(configService.getConfigVersion())  // Git 提交哈希
        .timestampMs(System.currentTimeMillis())
        .traceId(traceContext.traceId())
        .build();
```

这使得："在 14:30，会话 X 使用配置版本 abc1234（其 transferTimeout=180秒）从 IN_PROGRESS 迁移到 TRANSFERRED。"

### 3.6 回滚流程

```
场景: 坏配置部署到生产环境

1. 识别坏提交: git log --oneline config/
2. 还原 PR: git revert <bad-commit-hash>
3. 推送还原: git push origin main
4. 配置服务在 30 秒内检测到变更
5. 新加载并校验配置
6. 如果校验通过，缓存更新
7. 所有新事件使用还原后的配置
8. 进行中的会话继续使用其当前状态
   （配置变更不会追溯影响正在运行的会话）

总回滚时间: < 2 分钟
```

---

## 4. 实施路线图

### 阶段 1：配置仓库与模式（0.5 天）
- [ ] 创建配置 Git 仓库结构
- [ ] 为市场配置编写 JSON Schema
- [ ] 将现有配置迁移到 YAML 文件
- [ ] 添加配置元数据标准（版本、变更原因、工单）

### 阶段 2：CI 校验管道（1 天）
- [ ] 在 CI 中实现模式校验
- [ ] 实现语义校验器（7+ 条规则）
- [ ] 实现状态机可达性检查器
- [ ] 实现带检查清单的 PR 模板
- [ ] 实现带校验结果的自动生成 PR 评论

### 阶段 3：配置服务（1 天）
- [ ] 实现带基于拉取刷新的 GitConfigService
- [ ] 实现配置缓存（不可变快照）
- [ ] 实现缓存前校验（坏配置不替换好配置）
- [ ] 实现配置变更通知（监听器）
- [ ] 实现强制重载端点（管理员）

### 阶段 4：审计与回滚（0.5 天）
- [ ] 向 StateTransitionRecord 添加 configVersion
- [ ] 实现回滚运行手册
- [ ] 向健康端点添加配置版本
- [ ] 添加 CHANGELOG 自动生成

---

## 5. 风险评估

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|-----------|--------|------------|
| 配置服务无法访问 Git（网络问题） | 中 | 中 | 提供缓存配置；拉取失败时告警；缓存永不过期（最后已知良好） |
| 坏配置通过校验但导致运行时问题 | 中 | 高 | 全面推出前金丝雀发布；错误率飙升时自动回滚监控 |
| 配置热重载导致进行中会话状态不一致 | 低 | 中 | 配置是不可变快照；会话使用会话开始时（或事件时间）加载的配置 |
| Git 仓库成为单点故障 | 低 | 高 | 将仓库镜像到次要 Git 提供者；配置服务有本地克隆（在 Git 中断时存活） |
| PR 评审瓶颈减慢配置变更 | 中 | 低 | 定义紧急变更流程（关键修复绕过评审，事后评审） |
| 日志中的配置版本增加日志量 | 低 | 低 | 配置版本仅在迁移记录中（不是每个日志行）；在常规日志中采样 |
| 语义校验器有误报（阻止有效配置） | 中 | 中 | 警告不阻止；错误可用配置注释中的 `@SuppressWarnings` 注解覆盖 |

---

## 6. 成功标准

- [ ] 每个配置变更是带作者、时间戳和评审批准的 Git 提交
- [ ] 配置 PR 自动包含校验结果和影响分析
- [ ] 坏配置（校验错误）永远不会到达生产缓存
- [ ] 回滚到任何先前配置需要 < 2 分钟
- [ ] 每个状态迁移日志包含配置版本（Git 提交）
- [ ] 配置服务在 Git 中断期间继续服务（最后已知良好缓存）
- [ ] Git PR 流程之外不可能进行配置变更
- [ ] 配置变更日志自动生成且人类可读
