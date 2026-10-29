## Three-test value gate (required)

**Before each commit, ask all three questions and answer at least one concretely:**

1. Does it make delivering a professional ride easier or safer?
2. Does it make the rider's experience more dependable, transparent, or trustworthy?
3. Does it make the industry better?

- **Primary test passed (1 / 2 / 3):**
- **Specific outcome for ride safety, rider trust, or a better transportation industry:**
- **Why this is worth maintaining rather than feature bloat:**
- **Every commit includes an `NR-Value:` justification or an owner-approved `NR-Override: MAKE IT SO` declaration:** yes / no
- **Would anything be better if we did NOT ship this?** explain

## Manual owner override (if used)

- Did the product owner directly say **MAKE IT SO** for this exact change?
- Record the authorized scope and where the owner's instruction can be verified:
- Integration owner has verified the authorization: yes / no
- Safety, privacy, testing and release checks remain in force.

## Goal and roles affected
What user behavior should improve?

## Agent workstream
- Agent / workstream:
- Status: draft / ready / blocked
- Files and modules expected:
- Related web/backend PR or dependency:
- Other open PRs reviewed / overlap:

## Changes
Describe implementation and any network/API changes. Identify source-of-truth and backwards compatibility.

## Driver safety
- No-Surprises Rule checked (fixed controls, no focus/scroll/overlay surprises):
- LIGHT/DARK styling preserved:
- Permissions / private data boundaries:
- Navigation kept isolated from unrelated changes:

## Verification
- Source tests and Gradle/CI run URLs **actually executed**:
- Device verification actually performed:
- Known incomplete / untested cases:

## Release handoff
- Required web/server release order:
- Expected signed APK impact and versioning:
- Rollback steps:
- Integration owner decision:
