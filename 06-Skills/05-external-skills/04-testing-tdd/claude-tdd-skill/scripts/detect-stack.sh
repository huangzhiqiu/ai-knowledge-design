#!/bin/bash
# Stack Detection Script for TDD Skill
# Detects project technology stack and outputs to stdout

set -e

echo "# Project Stack"
echo ""
echo "**Detected**: $(date +%Y-%m-%d)"
echo ""

# ============== LANGUAGE DETECTION ==============
echo "## Language"

detect_language() {
    local languages=()

    # TypeScript
    if [ -f "tsconfig.json" ] || grep -q '"typescript"' package.json 2>/dev/null; then
        languages+=("TypeScript")
    fi

    # JavaScript
    if [ -f "package.json" ] && [ ${#languages[@]} -eq 0 ]; then
        languages+=("JavaScript")
    fi

    # Python
    if [ -f "requirements.txt" ] || [ -f "pyproject.toml" ] || [ -f "setup.py" ] || [ -f "Pipfile" ]; then
        languages+=("Python")
    fi

    # C# / .NET
    if find . -maxdepth 3 -name "*.csproj" 2>/dev/null | head -1 | grep -q .; then
        languages+=("C#/.NET")
    fi

    # Go
    if [ -f "go.mod" ]; then
        languages+=("Go")
    fi

    # Java
    if [ -f "pom.xml" ] || [ -f "build.gradle" ] || [ -f "build.gradle.kts" ]; then
        languages+=("Java")
    fi

    # Rust
    if [ -f "Cargo.toml" ]; then
        languages+=("Rust")
    fi

    # Ruby
    if [ -f "Gemfile" ]; then
        languages+=("Ruby")
    fi

    # PHP
    if [ -f "composer.json" ]; then
        languages+=("PHP")
    fi

    if [ ${#languages[@]} -eq 0 ]; then
        echo "- **Primary**: Unknown"
    else
        echo "- **Primary**: ${languages[0]}"
        if [ ${#languages[@]} -gt 1 ]; then
            echo "- **Additional**: ${languages[*]:1}"
        fi
    fi
}

detect_language

# ============== FRAMEWORK DETECTION ==============
echo ""
echo "## Framework"

detect_framework() {
    local frameworks=()

    # JavaScript/TypeScript frameworks
    if [ -f "package.json" ]; then
        [ -f "next.config.js" ] || [ -f "next.config.mjs" ] || [ -f "next.config.ts" ] && frameworks+=("Next.js")
        [ -f "nuxt.config.ts" ] || [ -f "nuxt.config.js" ] && frameworks+=("Nuxt")
        [ -f "angular.json" ] && frameworks+=("Angular")
        [ -f "svelte.config.js" ] && frameworks+=("SvelteKit")
        grep -q '"react"' package.json 2>/dev/null && frameworks+=("React")
        grep -q '"vue"' package.json 2>/dev/null && frameworks+=("Vue")
        grep -q '"express"' package.json 2>/dev/null && frameworks+=("Express")
        grep -q '"@nestjs/core"' package.json 2>/dev/null && frameworks+=("NestJS")
        grep -q '"fastify"' package.json 2>/dev/null && frameworks+=("Fastify")
    fi

    # Python frameworks
    if [ -f "requirements.txt" ] || [ -f "pyproject.toml" ]; then
        grep -qi "django" requirements.txt pyproject.toml 2>/dev/null && frameworks+=("Django")
        grep -qi "flask" requirements.txt pyproject.toml 2>/dev/null && frameworks+=("Flask")
        grep -qi "fastapi" requirements.txt pyproject.toml 2>/dev/null && frameworks+=("FastAPI")
    fi

    # .NET frameworks
    if find . -maxdepth 3 -name "*.csproj" 2>/dev/null | head -1 | grep -q .; then
        for csproj in $(find . -maxdepth 3 -name "*.csproj" 2>/dev/null); do
            grep -q "Microsoft.AspNetCore" "$csproj" 2>/dev/null && frameworks+=("ASP.NET Core")
            grep -q "Microsoft.NET.Sdk.Web" "$csproj" 2>/dev/null && frameworks+=("Web API")
            grep -q "Microsoft.NET.Sdk.Worker" "$csproj" 2>/dev/null && frameworks+=("Worker Service")
        done
    fi

    # Go frameworks
    if [ -f "go.mod" ]; then
        grep -q "gin-gonic" go.mod 2>/dev/null && frameworks+=("Gin")
        grep -q "gorilla/mux" go.mod 2>/dev/null && frameworks+=("Gorilla Mux")
        grep -q "fiber" go.mod 2>/dev/null && frameworks+=("Fiber")
    fi

    # Java frameworks
    if [ -f "pom.xml" ] || [ -f "build.gradle" ]; then
        grep -qi "spring" pom.xml build.gradle 2>/dev/null && frameworks+=("Spring")
    fi

    if [ ${#frameworks[@]} -eq 0 ]; then
        echo "- No specific framework detected"
    else
        for fw in "${frameworks[@]}"; do
            echo "- $fw"
        done
    fi
}

detect_framework

# ============== TEST RUNNER DETECTION ==============
echo ""
echo "## Test Runner"

detect_test_runner() {
    local runners=()

    # JavaScript/TypeScript
    if [ -f "package.json" ]; then
        [ -f "jest.config.js" ] || [ -f "jest.config.ts" ] || [ -f "jest.config.mjs" ] && runners+=("Jest")
        grep -q '"jest"' package.json 2>/dev/null && runners+=("Jest")
        [ -f "vitest.config.ts" ] || [ -f "vitest.config.js" ] && runners+=("Vitest")
        grep -q '"mocha"' package.json 2>/dev/null && runners+=("Mocha")
        [ -f "cypress.config.ts" ] || [ -f "cypress.config.js" ] && runners+=("Cypress (E2E)")
        [ -f "playwright.config.ts" ] && runners+=("Playwright (E2E)")
    fi

    # Python
    [ -f "pytest.ini" ] && runners+=("pytest")
    [ -f "pyproject.toml" ] && grep -q "\[tool.pytest" pyproject.toml 2>/dev/null && runners+=("pytest")
    [ -f "tox.ini" ] && runners+=("tox")

    # .NET
    for csproj in $(find . -maxdepth 3 -name "*.csproj" 2>/dev/null); do
        grep -qi "xunit" "$csproj" 2>/dev/null && runners+=("xUnit")
        grep -qi "nunit" "$csproj" 2>/dev/null && runners+=("NUnit")
        grep -qi "mstest" "$csproj" 2>/dev/null && runners+=("MSTest")
    done

    # Go - built-in
    [ -f "go.mod" ] && runners+=("go test (built-in)")

    # Java
    if [ -f "pom.xml" ]; then
        grep -qi "junit" pom.xml 2>/dev/null && runners+=("JUnit")
        grep -qi "testng" pom.xml 2>/dev/null && runners+=("TestNG")
    fi

    # Rust - built-in
    [ -f "Cargo.toml" ] && runners+=("cargo test (built-in)")

    # Remove duplicates
    runners=($(echo "${runners[@]}" | tr ' ' '\n' | sort -u | tr '\n' ' '))

    if [ ${#runners[@]} -eq 0 ]; then
        echo "- No test runner detected"
    else
        for runner in "${runners[@]}"; do
            echo "- $runner"
        done
    fi
}

detect_test_runner

# ============== BUILD TOOLS ==============
echo ""
echo "## Build Tools"

detect_build_tools() {
    local tools=()

    [ -f "Makefile" ] && tools+=("Make")
    [ -f "webpack.config.js" ] && tools+=("Webpack")
    [ -f "rollup.config.js" ] && tools+=("Rollup")
    [ -f "vite.config.ts" ] || [ -f "vite.config.js" ] && tools+=("Vite")
    [ -f "esbuild.config.js" ] && tools+=("esbuild")
    [ -f "turbo.json" ] && tools+=("Turborepo")
    [ -f "nx.json" ] && tools+=("Nx")
    [ -f "lerna.json" ] && tools+=("Lerna")
    [ -f "build.gradle" ] || [ -f "build.gradle.kts" ] && tools+=("Gradle")
    [ -f "pom.xml" ] && tools+=("Maven")
    find . -maxdepth 3 -name "*.csproj" 2>/dev/null | head -1 | grep -q . && tools+=("MSBuild/dotnet")
    [ -f "Cargo.toml" ] && tools+=("Cargo")

    if [ ${#tools[@]} -eq 0 ]; then
        echo "- No specific build tools detected"
    else
        for tool in "${tools[@]}"; do
            echo "- $tool"
        done
    fi
}

detect_build_tools

# ============== CI/CD ==============
echo ""
echo "## CI/CD"

detect_ci() {
    local ci_systems=()

    [ -d ".github/workflows" ] && ci_systems+=("GitHub Actions")
    [ -f "azure-pipelines.yml" ] || [ -f "azure-pipelines.yaml" ] && ci_systems+=("Azure DevOps")
    [ -f ".gitlab-ci.yml" ] && ci_systems+=("GitLab CI")
    [ -f "Jenkinsfile" ] && ci_systems+=("Jenkins")
    [ -d ".circleci" ] && ci_systems+=("CircleCI")
    [ -f ".travis.yml" ] && ci_systems+=("Travis CI")
    [ -f "bitbucket-pipelines.yml" ] && ci_systems+=("Bitbucket Pipelines")
    [ -f "cloudbuild.yaml" ] && ci_systems+=("Google Cloud Build")

    if [ ${#ci_systems[@]} -eq 0 ]; then
        echo "- No CI/CD configuration detected"
    else
        for ci in "${ci_systems[@]}"; do
            echo "- $ci"
        done
    fi
}

detect_ci

# ============== LINTERS/FORMATTERS ==============
echo ""
echo "## Linters & Formatters"

detect_linters() {
    local linters=()

    [ -f ".eslintrc" ] || [ -f ".eslintrc.js" ] || [ -f ".eslintrc.json" ] || [ -f ".eslintrc.yml" ] && linters+=("ESLint")
    [ -f ".prettierrc" ] || [ -f ".prettierrc.js" ] || [ -f ".prettierrc.json" ] && linters+=("Prettier")
    [ -f "biome.json" ] && linters+=("Biome")
    [ -f ".editorconfig" ] && linters+=("EditorConfig")
    [ -f "pylint.rc" ] || [ -f ".pylintrc" ] && linters+=("Pylint")
    [ -f ".flake8" ] && linters+=("Flake8")
    [ -f "mypy.ini" ] || grep -q "\[tool.mypy\]" pyproject.toml 2>/dev/null && linters+=("MyPy")
    [ -f ".rubocop.yml" ] && linters+=("RuboCop")
    [ -f "rustfmt.toml" ] && linters+=("rustfmt")
    [ -f ".golangci.yml" ] || [ -f ".golangci.yaml" ] && linters+=("golangci-lint")

    if [ ${#linters[@]} -eq 0 ]; then
        echo "- No linters detected"
    else
        for linter in "${linters[@]}"; do
            echo "- $linter"
        done
    fi
}

detect_linters

# ============== KEY DEPENDENCIES ==============
echo ""
echo "## Key Dependencies"

detect_dependencies() {
    # JavaScript/TypeScript
    if [ -f "package.json" ]; then
        echo ""
        echo "### From package.json"
        if command -v jq &> /dev/null; then
            jq -r '.dependencies // {} | keys[]' package.json 2>/dev/null | head -10 | while read dep; do
                echo "- $dep"
            done
        else
            grep -oP '(?<="dependencies": \{)[^}]+' package.json 2>/dev/null | grep -oP '"[^"]+' | tr -d '"' | head -10 | while read dep; do
                echo "- $dep"
            done
        fi
    fi

    # Python
    if [ -f "requirements.txt" ]; then
        echo ""
        echo "### From requirements.txt"
        grep -v '^#' requirements.txt 2>/dev/null | cut -d'=' -f1 | cut -d'>' -f1 | cut -d'<' -f1 | head -10 | while read dep; do
            [ -n "$dep" ] && echo "- $dep"
        done
    fi

    # .NET
    local csproj=$(find . -maxdepth 3 -name "*.csproj" 2>/dev/null | head -1)
    if [ -n "$csproj" ]; then
        echo ""
        echo "### From .csproj"
        grep -oP '(?<=PackageReference Include=")[^"]+' "$csproj" 2>/dev/null | head -10 | while read dep; do
            echo "- $dep"
        done
    fi
}

detect_dependencies

echo ""
echo "---"
echo "*Auto-generated by TDD skill stack detection*"
