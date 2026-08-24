# Claude Code TDD Skill

An interactive Test-Driven Development workflow for [Claude Code](https://docs.anthropic.com/en/docs/claude-code) that guides you through the RED-GREEN-REFACTOR cycle with checkpoints, research delegation, and automatic test documentation.

## Features

- **Interactive Checkpoints** - User prompted at every step for explicit control
- **Session Persistence** - Progress saved after each step, resume anytime
- **Research Delegation** - Sub-agents handle codebase exploration, keeping main context lean
- **Automatic Test Documentation** - Generates cumulative `tests.md` with test descriptions
- **Cross-Platform** - Works on Linux, macOS, and Windows

## Installation

### Linux / macOS / WSL

```bash
git clone https://github.com/or-ituran/claude-tdd-skill.git ~/.claude/skills/tdd
cd ~/.claude/skills/tdd && chmod +x setup.sh && ./setup.sh
```

### Windows (PowerShell)

```powershell
git clone https://github.com/or-ituran/claude-tdd-skill.git $env:USERPROFILE\.claude\skills\tdd
cd $env:USERPROFILE\.claude\skills\tdd; .\setup.ps1
```

> **Note:** The setup script creates symlinks from `~/.claude/agents/` to the skill's agents folder. On Windows, you may need to run PowerShell as Administrator or enable Developer Mode for symlinks to work.

## Usage

In Claude Code, use any of these triggers:

```
"implement with TDD"
"write tests first"
"start TDD session"
"/tdd"
```

### Example

```
> implement user authentication with TDD
```

Claude Code will guide you through:
1. **Stack Analysis** - Detect your project's language, framework, test runner
2. **Codebase Research** - Find related code and patterns
3. **Test Design** - Plan test cycles before implementation
4. **TDD Cycles** - RED → GREEN → REFACTOR with commits
5. **Session Review** - Extract learnings, update test documentation

## Workflow Overview

```
┌─────────────────────────────────────────────────────────────────┐
│              TDD Orchestrator (Main Conversation)               │
│  - Step management                                              │
│  - Progress file updates                                        │
│  - User prompts (AskUserQuestion)                               │
│  - Sub-agent invocation                                         │
│  - Git commits                                                  │
└──────────────────────────────┬──────────────────────────────────┘
                               │ Task tool calls
       ┌───────────────────────┼───────────────────────────────────┐
       │                       │                                   │
       ▼                       ▼                                   ▼
┌─────────────────┐   ┌─────────────────────┐   ┌─────────────────┐
│  RESEARCH       │   │  TDD CYCLE          │   │  COMPLETION     │
│  Agents         │   │  Agents             │   │  Agents         │
│ (INIT Phase)    │   │  (CYCLE Phase)      │   │  (FINAL Phase)  │
├─────────────────┤   ├─────────────────────┤   ├─────────────────┤
│ tdd-stack-      │   │ tdd-test-writer     │   │ tdd-coverage-   │
│   analyzer      │   │ tdd-implementer     │   │   checker       │
│ tdd-codebase-   │   │ tdd-failure-        │   │ tdd-session-    │
│   researcher    │   │   analyzer          │   │   reviewer      │
│ tdd-pattern-    │   │ tdd-refactorer      │   └─────────────────┘
│   researcher    │   └─────────────────────┘
│ tdd-dependency- │
│   researcher    │
│ tdd-test-       │
│   designer      │
└─────────────────┘
```

## Session Files

After running a TDD session, your project will have:

```
.tdd/
├── sessions/             # Active TDD sessions
├── archive/              # Completed sessions with summaries
├── stack.md              # Project stack profile
├── learnings.json        # Cross-session learning database
├── metrics-history.json  # Historical metrics
├── coverage-history.json # Coverage tracking
└── tests.md              # Cumulative test documentation
```

### Test Documentation (`tests.md`)

Automatically generated documentation of all tests written during TDD sessions:

```markdown
## UserValidatorTests

**Session**: 2026-02-02_user-auth
**File**: src/auth/UserValidator.spec.ts

| Test Name | Description | Input | Expected Result |
|-----------|-------------|-------|-----------------|
| `Should_ValidateEmail_WhenFormatIsCorrect` | Verifies valid email passes validation | Email: "user@example.com" | Returns true |
| `Should_RejectEmail_WhenMissingAtSymbol` | Verifies invalid email is rejected | Email: "userexample.com" | Returns false, error message |
```

## Platform Support

| Platform | Status | Notes |
|----------|--------|-------|
| Linux | ✅ Fully supported | Native bash scripts |
| macOS | ✅ Fully supported | Native bash scripts |
| Windows (WSL) | ✅ Fully supported | Use bash scripts |
| Windows (PowerShell) | ✅ Supported | Use `.ps1` scripts |
| Windows (Git Bash) | ✅ Supported | Use bash scripts |

## File Structure

```
claude-tdd-skill/
├── SKILL.md                 # Main orchestration document
├── README.md                # This file
├── agents/                  # Sub-agent definitions
│   ├── tdd-stack-analyzer.md
│   ├── tdd-codebase-researcher.md
│   ├── tdd-pattern-researcher.md
│   ├── tdd-dependency-researcher.md
│   ├── tdd-test-designer.md
│   ├── tdd-test-writer.md
│   ├── tdd-implementer.md
│   ├── tdd-failure-analyzer.md
│   ├── tdd-refactorer.md
│   ├── tdd-coverage-checker.md
│   └── tdd-session-reviewer.md
├── references/              # Best practices and templates
│   ├── tdd-best-practices.md
│   ├── test-templates.md
│   ├── tests-md-template.md
│   └── ...
├── scripts/                 # Cross-platform init scripts
│   ├── init-tdd-folder.sh   # Linux/macOS/WSL
│   └── init-tdd-folder.ps1  # Windows PowerShell
└── examples/
    └── progress-template.md
```

## Resuming Sessions

Sessions are automatically saved. To resume:

```
"resume TDD"
"/tdd"
```

Claude Code will detect the paused session and offer to continue from where you left off.

## Integration with Other Skills

| Skill | Integration |
|-------|-------------|
| `spec-driven-dev` | Read acceptance criteria from spec |
| `test-runner` | Used by sub-agents to run tests |
| `clean-code` | tdd-refactorer may consult |
| `dotnet-oop` | tdd-refactorer may consult for .NET |

## Requirements

- [Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code)
- Git (for commits during TDD cycles)

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

## License

MIT License - see [LICENSE](LICENSE) for details.

## Acknowledgments

Built for use with [Claude Code](https://docs.anthropic.com/en/docs/claude-code) by Anthropic.
