---
name: react-code-reviewer
description: Perform a strict React/UI code review for antipatterns, performance, regressions, and AGENTS.md adherence. Use when React changes need a quality gate before merge.
---

# React Code Reviewer

## Overview

Review React UI changes and produce a concise fix list or REVIEW.md focused on risk and quality.

## Recommended Dependencies

- Implementation from `react-ui-implementer`.
- Tests from `react-test-architect` when coverage is required.
- `PLAN.md` and `ANALYSIS.md` for intended behavior and edge cases.

## Review Focus

- Antipatterns: prop-drilling, excessive nesting / state, unnecessary dependencies.
- Performance: unnecessary re-renders, expensive effects, list rendering.
- Correctness: state flow, edge cases, loading/error states, routing.
- Maintainability: duplication, unclear naming, brittle logic.
- Testing: missing tests, inadequate coverage, edge cases, flakiness.
- AGENTS.md compliance and repo conventions.

## Output

Write `REVIEW.md` (or a fix list in the response) with:
- Findings ordered by severity, each with file path and reason.
- Concrete fixes or test additions.
- Open questions/assumptions.

If issues are found, request re-work via `$react-ui-implementer` and/or `$react-test-architect`.
