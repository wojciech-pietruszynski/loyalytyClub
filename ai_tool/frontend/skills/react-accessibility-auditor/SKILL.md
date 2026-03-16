---
name: react-accessibility-auditor
description: Audit React UI changes for accessibility (a11y) issues and risks. Use when React components, pages, or flows are modified and need an accessibility quality gate.
---

# React Accessibility Auditor

## Overview

Review React UI changes for accessibility gaps and produce a concise fix list with file-level pointers.

## Recommended Dependencies

- Implementation from `react-ui-implementer`.
- `PLAN.md` for intended UI states (loading/error/empty).
- `ANALYSIS.md` for required user flows.

## Audit Focus

- Semantics: correct elements, headings, lists, landmarks.
- Labels: inputs, buttons, icons, and controls have accessible names.
- Keyboard: focus order, tab stops, skip links, traps.
- ARIA: appropriate roles, attributes, and live regions.
- Contrast & visibility: text, focus rings, disabled states.
- Dynamic UI: modals, toasts, popovers, and announcements.

## Workflow

1. Read AGENTS.md and any repo a11y guidelines.
2. Inspect changed components/pages for the focus areas above.
3. Check for missing or incorrect semantics and labels.
4. Verify keyboard navigation and focus management for interactive flows.
5. Provide a prioritized fix list with file paths and rationale.

## Output

Write `REVIEW.md` (or respond with a fix list) with:
- Findings ordered by severity (blocker/major/minor).
- File path and reason for each finding.
- Concrete fix suggestion (code-level guidance).
- Any open questions or unknowns.
