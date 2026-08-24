# 08 — Session & Conversation History

> Reference projects for session management, conversation history search, transcript analysis, and session persistence.

## Project Index

| # | Project | Author | Type |
|---|---------|--------|------|
| 1 | [claude-friends Session API](#1-claude-friends-session-api) | dgrims3 | REST + WebSocket session API |
| 2 | [Claudest recall-conversations](#2-claudest-recall-conversations) | llaith-ai | FTS5 conversation search |
| 3 | [session-rag](#3-session-rag) | mwgreen | Vector search conversation history |
| 4 | [Session Transcript Analysis](#4-session-transcript-analysis) | claude-code-internals | Transcript service deep dive |

---

## 1. claude-friends Session API

**URL**: https://github.com/dgrims3/claude-friends
**Author**: dgrims3
**Last updated**: 2026-08-17

### Description
Mobile chat application with comprehensive session management API. Features session start/resume, status checking, message history, and real-time WebSocket chat.

### Session API Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /sessions/:id/start | Start or resume a session |
| GET | /sessions/:id/status | Check session status |
| GET | /sessions/:id/history | Get message history |
| DELETE | /sessions/:id | Kill a session |
| WS | /sessions/:id/ws | Real-time chat WebSocket |

### Session Lifecycle
```
Create Session → POST /sessions/:id/start
      ↓
Session Active → WS /sessions/:id/ws (real-time chat)
      ↓
Check Status → GET /sessions/:id/status
      ↓
Get History → GET /sessions/:id/history
      ↓
End Session → DELETE /sessions/:id
```

### CBOL Relevance
- **Session management API**: Direct reference for CBOL conversation session lifecycle
- **Start/resume**: Pattern for handling conversation reconnection
- **Status checking**: Monitor active conversations in CBOL
- **Message history**: Retrieve and display conversation history
- **Session termination**: Clean up resources when conversation ends
- **WebSocket per session**: Pattern for real-time messaging per conversation
- **Direct mapping**: CBOL conversation states (INIT, AI_PROCESSING, etc.) map to session status

---

## 2. Claudest recall-conversations

**URL**: https://github.com/llaith-ai/Claudest
**Author**: llaith-ai
**Last updated**: 2026-08-20

### Description
Skill that lets agent search conversation history by keywords, browse recent sessions, or run structured analyses like retrospectives and gap-finding. Uses FTS5 full-text search.

### Key Features
- **Keyword search**: Search conversation history by keywords
- **Browse recent sessions**: Navigate through past conversations
- **Structured analyses**: Retrospectives, gap-finding
- **FTS5 full-text search**: Fast full-text search
- **Agent-constructed queries**: AI extracts keywords, not user
- **Iterative search**: If first results aren't relevant, agent refines query

### Search Flow
```
User: "what did we decide about the API design?"
      ↓
Agent extracts keywords: ["API design", "decide", "design decision"]
      ↓
FTS5 search across conversation history
      ↓
Results returned
      ↓
If not relevant → agent refines query → search again
      ↓
Return decisions from past conversations
```

### CBOL Relevance
- **Conversation history search**: Reference for searching past CBOL design discussions
- **FTS5 search**: Full-text search for CBOL conversation logs
- **Retrospectives**: Pattern for reviewing CBOL project progress
- **Gap-finding**: Identify missing pieces in CBOL design
- **Agent-constructed queries**: AI automatically searches relevant past conversations
- **Iterative refinement**: Improve search results by refining queries
- **Direct application**: When designing new CBOL features, search past similar decisions

---

## 3. session-rag

**URL**: https://github.com/mwgreen/claude-code-session-rag
**Author**: mwgreen
**Last updated**: 2026-08-11

### Description
Indexes conversation turns into a vector database so Claude can search past discussions by semantic meaning. Uses local embedding models and Milvus Lite for vector storage.

### Key Features
- **Conversation turn indexing**: Each turn becomes a vector
- **Semantic search**: Search by meaning, not just keywords
- **Local embeddings**: EmbeddingGemma-300M (default) or ModernBERT Embed Base
- **Milvus Lite**: Lightweight vector store (`~/.session-rag/milvus.db`)
- **Apple Silicon optimized**: via mlx-embeddings
- **Cross-session search**: Search across all past sessions

### Architecture
```
Conversation Turns → Embedding Model → Vector Database (Milvus Lite)
                                          ↓
New Query → Embedding → Semantic Search → Relevant Past Turns
                                          ↓
                                  Injected into Context
```

### CBOL Relevance
- **Semantic conversation search**: Search past CBOL discussions by meaning
- **Local embeddings**: Privacy-preserving vector search for CBOL
- **Milvus Lite**: Lightweight vector store option for CBOL knowledge base
- **Cross-session search**: Search across all CBOL design sessions
- **Turn-level indexing**: Fine-grained search of conversation history
- **Direct application**: When working on CBOL messaging features, semantically search past similar discussions

---

## 4. Session Transcript Analysis

**URL**: https://github.com/claude-code-internals/claude-code-runnable/blob/main/docs/en/05_module_context.md
**Author**: claude-code-internals
**Last updated**: 2026-08-19
**Size**: 680 lines, 23.7KB

### Description
Deep dive into Claude Code's session transcript service, context construction, and conversation management. Covers transcript persistence, session memory, and how context is built from transcript history.

### Key Topics
- **Session transcript service**: Persists all conversation turns
- **Transcript classifier**: Categorizes transcript content
- **Context construction**: Builds context from transcript + tools + files
- **Conversation compaction**: Compresses long conversations
- **Session memory**: Cross-session memory persistence
- **Multi-layer cache**: Optimize context retrieval

### Transcript Structure
```
Session Transcript (JSONL)
├── Turn 1
│   ├── User message
│   ├── Tool calls
│   ├── Tool results
│   └── Assistant response
├── Turn 2
│   └── ...
└── Turn N
```

### CBOL Relevance
- **Transcript persistence**: Reference for persisting CBOL conversation history
- **Context construction**: How to build context from conversation history
- **Transcript classification**: Categorize CBOL message types
- **Conversation compaction**: Handle long CBOL conversations
- **Session memory**: Cross-session memory for CBOL project
- **Multi-layer cache**: Optimize CBOL conversation retrieval
- **Internal architecture**: Understanding how AI agents manage conversation context

---

## Summary: Session & Conversation Patterns for CBOL

| Pattern | Source | CBOL Application |
|---------|--------|-----------------|
| Session lifecycle API | claude-friends | CBOL conversation session management |
| Start/resume session | claude-friends | Conversation reconnection in CBOL |
| Session status checking | claude-friends | Monitor active CBOL conversations |
| Message history retrieval | claude-friends | Retrieve CBOL conversation history |
| Session termination | claude-friends | Clean up CBOL conversation resources |
| WebSocket per session | claude-friends | Real-time messaging per CBOL conversation |
| FTS5 conversation search | Claudest | Search past CBOL design discussions |
| Keyword search | Claudest | Find past CBOL decisions by keyword |
| Retrospective analysis | Claudest | Review CBOL project progress |
| Gap-finding analysis | Claudest | Identify missing CBOL design pieces |
| Agent-constructed queries | Claudest | AI auto-searches relevant past conversations |
| Iterative search refinement | Claudest | Improve CBOL conversation search results |
| Semantic conversation search | session-rag | Search CBOL discussions by meaning |
| Local embeddings | session-rag | Privacy-preserving CBOL vector search |
| Milvus Lite vector store | session-rag | Lightweight CBOL knowledge base storage |
| Cross-session search | session-rag | Search across all CBOL design sessions |
| Turn-level indexing | session-rag | Fine-grained CBOL conversation search |
| Transcript persistence | claude-code-internals | Persist CBOL conversation history |
| Context construction | claude-code-internals | Build context from CBOL conversation |
| Transcript classification | claude-code-internals | Categorize CBOL message types |
| Conversation compaction | claude-code-internals | Handle long CBOL conversations |
| Session memory | claude-code-internals | Cross-session CBOL project memory |
| Multi-layer cache | claude-code-internals | Optimize CBOL conversation retrieval |

---

*Session & Conversation History Reference — 2026-08-24*
