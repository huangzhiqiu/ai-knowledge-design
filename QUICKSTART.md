# Quick Start

> Get started with the CBOL Refactor AI development pipeline in 5 minutes.

---

## Prerequisites

- [OpenCode](https://opencode.ai) (or Claude Code / Cursor)
- Java 17+ and Maven (for code generation)
- Jira account with API token
- GitHub account with API token

---

## Step 1: Configure

```bash
# 1. Copy example config
cp .ai-workflow/config.example.yaml .ai-workflow/config.yaml

# 2. Edit config with your Jira/GitHub info
#    - jira.base_url, jira.email, jira.project_key
#    - github.owner, github.repo

# 3. Set environment variables (NEVER commit tokens)
export JIRA_API_TOKEN="your-jira-token"
export GITHUB_TOKEN="your-github-token"
```

**Get Jira API token**: https://id.atlassian.com/manage-profile/security/api-tokens
**Get GitHub token**: https://github.com/settings/tokens (fine-grained, Contents + Pull requests read/write)

---

## Step 2: Install Skills

```powershell
# Windows - install all pipeline skills to OpenCode
$skills = @(
    "06-Skills/01-ai-development-pipeline/workflow-ticket-to-deploy",
    "06-Skills/01-ai-development-pipeline/jira-ticket-fetcher",
    "06-Skills/01-ai-development-pipeline/sdd-generator",
    "06-Skills/01-ai-development-pipeline/tdd-implementer",
    "06-Skills/01-ai-development-pipeline/test-verifier",
    "06-Skills/01-ai-development-pipeline/pr-creator",
    "06-Skills/01-ai-development-pipeline/deploy-doc-updater"
)
foreach ($s in $skills) {
    $name = Split-Path $s -Leaf
    Copy-Item -Recurse -Force $s "$env:USERPROFILE\.config\opencode\skills\$name"
}
```

Restart OpenCode after installation.

---

## Step 3: Run the Pipeline

```
# In OpenCode, start the pipeline for a ticket
/workflow-ticket-to-deploy jira_key=CBOL-123
```

The pipeline will guide you through 7 stages with 6 approval gates:

```
Stage 0: Ticket Intake      → Gate 0: Clarity check
Stage 1: Requirements        → Gate 1: Human review
Stage 2: SDD Generation      → Gate 2: Human review
Stage 3: TDD Implementation  → Gate 3: Automated self-review
Stage 4: Test & Verification → Gate 4: Human review
Stage 5: PR Creation         → Gate 5: Peer review (enforced)
Stage 6: Deploy & Doc Update → Complete
```

**At each gate**, the AI pauses and waits for your approval. You can:
- ✅ Approve → proceed to next stage
- 🔄 Request revisions → AI revises and re-submits (max 3 loops)
- ❓ Ask questions → AI answers, then you decide

---

## Step 4: Review Artifacts

All artifacts are stored in the project:

| Artifact | Location |
|----------|----------|
| Operation logs | `docs/operations/CBOL-123/` |
| SDD | `design/sdd/CBOL-123.md` |
| TDD cycles | `docs/operations/CBOL-123/tdd-cycles.md` |
| Test report | `docs/operations/CBOL-123/test-report.md` |
| GitHub PR | Link in Jira comment + `docs/operations/CBOL-123/05-pr-v1.md` |

---

## Key Commands

| Command | Purpose |
|---------|---------|
| `/workflow-ticket-to-deploy jira_key=CBOL-123` | Start full pipeline |
| `/workflow-ticket-to-deploy jira_key=CBOL-123 start_from=stage3` | Resume from Stage 3 |
| `/jira-ticket-fetcher jira_key=CBOL-123` | Just fetch and structure a ticket |
| `/sdd-generator jira_key=CBOL-123` | Just generate SDD (requires ticket doc) |
| `/tdd-implementer jira_key=CBOL-123` | Just implement (requires SDD) |

---

## Best Practices

1. **Start small**: First run with a simple ticket to validate the pipeline
2. **Review SDD carefully**: Stage 2 is the most important gate — good SDD = good code
3. **Don't skip gates**: Each gate prevents errors from propagating downstream
4. **Keep tickets focused**: One ticket = one feature/fix. Split large tickets.
5. **Maintain the knowledge base**: Update `01-CBOL-Domain-Knowledge/` as you learn

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Jira authentication fails | Check `JIRA_API_TOKEN` env var and email in config |
| GitHub push fails | Check `GITHUB_TOKEN` env var and repo permissions |
| AI goes off-track | Stop, review operation logs, restart from last completed gate |
| Tests fail repeatedly | After 3 failures, pipeline escalates — review the fix loop log |
| Skill not found | Restart OpenCode after installing skills |

---

## Next Steps

- Read [AGENTS.md](./AGENTS.md) for AI development guidelines
- Read [06-Skills/README.md](./06-Skills/README.md) for all skill documentation
- Read [05-References/ai-driven-development.md](./05-References/ai-driven-development.md) for reference projects
