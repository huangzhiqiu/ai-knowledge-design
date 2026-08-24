#!/bin/bash
# TDD Branch Management Script
# Creates and manages TDD-specific branches

set -e

ACTION="${1:-create}"    # create, complete, status
FEATURE="${2:-feature}"  # Feature name for branch

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Check if we're in a git repository
if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
    echo -e "${RED}Error: Not in a git repository${NC}"
    exit 1
fi

# Sanitize feature name for branch
BRANCH_NAME="tdd/$(echo "$FEATURE" | tr ' ' '-' | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9-]//g')"

case "$ACTION" in
    create)
        # Get current branch (usually main/master)
        BASE_BRANCH=$(git symbolic-ref --short HEAD 2>/dev/null || echo "main")

        # Check if branch already exists
        if git show-ref --verify --quiet "refs/heads/$BRANCH_NAME"; then
            echo -e "${YELLOW}Branch $BRANCH_NAME already exists${NC}"
            echo -e "Switching to existing branch..."
            git checkout "$BRANCH_NAME"
        else
            echo -e "${GREEN}Creating TDD branch: $BRANCH_NAME${NC}"
            git checkout -b "$BRANCH_NAME"
        fi

        echo -e "${GREEN}Ready for TDD on branch: $BRANCH_NAME${NC}"
        echo "$BRANCH_NAME"
        ;;

    complete)
        CURRENT_BRANCH=$(git symbolic-ref --short HEAD)

        # Verify we're on a TDD branch
        if [[ ! "$CURRENT_BRANCH" =~ ^tdd/ ]]; then
            echo -e "${RED}Not on a TDD branch. Current branch: $CURRENT_BRANCH${NC}"
            exit 1
        fi

        # Determine target branch (main or master)
        if git show-ref --verify --quiet refs/heads/main; then
            TARGET="main"
        elif git show-ref --verify --quiet refs/heads/master; then
            TARGET="master"
        else
            echo -e "${RED}Could not find main or master branch${NC}"
            exit 1
        fi

        echo -e "${YELLOW}Completing TDD branch: $CURRENT_BRANCH${NC}"
        echo -e "Target branch: $TARGET"

        # Check for uncommitted changes
        if ! git diff --quiet || ! git diff --cached --quiet; then
            echo -e "${RED}Error: Uncommitted changes. Please commit or stash first.${NC}"
            exit 1
        fi

        # Switch to target and merge
        git checkout "$TARGET"
        git merge "$CURRENT_BRANCH" --no-ff -m "Merge TDD branch: $CURRENT_BRANCH"

        echo -e "${GREEN}TDD branch merged successfully${NC}"
        echo -e "${YELLOW}You may want to delete the TDD branch:${NC}"
        echo "  git branch -d $CURRENT_BRANCH"
        ;;

    status)
        CURRENT_BRANCH=$(git symbolic-ref --short HEAD)

        if [[ "$CURRENT_BRANCH" =~ ^tdd/ ]]; then
            echo -e "${GREEN}On TDD branch: $CURRENT_BRANCH${NC}"

            # Show commits on this branch
            echo -e "\n${YELLOW}TDD commits on this branch:${NC}"
            git log --oneline main..HEAD 2>/dev/null || git log --oneline master..HEAD 2>/dev/null || echo "No TDD commits yet"
        else
            echo -e "${YELLOW}Not on a TDD branch. Current: $CURRENT_BRANCH${NC}"

            # List existing TDD branches
            echo -e "\n${YELLOW}Existing TDD branches:${NC}"
            git branch --list 'tdd/*' || echo "None"
        fi
        ;;

    *)
        echo "TDD Branch Manager"
        echo ""
        echo "Usage: tdd-branch.sh [action] [feature-name]"
        echo ""
        echo "Actions:"
        echo "  create   - Create a new TDD branch (or switch to existing)"
        echo "  complete - Merge TDD branch back to main/master"
        echo "  status   - Show TDD branch status"
        echo ""
        echo "Examples:"
        echo "  tdd-branch.sh create user-authentication"
        echo "  tdd-branch.sh complete"
        echo "  tdd-branch.sh status"
        ;;
esac
