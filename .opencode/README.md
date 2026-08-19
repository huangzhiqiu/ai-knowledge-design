# OpenCode Configuration

> OpenCode (https://github.com/sst/opencode) project configuration for CBOL Messaging Hub.

## Directory Structure

```
.opencode/
├── opencode.json          # Main configuration (schema: https://opencode.ai/config.json)
├── skills/                # Project-specific skills (junctions to 06-Skills/)
│   ├── workflow-ticket-to-deploy/
│   ├── jira-ticket-fetcher/
│   ├── sdd-generator/
│   ├── tdd-implementer/
│   ├── test-verifier/
│   ├── pr-creator/
│   ├── deploy-doc-updater/
│   ├── java-maven-project-analyzer/
│   ├── architecture-analyzer/
│   └── codebase-architecture-analyst/
├── commands/              # Custom slash commands
│   ├── workflow.md        # /workflow jira_key=CBOL-123
│   ├── analyze.md         # /analyze path=./project
│   └── sdd.md             # /sdd jira_key=CBOL-123
└── rules/                 # Additional rules files
```

## Quick Start

### 1. Install OpenCode

```bash
npm install -g opencode-ai
# or
brew install opencode
```

### 2. Set Environment Variables

```bash
# Jira (for ticket fetching)
export JIRA_API_TOKEN="your-jira-api-token"

# GitHub (for PR creation)
export GITHUB_TOKEN="your-github-token"
```

Or copy `.ai-workflow/config.example.yaml` to `.ai-workflow/config.yaml` and fill in values.

### 3. Start OpenCode

```bash
cd /path/to/ai-knowledge-design
opencode
```

### 4. Use Skills and Commands

```bash
# List available skills
/skills

# Load a specific skill
/skill workflow-ticket-to-deploy

# Use custom commands
/workflow jira_key=CBOL-123
/analyze path=./my-java-project
/sdd jira_key=CBOL-123
```

## Available Skills

### AI Development Pipeline (01)

| Skill | Description |
|-------|-------------|
| `workflow-ticket-to-deploy` | Orchestration skill: 7-stage pipeline from Jira ticket to deployment |
| `jira-ticket-fetcher` | Fetch and parse Jira tickets into structured requirements |
| `sdd-generator` | Generate Software Design Documents with knowledge injection |
| `tdd-implementer` | TDD implementation: RED → GREEN → REFACTOR → Commit |
| `test-verifier` | Verify tests pass, coverage thresholds, quality gates |
| `pr-creator` | Create GitHub PR with template, labels, reviewers |
| `deploy-doc-updater` | Update deployment documentation and runbooks |

### Code Analysis (02)

| Skill | Description |
|-------|-------------|
| `java-maven-project-analyzer` | Analyze Java Maven project: interfaces, models, logic, boundaries, deps |
| `architecture-analyzer` | Deep architecture analysis: patterns, design principles, network comm |
| `codebase-architecture-analyst` | Comprehensive codebase architecture extraction |

### Knowledge Collection (03)

| Skill | Description |
|-------|-------------|
| `cbol-knowledge-collector` | Collect and organize CBOL-specific domain knowledge |
| `chat-pattern-collector` | Collect chat/IM patterns from open source projects |

## Custom Commands

| Command | Description |
|---------|-------------|
| `/workflow jira_key=CBOL-123` | Start or resume the ticket-to-deploy pipeline |
| `/analyze path=./project` | Analyze a Java Maven project |
| `/sdd jira_key=CBOL-123` | Generate an SDD for a Jira ticket |

## Skill Junctions

The `.opencode/skills/` directory contains **directory junctions** (Windows) or **symlinks** (Linux/Mac) pointing to `06-Skills/`. This allows:

1. OpenCode to discover skills from its default `.opencode/skills/` path
2. Maintaining the categorized structure in `06-Skills/` for the knowledge base
3. Single source of truth — edit skills in `06-Skills/`, changes reflect in `.opencode/skills/`

### Recreating Junctions (if needed)

**Windows (PowerShell):**
```powershell
New-Item -ItemType Junction -Path ".opencode\skills\skill-name" -Target "06-Skills\category\skill-name"
```

**Linux/Mac:**
```bash
ln -s ../../06-Skills/category/skill-name .opencode/skills/skill-name
```

## Configuration

### opencode.json

Main configuration file. Key settings:

- `instructions`: Loads `AGENTS.md` as project instructions
- `env`: Required environment variables (JIRA_API_TOKEN, GITHUB_TOKEN)
- `permissions`: Allow/deny lists for file operations and commands

### AGENTS.md

Project-level AI instructions. OpenCode automatically reads this file on startup.

## Pipeline Workflow

```
/workflow jira_key=CBOL-123
    │
    ▼
[0] Ticket Intake ──── Fetch Jira ticket, parse requirements
    │
    ▼
[1] Requirements ──── Clarify, structure, get approval
    │
    ▼
[2] SDD ───────────── Generate design doc, get approval
    │
    ▼
[3] TDD Implement ─── RED → GREEN → REFACTOR → Commit
    │
    ▼
[4] Test ──────────── Verify tests, coverage, quality gates
    │
    ▼
[5] PR ────────────── Create PR, peer review
    │
    ▼
[6] Deploy + Docs ─── Update deployment docs, complete
```

## Troubleshooting

### Skills not showing up

1. Verify junctions exist: `ls -la .opencode/skills/`
2. Recreate junctions if missing (see above)
3. Restart OpenCode

### Jira/GitHub authentication fails

1. Verify environment variables are set: `echo $JIRA_API_TOKEN`
2. Or verify `.ai-workflow/config.yaml` exists and has correct values
3. Check token permissions (Jira: read access; GitHub: repo + pull_request)

### Permission denied

1. Check `opencode.json` permissions section
2. Run with `--allow-all` flag for testing (not recommended for production)

## References

- OpenCode GitHub: https://github.com/sst/opencode
- OpenCode Docs: https://dev.opencode.ai/docs/
- Skills Specification: https://docs.anthropic.com/en/docs/agents-and-tools/skills
- Project Knowledge Base: `../README.md`
- Quick Start: `../QUICKSTART.md`
