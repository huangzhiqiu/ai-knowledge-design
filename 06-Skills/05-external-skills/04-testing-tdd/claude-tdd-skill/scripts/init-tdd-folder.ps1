# Initialize .tdd folder structure for TDD Skill v2.0
# PowerShell version for Windows users

$ErrorActionPreference = "Stop"

$TDD_ROOT = ".tdd"

Write-Host "Initializing TDD folder structure..." -ForegroundColor Green

# Create main structure
New-Item -ItemType Directory -Force -Path "$TDD_ROOT/sessions" | Out-Null
New-Item -ItemType Directory -Force -Path "$TDD_ROOT/archive" | Out-Null

# Create stack.md if it doesn't exist
if (-not (Test-Path "$TDD_ROOT/stack.md")) {
    @"
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
"@ | Out-File -FilePath "$TDD_ROOT/stack.md" -Encoding utf8
    Write-Host "Created $TDD_ROOT/stack.md (empty template)" -ForegroundColor Yellow
}

# Initialize learning database if it doesn't exist
if (-not (Test-Path "$TDD_ROOT/learnings.json")) {
    @"
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
"@ | Out-File -FilePath "$TDD_ROOT/learnings.json" -Encoding utf8
    Write-Host "Created $TDD_ROOT/learnings.json" -ForegroundColor Green
}

# Initialize metrics history if it doesn't exist
if (-not (Test-Path "$TDD_ROOT/metrics-history.json")) {
    @"
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
"@ | Out-File -FilePath "$TDD_ROOT/metrics-history.json" -Encoding utf8
    Write-Host "Created $TDD_ROOT/metrics-history.json" -ForegroundColor Green
}

# Initialize coverage history if it doesn't exist
if (-not (Test-Path "$TDD_ROOT/coverage-history.json")) {
    @"
{
  "version": "1.0",
  "history": [],
  "current": null
}
"@ | Out-File -FilePath "$TDD_ROOT/coverage-history.json" -Encoding utf8
    Write-Host "Created $TDD_ROOT/coverage-history.json" -ForegroundColor Green
}

# Initialize tests.md if it doesn't exist
if (-not (Test-Path "$TDD_ROOT/tests.md")) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    @"
# Test Documentation

This document describes all tests written during TDD sessions.

**Last Updated**: $timestamp
**Total Tests**: 0

---

<!-- Tests will be added here after each TDD session -->
"@ | Out-File -FilePath "$TDD_ROOT/tests.md" -Encoding utf8
    Write-Host "Created $TDD_ROOT/tests.md" -ForegroundColor Green
}

# Create .gitignore for .tdd folder
@"
# TDD session context files - contain implementation details
sessions/*/context.json

# Keep archive summaries but not full context
archive/*/context.json

# Local coverage data
coverage-history.json

# Keep learnings and stack for team sharing
# !learnings.json
# !stack.md
"@ | Out-File -FilePath "$TDD_ROOT/.gitignore" -Encoding utf8

Write-Host ""
Write-Host "Initialized TDD folder structure:" -ForegroundColor Green
Write-Host "  $TDD_ROOT/"
Write-Host "  ├── sessions/             (active TDD sessions)"
Write-Host "  ├── archive/              (completed sessions)"
Write-Host "  ├── stack.md              (project stack profile)"
Write-Host "  ├── learnings.json        (cross-session learning database)"
Write-Host "  ├── metrics-history.json  (historical metrics)"
Write-Host "  ├── coverage-history.json (coverage tracking)"
Write-Host "  ├── tests.md              (cumulative test documentation)"
Write-Host "  └── .gitignore"
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Run tdd-stack-analyzer for comprehensive stack detection"
Write-Host "  2. Start a TDD session with: 'implement X with TDD'"
