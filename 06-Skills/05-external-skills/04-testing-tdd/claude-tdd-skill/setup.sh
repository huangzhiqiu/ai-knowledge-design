#!/bin/bash
# Setup script for Claude Code TDD Skill
# Creates symlinks in ~/.claude/agents/ for all TDD agents

set -e

SKILL_DIR="$(cd "$(dirname "$0")" && pwd)"
AGENTS_DIR="$HOME/.claude/agents"

echo "Setting up Claude Code TDD Skill..."
echo "Skill directory: $SKILL_DIR"
echo "Agents directory: $AGENTS_DIR"

# Create agents directory if it doesn't exist
mkdir -p "$AGENTS_DIR"

# Create symlinks for all TDD agents
for agent in "$SKILL_DIR/agents/"*.md; do
    agent_name=$(basename "$agent")
    ln -sf "$agent" "$AGENTS_DIR/$agent_name"
    echo "  Linked: $agent_name"
done

echo ""
echo "✓ TDD Skill setup complete!"
echo ""
echo "Agents linked:"
ls -la "$AGENTS_DIR" | grep "tdd-" | awk '{print "  " $NF}'
echo ""
echo "Usage: In Claude Code, say 'implement with TDD' or use /tdd"
