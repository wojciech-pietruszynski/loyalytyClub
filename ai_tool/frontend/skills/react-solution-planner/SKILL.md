---
name: react-solution-planner
description: Create a React/UI implementation plan (PLAN.md) from ANALYSIS.md. Use when a UI task is analyzed and you need component architecture, state/data flow, file touch list, and sequencing before coding.
---

# React Solution Planner

## Overview

Turn `ANALYSIS.md` into a concrete React implementation plan with components, data flow, routes, and file changes.

## Recommended Dependencies

- `ANALYSIS.md` is produced by `react-problem-analyzer`; if missing, run that skill first.
- Repo rules in `AGENTS.md` (if present).

## Workflow

1. Read `ANALYSIS.md` and any AGENTS.md rules in the repo.
2. Identify UI surfaces, components, routes, and data dependencies.
3. Propose state management approach (local state, context, store) and justify briefly.
4. Define API calls and loading/error/empty states.
5. List affected files and new files with paths.
6. Call out risks, open questions, and needed decisions.

## Output Format

Write `PLAN.md` with the following sections, in order:

- `Goals` (from ANALYSIS What/Why)
- `Component Map` (tree or list)
- `State & Data Flow`
- `Routes & Navigation`
- `API & Side Effects`
- `Files To Change`
- `Risks / Open Questions`

Rules:
- Use only facts from `ANALYSIS.md`. If assumptions are required, label them clearly.
- Keep it concise and actionable for implementation.
