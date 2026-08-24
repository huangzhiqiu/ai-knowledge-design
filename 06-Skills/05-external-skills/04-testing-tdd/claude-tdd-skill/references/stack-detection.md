# Stack Detection Reference

Comprehensive patterns for detecting project technology stack.

## Detection Priority

1. Package manifests (most reliable)
2. Configuration files
3. File extensions
4. Directory structure

## Language Detection

### JavaScript/TypeScript
```bash
# Check for TypeScript
[ -f "tsconfig.json" ] && echo "typescript"

# Check package.json for TS dependency
grep -q '"typescript"' package.json && echo "typescript"

# Fallback to JS
[ -f "package.json" ] && echo "javascript"
```

### Python
```bash
[ -f "requirements.txt" ] || [ -f "pyproject.toml" ] || [ -f "setup.py" ] && echo "python"
```

### C# / .NET
```bash
find . -name "*.csproj" -o -name "*.sln" | head -1 && echo "dotnet"
```

### Go
```bash
[ -f "go.mod" ] && echo "go"
```

### Java
```bash
[ -f "pom.xml" ] || [ -f "build.gradle" ] && echo "java"
```

### Rust
```bash
[ -f "Cargo.toml" ] && echo "rust"
```

## Framework Detection

### JavaScript/TypeScript Frameworks
| File/Pattern | Framework |
|--------------|-----------|
| `next.config.js` | Next.js |
| `nuxt.config.ts` | Nuxt |
| `angular.json` | Angular |
| `vite.config.ts` | Vite |
| `"react"` in package.json | React |
| `"vue"` in package.json | Vue |
| `"express"` in package.json | Express |
| `"nestjs"` in package.json | NestJS |

### Python Frameworks
| File/Pattern | Framework |
|--------------|-----------|
| `manage.py` + `settings.py` | Django |
| `"flask"` in requirements | Flask |
| `"fastapi"` in requirements | FastAPI |
| `"pytest"` in requirements | pytest (testing) |

### .NET Frameworks
| Pattern in .csproj | Framework |
|--------------------|-----------|
| `Microsoft.AspNetCore` | ASP.NET Core |
| `Microsoft.NET.Sdk.Web` | Web API |
| `Microsoft.NET.Sdk.Worker` | Worker Service |
| `WPF` or `WindowsDesktop` | WPF |

## Test Runner Detection

### JavaScript/TypeScript
| File/Pattern | Test Runner |
|--------------|-------------|
| `jest.config.js/ts` | Jest |
| `"jest"` in package.json | Jest |
| `vitest.config.ts` | Vitest |
| `"mocha"` in package.json | Mocha |
| `cypress.config.ts` | Cypress (E2E) |
| `playwright.config.ts` | Playwright (E2E) |

### Python
| File/Pattern | Test Runner |
|--------------|-------------|
| `pytest.ini` or `pyproject.toml [tool.pytest]` | pytest |
| `"unittest"` imports | unittest |
| `tox.ini` | tox |

### .NET
| File/Pattern | Test Runner |
|--------------|-------------|
| `xunit` in .csproj | xUnit |
| `NUnit` in .csproj | NUnit |
| `MSTest` in .csproj | MSTest |

### Go
Go has built-in testing: `go test`

### Java
| File/Pattern | Test Runner |
|--------------|-------------|
| `junit` in pom.xml | JUnit |
| `testng` in pom.xml | TestNG |

## Dependency Detection

### Package Lock Files
| File | Package Manager |
|------|-----------------|
| `package-lock.json` | npm |
| `yarn.lock` | Yarn |
| `pnpm-lock.yaml` | pnpm |
| `Pipfile.lock` | Pipenv |
| `poetry.lock` | Poetry |
| `go.sum` | Go modules |
| `Cargo.lock` | Cargo |

### Extracting Dependencies
```bash
# npm/yarn - from package.json
jq -r '.dependencies, .devDependencies | keys[]' package.json

# Python - from requirements.txt
cat requirements.txt | grep -v '^#' | cut -d'=' -f1

# .NET - from .csproj
grep -oP '(?<=PackageReference Include=")[^"]+' *.csproj
```

## Build Tools Detection

| File | Build Tool |
|------|------------|
| `Makefile` | Make |
| `webpack.config.js` | Webpack |
| `rollup.config.js` | Rollup |
| `esbuild.config.js` | esbuild |
| `turbo.json` | Turborepo |
| `nx.json` | Nx |
| `build.gradle` | Gradle |
| `pom.xml` | Maven |
| `*.csproj` | MSBuild |

## CI/CD Detection

| File/Directory | CI System |
|----------------|-----------|
| `.github/workflows/` | GitHub Actions |
| `azure-pipelines.yml` | Azure DevOps |
| `.gitlab-ci.yml` | GitLab CI |
| `Jenkinsfile` | Jenkins |
| `.circleci/` | CircleCI |
| `.travis.yml` | Travis CI |
| `bitbucket-pipelines.yml` | Bitbucket |

## Linter Detection

| File | Linter/Formatter |
|------|------------------|
| `.eslintrc*` | ESLint |
| `.prettierrc*` | Prettier |
| `biome.json` | Biome |
| `.editorconfig` | EditorConfig |
| `pylint.rc` | Pylint |
| `.flake8` | Flake8 |
| `mypy.ini` | MyPy |
| `.rubocop.yml` | RuboCop |
| `rustfmt.toml` | rustfmt |

## Stack.md Template

```markdown
# Project Stack

**Detected**: 2026-01-29

## Language
- **Primary**: TypeScript 5.3
- **Runtime**: Node.js 20

## Framework
- **Backend**: NestJS 10.x
- **Frontend**: React 18

## Test Runner
- **Unit**: Jest 29.x
- **E2E**: Playwright

## Dependencies (Key)
- express: ^4.18
- prisma: ^5.0
- zod: ^3.22

## Build Tools
- esbuild (bundler)
- turbo (monorepo)

## CI/CD
- GitHub Actions
- Workflows: test.yml, deploy.yml

## Linters
- ESLint + Prettier
- TypeScript strict mode
```

## Detection Script Usage

```bash
# Run from project root
~/.claude/skills/tdd/scripts/detect-stack.sh > .tdd/stack.md
```
