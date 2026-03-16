---
name: react-test-architect
description: Write React Testing Library/Vitest tests for UI changes based on ANALYSIS.md and updated React files. Use when new or changed UI behavior needs coverage.
---

# React Test Architect

## Overview

Create UI tests that cover behavior, edge cases, and regressions for React changes.

## Recommended Dependencies

- Updated UI implementation from `react-ui-implementer`.
- `PLAN.md` from `react-solution-planner` (for intended behavior).
- `ANALYSIS.md` from `react-problem-analyzer` (for edge cases).

## Workflow

1. Read `ANALYSIS.md`, `PLAN.md`, and the changed UI code.
2. Identify user flows, edge cases, and state transitions to cover.
3. Follow existing test conventions (location, naming, utilities).
4. Write tests using React Testing Library + Vitest (or repo standard).
5. Cover loading/error/empty states where applicable.
6. Avoid snapshot-only coverage; assert behavior.

## Output

Add test files under the project’s existing test structure (e.g., `__tests__` or alongside components).

Rules:
- Use repo helpers/mocks where available.
- Prefer user-centric queries and interactions.
- Keep tests deterministic.
