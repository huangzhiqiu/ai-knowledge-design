# 02 — WebSocket & Realtime Communication

> Reference projects and guides for WebSocket implementation, real-time communication, auth, reconnects, heartbeats, and backpressure.

## Project Index

| # | Project | Author | Type |
|---|---------|--------|------|
| 1 | [Claude Code WebSocket Realtime Guide](#1-claude-code-websocket-realtime-guide) | claudecode-lab.com | Implementation guide |
| 2 | [claude-chat (WebSocket broker)](#2-claude-chat-websocket-broker) | vikrantjain | Plugin with WebSocket broker |
| 3 | [claude-agent-chatroom (WebSocket server)](#3-claude-agent-chatroom-websocket-server) | ctb111 | MCP + WebSocket + UI |

---

## 1. Claude Code WebSocket Realtime Guide

**URL**: https://claudecode-lab.com/en/blog/claude-code-websocket-realtime/
**Author**: claudecode-lab.com
**Published**: 2026-03-16

### Description
Comprehensive guide to implementing WebSocket and real-time communication with Claude Code. Covers auth, reconnects, heartbeats, rooms, and backpressure.

### Key Features
- **WebSocket vs SSE vs Polling**: Comparison table with use cases
- **Auth**: Token-based auth, connection validation
- **Reconnects**: Automatic reconnection with exponential backoff
- **Heartbeats**: Ping/pong to detect dead connections
- **Rooms**: Multi-room broadcast patterns
- **Backpressure**: Flow control for high-volume messaging

### Communication Protocol Comparison

| Option | Best For | Avoid For | Instruction |
|--------|----------|-----------|-------------|
| WebSocket | Chat, collaborative UI, live job progress, room broadcasts | Rare notifications, searchable history | Include auth, reconnects, heartbeat, and backpressure checks |
| Server-Sent Events | One-way server-to-browser updates | Frequent browser-to-server actions | Design event IDs and event types |
| Polling | Admin pages that refresh every 15-60 seconds | Low-latency UX | Add caching and rate limits |

### WebSocket Implementation Patterns

```javascript
// Client-side example
socket.on('connect', () => {
  console.log('Connected');
});

socket.on('message', (message: ChatMessage) => {
  setMessages(prev => [...prev, message]);
});

socket.on('user-typing', ({ userId, isTyping }) => {
  setTypingUsers(prev => {
    const next = new Set(prev);
    isTyping ? next.add(userId) : next.delete(userId);
    return next;
  });
});

socket.on('disconnect', () => {
  // Auto-reconnect with backoff
});
```

### CBOL Relevance
- **WebSocket best practices**: Direct reference for implementing WebSocket in CBOL messaging
- **Auth patterns**: Token-based auth for WebSocket connections
- **Reconnect logic**: Exponential backoff for connection recovery
- **Heartbeat mechanism**: Ping/pong for detecting dead connections
- **Room broadcast**: Patterns for group messaging and multi-user rooms
- **Backpressure handling**: Flow control for high-volume message forwarding
- **Protocol selection**: When to use WebSocket vs SSE vs polling

---

## 2. claude-chat (WebSocket broker)

**URL**: https://github.com/vikrantjain/claude-chat
**Author**: vikrantjain
**Last updated**: 2026-08-09

### Description
Claude Code plugin with a shared WebSocket broker for multi-instance real-time chat.

### Key WebSocket Features
- Shared WebSocket broker for message routing
- Cross-machine, cross-container communication
- Real-time message delivery
- Built on Claude Code Channels API

### CBOL Relevance
- **Broker pattern**: Reference for building a central message broker
- **Multi-client routing**: Patterns for routing messages between multiple connected clients
- **Cross-network**: Handling distributed WebSocket connections

---

## 3. claude-agent-chatroom (WebSocket server)

**URL**: https://github.com/ctb111/claude-agent-chatroom
**Author**: ctb111
**Last updated**: 2026-08-18

### Description
Chatroom with WebSocket server that routes messages between agents and UI. Combines MCP Server, WebSocket Server, and Terminal UI.

### Key WebSocket Features
- WebSocket server for message routing
- MCP tools for chatroom operations
- Real-time message display in terminal UI
- Multi-agent message exchange

### Architecture
```
Agent A ←→ WebSocket Server ←→ Agent B
              ↓
         Terminal UI
```

### CBOL Relevance
- **WebSocket server implementation**: Reference for building server-side WebSocket handling
- **Message routing**: Patterns for routing messages between multiple participants
- **MCP + WebSocket**: Integration pattern for combining MCP tools with WebSocket
- **Real-time UI**: Patterns for displaying real-time messages in admin/debug interface

---

## Summary: WebSocket Patterns for CBOL

| Pattern | Source | CBOL Application |
|---------|--------|-----------------|
| Token-based WebSocket auth | claudecode-lab guide | User authentication for WebSocket connections |
| Exponential backoff reconnect | claudecode-lab guide | Connection recovery after network issues |
| Ping/pong heartbeat | claudecode-lab guide | Detect dead connections, clean up sessions |
| Room broadcast | claudecode-lab guide, claude_chat_room | Group messaging, multi-user conversations |
| Backpressure flow control | claudecode-lab guide | Handle high-volume message forwarding |
| Shared broker pattern | claude-chat | Central message routing between clients |
| MCP + WebSocket integration | claude-agent-chatroom | Connect AI agent tools to WebSocket messaging |
| Typing indicator | claudecode-lab guide | User is typing status in chat UI |
| Message history on reconnect | claude-friends | Sync missed messages after reconnection |

---

*WebSocket & Realtime Communication Reference — 2026-08-24*
