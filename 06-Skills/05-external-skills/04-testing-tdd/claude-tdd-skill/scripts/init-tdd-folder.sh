#!/bin/bash
# Initialize .tdd folder structure for TDD Skill v2.0
# Includes learning system and improved structure

set -e

TDD_ROOT=".tdd"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}Initializing TDD folder structure...${NC}"

# Create main structure
mkdir -p "$TDD_ROOT/sessions"
mkdir -p "$TDD_ROOT/archive"

# Create stack.md if it doesn't exist
if [ ! -f "$TDD_ROOT/stack.md" ]; then
    SCRIPT_DIR="$(dirname "$0")"
    if [ -f "$SCRIPT_DIR/detect-stack.sh" ]; then
        echo -e "${YELLOW}Detecting project stack...${NC}"
        bash "$SCRIPT_DIR/detect-stack.sh" > "$TDD_ROOT/stack.md"
        echo -e "${GREEN}Created $TDD_ROOT/stack.md with detected stack${NC}"
    else
        cat > "$TDD_ROOT/stack.md" << 'EOF'
# Project Stack

**Detected**: (pending)

## Language
- **Primary**: (to be detected)

## Framework
- (to be detected)

## Test Runner
- (to be detected)

## Build Tools
- (to be detected)

## CI/CD
- (to be detected)

## Linters & Formatters
- (to be detected)

---
*Run tdd-stack-analyzer agent for comprehensive detection*
EOF
        echo -e "${YELLOW}Created $TDD_ROOT/stack.md (empty template)${NC}"
    fi
fi

# Initialize learning database if it doesn't exist
if [ ! -f "$TDD_ROOT/learnings.json" ]; then
    cat > "$TDD_ROOT/learnings.json" << 'EOF'
{
  "version": "1.0",
  "patterns": {
    "success": [],
    "antipatterns": []
  },
  "tips": [],
  "failure_patterns": [],
  "last_updated": null
}
EOF
    echo -e "${GREEN}Created $TDD_ROOT/learnings.json${NC}"
fi

# Initialize metrics history if it doesn't exist
if [ ! -f "$TDD_ROOT/metrics-history.json" ]; then
    cat > "$TDD_ROOT/metrics-history.json" << 'EOF'
{
  "version": "1.0",
  "sessions": [],
  "aggregates": {
    "total_sessions": 0,
    "total_cycles": 0,
    "total_tests_written": 0,
    "avg_cycle_time_minutes": null,
    "avg_green_attempts": null
  }
}
EOF
    echo -e "${GREEN}Created $TDD_ROOT/metrics-history.json${NC}"
fi

# Initialize coverage history if it doesn't exist
if [ ! -f "$TDD_ROOT/coverage-history.json" ]; then
    cat > "$TDD_ROOT/coverage-history.json" << 'EOF'
{
  "version": "1.0",
  "history": [],
  "current": null
}
EOF
    echo -e "${GREEN}Created $TDD_ROOT/coverage-history.json${NC}"
fi

# Initialize tests.md if it doesn't exist
if [ ! -f "$TDD_ROOT/tests.md" ]; then
    cat > "$TDD_ROOT/tests.md" << EOF
# Test Documentation

This document describes all tests written during TDD sessions.

**Last Updated**: $(date '+%Y-%m-%d %H:%M:%S')
**Total Tests**: 0

---

<!-- Tests will be added here after each TDD session -->
EOF
    echo -e "${GREEN}Created $TDD_ROOT/tests.md${NC}"
fi

# Create .gitignore for .tdd folder
cat > "$TDD_ROOT/.gitignore" << 'EOF'
# TDD session context files - contain implementation details
sessions/*/context.json

# Keep archive summaries but not full context
archive/*/context.json

# Local coverage data
coverage-history.json

# Keep learnings and stack for team sharing
# !learnings.json
# !stack.md
EOF

echo ""
echo -e "${GREEN}Initialized TDD folder structure:${NC}"
echo "  $TDD_ROOT/"
echo "  ├── sessions/             (active TDD sessions)"
echo "  ├── archive/              (completed sessions)"
echo "  ├── stack.md              (project stack profile)"
echo "  ├── learnings.json        (cross-session learning database)"
echo "  ├── metrics-history.json  (historical metrics)"
echo "  ├── coverage-history.json (coverage tracking)"
echo "  ├── tests.md              (cumulative test documentation)"
echo "  └── .gitignore"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "  1. Run tdd-stack-analyzer for comprehensive stack detection"
echo "  2. Start a TDD session with: 'implement X with TDD'"
