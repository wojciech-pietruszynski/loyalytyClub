# Frontend Review

## Findings

1. High: `frontend/src/App.tsx` had `any` in API error paths, which bypassed type safety and let transport/runtime errors leak into UI handling in an unsafe way.
   Fix: replaced `any` with `unknown`, added typed error extraction, and normalized unauthorized/session-expired detection.

2. Medium: `frontend/src/App.tsx` used effects with hidden function dependencies (`handleLogout`, `fetchData`, modal closers), which made hook behavior brittle and triggered `react-hooks/exhaustive-deps`.
   Fix: moved effect-triggered logic behind `useEffectEvent` for session refresh, active-tab reload, and global Escape handling.

3. Medium: frontend quality gate was not green because the React code did not pass lint.
   Fix: refactored the flagged areas so the frontend now passes `npm run lint`, `npm test`, and `npm run build`.

## Open Questions / Assumptions

- `frontend/src/App.tsx` is still a large component. I treated that as a maintainability risk, but not a blocking defect for this pass because the user asked for corrective rework, not architectural decomposition.
- The production bundle warning about chunk size remains informational. I did not change bundling strategy in this review because it would be a separate optimization task with broader impact.
