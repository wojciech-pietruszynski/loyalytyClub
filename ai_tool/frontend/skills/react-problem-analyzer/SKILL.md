---
name: react-problem-analyzer
description: Extract the "What" and "Why" (plus scope and acceptance criteria) from React/Frontend task descriptions and produce a concise technical spec in ANALYSIS.md. Use when a user provides a UI/React requirement, ticket, or goal and you must clarify unknowns by asking the user and then documenting them.
---

# React Problem Analyzer

## Overview

Convert a React/UI task description into a clean technical spec in `ANALYSIS.md` using only explicit statements. Identify unknowns and ask the user for missing details before finalizing.

## Recommended Dependencies

- A clear task description, ticket, or goal statement from the user.

## Workflow

1. Read the task description carefully and identify explicit statements only.
2. Extract the "What" (required UI behavior or change) using only stated facts.
3. Extract the "Why" (user or business motivation) only if explicitly stated; otherwise mark as Unknown.
4. Summarize scope items exactly as given; do not add implied work.
5. Convert acceptance criteria into clear, testable bullets without adding new constraints.
6. List unknowns and open questions for anything required to implement but not provided.
7. If unknowns exist, ask the user concise, prioritized questions to fill the missing pieces. Do not speculate.
8. After the user answers, update `ANALYSIS.md` and keep Unknowns/Open Questions only if unresolved.

## Output Format

Write `ANALYSIS.md` with the following sections, in order:

- `What`
- `Why`
- `Scope`
- `Acceptance Criteria`
- `Unknowns`
- `Open Questions`

Rules:
- Use only information stated in the task description or clarified by the user's answers.
- Do not infer motivations or behavior.
- If a section has no data, write `Unknown` or `None` (prefer `Unknown` for missing required info; `None` for optional lists).
- Keep wording concise and technical, with a React/UI focus (components, views, state, routes, data sources).

## Questioning Guidance

When unknowns block implementation, ask targeted questions before finalizing. Prefer:
- User-facing behavior clarifications (flows, states, edge cases)
- Data and API dependencies (endpoints, payloads, loading/error states)
- Design constraints (layout, breakpoints, theming, accessibility)
- Navigation/routing (paths, auth, guards)
- Non-functional constraints (performance, SSR, analytics)

Keep questions short and numbered. Avoid more than 5 at once.

## Example (abbreviated)

**Input (short):**
"Goal: Add a new settings page with profile and notification sections. Scope: Settings page only. Acceptance criteria: user can update profile info, toggle notifications, and sees success/error toasts."

**Expected output shape:**
- What: Add Settings page with profile and notification sections; update profile; toggle notifications; show toasts.
- Why: Unknown.
- Scope: Settings page only (as stated).
- Acceptance Criteria: testable bullets.
- Unknowns/Open Questions: list missing details (e.g., data source, route path, validation rules) if not stated.
