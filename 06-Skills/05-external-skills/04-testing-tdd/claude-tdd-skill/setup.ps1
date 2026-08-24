# Setup script for Claude Code TDD Skill
# Creates symlinks in ~/.claude/agents/ for all TDD agents

$ErrorActionPreference = "Stop"

$SkillDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AgentsDir = "$env:USERPROFILE\.claude\agents"

Write-Host "Setting up Claude Code TDD Skill..." -ForegroundColor Green
Write-Host "Skill directory: $SkillDir"
Write-Host "Agents directory: $AgentsDir"

# Create agents directory if it doesn't exist
New-Item -ItemType Directory -Force -Path $AgentsDir | Out-Null

# Create symlinks for all TDD agents
Get-ChildItem "$SkillDir\agents\*.md" | ForEach-Object {
    $agentName = $_.Name
    $targetPath = "$AgentsDir\$agentName"

    # Remove existing symlink/file if it exists
    if (Test-Path $targetPath) {
        Remove-Item $targetPath -Force
    }

    # Create symlink (requires admin or developer mode on Windows)
    try {
        New-Item -ItemType SymbolicLink -Path $targetPath -Target $_.FullName | Out-Null
        Write-Host "  Linked: $agentName" -ForegroundColor Cyan
    }
    catch {
        # Fallback to copy if symlinks aren't supported
        Copy-Item $_.FullName $targetPath
        Write-Host "  Copied: $agentName (symlinks not available)" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "TDD Skill setup complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Agents linked:" -ForegroundColor Cyan
Get-ChildItem "$AgentsDir\tdd-*.md" | ForEach-Object { Write-Host "  $($_.Name)" }
Write-Host ""
Write-Host "Usage: In Claude Code, say 'implement with TDD' or use /tdd" -ForegroundColor Yellow
