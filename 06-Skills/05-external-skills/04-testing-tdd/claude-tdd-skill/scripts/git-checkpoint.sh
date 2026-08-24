#!/bin/bash
# Git Checkpoint Script for TDD Skill
# Creates commits at key TDD milestones with structured messages

set -e

# Arguments
PHASE="${1:-green}"      # red, green, refactor, complete
CYCLE="${2:-1}"          # Cycle number
FEATURE="${3:-feature}"  # Feature name
MESSAGE="${4:-}"         # Optional additional message

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if we're in a git repository
if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
    echo -e "${RED}Error: Not in a git repository${NC}"
    exit 1
fi

# Check for uncommitted changes
if git diff --quiet && git diff --cached --quiet; then
    echo -e "${YELLOW}No changes to commit${NC}"
    exit 0
fi

# Build commit message based on phase
case "$PHASE" in
    red)
        COMMIT_MSG="[TDD] Cycle ${CYCLE}: ${FEATURE} (RED)

Phase: RED - Failing test written
${MESSAGE}

Co-Authored-By: Claude <noreply@anthropic.com>"
        ;;
    green)
        COMMIT_MSG="[TDD] Cycle ${CYCLE}: ${FEATURE} (GREEN)

Phase: GREEN - Test passing
${MESSAGE}

Co-Authored-By: Claude <noreply@anthropic.com>"
        ;;
    refactor)
        COMMIT_MSG="[TDD] Cycle ${CYCLE}: ${FEATURE} (REFACTOR)

Phase: REFACTOR - Code improved
${MESSAGE}

Co-Authored-By: Claude <noreply@anthropic.com>"
        ;;
    complete)
        COMMIT_MSG="[TDD] Complete: ${FEATURE}

All cycles completed successfully.
${MESSAGE}

Co-Authored-By: Claude <noreply@anthropic.com>"
        ;;
    *)
        echo -e "${RED}Unknown phase: ${PHASE}${NC}"
        echo "Usage: git-checkpoint.sh [red|green|refactor|complete] [cycle] [feature] [message]"
        exit 1
        ;;
esac

# Show what will be committed
echo -e "${YELLOW}Files to commit:${NC}"
git status --short

# Stage all changes (can be customized to stage specific files)
# For more control, the orchestrator should stage specific files before calling this script
if ! git diff --cached --quiet; then
    # There are already staged changes, use those
    echo -e "${GREEN}Using already staged changes${NC}"
else
    # Stage all changes
    git add -A
fi

# Create the commit
echo -e "${GREEN}Creating commit...${NC}"
git commit -m "$COMMIT_MSG"

# Show result
COMMIT_HASH=$(git rev-parse --short HEAD)
echo -e "${GREEN}Committed: ${COMMIT_HASH}${NC}"
echo -e "${GREEN}Phase: ${PHASE} | Cycle: ${CYCLE}${NC}"

# Output commit hash for context.json update
echo "$COMMIT_HASH"
