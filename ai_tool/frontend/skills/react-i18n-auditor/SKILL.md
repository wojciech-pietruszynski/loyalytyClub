---
name: react-i18n-auditor
description: Audit React UI changes for internationalization readiness (i18n). Use when React components, pages, or flows are modified and all user-facing text must be localized; RTL is out of scope.
---

# React i18n Auditor

## Overview

Review React UI changes for i18n gaps and produce a concise fix list with file-level pointers.

## Recommended Dependencies

- Implementation from `react-ui-implementer`.
- `PLAN.md` for intended UI states and content structure.
- `ANALYSIS.md` for required user flows and messages.

## Audit Focus

- Text externalization: no hardcoded user-facing strings.
- Translation key quality: naming, namespace placement, missing/duplicate keys.
- Interpolation/pluralization: variables and counts handled via i18n APIs.
- Locale formatting: dates, times, numbers, currency, units via formatters.
- Content flexibility: long-string tolerance, truncation risks.
- Error/validation messages: localized mapping, no raw server strings.
- Accessibility strings: aria-labels, alt text, sr-only content localized.

## Workflow

1. Read AGENTS.md and any repo i18n conventions.
2. Inspect changed components/pages for hardcoded strings.
3. Check translation keys, namespaces, and duplication.
4. Verify interpolation, pluralization, and formatter usage.
5. Note layout risks from longer translations.
6. Provide a prioritized fix list with file paths and rationale.

## Output

Write `REVIEW.md` (or respond with a fix list) with:
- Findings ordered by severity (blocker/major/minor).
- File path and reason for each finding.
- Concrete fix suggestion (code-level guidance).
- Any open questions or unknowns.
