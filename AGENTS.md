# NightRider Android — coding-agent instructions

**Read before making any change.** Multiple independent ChatGPT/frontier-model agents may work on the NightRider ecosystem simultaneously. They do **not** automatically share conversation state. Use GitHub PRs as the canonical live coordination board.

## Mandatory NightRider value gate — BEFORE EVERY COMMIT

**Stop before each commit, including docs, tests, configuration changes, and automated GitHub file writes. Ask all three questions, every time:**

1. **Does it make delivering a professional ride easier or safer?**
2. **Does it improve the driver's profitability, independence, or ability to retain customers?**
3. **Does it make a customer's experience more dependable, transparent, or trustworthy?**

If you cannot clearly explain how the proposed change passes **at least one** test, **do not commit it**. Cut scope, improve the change, or discard it. Maintenance and testing must show a concrete link to one of those three outcomes; calling something "technical debt" is not an automatic exemption. Answer with the specific benefit, not a slogan. Never use these questions to bypass security, privacy, accessibility, or driver-safety requirements.

Every new commit message MUST include a substantive trailer as its own line (at least 15 characters after the dash):

```text
NR-Value: safety - Keeps pickup navigation stable when GPS fixes are stale.
```

Use one primary category: `safety` (question 1), `driver-business` (question 2), or `customer-trust` (question 3). You may explain other benefits in the PR. A claim that is false, vague, or unrelated to the diff fails review even if its syntax passes CI. Each commit must be cohesive, and the answer must be decided **before** writing it, not retrofitted as boilerplate. **For squash merges, the integration owner MUST preserve a valid NR-Value trailer in the final squash commit message as well**; a green feature-branch check alone is not proof the final main commit carries its justification.

Use `./scripts/setup-commit-gate.sh` once per local clone for the interactive Git commit hook/template. GitHub API writes bypass local hooks, so agents using GitHub tools MUST ask the questions themselves before calling `create_file`, `update_file`, or making any other commit. The `NightRider commit value gate` PR workflow checks the message on *every* commit in a PR. It is an after-the-fact CI safety net; actual prevention of merging a red check also requires a repository ruleset/branch protection with this check required. Do not falsely claim hard server-side enforcement until that rule is configured.

## Before touching code

1. Read this file and `README.md`, then check both [Android PRs](https://github.com/snurklefurk-hub/nightrider-driver-android/pulls) and [web/backend PRs](https://github.com/snurklefurk-hub/NightRider/pulls), plus recent commits in both `main` branches.
2. Work on a task-specific branch cut from current `main`; announce your work immediately with a **draft PR**, even if code is not complete. List expected changed files and related PRs in the template.
3. Check for overlap in Android code, shared assets, navigation, messaging and server API contracts. When another PR overlaps, read its diff and leave comments / link your PR. Do not silently overwrite another agent.
4. Update the PR with tests, compatibility implications and deployment order. Mark ready only when checks pass. Do not merge unless designated as integration owner.
5. Read the automatic **NightRider agent coordination** bot report in the PR. It lists other open PRs in BOTH repositories and warns if two PRs edit the same files in this repo. A clean report cannot rule out semantic/API conflicts.

## Release and integration safety

- Never commit or force-push directly to `main`. Never auto-merge. **WORK NR is the default integration owner** unless the product owner explicitly delegates it. Other agents prepare PRs; the integrator sequences merges and verifies current branch, tests and cross-repo compatibility.
- **A push to Android `main` can trigger a signed production/pilot APK release.** A web/backend merge can redeploy Render. Do not merge changes casually just to update documentation.
- Do not bypass a tool/provider safety block through a different write mechanism.
- Android API assumptions must match current `NightRider/server.py`; if an API contract changes, link the companion PR and identify backward-compatible rollout order.
- The production app lives at repository root `app/` and builds through root `settings.gradle`; `nav-prototype/` is separate.
- Keep nav and chat workstreams isolated; preserve the Android driver UI's No-Surprises Rule: predictable controls, no involuntary scrolling, focus theft, popups, moving buttons, or distracting animations.
- Keep unified LIGHT/DARK theme behavior. Require safe, stationary-device testing for driver-facing interaction changes.
- Use test fixtures; no live ride/database testing or real credentials unless explicitly instructed.
- Communicate results in plain language; don't ask the product owner to relay messages between AI agents.

## Open PR ownership and handoff

Use `.github/PULL_REQUEST_TEMPLATE.md`. Include feature scope, agent/workstream, expected files, any cross-repo dependency, **tests actually run**, untested cases, release sequence, and rollback plan. Draft PRs function as live work registrations.

Never maintain a fixed active-agent roster in this file; it becomes stale. [Android live PRs](https://github.com/snurklefurk-hub/nightrider-driver-android/pulls) and [web live PRs](https://github.com/snurklefurk-hub/NightRider/pulls) are the authoritative work inventory.

The messaging privacy contract is in the web repository's [CHAT-POLICY.md](https://github.com/snurklefurk-hub/NightRider/blob/main/CHAT-POLICY.md). Never infer privacy rules from UI labels alone.
