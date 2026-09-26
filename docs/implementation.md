# Ploi Panel Implementation Plan

> **For Hermes:** Follow `writing-plans` and `quality-gates` to split each milestone into bite-sized RED/GREEN/REFACTOR tasks before implementation. Never claim full API coverage before the endpoint inventory is complete and tested.

**Goal:** Deliver a publishable Android-native client for every documented Ploi API operation, with local-only secrets, adaptive layouts, widgets, monitoring and optional SSH.

**Architecture:** Single Android app with feature packages, a typed Ploi API client, encrypted per-profile local storage and a shared refresh coordinator for foreground, background and widgets. Optional SSH and optional server agent are separate opt-in features; never a prerequisite for Ploi API management.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 adaptive, Android Keystore, Room/DataStore as appropriate, WorkManager, Glance/AppWidget; select pinned versions after a verified build. Use Android-native biometric prompt and secure file-picker APIs.

---

## Gate 0 — Auditable API contract and foundation

1. Inventory each official API route by domain (user, credentials/providers, servers, sites, databases, deployments, SSL, backups, cron, services, DNS, etc.). For each, record method/path, scopes, request/response schema, subscription restrictions, paging, asynchronous behavior and available create/update/delete action. Mark undocumented account-web features as unavailable, not fake API endpoints. Store as `docs/api-coverage.md` and create machine-checkable coverage tests.
2. Bootstrap Gradle wrapper, Android app, bilingual resources, GPL headers, `.gitignore`, Dependabot and CI (assemble, unit tests, lint, dependency checks). Pin Android min/target SDK based on the current Play Store requirements and test phones. Verify `./gradlew testDebugUnitTest lintDebug assembleDebug` before publishing code.
3. Establish package contracts: `core/network`, `core/model`, `core/security`, `core/storage`, `core/ui`, `feature/*`, `widget/*`, `ssh/*`. Document error states (offline, token invalid, missing scope, plan not eligible, rate limit, task pending).

## Gate 1 — Safe multi-profile Ploi access

4. Test then implement token capture (manual and on-device OCR/photo, with no image persisted), profile creation/switch/remove, and protected encrypted storage. Verify that deleting a profile removes cached data and keys without touching others.
5. Test then implement code onboarding, biometric activation where supported, auto-lock policies, background behavior, re-authentication for sensitive operations, recovery limitations and threat model. No silent biometric bypass and no secrets in Auto Backup.
6. Test then implement HTTP client (Bearer per profile, server-side pagination up to 50, selectable client-side page size, request deduplication, retry-after/rate-limit, field validation). Mock 401, 403, 429, malformed payload, offline and token rotation.

## Gate 2 — All documented operations

7. Implement and test each API domain in the same order as the inventory, with read/create/update/delete and domain actions together. Start with user/providers and servers; then sites/deployments; then databases, networking/security, backups/automation, and remaining domains. Every route gets request/response/error tests and a working UI path.
8. For server creation, populate credentials, plans and regions from Ploi; unavailable providers get a truthful explanation and deep link where the API cannot create them. Handle installation progress and failure, not just a success response.
9. Per profile/server/site, cache recent data locally, support configured pagination and offline read-only mode. State clearly when data is stale. Confirm destructive actions after code/biometric verification; audit all action entry points.

## Gate 3 — Operations experience and monitoring

10. Build modern adaptive navigation and master/detail lists for compact, medium and expanded widths; test on standard phones and representative Fold screen sizes/postures. Provide FR/EN and light/dark/system settings, accessibility, dynamic font sizes and touch targets.
11. Implement Ploi Monitoring with missing-installation and plan-ineligible states, timestamps and unit-tested metric normalization; do not treat older samples as live. Add opt-in site HTTP(S) checks with configurable target, timeout, accepted status and failure debounce.
12. Provide individual and multi-server widgets with selectable sites, sizes and stale states. Share the cache and polling budget; instrument rate-limit tests. Background work requests a five-minute freshness goal but UI and documentation must say *best effort*. Test notifications, denial of notification permission and app process death.
13. Optional agent: define authenticated protocol and threat model only after confirming which desired metrics are missing from Ploi. Avoid mandatory centralized infrastructure. SSH: host-key pinning/TOFU confirmation, encrypted keys and terminal session teardown tests.

## Gate 4 — Portability and release

14. Export/import every profile, preference, monitored target and relevant cache by a versioned authenticated encrypted archive. Include migration tests, tamper/wrong-password tests, no-plaintext-at-rest tests and restore to a different device.
15. Run comprehensive unit/integration/UI checks, foldable emulator/device checks, accessibility, security and Play policy review; produce reproducible signed release instructions without committing signing keys. Ship via PR + verified CI only on explicit publication approval.

**Operating rule:** Each milestone is incomplete until implementation + tests + build + security review succeed. No EAS build; this is a native Android project. No real-server destructive tests without a separate, explicit test-resource authorization.
