# 01 — Instant Messaging & Chat Systems

> Reference projects for building chat applications, IM bridges, multi-agent chatrooms, and WebSocket-based messaging systems.

## Project Index

| # | Project | Author | Stars | Type |
|---|---------|--------|-------|------|
| 1 | [claude-chat](#1-claude-chat) | vikrantjain | — | Multi-instance chat plugin |
| 2 | [claude-code-chat](#2-claude-code-chat) | vikrantjain | — | Claude-to-Claude collaboration |
| 3 | [claude-friends](#3-claude-friends) | dgrims3 | — | Mobile chat app with WebSocket |
| 4 | [claude-agent-chatroom](#4-claude-agent-chatroom) | ctb111 | — | Agent chatroom with MCP + WebSocket |
| 5 | [claude_chat_room](#5-claude_chat_room) | BruceWW | — | Multi-agent chat room, WebSocket rooms |
| 6 | [Claude-to-IM-skill](#6-claude-to-im-skill) | harrychin-cn | — | Bridge to Telegram/Discord/Feishu/QQ/WeChat |
| 7 | [feishu-claude-code](#7-feishu-claude-code) | wilburx813 | — | Feishu WebSocket integration |
| 8 | [claude-to-im-skill (skillsllm)](#8-claude-to-im-skill-skillsllm) | skillsllm.com | — | IM bridge skill listing |

---

## 1. claude-chat

**URL**: https://github.com/vikrantjain/claude-chat
**Author**: vikrantjain
**Last updated**: 2026-08-09

### Description
A Claude Code plugin that lets multiple Claude Code instances chat with each other in real time — across machines, containers, and networks — through a shared WebSocket broker. Built on the experimental Channels API.

### Key Features
- Real-time multi-instance chat via WebSocket broker
- Cross-machine, cross-container, cross-network communication
- Built on Claude Code Channels API (experimental)
- Note: Claude Code v2.1.22+ ships cross-session messaging built-in

### Architecture
```
Claude Instance A ←→ WebSocket Broker ←→ Claude Instance B
                      (shared channel)
```

### CBOL Relevance
- **WebSocket broker pattern**: Reference for building message routing between multiple clients
- **Cross-network communication**: Patterns for handling distributed messaging
- **Channels API**: If using Claude Code as part of workflow, this shows how to leverage built-in messaging

---

## 2. claude-code-chat

**URL**: https://github.com/vikrantjain/claude-code-chat
**Author**: vikrantjain
**Last updated**: 2026-08-23

### Description
Built on Claude Code Channels, an experimental API that lets MCP servers push real-time notifications into a Claude Code session. Each agent connects to a shared WebSocket broker through an MCP channel server, enabling Claude-to-Claude collaboration.

### Key Features
- Task delegation between Claude instances
- API contract negotiation
- Coordinated multi-agent development
- MCP channel server for WebSocket connectivity
- Requires `--dangerously-load-development-channels` flag

### CBOL Relevance
- **Multi-agent coordination**: Patterns for AI agent collaboration in messaging workflow
- **MCP + WebSocket**: Integration pattern for connecting external systems via WebSocket
- **Task delegation**: Reference for building message forwarding and delegation features

---

## 3. claude-friends

**URL**: https://github.com/dgrims3/claude-friends
**Author**: dgrims3
**Last updated**: 2026-08-17

### Description
A mobile-first chat application for interacting with AI agents. Features session management, message history, and real-time WebSocket chat.

### Key Features
- REST API for agent/session management
- WebSocket endpoint for real-time chat: `WS /sessions/:id/ws`
- Session start/resume: `POST /sessions/:id/start`
- Session status: `GET /sessions/:id/status`
- Message history: `GET /sessions/:id/history`
- Agent management: CRUD for AI agents

### API Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| DELETE | /friends/:id | Remove an agent |
| POST | /sessions/:id/start | Start or resume a session |
| GET | /sessions/:id/status | Check session status |
| GET | /sessions/:id/history | Get message history |
| DELETE | /sessions/:id | Kill a session |
| WS | /sessions/:id/ws | Real-time chat WebSocket |

### CBOL Relevance
- **Session management API**: Direct reference for building conversation session lifecycle
- **WebSocket chat endpoint**: Pattern for real-time message delivery
- **Message history**: Reference for storing and retrieving conversation history
- **Mobile-first design**: Considerations for multi-device messaging

---

## 4. claude-agent-chatroom

**URL**: https://github.com/ctb111/claude-agent-chatroom
**Author**: ctb111
**Last updated**: 2026-08-18

### Description
A chatroom for multiple Claude Code agents to communicate. Combines PreToolUse Hook, MCP Server, WebSocket Server, and Terminal UI.

### Key Features
- **PreToolUse Hook**: Auto-starts chatroom server/UI when Task is called
- **MCP Server**: Provides `chatroom_*` tools via Model Context Protocol
- **WebSocket Server**: Routes messages between agents and UI
- **Terminal UI**: Displays messages, accepts user input
- **/chatroom Skill**: Manual way to start the chatroom

### Architecture
```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  Agent A    │←───→│  WebSocket   │←───→│  Agent B    │
│  (Claude)   │     │    Server    │     │  (Claude)   │
└──────┬──────┘     └──────┬───────┘     └──────┬──────┘
       │                     │                      │
       ↓                     ↓                      ↓
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│ MCP Server  │     │ Terminal UI  │     │ PreToolUse  │
│ chatroom_*  │     │  (Ink/React) │     │    Hook     │
└─────────────┘     └──────────────┘     └─────────────┘
```

### CBOL Relevance
- **Multi-agent chatroom**: Reference for building AI agent collaboration in messaging
- **MCP + WebSocket + Hook**: Integration pattern for connecting multiple systems
- **Message routing**: WebSocket server patterns for routing messages between participants
- **Terminal UI**: If building admin/debug interface for messaging system

---

## 5. claude_chat_room

**URL**: https://github.com/BruceWW/claude_chat_room
**Author**: BruceWW
**Last updated**: 2026-08-09

### Description
A multi-agent chat room with WebSocket rooms, agent management, and configuration API.

### Key Features
- WebSocket rooms: `WS /ws/rooms/{id}` for real-time message stream
- Agent management: CRUD + restart
- Configuration API: Get/save config.yaml with validation
- Multi-room support
- Real-time message streaming

### API Endpoints
| Method | Path | Description |
|--------|------|-------------|
| DELETE | /api/agents/{name} | Remove agent |
| POST | /api/agents/{name}/restart | Restart agent |
| GET | /api/config | Get config.yaml content |
| PUT | /api/config | Save config.yaml (with validation) |
| POST | /api/restart | Reload config and restart all agents |
| WS | /ws/rooms/{id} | Real-time message stream |

### CBOL Relevance
- **WebSocket rooms**: Direct reference for building chat rooms / conversation channels
- **Agent management**: Patterns for managing AI agent lifecycle in messaging
- **Config API**: Reference for runtime configuration management
- **Real-time streaming**: Message stream patterns for WebSocket

---

## 6. Claude-to-IM-skill

**URL**: https://github.com/harrychin-cn/Claude-to-IM-skill
**Author**: harrychin-cn
**Last updated**: 2026-08-17

### Description
A skill that bridges Claude Code or Codex session to Telegram, Discord, Feishu/Lark, QQ, or WeChat. Messages from IM are forwarded to the AI coding agent, and responses (including tool use, permission requests, streaming previews) are sent back to chat.

### Key Features
- Multi-platform support: Telegram, Discord, Feishu/Lark, QQ, WeChat
- Background daemon (Node.js) for message relay
- Configurable runtime: Claude Agent SDK or Codex SDK
- Tool use and permission requests forwarded to IM
- Streaming previews in chat
- Bot API for each platform

### Architecture
```
User (Telegram/Discord/Feishu/QQ/WeChat)
        ↕ Bot API
Background Daemon (Node.js)
        ↕ Claude Agent SDK / Codex SDK
Claude Code / Codex Session
```

### CBOL Relevance
- **Multi-platform IM bridge**: Reference for building message forwarding to multiple chat platforms
- **Bot API integration**: Patterns for integrating with Telegram, Discord, Feishu, WeChat bots
- **Message relay daemon**: Background service pattern for bidirectional message forwarding
- **Streaming in chat**: Patterns for sending AI streaming responses to IM platforms

---

## 7. feishu-claude-code

**URL**: https://github.com/wilburx813/feishu-claude-code
**Author**: wilburx813
**Last updated**: 2026-08-18

### Description
Feishu (Lark) integration with Claude Code. Feishu pushes messages via WebSocket to a local process, which calls Claude CLI in streaming mode and updates Feishu card messages in real time via patch API.

### Key Features
- Feishu WebSocket for receiving messages (push model)
- Claude CLI `--print --output-format stream-json` for streaming output
- Feishu card message patch API for real-time updates
- Enterprise self-built app integration

### CBOL Relevance
- **Feishu/Lark WebSocket**: Direct reference if integrating with Feishu for message reception
- **Push model messaging**: WebSocket push pattern for receiving messages from external platforms
- **Streaming card updates**: Pattern for real-time message update in chat UI
- **Enterprise app integration**: Reference for building enterprise IM integrations

---

## 8. claude-to-im-skill (skillsllm)

**URL**: https://skillsllm.com/skill/claude-to-im-skill
**Source**: skillsllm.com skill marketplace
**Published**: 2026-06-03

### Description
Skill listing for bridging Claude Code or Codex session to IM platforms. Similar to harrychin-cn's implementation, listed on the skillsllm marketplace.

### Key Features
- Multi-platform IM bridge
- Configurable via environment variables
- SKILL.md with standard frontmatter

### CBOL Relevance
- **Skill marketplace pattern**: Reference for how IM bridge skills are packaged and distributed
- **Standard SKILL.md format**: Reference for skill frontmatter and structure

---

## Summary: Key Patterns for CBOL

| Pattern | Projects | CBOL Application |
|---------|----------|-----------------|
| WebSocket broker for multi-client | claude-chat, claude-code-chat | Message routing between users/agents |
| Session management REST API | claude-friends | Conversation session lifecycle |
| WebSocket rooms/channels | claude_chat_room | Chat rooms, group conversations |
| Multi-platform IM bridge | Claude-to-IM-skill, feishu-claude-code | Message forwarding to external platforms |
| MCP + WebSocket integration | claude-agent-chatroom | Connecting AI agents to messaging system |
| Real-time streaming updates | feishu-claude-code | AI response streaming in chat UI |
| Agent lifecycle management | claude_chat_room, claude-friends | AI agent connection/disconnection |

---

*Instant Messaging & Chat Systems Reference — 2026-08-24*
