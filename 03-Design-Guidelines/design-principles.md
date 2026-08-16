# Software Design Principles

> Industry-standard software design principles for maintainable, extensible systems.

## Core Principles

### SOLID Principles

| Letter | Principle | Core Idea |
|--------|-----------|-----------|
| **S** | Single Responsibility (SRP) | A class should have one, and only one, reason to change |
| **O** | Open-Closed (OCP) | Open for extension, closed for modification |
| **L** | Liskov Substitution (LSP) | Subtypes must be substitutable for their base types |
| **I** | Interface Segregation (ISP) | Clients should not be forced to depend on methods they don't use |
| **D** | Dependency Inversion (DIP) | Depend on abstractions, not concretions |

#### Single Responsibility Principle
- A class/module should have one job
- **Signal**: If explaining what something does requires "and", it likely violates SRP
- **Example**: `UserService` should handle user logic, not also send emails and log to database

#### Open-Closed Principle
- Use abstraction and polymorphism to extend behavior
- Avoid modifying existing, tested code for new features
- **Pitfall**: Over-engineering abstraction layers "just in case"

#### Liskov Substitution Principle
- Derived classes must honor the contract of base classes
- Preconditions cannot be strengthened in subtype
- Postconditions cannot be weakened in subtype
- **Violation example**: `Square extends Rectangle` with overridden setters that break rectangle invariants

#### Interface Segregation Principle
- Prefer small, focused interfaces over fat interfaces
- A client should never depend on methods it doesn't use
- **Example**: `Eatable`, `Sleepable`, `Workable` instead of one `Worker` interface

#### Dependency Inversion Principle
- High-level modules should not depend on low-level modules; both depend on abstractions
- Abstractions should not depend on details; details depend on abstractions
- **Implementation**: Constructor injection, interface-based design

### DRY - Don't Repeat Yourself
- Every piece of knowledge must have a single, unambiguous, authoritative representation
- Avoid copy-pasting business logic
- **Caveat**: "Duplication is far cheaper than the wrong abstraction" (Sandi Metz)
- Two functions that look similar but represent different concepts should NOT be merged

### KISS - Keep It Simple, Stupid
- Systems work best when kept simple
- Two sins of complexity: too many parts, too many interconnected parts
- **Simple ≠ Easy**: Simple = few interconnected parts; Easy = little effort
- **Measure complexity**: Cyclomatic complexity ≤ 10 per function

### YAGNI - You Aren't Gonna Need It
- Don't implement functionality until it's actually needed
- No speculative generality, no "future-proofing"
- Question: "Is this solving today's problem?"
- **Apply after**: SOLID is for when code already exists and shows tangled responsibilities

### Principle of Least Astonishment (POLA)
- Follow established conventions in the language and codebase
- Clever code that surprises maintainers is rejected in review
- API behavior should match user expectations
- **Example**: A method named `getUser()` should not delete the user

### Separation of Concerns
- Different concerns (UI, business logic, data access) in different modules
- Each concern has its own layer
- Enables independent development, testing, and maintenance

### High Cohesion, Low Coupling
- **Cohesion**: How closely related functions in a module are (aim for high)
- **Coupling**: How dependent modules are on each other (aim for low)
- High cohesion + low coupling = maintainable system

### Fail Fast
- Detect and report errors as early as possible
- Validate inputs at boundaries
- Fail loudly in development, gracefully in production

## Design Principle Application Order

```
1. YAGNI/KISS first — cut the superfluous
2. DRY — eliminate true duplication (not accidental similarity)
3. SOLID — apply when code shows real tangled responsibilities
4. Never use SOLID to justify premature abstraction
```

## Anti-Patterns to Avoid

| Anti-Pattern | Description |
|-------------|-------------|
| God Object | One class doing everything |
| Spaghetti Code | Unstructured, tangled control flow |
| Golden Hammer | Using one tool/pattern for everything |
| Boat Anchor | Useless code kept "just in case" |
| Dead Code | Unused code that's never cleaned up |
| Premature Optimization | Optimizing before knowing bottleneck |
| Cargo Cult | Copying patterns without understanding why |
| Lava Flow | Dead/obsolete code that can't be removed |

## References
- Clean Code by Robert C. Martin
- Design Patterns by GoF
- Domain-Driven Design by Eric Evans
- https://github.com/Harshsanket/principles
- https://github.com/namecoder1/coding-principles
