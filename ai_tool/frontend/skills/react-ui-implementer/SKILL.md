---
name: react-ui-implementer
description: Implement React UI changes from PLAN.md into the codebase. Use when a React plan exists and production UI code needs to be written (no tests).
---

# React UI Implementer

## Overview

Implement the React UI per `PLAN.md`, following repo conventions and AGENTS.md rules.

## Recommended Dependencies

- `PLAN.md` is produced by `react-solution-planner`; if missing, run that skill first.
- `ANALYSIS.md` is produced by `react-problem-analyzer`; if missing, run that skill first.
- `AGENTS.md` files are present in the repository.

## Workflow

1. Read `PLAN.md`, `ANALYSIS.md`, and AGENTS.md rules.
2. Locate existing patterns (components, hooks, styling, routing).
3. Implement the planned components, state, and data flow.
4. Add basic loading/error/empty states if specified in the plan.
5. Keep changes scoped to the plan; note any deviations explicitly.
6. Do not add tests in this skill.

## Output

Deliver code changes under the existing React project structure. Keep a brief change summary.

Rules:
- Follow established patterns in the repo (styling, naming, hooks, data fetching).
- Avoid unplanned refactors.
- Add small comments only where logic is non-obvious.
