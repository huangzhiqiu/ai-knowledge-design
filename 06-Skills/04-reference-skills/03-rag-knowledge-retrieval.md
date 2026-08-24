# 03 — RAG & Knowledge Retrieval

> Reference projects for Retrieval-Augmented Generation (RAG), code-level knowledge bases, semantic search, and vector embeddings.

## Project Index

| # | Project | Author | Type |
|---|---------|--------|------|
| 1 | [clr — Code-Level RAG](#1-clr--code-level-rag) | cjus | RAG pipeline for PR knowledge |
| 2 | [rag-cli v2.0](#2-rag-cli-v20) | itmediatech | RAG CLI with vector embeddings |
| 3 | [Hybrid RAG System](#3-hybrid-rag-system) | glowElephant | RAG with knowledge graph + vector search |
| 4 | [session-rag](#4-session-rag) | mwgreen | Conversation history vector search |
| 5 | [Multi-Agent Research Skill](#5-multi-agent-research-skill) | ahmedibrahim085 | Semantic search RAG skill |
| 6 | [Code-level RAG Guide](#6-code-level-rag-guide) | ashishranjandev | RAG concept and implementation |

---

## 1. clr — Code-Level RAG

**URL**: https://github.com/cjus/clr
**Author**: cjus
**Last updated**: 2026-08-21

### Description
A RAG pipeline that captures the full story of every code change (plan, conversation log, summary) and makes it searchable through vector embeddings. When starting new work, Claude queries this knowledge base for relevant past implementations.

### Key Features
- Captures PR plan, conversation log, and summary
- Vector embeddings for semantic search
- ~450 indexed documents
- Query before writing code
- Learns from every PR shipped

### Architecture
```
PR Merged → Capture (plan + conversation + summary)
              ↓
         Vector Embeddings
              ↓
         Searchable KB
              ↓
New Work → Query KB → Relevant Past Impls → Injected into Prompt
```

### CBOL Relevance
- **Code-level RAG**: Reference for building a knowledge base from past CBOL implementations
- **PR capture**: Pattern for capturing design decisions and implementation details from PRs
- **Semantic search**: When starting new messaging features, search past implementations
- **Knowledge accumulation**: Build institutional knowledge over time

---

## 2. rag-cli v2.0

**URL**: https://github.com/itmediatech/rag-cli
**Author**: itmediatech
**Last updated**: 2026-08-18

### Description
RAG CLI tool with Claude Code plugin support. Auto-indexes current project, supports manual indexing, and provides search command.

### Key Features
- Claude Code plugin: `/rag-project` auto-indexes current project
- Manual indexing: `python scripts/index.py --input data/documents --output data/vectors`
- Search command: `/search "query"`
- Vector embeddings for document retrieval
- Project-aware RAG

### CBOL Relevance
- **Project auto-indexing**: Reference for indexing CBOL codebase and knowledge base
- **Search integration**: `/search` command pattern for querying knowledge
- **CLI tooling**: Pattern for building RAG tooling that integrates with Claude Code
- **Vector store**: Reference for choosing and implementing vector storage

---

## 3. Hybrid RAG System

**URL**: https://github.com/glowElephant/claude-code-rag-setup
**Author**: glowElephant
**Last updated**: 2026-08-22

### Description
Hybrid RAG system that combines knowledge graph traversal with vector search. Automatically classifies queries and chooses the best search strategy.

### Key Features
- **Auto-classification**: Query type detection (relationship query → graph-first, keyword → vector-first)
- **Knowledge graph**: Traverses dependencies, inheritance, field relationships
- **Vector search**: Semantic similarity for related code
- **Reranking**: Results reranked by relevance
- **Multi-source**: Past sessions, project code, documents, knowledge graph

### Query Example
```
User: "What classes are related to OutlineController?"
→ rag_agent auto-classifies: "relationship query → graph-first search"
→ Traverses dependencies/inheritance/field relationships in the graph
→ Supplements with vector search for related code
→ Reranks results by relevance
→ Claude Code answers with full context
```

### CBOL Relevance
- **Hybrid search**: Reference for combining graph-based and vector-based search in CBOL knowledge base
- **Query classification**: Pattern for choosing search strategy based on query type
- **Knowledge graph**: Building a graph of CBOL domain entities and relationships
- **Reranking**: Improving search result quality

---

## 4. session-rag

**URL**: https://github.com/mwgreen/claude-code-session-rag
**Author**: mwgreen
**Last updated**: 2026-08-11

### Description
Indexes conversation turns into a vector database so Claude can search past discussions. Uses local embedding models and Milvus Lite for vector storage.

### Key Features
- Conversation turn indexing
- Vector search across past sessions
- Embedding model: EmbeddingGemma-300M (default) or ModernBERT Embed Base
- Vector store: Milvus Lite (global DB at `~/.session-rag/milvus.db`)
- Apple Silicon optimized via mlx-embeddings

### CBOL Relevance
- **Conversation history search**: Reference for searching past CBOL design discussions
- **Local embeddings**: Using local models for privacy and cost efficiency
- **Milvus Lite**: Lightweight vector store option for knowledge base
- **Session indexing**: Pattern for indexing design sessions and decision logs

---

## 5. Multi-Agent Research Skill

**URL**: https://github.com/ahmedibrahim085/claude-multi-agent-research-system-skill
**Author**: ahmedibrahim085
**Last updated**: 2026-08-19

### Description
Claude Code skill with semantic-search that implements RAG for code. Converts code into vector embeddings and uses semantic similarity to retrieve contextually relevant chunks.

### Key Features
- Semantic search for code (meaning-based, not keyword)
- Vector embeddings: `google/embeddinggemma-300m` (768 dimensions)
- ~1.5GB disk space for embedding model
- Natural language code queries
- Multi-agent research system

### CBOL Relevance
- **Code semantic search**: Reference for searching CBOL codebase by meaning
- **Embedding model**: Reference for choosing embedding model for code
- **Natural language queries**: "Find code related to message forwarding" pattern
- **Skill packaging**: How to package RAG as a Claude Code skill

---

## 6. Code-level RAG Guide

**URL**: https://github.com/ashishranjandev/developer-wiki/wiki/Claude-Code/ca6f72e9358af182f69e7f389cf8dfe6afee7fe
**Author**: ashishranjandev
**Last updated**: 2026-08-20

### Description
Conceptual guide to RAG (Retrieval-Augmented Generation) — retrieve relevant information from external source at query time and inject into prompt, like an open-book exam.

### Key Concepts
- **Traditional LLM**: User asks → LLM answers from memory → may be wrong or stale
- **RAG**: User asks → retrieve relevant docs → inject into prompt → LLM answers with real data
- **Four steps**: Chunking → Embedding → Retrieval → Prompt assembly

### CBOL Relevance
- **RAG fundamentals**: Conceptual foundation for building CBOL knowledge retrieval
- **Open-book exam pattern**: Reference for how to inject CBOL domain knowledge into AI workflow
- **Four-step pipeline**: Chunking, embedding, retrieval, assembly — reference for implementation

---

## Summary: RAG Patterns for CBOL

| Pattern | Source | CBOL Application |
|---------|--------|-----------------|
| PR knowledge capture | clr | Capture design decisions from CBOL PRs |
| Project auto-indexing | rag-cli | Index CBOL codebase and docs |
| Hybrid graph + vector search | glowElephant | Search CBOL domain entities + code |
| Conversation history search | session-rag | Search past CBOL design discussions |
| Code semantic search | ahmedibrahim085 | Search CBOL code by meaning |
| Local embeddings | session-rag | Privacy-preserving vector search |
| Query classification | glowElephant | Choose search strategy for CBOL queries |
| Reranking | glowElephant | Improve KB search result quality |
| Four-step RAG pipeline | ashishranjandev | Foundation for CBOL RAG implementation |

---

*RAG & Knowledge Retrieval Reference — 2026-08-24*
