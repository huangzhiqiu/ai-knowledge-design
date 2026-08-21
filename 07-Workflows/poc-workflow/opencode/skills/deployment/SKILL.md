---
name: deployment
description: Auto-deploy merged PR and run post-deployment verification. Triggers CI/CD pipeline, waits for rollout, runs health checks and smoke tests, monitors logs/metrics. Rolls back if health check fails. Use after pr-review skill (when PR is merged), or when you need to deploy and verify.
version: 1.0.0
author: CBOL Self-Development
tags: [deployment, ci-cd, deploy, health-check, smoke-test, rollback, poc]
triggers:
  - "deploy"
  - "auto deploy"
  - "deploy to staging"
  - "post-deploy verify"
arguments:
  - name: jira_key
    description: Jira ticket key (e.g., CBOL-123)
    required: true
  - name: environment
    description: Target environment (staging/production). Default: staging
    required: false
---

# Deployment Skill

Auto-deploy merged PR + post-deployment verification + rollback if needed.

## References

- [Kevinweisl/claude-skills-cicd](https://github.com/Kevinweisl/claude-skills-cicd) — build-and-release skill with dry-run
- [claude-skills-cicd deploy](https://github.com/Kevinweisl/claude-skills-cicd) — dependency-audit, multi-ecosystem
- [Claude Code CI/CD Setup](https://claudecode-lab.com/en/blog/claude-code-ci-cd-setup/) — safe GitHub Actions for PRs, Deploys, Rollback
- [headless-claude](https://github.com/mjmirza/headless-claude) — CI/CD integration guide
- [POC Stage 7 Doc](../../stages/07-deployment.md) — Stage documentation
- [POC Verify Checklist](../../verify-checklist.md) — Gate 7 criteria

## Prerequisites

1. Stage 6 (pr-review) completed — PR merged to main
2. CI/CD pipeline configured (GitHub Actions / Jenkins / ArgoCD)
3. Deployment config exists (k8s manifests / docker-compose / cloud config)
4. Health check endpoint configured (`/actuator/health`)
5. Operation directory exists: `docs/operations/{JIRA_KEY}/07-deployment/`

## Deployment Environments

| Environment | Trigger | Approval | Rollback |
|-------------|---------|----------|----------|
| staging | Auto on merge to main | None | Auto on health check fail |
| production | Manual / workflow_dispatch | Human approval | Manual |

Default: staging.

## Execution Steps

### Step 1: Verify PR Merged

```bash
# Check if PR is merged
gh pr view {PR_NUMBER} --json merged,mergedAt
```

If not merged, wait for merge or ask user to merge.

### Step 2: Inject Knowledge Base

**Mandatory reads**:
- `03-Design-Guidelines/05-reliability/high-availability.md`
- `03-Design-Guidelines/05-reliability/observability-design.md`
- `03-Design-Guidelines/05-reliability/resilience-patterns.md`
- `04-Coding-Guidelines/` (deployment-related)

### Step 3: Trigger Deployment

**Option A: GitHub Actions (auto on merge)**
```bash
# If workflow triggers on push to main, just wait
gh run list --branch main --limit 5
```

**Option B: Manual trigger**
```bash
gh workflow run deploy.yml -f environment={staging/production}
```

**Option C: Direct deploy (kubectl)**
```bash
# Build artifact
mvn clean package -DskipTests
docker build -t {image}:{tag} .
docker push {registry}/{image}:{tag}

# Deploy
kubectl set image deployment/{name} {container}={registry}/{image}:{tag}
kubectl apply -f k8s/deployment.yaml
```

### Step 4: Wait for Rollout

```bash
# Kubernetes
kubectl rollout status deployment/{name} --timeout=300s

# Docker Compose
docker-compose up -d
sleep 10
docker-compose ps
```

If rollout times out, check logs and consider rollback.

### Step 5: Health Check

```bash
# Spring Boot Actuator
curl -s http://{host}/actuator/health | jq .

# Expected: {"status":"UP"}
```

**Check components**:
- Database connectivity
- Redis connectivity
- Message queue connectivity
- External service dependencies

If health check FAILS → go to Step 9 (Rollback).

### Step 6: Smoke Tests

Run basic functionality tests:

```bash
#!/bin/bash
# smoke-test.sh
BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0

check() {
  local name="$1"
  local cmd="$2"
  if eval "$cmd"; then
    echo "✅ PASS: $name"
    PASS=$((PASS+1))
  else
    echo "❌ FAIL: $name"
    FAIL=$((FAIL+1))
  fi
}

# Health
check "Health endpoint" "curl -sf $BASE_URL/actuator/health | grep -q '\"status\":\"UP\"'"

# API endpoints (adjust based on ticket)
check "Messages API" "curl -sf $BASE_URL/api/messages | jq -e ."

# WebSocket (if applicable)
# check "WebSocket connect" "wscat -c ws://$BASE_URL/ws -x 'ping' | grep -q pong"

echo ""
echo "Results: $PASS passed, $FAIL failed"
[ $FAIL -eq 0 ]
```

Run:
```bash
chmod +x smoke-test.sh
./smoke-test.sh http://{host} 2>&1 | tee "docs/operations/{JIRA_KEY}/07-deployment/smoke-test-output.txt"
```

If smoke tests FAIL → go to Step 9 (Rollback).

### Step 7: Monitor Logs and Metrics

```bash
# Check logs for errors
kubectl logs deployment/{name} --tail=200 | grep -i "error\|exception\|fatal"

# Check metrics (if Prometheus available)
curl -s http://{host}/actuator/prometheus | grep -E "http_server_requests_seconds_count|jvm_memory_used_bytes"
```

**Check for**:
- ERROR/FATAL log entries
- High error rate (> 1%)
- High response time (> 2x baseline)
- High CPU/memory (> 90%)
- Connection pool exhaustion
- Database connection errors

If anomalies found → consider rollback (Step 9).

### Step 8: Generate Deploy Report

Write `docs/operations/{JIRA_KEY}/07-deployment/deploy-report.md`:

```markdown
# Deployment Report — {JIRA_KEY}

**Environment**: {staging/production}
**Date**: {ISO timestamp}
**Image**: {registry}/{image}:{tag}
**Result**: SUCCESS / FAILED / ROLLED BACK

## Deployment
- Trigger: {auto on merge / manual}
- Pipeline: {CI/CD URL}
- Rollout duration: {X}s
- Rollout status: {success/failed}

## Health Check
- Status: {UP/DOWN}
- Components: {list}
- Output: {summary}

## Smoke Tests
- Total: {N}
- Passed: {N}
- Failed: {N}
- Details: smoke-test-output.txt

## Monitoring
- Error rate: {X}%
- Avg response time: {X}ms
- CPU: {X}%
- Memory: {X}%
- Anomalies: {none/list}

## Rollback
- Required: {yes/no}
- Reason: {if applicable}
- Rollback duration: {X}s
```

### Step 9: Rollback (if needed)

If health check / smoke tests / monitoring fails:

```bash
# Kubernetes rollback
kubectl rollout undo deployment/{name}
kubectl rollout status deployment/{name}

# Verify rollback
curl -s http://{host}/actuator/health
```

After rollback:
1. Verify previous version is healthy
2. Create incident report
3. Notify human
4. Escalate for root cause analysis

### Step 10: Update Jira (if applicable)

If deployment successful:
- Transition Jira ticket to "Done"
- Add comment with deployment report link

### Step 11: Mark Pipeline Complete

Update `pipeline-state.json`:
```json
{
  "status": "completed",
  "completed_at": "{ISO timestamp}",
  "stages": {
    "7_deployment": {
      "status": "completed",
      "verify_passed": true
    }
  }
}
```

### Step 12: Commit Operation Logs

```bash
git add docs/operations/{JIRA_KEY}/
git commit -m "docs(operations): pipeline complete for {JIRA_KEY}"
git push origin main
```

## Verify Gate (Automated)

| Criteria | Method | Evidence |
|----------|--------|----------|
| PR merged to main | GitHub PR status | `gh pr view` |
| Deployment triggered | CI/CD pipeline started | deploy-log.txt |
| Build successful | Build exit code 0 | Build log |
| Rollout complete | `kubectl rollout status` | deploy-log.txt |
| Health check PASS | `/actuator/health` returns UP | health-check-output.txt |
| All dependencies healthy | Component health | health-check-output.txt |
| Smoke tests PASS | All smoke tests pass | smoke-test-output.txt |
| No ERROR/FATAL logs | Log scan | Log output |
| Error rate < 1% | Metrics | Metrics output |
| Response time within baseline | Metrics | Metrics output |
| CPU/memory < 90% | Metrics | Metrics output |
| Rollback ready (if needed) | Previous version available | Deploy config |
| Deploy report generated | File exists | `ls` output |
| Pipeline state updated | State file | `cat pipeline-state.json` |
| Operation logs committed | Git commit | Git log |
| Jira updated (if applicable) | Jira status | Jira API |

**PASS** → Deployment complete + health check PASS + smoke tests PASS + monitoring normal → Pipeline COMPLETE
**FAIL** → Rollback + escalate to human (max 3 deploy retries, then escalate)

## KB Injection

**Read**:
- `03-Design-Guidelines/05-reliability/` (ALL)
- `04-Coding-Guidelines/` (deployment-related)

**Write**: None (unless new deployment patterns discovered)

## Rollback Triggers

Rollback immediately if ANY of these occur:
- Health check returns DOWN
- Smoke tests fail (> 0 failures)
- Error rate > 5%
- Response time > 5x baseline
- CPU/memory > 95% for > 60s
- Data corruption detected
- Service crash loop (restart count > 3)

## Post-Deployment Monitoring Window

After successful deployment, monitor for:
- **15 minutes**: Critical metrics (error rate, response time, crash loops)
- **1 hour**: Performance trends, resource usage
- **24 hours**: Long-term stability, memory leaks

If issues found during monitoring window → rollback + escalate.

## Error Handling

| Error | Resolution |
|-------|-----------|
| PR not merged | Wait for merge or ask user to merge |
| Build fails | Check build logs, fix, re-trigger |
| Rollout timeout | Check pod status, logs, consider rollback |
| Health check fails | Rollback immediately, investigate |
| Smoke tests fail | Rollback, fix, re-deploy |
| Monitoring anomalies | Assess severity, rollback if critical |
| Rollback fails | Escalate to SRE/DevOps immediately |

## Output Artifacts

- `docs/operations/{JIRA_KEY}/07-deployment/deploy-log.txt` — Deployment log
- `docs/operations/{JIRA_KEY}/07-deployment/health-check-output.txt` — Health check
- `docs/operations/{JIRA_KEY}/07-deployment/smoke-test-output.txt` — Smoke tests
- `docs/operations/{JIRA_KEY}/07-deployment/deploy-report.md` — Deploy report
- `docs/operations/{JIRA_KEY}/07-deployment/verify-report.md` — Verify report
- `docs/operations/{JIRA_KEY}/07-deployment/operation-log.md` — Operation log
- Updated `pipeline-state.json` — Pipeline marked complete

---

*Deployment Skill v1.0.0 — 2026-08-21*
