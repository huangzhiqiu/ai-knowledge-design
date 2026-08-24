---
name: skill-create
description: Analyze local git history to extract coding patterns and generate SKILL.md files. Use when you want to capture a project's conventions as a reusable skill.
argument-hint: [--commits N] [--output path]
allowed-tools: Bash, Read, Write, Grep, Glob
---

# Skill Create

Analyze your repository's git history to extract coding patterns and generate SKILL.md files that teach Claude your team's practices.

## Usage

```bash
/skill-create                    # Analyze current repo (last 200 commits)
/skill-create --commits 100      # Analyze last 100 commits
/skill-create --output ./skills  # Custom output directory
```

## Step 1: Gather Git Data

```bash
# Recent commits with file changes
git log --oneline -n 200 --name-only --pretty=format:"%H|%s|%ad" --date=short

# Most frequently changed files
git log --oneline -n 200 --name-only | grep -v "^$" | grep -v "^[a-f0-9]" | sort | uniq -c | sort -rn | head -20

# Commit message patterns
git log --oneline -n 200 | cut -d' ' -f2- | head -50
```

## Step 2: Detect Patterns

| Pattern | Detection Method |
|---------|-----------------|
| Commit conventions | Regex on commit messages (feat:, fix:, chore:) |
| File co-changes | Files that always change together |
| Workflow sequences | Repeated file change patterns |
| Architecture | Folder structure and naming conventions |
| Testing patterns | Test file locations, naming, framework |

## Step 3: Generate SKILL.md

Output to `~/.claude/skills/<repo-name>-patterns/SKILL.md`:

```markdown
---
name: {repo-name}-patterns
description: Coding patterns extracted from {repo-name} git history
---

# {Repo Name} Patterns

## Commit Conventions
{detected patterns}

## Code Architecture
{detected folder structure}

## Workflows
{detected repeating change patterns}

## Testing Patterns
{detected test conventions}

## Gotchas
{detected common fix patterns from commit messages}
```

## Output Location

Default: `~/.claude/skills/<repo-name>-patterns/SKILL.md`
Custom: use `--output <path>`

After generating, restart Claude Code or run `/skills` to confirm it loaded.
