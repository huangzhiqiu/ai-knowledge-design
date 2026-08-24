---
name: deployment
description: Deploy approved PR to staging then production with health checks, smoke tests, and automatic rollback. Dry-run by default — never deploys without explicit --no-dry-run flag. Staging auto-deploys, production requires manual approval. Use after PR approval, or when you need to deploy with verification.
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash(git:*)
  - Bash(gh:*)
  - Bash(kubectl:*)
  - Bash(docker:*)
  - Bash(curl:*)
  - Bash(jq:*)
  - Bash(mvn:*)
  - Bash(grep:*)
  - Bash(cat:*)
---

# Deployment Skill

Deploy with staging → production flow, health checks, smoke tests, automatic rollback.

## CRITICAL RULES

1. **DRY-RUN BY DEFAULT**: This skill NEVER actually deploys unless explicitly passed `--no-dry-run` or user explicitly confirms deployment. Default mode is dry-run (show what would happen).
2. **DISABLE MODEL INVOCATION**: This skill should NOT be auto-triggered by model from natural language. It requires explicit user invocation. Set `disable-model-invocation: true` in frontmatter (if supported by your client).
3. **STAGING FIRST**: Always deploy to staging first. Production deployment ONLY after staging health checks + smoke tests pass AND human explicitly approves.
4. **HEALTH CHECK MANDATORY**: After every deployment, run health checks. If health check fails, automatically rollback.
5. **SMOKE TEST MANDATORY**: After health checks pass, run smoke tests. If smoke tests fail, automatically rollback.
6. **ROLLBACK READY**: Before deploying, verify rollback path exists. After deployment, keep previous version available for immediate rollback.
7. **NO PRODUCTION AUTO-MERGE**: Production deployment requires explicit human approval. Never auto-deploy to production.
8. **SECRETS IN ENVIRONMENT**: Never hardcode secrets in deployment scripts. Use environment variables or secret management.
9. **EVIDENCE REQUIRED**: Every deployment step needs command + output + exit code. No "it worked" claims without evidence.
10. **MONITOR AFTER DEPLOY**: After production deploy, monitor for 5-10 minutes (error rate, latency, resource usage). Report anomalies.

## References

- [Kevinweisl/claude-skills-cicd](https://github.com/Kevinweisl/claude-skills-cicd) — build-and-release (dry-run default), dependency-audit, disable-model-invocation safety
- [excalibase/claude-toolkiit](https://github.com/excalibase/claude-toolkiit) — deployment-patterns, docker-patterns, database-migrations
- [fancybread-com/sdlc-workflow-skills](https://github.com/fancybread-com/sdlc-workflow-skills) — complete-task with CI/CD monitoring, issue transition
- [adamcaviness/agentic-toolkit](https://github.com/adamcaviness/agentic-toolkit) — ship skill for deployment
- [Streamlinity/claude-skills-deploy](https://github.com/Streamlinity/claude-skills-deploy) — Staging → production flow, Doppler secrets, smoke tests
- [claudecode-lab CI/CD setup](https://claudecode-lab.com/en/blog/claude-code-ci-cd-setup/) — GitHub Actions safe deploy, rollback workflow, Environment approval
- [Jackela/claude-ci-skills](https://github.com/Jackela/claude-ci-skills) — ci-deploy-pipeline, ci-quality-gates, ci-security-scan
- [jofu-tofu/AI-Workflow-CLI BEST-PRACTICES](https://github.com/jofu-tofu/AI-Workflow-CLI/blob/master/docs/BEST-PRACTICES.md) — kubectl rollout, smoke tests, rollback procedure
- [POC Stage 7 Doc](../../stages/07-deployment.md) — Stage documentation
- [POC Verify Checklist](../../verify-checklist.md) — Gate 7 criteria
- [KB Integration](../../knowledge-integration.md) — KB read/write protocol

## External Skill Synergy

| External Skill | When to Use | How to Integrate |
|---------------|-------------|-----------------|
| `build-and-release` (Kevinweisl) | Build and release pipeline | Delegate build/release to build-and-release (dry-run default); use this skill for staging→production flow |
| `excalibase-deployment-patterns` | Deployment strategy reference | Reference for blue-green, canary, rolling deployment patterns |
| `excalibase-docker-patterns` | Docker/container deployment | Reference for Dockerfile, docker-compose, container best practices |
| `excalibase-database-migrations` | Database migrations | Use for schema migration planning and execution |
| `dependency-audit` (Kevinweisl) | CVE/dependency scanning | Run before deployment for security audit |
| `security-scan` (Kevinweisl) | Security scanning | Run before deployment for SAST/DAST |
| `ship` (agentic-toolkit) | Quick ship/deploy | Use for simple deployments; use this skill for complex staging→production |
| `complete-task` (fancybread) | Full task completion workflow | Use for end-to-end commit+PR+deploy+issue transition |

**Delegation pattern**: For simple deployments, use `ship` or `build-and-release`. For complex staging→production with health checks, smoke tests, and rollback, use this skill. Always run `dependency-audit` and `security-scan` before production deployment.

## Prerequisites

1. Stage 6 (pr-review) completed — PR approved by human
2. `docs/operations/{JIRA_KEY}/06-pr-review/human-decision.md` shows Approved
3. PR merged to main (or deployment branch)
4. Deployment infrastructure configured:
   - Kubernetes cluster (kubectl access) OR
   - Docker + container registry OR
   - CI/CD pipeline (GitHub Actions, Jenkins, etc.)
5. Environment configs exist: staging, production
6. Health check endpoint defined
7. Smoke test suite defined
8. Operation directory exists: `docs/operations/{JIRA_KEY}/07-deployment/`

## Execution Steps

### Step 0: Dry-Run Check

```bash
# Check if --no-dry-run flag is present
# If NOT present, run in DRY-RUN mode (show plan, do NOT execute)
DRY_RUN=true
if [[ "$*" == *"--no-dry-run"* ]]; then
  DRY_RUN=false
fi

echo "Deployment mode: $([ "$DRY_RUN" = true ] && echo "DRY-RUN (no changes will be made)" || echo "LIVE (changes WILL be made)")"
```

**If dry-run**: Show deployment plan, ask user to confirm with `--no-dry-run` for actual deployment. STOP here.

**If live**: Continue.

### Step 1: Pre-Deployment Checks

#### 1a: Verify PR Approved and Merged

```bash
# Check PR status
gh pr view {PR_NUMBER} --json state,mergedAt,mergeCommit --jq '.state, .mergedAt, .mergeCommit.oid'

# Verify merged to main
git checkout main
git pull origin main
git log --oneline -5
```

**Verify**: PR state = MERGED, merge commit exists in main.

#### 1b: Build and Verify Artifact

```bash
# Build
mvn clean package -DskipTests -q
echo "Build exit: $?"

# Verify artifact exists
ls -la target/*.jar

# (If Docker) Build image
docker build -t {registry}/{image}:{version} .
echo "Docker build exit: $?"
```

#### 1c: Security Scan (Pre-Deploy)

```bash
# Dependency audit
mvn dependency-check:check -q 2>&1 | tail -10

# Or npm audit / pip-audit depending on ecosystem
# (If Docker) Trivy scan
trivy image {registry}/{image}:{version} --severity CRITICAL,HIGH 2>&1 | tail -10
```

**Verify**: No CRITICAL vulnerabilities. HIGH vulnerabilities documented and accepted.

#### 1d: Verify Rollback Path

```bash
# Check previous version is available
kubectl get deployment {service} -n {namespace} -o jsonpath='{.spec.template.spec.containers[0].image}'
echo ""
# Or check previous Docker tag
docker images {registry}/{image} --format "{{.Tag}}" | head -5
```

**Verify**: Previous version/image exists and can be rolled back to.

**Interactive checkpoint**:
> Pre-deployment checks complete.
> Build: ✅ / Security: {N} CRITICAL / Rollback path: ✅
> Options: [Deploy to staging] [View full report] [Stop]

### Step 2: Deploy to Staging

#### 2a: Deploy

```bash
# Kubernetes deployment
kubectl set image deployment/{service} {container}={registry}/{image}:{version} -n staging
echo "Deploy exit: $?"

# Or apply manifest
kubectl apply -f k8s/staging/deployment.yaml -n staging

# Or trigger CI/CD pipeline
gh workflow run deploy-staging.yml -f version={version}
```

#### 2b: Wait for Rollout

```bash
kubectl rollout status deployment/{service} -n staging --timeout=120s
echo "Rollout exit: $?"
```

**If rollout fails** → automatic rollback (Step 2e).

#### 2c: Health Check

```bash
# Wait for pod to be ready
sleep 10

# Health check
HEALTH_URL="http://staging.{domain}/actuator/health"
curl -sf "$HEALTH_URL" | jq .
echo "Health check exit: $?"

# Check pod status
kubectl get pods -n staging -l app={service}
```

**Verify**: Health endpoint returns UP, all pods Running/Ready.

**If health check fails** → automatic rollback (Step 2e).

#### 2d: Smoke Tests

```bash
# Run smoke test suite
# Option 1: Maven smoke tests
mvn test -Dtest=SmokeTest* -pl {module} -Dspring.profiles.active=staging -q 2>&1 | tail -20

# Option 2: curl-based smoke tests
curl -sf "http://staging.{domain}/api/v1/messages" -H "Authorization: Bearer {token}" | jq '. | length'
curl -sf "http://staging.{domain}/api/v1/websocket/info" | jq .

echo "Smoke tests exit: $?"
```

**Verify**: All smoke tests pass.

**If smoke tests fail** → automatic rollback (Step 2e).

#### 2e: Automatic Rollback (If Any Check Fails)

```bash
echo "=== DEPLOYMENT FAILED — AUTOMATIC ROLLBACK ==="

# Rollback Kubernetes deployment
kubectl rollout undo deployment/{service} -n staging
kubectl rollout status deployment/{service} -n staging --timeout=120s

# Verify rollback health
sleep 10
curl -sf "$HEALTH_URL" | jq .

echo "Rollback complete. Previous version restored."
```

**Report failure**, do NOT proceed to production.

#### 2f: Staging Monitoring (5 minutes)

```bash
# Monitor error rate and latency
for i in {1..5}; do
  echo "=== Check $i/5 ==="
  kubectl get pods -n staging -l app={service}
  curl -sf "http://staging.{domain}/actuator/metrics/http.server.requests" | jq '.measurements[] | select(.statistic=="MAX") | .value' 2>/dev/null
  sleep 60
done
```

**Verify**: No crash loops, no error spikes, latency within normal range.

**Interactive checkpoint**:
> Staging deployment complete ✅
> Health: ✅ / Smoke tests: {N}/{N} passed / Monitoring: 5min stable
> Options: [Deploy to production] [View staging logs] [Run more tests] [Stop]

### Step 3: Production Deployment (Human Approval Required)

#### 3a: Request Human Approval

```bash
# Display production deployment plan
echo "=== PRODUCTION DEPLOYMENT PLAN ==="
echo "Service: {service}"
echo "Version: {version}"
echo "Previous version: {previous_version}"
echo "Staging verified: ✅ (health + smoke + 5min monitor)"
echo ""
echo "This WILL deploy to production."
echo "Rollback path: kubectl rollout undo deployment/{service} -n production"
```

**Interactive checkpoint**:
> Ready to deploy to production.
> Version: {version} / Staging: ✅ verified
> Options: [Confirm production deploy] [View deployment plan] [Stop]

**If human does NOT confirm** → STOP. Do NOT deploy to production.

#### 3b: Deploy to Production

```bash
# Same as staging but production namespace
kubectl set image deployment/{service} {container}={registry}/{image}:{version} -n production
kubectl rollout status deployment/{service} -n production --timeout=120s
```

#### 3c: Production Health Check

```bash
sleep 10
curl -sf "https://{domain}/actuator/health" | jq .
kubectl get pods -n production -l app={service}
```

#### 3d: Production Smoke Tests

```bash
mvn test -Dtest=SmokeTest* -pl {module} -Dspring.profiles.active=production -q 2>&1 | tail -20
```

#### 3e: Production Automatic Rollback (If Any Check Fails)

```bash
kubectl rollout undo deployment/{service} -n production
kubectl rollout status deployment/{service} -n production --timeout=120s
```

#### 3f: Production Monitoring (10 minutes)

```bash
for i in {1..10}; do
  echo "=== Production Monitor $i/10 ==="
  kubectl get pods -n production -l app={service}
  # Check error rate via metrics
  curl -sf "https://{domain}/actuator/metrics/http.server.requests" 2>/dev/null | jq '.measurements[] | select(.statistic=="COUNT") | .value'
  sleep 60
done
```

### Step 4: Post-Deployment

#### 4a: Update Jira Ticket

```bash
# Transition Jira ticket to Done
# (Via REST API or MCP)
curl -X POST "$JIRA_BASE_URL/rest/api/3/issue/{JIRA_KEY}/transitions" \
  -u "$JIRA_EMAIL:$JIRA_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"transition": {"id": "{done_transition_id}"}}'
```

#### 4b: Generate Deployment Report

Write `deployment-report.md`:
```markdown
# Deployment Report — {JIRA_KEY}

**Service**: {service}
**Version**: {version}
**Date**: {timestamp}
**Deployed by**: AI (human approved production)

## Staging Deployment
- **Status**: ✅ Success / ❌ Failed
- **Start time**: {time}
- **End time**: {time}
- **Duration**: {N}s
- **Health check**: ✅ / ❌
- **Smoke tests**: {N}/{N} passed
- **Monitoring (5min)**: ✅ stable / ❌ issues

## Production Deployment
- **Status**: ✅ Success / ❌ Failed
- **Human approval**: {name} at {time}
- **Start time**: {time}
- **End time**: {time}
- **Duration**: {N}s
- **Health check**: ✅ / ❌
- **Smoke tests**: {N}/{N} passed
- **Monitoring (10min)**: ✅ stable / ❌ issues

## Rollback
- **Required**: No / Yes (at {time}, reason: {reason})
- **Rollback to**: {previous_version}
- **Rollback health**: ✅ / ❌

## Security Scan
- **CRITICAL**: {N}
- **HIGH**: {N}
- **Action**: {none / documented / fixed}

## Artifacts
- **Docker image**: {registry}/{image}:{version}
- **Commit**: {sha}
- **PR**: #{PR_NUMBER}

## Evidence
- Build: {command} exit {code}
- Staging deploy: {command} exit {code}
- Staging health: {command} exit {code}
- Staging smoke: {command} exit {code}
- Production deploy: {command} exit {code}
- Production health: {command} exit {code}
- Production smoke: {command} exit {code}
```

#### 4c: Update Pipeline State

Mark all stages complete in `pipeline-state.json`.

## Verify Gate (Automated)

| Criteria | Method | Evidence |
|----------|--------|----------|
| Dry-run respected (if no --no-dry-run) | Mode check | operation-log.md |
| PR approved and merged | gh PR view | deployment-report.md |
| Build successful | Maven exit code | deployment-report.md |
| Security scan no CRITICAL | Scan output | deployment-report.md |
| Rollback path verified | Previous version exists | deployment-report.md |
| Staging deploy successful | kubectl rollout status | deployment-report.md |
| Staging health check passed | curl exit code | deployment-report.md |
| Staging smoke tests passed | Test exit code | deployment-report.md |
| Staging monitoring (5min) stable | Pod status + metrics | deployment-report.md |
| Human approved production | Approval record | deployment-report.md |
| Production deploy successful | kubectl rollout status | deployment-report.md |
| Production health check passed | curl exit code | deployment-report.md |
| Production smoke tests passed | Test exit code | deployment-report.md |
| Production monitoring (10min) stable | Pod status + metrics | deployment-report.md |
| Automatic rollback on failure (if applicable) | Rollback executed | deployment-report.md |
| Jira ticket updated | Transition executed | deployment-report.md |
| Deployment report generated | File exists | deployment-report.md |
| Pipeline state updated | State file | pipeline-state.json |

**PASS** → All checks ✅ → Pipeline complete 🎉
**FAIL** → If staging fails → rollback, report, do NOT proceed to production. If production fails → automatic rollback, report, escalate.

## KB Injection

**Read**:
- `04-Coding-Guidelines/` — Code quality (for pre-deploy checks)
- `03-Design-Guidelines/` — Architecture (for deployment topology)
- `04-Coding-Guidelines/security/` — Security (for pre-deploy scan)
- `AGENTS.md` — Project config

**Write**:
- Deployment patterns → `03-Design-Guidelines/` (if new patterns discovered)
- Runbook entries → new `08-Runbooks/` directory (if created)

## Error Handling

| Error | Resolution |
|-------|-----------|
| Dry-run mode (no --no-dry-run) | Show plan, ask user to re-run with --no-dry-run |
| PR not merged | Wait for merge, or ask user to merge |
| Build fails | Fix build issues, re-run (max 3 retries) |
| Security scan finds CRITICAL | STOP deployment, fix vulnerability, re-scan |
| kubectl not configured | Verify kubeconfig, ask user for cluster access |
| Staging rollout timeout | Automatic rollback, check pod logs, report |
| Staging health check fails | Automatic rollback, check logs, report |
| Staging smoke tests fail | Automatic rollback, fix tests/code, re-deploy |
| Human does not approve production | STOP, do NOT deploy to production |
| Production rollout fails | Automatic rollback, escalate immediately |
| Production health check fails | Automatic rollback, escalate, check monitoring |
| Monitoring detects anomalies | Report to user, consider rollback if severe |
| Jira transition fails | Note in report, ask user to manually update ticket |

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Deploying without dry-run | Dry-run by default. Never deploy without explicit --no-dry-run or user confirmation. |
| Deploying to production directly | Staging first. Production ONLY after staging health+smoke pass AND human approval. |
| Skipping health checks after deploy | Health check mandatory. If fails, automatic rollback. |
| Skipping smoke tests | Smoke test mandatory after health checks. If fails, automatic rollback. |
| No rollback path verified | Before deploying, verify rollback path exists. Keep previous version available. |
| Hardcoding secrets in scripts | Use environment variables or secret management. Never hardcode. |
| Auto-deploying to production | Production requires explicit human approval. Never auto-deploy. |
| Not monitoring after deploy | Monitor 5-10 min after production deploy (error rate, latency, resources). Report anomalies. |
| No evidence for deployment steps | Every step needs command + output + exit code. No "it worked" claims. |
| Skipping security scan before production | Run security-scan + dependency-audit before production deployment. |
| Not updating Jira ticket status | After deployment, transition ticket to Done/Resolved. |

## Output Artifacts

- `docs/operations/{JIRA_KEY}/07-deployment/deployment-report.md` — Full deployment report
- `docs/operations/{JIRA_KEY}/07-deployment/verify-report.md` — Verify report
- `docs/operations/{JIRA_KEY}/07-deployment/operation-log.md` — Operation log (all commands + output)
- `docs/operations/{JIRA_KEY}/07-deployment/staging-logs.txt` — Staging deployment logs
- `docs/operations/{JIRA_KEY}/07-deployment/production-logs.txt` — Production deployment logs
- Updated Jira ticket status
- Deployed service in staging + production

---

*Deployment Skill v2.0.0 — 2026-08-24*
*Optimized with: Dry-run by default, disable-model-invocation safety, staging→production flow, automatic rollback, health+smoke+monitoring gates, CRITICAL rules, precise allowed-tools*
