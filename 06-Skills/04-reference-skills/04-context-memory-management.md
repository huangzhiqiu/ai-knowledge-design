# 04 — Context & Memory Management

> Reference projects for context compression, long-term memory, session recall, conversation preservation, and compaction strategies.

## Project Index

| # | Project | Author | Type |
|---|---------|--------|------|
| 1 | [Rolling Context](#1-rolling-context) | NodeNestor | Background context compression |
| 2 | [CPR — Compress Preserve Resume](#2-cpr--compress-preserve-resume) | EliaAlberti | 3-skill memory system |
| 3 | [Recall](#3-recall) | flinedev | Conversation detail preservation |
| 4 | [Infinite Context](#4-infinite-context) | rocketlabs-ai | Turn-by-turn summary rebuild |
| 5 | [claude-context-mem](#5-claude-context-mem) | cylnet | Cross-session long-term memory |
| 6 | [Claudest — recall-conversations](#6-claudest--recall-conversations) | llaith-ai | FTS5 conversation search |
| 7 | [Claude Skills Automation](#7-claude-skills-automation) | Toowiredd | Context auto-restore + decisions |
| 8 | [Context Management Deep Dive](#8-context-management-deep-dive) | claude-code-internals | 40+ source files analysis |

---

## 1. Rolling Context

**URL**: https://github.com/NodeNestor/claude-rolling-context
**Author**: NodeNestor
**Last updated**: 2026-08-20

### Description
Rolling context compression for Claude Code. Merges summaries each compression cycle, building a rolling timeline. Runs in background, applied on next request.

### Key Features
- **Merges summaries**: Each compression cycle merges with previous summary
- **Rolling timeline**: Builds cumulative context over time
- **Never blocks**: Compression runs in background
- **Full transcripts preserved**: JSONL in `~/.claude/projects/`
- Claude Code Plugin installation

### CBOL Relevance
- **Rolling summary**: Reference for maintaining conversation context in long CBOL design sessions
- **Background compression**: Non-blocking context management pattern
- **Timeline building**: Cumulative context for multi-session projects

---

## 2. CPR — Compress Preserve Resume

**URL**: https://github.com/EliaAlberti/cpr-compress-preserve-resume
**Author**: EliaAlberti
**Last updated**: 2026-08-20

### Description
Three-skill memory system for Claude Code: `/preserve`, `/compress`, `/resume`. Keeps CLAUDE.md lean, captures full session into searchable log, restores context on new sessions.

### Key Features
- **/preserve**: Updates CLAUDE.md with key learnings, keeps under 280 lines, auto-archives when too long
- **/compress**: Captures full session (decisions, solutions, files, errors) into structured searchable log
- **/resume**: Loads CLAUDE.md + last N session log summaries, supports topic search across past sessions

### Architecture
```
Session Running → /compress → structured log file
                     ↓
                /preserve → CLAUDE.md (lean, <280 lines)
                     ↓
New Session → /resume → CLAUDE.md + recent logs → full context restored
```

### CBOL Relevance
- **Three-skill pattern**: Reference for building CBOL knowledge preservation workflow
- **Lean CLAUDE.md**: Keeping AGENTS.md / project docs lean with auto-archiving
- **Structured session logs**: Capturing CBOL design decisions in searchable format
- **Resume capability**: Restoring context for multi-session CBOL development

---

## 3. Recall

**URL**: https://github.com/flinedev/recall
**Author**: flinedev
**Last updated**: 2026-08-23

### Description
Preserves far more conversation detail (15-18K tokens of actual conversation) across compactions, giving Claude the full conversation arc to continue where you left off.

### Key Features
- Preserves 15-18K tokens of actual conversation (vs summary loss)
- Full conversation arc preservation
- Complement to built-in compaction
- Reduces detail loss in long sessions

### CBOL Relevance
- **Detail preservation**: Reference for preserving detailed CBOL design discussions across context limits
- **Conversation arc**: Maintaining full context of complex messaging design decisions
- **Compaction complement**: How to supplement built-in compaction with more detail

---

## 4. Infinite Context

**URL**: https://github.com/rocketlabs-ai/infinite-context
**Author**: rocketlabs-ai
**Last updated**: 2026-08-22

### Description
Rebuilds context from session JSONL using an Opus agent to write turn-by-turn summaries. Preserves pre-rebuild notes for guidance on what matters.

### Key Features
- Opus agent reads full JSONL, writes turn-by-turn summary
- Alternating USER: and ASSISTANT: lines format
- Pre-rebuild notes for guidance
- Full transcript preservation

### Summarizer Prompt Template
```
Read the session JSONL at <path>.
Also read the pre-rebuild notes at <notes-path>.

Write a turn-by-turn summary as alternating USER: and ASSISTANT: lines.

Rules:
- Preserve the actual...
```

### CBOL Relevance
- **Turn-by-turn summary**: Reference for summarizing long CBOL design sessions
- **Opus agent for summarization**: Using higher-tier model for context compression
- **Pre-rebuild notes**: Guidance for what CBOL-specific details to preserve

---

## 5. claude-context-mem

**URL**: https://github.com/cylnet/claude-context-mem
**Author**: cylnet
**Last updated**: 2026-08-21

### Description
Provides cross-session, recallable, updatable long-term memory for Claude without modifying the model. Just a few lines of code to upgrade "goldfish memory" to long-term memory.

### Key Features
- Cross-session memory persistence
- Recallable and updatable
- No model modification needed
- Plugin marketplace installation
- Auto-appears in new sessions

### CBOL Relevance
- **Cross-session memory**: Reference for maintaining CBOL project context across sessions
- **Recallable memory**: Pattern for storing and retrieving project-specific knowledge
- **Plugin format**: How to package memory as a plugin

---

## 6. Claudest — recall-conversations

**URL**: https://github.com/llaith-ai/Claudest
**Author**: llaith-ai
**Last updated**: 2026-08-20

### Description
Skill that lets agent search conversation history by keywords, browse recent sessions, or run structured analyses like retrospectives and gap-finding. Uses FTS5 full-text search.

### Key Features
- Keyword search across conversation history
- Browse recent sessions
- Structured analyses: retrospectives, gap-finding
- FTS5 full-text search
- Agent constructs queries (not user)

### Query Example
```
User: "what did we decide about the API design?"
→ Agent extracts keywords
→ Sends to FTS5 search
→ Iterates if first results aren't relevant
→ Returns decisions from past conversations
```

### CBOL Relevance
- **Conversation search**: Reference for searching past CBOL design decisions
- **FTS5 search**: Full-text search for conversation history
- **Retrospectives**: Pattern for reviewing CBOL project progress and identifying gaps
- **Agent-constructed queries**: AI extracts keywords automatically

---

## 7. Claude Skills Automation

**URL**: https://github.com/Toowiredd/claude-skills-automation
**Author**: Toowiredd
**Last updated**: 2026-08-20

### Description
Automation skills for context restoration and decision tracking. Solves ADHD and SDAM (No Episodic Memory) developer needs.

### Key Features
- **Context auto-restored**: No more lost context between sessions
- **Decisions auto-extracted**: All decisions saved automatically
- **Zero manual work**: No manual memory management
- **Before/After comparison table**

### CBOL Relevance
- **Auto context restore**: Reference for automatic CBOL project context restoration
- **Decision extraction**: Pattern for automatically extracting design decisions
- **Zero manual work**: Automation for knowledge management

---

## 8. Context Management Deep Dive

**URL**: https://github.com/claude-code-internals/claude-code-runnable/blob/main/docs/en/05_module_context.md
**Author**: claude-code-internals
**Last updated**: 2026-08-19
**Size**: 680 lines, 23.7KB

### Description
Deep dive into Claude Code's context construction, conversation compaction, session memory, and multi-layer cache system. Spans 40+ source files.

### Key Topics
- Context construction pipeline
- Conversation compaction (iterative v6)
- Session memory
- Multi-layer cache system
- Information density preservation
- Prompt cache optimization

### CBOL Relevance
- **Internal context management**: Understanding how Claude Code manages context for building CBOL workflow
- **Compaction strategies**: Reference for handling long CBOL design sessions
- **Cache optimization**: Patterns for optimizing context usage in AI workflow

---

## Summary: Context & Memory Patterns for CBOL

| Pattern | Source | CBOL Application |
|---------|--------|-----------------|
| Rolling summary timeline | Rolling Context | Maintain context across long CBOL sessions |
| 3-skill preserve/compress/resume | CPR | CBOL knowledge preservation workflow |
| Conversation detail preservation | Recall | Preserve detailed messaging design discussions |
| Turn-by-turn summary | Infinite Context | Summarize CBOL design sessions |
| Cross-session long-term memory | claude-context-mem | CBOL project memory across sessions |
| FTS5 conversation search | Claudest | Search past CBOL design decisions |
| Auto decision extraction | Claude Skills Automation | Auto-extract CBOL design decisions |
| Lean docs with auto-archive | CPR | Keep AGENTS.md / KB docs lean |
| Background non-blocking compression | Rolling Context | Non-blocking context management |
| Pre-rebuild guidance notes | Infinite Context | CBOL-specific preservation guidance |

---

*Context & Memory Management Reference — 2026-08-24*
