---
name: prd-generator
description: Generate comprehensive Product Requirements Documents (PRDs) for product managers. Use this skill when users ask to "create a PRD", "write product requirements", "document a feature", or need help structuring product specifications.
---
# PRD Generator

## Overview
Generate comprehensive, well-structured Product Requirements Documents (PRDs) that follow industry best practices. This skill helps product managers create clear, actionable requirements documents that align stakeholders and guide development teams.

## Core Workflow

### Step 1: Gather Context
Before generating the PRD, collect essential information through a discovery conversation:

**Required Information:**
- **Feature/Product Name**: What are we building?
- **Problem Statement**: What problem does this solve?
- **Target Users**: Who is this for?
- **Business Goals**: What are we trying to achieve?
- **Success Metrics**: How will we measure success?
- **Timeline/Constraints**: Any deadlines or limitations?

**Discovery Questions:**
1. What problem are you trying to solve?
2. Who is the primary user/audience for this feature?
3. What are the key business objectives?
4. Are there any technical constraints we should be aware of?
5. What does success look like? How will you measure it?
6. What's the timeline for this feature?
7. What's explicitly out of scope?

### Step 2: Generate PRD Structure
Use the standard PRD template. The PRD should include:
1. **Executive Summary** - High-level overview (2-3 paragraphs)
2. **Problem Statement** - Clear articulation of the problem
3. **Goals & Objectives** - What we're trying to achieve
4. **User Personas** - Who we're building for
5. **User Stories & Requirements** - Detailed functional requirements
6. **Success Metrics** - KPIs and measurement criteria
7. **Scope** - What's in and out of scope
8. **Technical Considerations** - Architecture, dependencies, constraints
9. **Design & UX Requirements** - UI/UX considerations
10. **Timeline & Milestones** - Key dates and phases
11. **Risks & Mitigation** - Potential issues and solutions
12. **Dependencies & Assumptions** - What we're relying on
13. **Open Questions** - Unresolved items

### Step 3: Create User Stories
For each major requirement, generate user stories using the standard format:
```
As a [user type],
I want to [action],
So that [benefit/value].

Acceptance Criteria:
- [Specific, testable criterion 1]
- [Specific, testable criterion 2]
- [Specific, testable criterion 3]
```

### Step 4: Define Success Metrics
Use appropriate metrics frameworks:
- **AARRR (Pirate Metrics)**: Acquisition, Activation, Retention, Revenue, Referral
- **HEART Framework**: Happiness, Engagement, Adoption, Retention, Task Success
- **North Star Metric**: Single key metric that represents core value
- **OKRs**: Objectives and Key Results

### Step 5: Validate & Review
Run validation to ensure PRD completeness:
- All required sections present
- User stories follow proper format
- Success metrics are defined
- Scope is clearly articulated
- No placeholder text remains

## Usage Patterns

### Pattern 1: New Feature PRD
**User Request:** "Create a PRD for adding dark mode to our mobile app"
**Execution:**
1. Ask discovery questions about dark mode requirements
2. Generate PRD using template
3. Create user stories for: theme switching, preference persistence, system-level sync, design token updates
4. Define success metrics (adoption rate, user satisfaction)
5. Identify technical dependencies (design system, platform APIs)

### Pattern 2: Product Enhancement PRD
**User Request:** "Write requirements for improving our search functionality"
**Execution:**
1. Gather context on current search limitations
2. Identify user pain points and desired improvements
3. Generate PRD with focus on: current state analysis, proposed enhancements, impact assessment
4. Create prioritized user stories
5. Define before/after metrics

### Pattern 3: New Product PRD
**User Request:** "I need a PRD for a new analytics dashboard product"
**Execution:**
1. Comprehensive discovery (market analysis, user research)
2. Generate full PRD with: market opportunity, competitive analysis, product vision, MVP scope, go-to-market considerations
3. Detailed user stories for core features
4. Phased rollout plan
5. Success metrics aligned with business goals

### Pattern 4: Quick PRD / One-Pager
**User Request:** "Create a lightweight PRD for a small bug fix feature"
**Execution:**
1. Generate simplified PRD focusing on: problem statement, solution approach, acceptance criteria, success metrics
2. Skip sections not relevant for small scope
3. Keep document concise (1-2 pages)

## PRD Best Practices

### Writing Quality Requirements
**Good Requirements Are:**
- **Specific**: Clear and unambiguous
- **Measurable**: Can be verified/tested
- **Achievable**: Technically feasible
- **Relevant**: Tied to user/business value
- **Time-bound**: Has clear timeline

**Avoid:**
- Vague language ("fast", "easy", "intuitive")
- Implementation details (let engineers decide how)
- Feature creep (stick to core requirements)
- Assumptions without validation

### User Story Best Practices
**DO:** Focus on user value, write from user perspective, include clear acceptance criteria, keep stories independent and small, use consistent format
**DON'T:** Write technical implementation details, create dependencies between stories, make stories too large (epics), use internal jargon, skip acceptance criteria

### Scope Management
**In-Scope:** List specific features/capabilities included, be explicit and detailed, link to user stories
**Out-of-Scope:** Explicitly state what's NOT included, prevents scope creep, manages stakeholder expectations, can include "future considerations"

### Success Metrics Guidelines
**Choose Metrics That:** Align with business objectives, are measurable and trackable, have clear targets/thresholds, include both leading and lagging indicators, consider user and business value

**Typical Metric Categories:**
- **Adoption**: How many users use the feature?
- **Engagement**: How often do they use it?
- **Satisfaction**: Do users like it?
- **Performance**: Does it work well?
- **Business Impact**: Does it drive business goals?

## Advanced Features

### PRD Templates for Different Contexts
- **Standard PRD** - Full comprehensive document
- **Lean PRD** - Streamlined for agile teams
- **One-Pager** - Executive summary format
- **Technical PRD** - Engineering-focused requirements
- **Design PRD** - UX/UI-focused requirements

### Integration with Design
**Design Requirements Section Should Include:** Visual design requirements, interaction patterns, accessibility requirements (WCAG compliance), responsive design considerations, design system components to use, user flow diagrams, wireframe/mockup references

### Technical Considerations Section
**Should Address:** Architecture, Dependencies, Security, Performance, Compatibility, Data, Integration

### Stakeholder Alignment
**PRD Should Help:** Align cross-functional teams, set clear expectations, enable parallel work streams, facilitate decision-making, provide single source of truth

**Distribution Checklist:**
- [ ] Engineering reviewed technical feasibility
- [ ] Design reviewed UX requirements
- [ ] Product leadership approved scope
- [ ] Stakeholders understand timeline
- [ ] Success metrics agreed upon

## Common PRD Scenarios

### Scenario 1: Feature Request from Customer
1. Document the customer request verbatim
2. Analyze the underlying problem
3. Generalize the solution for all users
4. Validate with product strategy
5. Scope appropriately

### Scenario 2: Strategic Initiative
1. Link to company OKRs/goals
2. Include market analysis
3. Consider competitive landscape
4. Think multi-phase rollout
5. Include success criteria aligned with strategy

### Scenario 3: Technical Debt / Infrastructure
1. Explain user impact (even if indirect)
2. Document current limitations
3. Articulate benefits (speed, reliability, maintainability)
4. Include engineering input heavily
5. Define measurable improvements

### Scenario 4: Compliance / Regulatory
1. Reference specific regulations (GDPR, HIPAA, etc.)
2. Include legal/compliance review
3. Deadline is usually non-negotiable
4. Focus on minimum viable compliance
5. Document audit trail requirements

## Validation & Quality Checks

### Self-Review Checklist
Before finalizing the PRD, verify:
- [ ] **Problem is clear**: Anyone can understand what we're solving
- [ ] **Users are identified**: We know who this is for
- [ ] **Success is measurable**: We can determine if it worked
- [ ] **Scope is bounded**: Clear what's in and out
- [ ] **Requirements are testable**: QA can verify completion
- [ ] **Timeline is realistic**: Estimates validated with engineering
- [ ] **Risks are identified**: We've thought through what could go wrong
- [ ] **Stakeholders aligned**: Key people have reviewed and approved

## Resources
This skill includes bundled resources:
### scripts/
- **generate_prd.sh** - Interactive PRD generation workflow
- **validate_prd.sh** - Validates PRD completeness and quality
### references/
- **prd_template.md** - Standard PRD template structure
- **user_story_examples.md** - User story patterns and examples
- **metrics_frameworks.md** - Guide to PM metrics (AARRR, HEART, OKRs)

## Best Practices Summary
1. **Start with the problem, not the solution**
2. **Write for your audience** (execs need summary, engineers need details)
3. **Be specific and measurable** (avoid vague language)
4. **Include visuals** (mockups, diagrams, flows)
5. **Define success upfront** (metrics, not features)
6. **Scope aggressively** (MVP mentality)
7. **Collaborate, don't dictate** (get input from all functions)
8. **Keep it updated** (PRD is a living document)
9. **Focus on "why" and "what", not "how"** (let engineers solve "how")
10. **Make it skimmable** (headers, bullets, summaries)

---
*Source: https://github.com/jamesrochabrun/skills (skills/prd-generator/)*
