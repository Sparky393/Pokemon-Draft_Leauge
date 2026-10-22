# Neon egress follow-up on PR #1

Base: `main` at `e0f80fe21afb7d67b573e9f4902be2150772b606` (merged PR #1).
Local branch: `optimize/neon-egress-followup`.

## Changes and compatibility

1. **Standings and schedule reconciliation:** metadata reads exclude Base64 logos. Targeted updates write only standings or official-slot columns. Metadata constructor results are never saved as entities. Scoring, regular-season calculations, seeding, logo bytes, budgets, and team identity remain unchanged.
2. **Logo caching:** an authenticated request checks current version/type/presence before a cache hit. A miss reads only logo columns. The per-instance decoded-byte cache retains at most 32 entries / 16 MiB, and oversized imported images are served without caching. Upload/delete invalidates locally; other instances see the database version change on their next request. Original image bytes, upload validation, fallback behavior and private seven-day browser cache headers remain unchanged.
3. **Notifications:** existence queries replace full matchup/submission reads. Time checks happen before querying lineup conditions. Won notices project only ID, amount and Pokemon name in one left join, preserving missing-Pokemon fallback text. All notice content, per-team permissions and the two-minute cadence remain unchanged. Active state is not cached.
4. **Home:** the frontend uses a new `/api/dashboard/view` endpoint, excluding the unused roster. Existing `/api/dashboard` still returns its original response, including `myRoster` for coaches. Both endpoints authenticate freshly and use the same dashboard calculations.
5. **Request reuse:** identical concurrent default GET requests share a promise but receive independent cloned results. Completed responses are never reused. Mutations clear in-flight reuse before and after execution. Explicit request options/headers/signals bypass reuse. Consecutive navigation clicks share an unfinished render; later clicks still refresh. Removed the consecutive base refresh before the commissioner reset handler calls `renderManager`, which already refreshes. Script versions are bumped together.
6. **History:** immutable chunks cover 256 ID slots, with at most 32 chunks (8192 records maximum) retained. Every request reads a fresh count/max revision. On append, validate the old prefix count and reload only the tail/new chunks. Deletes and resets clear all chunks. Older uncached chunks are still fetched and returned, so no records are hidden or paginated away. Preserve the existing maximum 30-second refresh interval for out-of-band description edits. Backdated rows are sorted by creation time as before.
7. **Lineups and FAAB:** pass fresh settings within a request; retrieve all missing automatic-lineup rosters and latest historical submissions in batches. Keep roster ownership checks, stable automatic selections, submission locks, hidden lineup rules and deadline enforcement. FAAB submission/cancellation/processing reuse the locked settings row; processing reuses its freshly read period count. No cache of bids, ownership or spendable budget.

## Validation and measurements

- Baseline: **100 Java tests passed**, zero failures/errors/skips.
- Implementation: **106 Java tests passed**, zero failures/errors/skips; Maven package succeeded.
- Added six H2 integration regressions: logo-free result updates with data preservation, versioned/authenticated logo cache, compatible lightweight dashboard, 5200-record history append/delete/reset, batched automatic lineups, and fresh notification deadlines/results.
- Existing full-draft, FAAB, trade, lineup visibility, permissions, playoff and deployment-persistence tests remain enabled.
- Node request-helper regressions cover concurrent reads, response-object isolation, mutation invalidation, retries and explicit options.
- Browser regressions cover concurrent navigation and later fresh reloads, one initial dashboard, shared draft snapshot, active/paused/finalized polling and schedule filtering.
- All 19 JavaScript test/application files passed syntax checks; whitespace checks passed.

Measured SQL assertions use disposable H2 only; they do **not** measure Neon network bytes:

| Scenario | Verified result |
| --- | --- |
| Original full coach dashboard | 7 SELECTs on representative fixture |
| New Home endpoint | 5 SELECTs; no roster query |
| Result save/clear | Zero logo-payload SELECTs; image/name/budget unchanged |
| Warm logo request | Zero logo-payload SELECTs; metadata and authentication still fresh |
| Notification poll with distant deadline and one won bid | 3 SELECTs |
| Deadline processing for 16 missing teams | At most 5 SELECTs, then unchanged required inserts |
| Warm history with 5200 records | 1 revision SELECT, no history payload reread |
| Append to warmed 5200-record history | At most 2 payload chunk SELECTs rather than reloading all history |
| Three simultaneous roster navigation calls | 1 roster HTTP request; a later reload makes a new request |

Estimated payload saving: avoiding one all-team read with sixteen 100 KiB raw logos avoids approximately **2.08 MiB** of Base64 text. A warmed logo cache avoids approximately **133 KiB** per uncached-browser request for a 100 KiB image. These are illustrative payload estimates, not measured wire savings or a monthly forecast.

## Limits

- No production connection, migrations, dependency changes, deployment changes, push or merge.
- H2 and browser checks cannot prove PostgreSQL wire-byte savings or all production concurrency behavior.
- Necessary draft/notification polling intervals are unchanged. No cross-tab leader election was added; it was explicitly lower priority than these seven changes.
- Image caches are per Render instance and cold after a restart. Requests overlapping an upload may complete using the version observed before that upload, as with ordinary concurrent reads; subsequent requests check the new version.
- History cold loads and compatibility refreshes still read the full history in chunks. Only the newest 32 ID chunks are retained, so very large older histories still generate reads. This preserves complete API responses and bounds memory rather than silently truncating results.
- External history edits preserving count/max remain subject to the prior 30-second refresh bound. Arbitrary out-of-band replacement of IDs while preserving count/max is not a supported revision mechanism; use a fresh app process for such maintenance.
- Existing legacy trade-route gaps documented in `neon-egress-optimization.md` are unchanged.

## Files changed

| File | Purpose |
| --- | --- |
| [.github/workflows/mobile-smoke.yml](../.github/workflows/mobile-smoke.yml) | Run the new API-helper regression alongside browser tests. |
| [docs/neon-egress-followup.md](../docs/neon-egress-followup.md) | Implementation details, all files, measured checks and remaining limits. |
| [src/main/java/com/pokemondraft/leagueportal/controllers/ApiController.java](../src/main/java/com/pokemondraft/leagueportal/controllers/ApiController.java) | Add the lightweight Home endpoint; preserve the original dashboard. |
| [src/main/java/com/pokemondraft/leagueportal/controllers/LeagueFlowController.java](../src/main/java/com/pokemondraft/leagueportal/controllers/LeagueFlowController.java) | Reuse fresh lineup settings during deadline processing. |
| [src/main/java/com/pokemondraft/leagueportal/controllers/LeagueNotificationController.java](../src/main/java/com/pokemondraft/leagueportal/controllers/LeagueNotificationController.java) | Narrow won notices and existence checks; skip irrelevant deadline reads. |
| [src/main/java/com/pokemondraft/leagueportal/controllers/TeamBrandingController.java](../src/main/java/com/pokemondraft/leagueportal/controllers/TeamBrandingController.java) | Bounded version-checked decoded-logo cache with authenticated access. |
| [src/main/java/com/pokemondraft/leagueportal/repositories/FreeAgentBidRepository.java](../src/main/java/com/pokemondraft/leagueportal/repositories/FreeAgentBidRepository.java) | Project only the fields used by won-bid notifications. |
| [src/main/java/com/pokemondraft/leagueportal/repositories/MatchupRepository.java](../src/main/java/com/pokemondraft/leagueportal/repositories/MatchupRepository.java) | Targeted matchup existence query. |
| [src/main/java/com/pokemondraft/leagueportal/repositories/RosterEntryRepository.java](../src/main/java/com/pokemondraft/leagueportal/repositories/RosterEntryRepository.java) | Batch roster entries for missing automatic lineups. |
| [src/main/java/com/pokemondraft/leagueportal/repositories/TeamRepository.java](../src/main/java/com/pokemondraft/leagueportal/repositories/TeamRepository.java) | Targeted standings/slot updates and narrow image metadata/payload queries. |
| [src/main/java/com/pokemondraft/leagueportal/repositories/TransactionRecordRepository.java](../src/main/java/com/pokemondraft/leagueportal/repositories/TransactionRecordRepository.java) | Read occupied ID buckets, chunks and append-validation counts. |
| [src/main/java/com/pokemondraft/leagueportal/repositories/WeeklySubmissionRepository.java](../src/main/java/com/pokemondraft/leagueportal/repositories/WeeklySubmissionRepository.java) | Submission existence and batched most-recent prior lineups. |
| [src/main/java/com/pokemondraft/leagueportal/services/FreeAgencyService.java](../src/main/java/com/pokemondraft/leagueportal/services/FreeAgencyService.java) | Reuse locked settings and fresh processed-period count. |
| [src/main/java/com/pokemondraft/leagueportal/services/ScheduleService.java](../src/main/java/com/pokemondraft/leagueportal/services/ScheduleService.java) | Exclude logos from scoring and schedule reconciliation. |
| [src/main/java/com/pokemondraft/leagueportal/services/TransactionHistoryService.java](../src/main/java/com/pokemondraft/leagueportal/services/TransactionHistoryService.java) | Bounded incremental history chunks; complete fresh responses. |
| [src/main/java/com/pokemondraft/leagueportal/services/WeeklyLineupService.java](../src/main/java/com/pokemondraft/leagueportal/services/WeeklyLineupService.java) | Batch deadline processing and reuse settings/submission lookups. |
| [src/main/resources/static/index.html](../src/main/resources/static/index.html) | Version changed scripts to avoid mixed cached frontend assets. |
| [src/main/resources/static/js/api-fix.js](../src/main/resources/static/js/api-fix.js) | Coalesce default concurrent GETs with independent results and mutation invalidation. |
| [src/main/resources/static/js/app.js](../src/main/resources/static/js/app.js) | Use lightweight Home data and coalesce unfinished same-page renders. |
| [src/main/resources/static/js/modern.js](../src/main/resources/static/js/modern.js) | Use lightweight data on subsequent Home visits. |
| [src/main/resources/static/js/testing-ui.js](../src/main/resources/static/js/testing-ui.js) | Remove the redundant commissioner reset refresh. |
| [src/test/java/com/pokemondraft/leagueportal/EgressRegressionTest.java](../src/test/java/com/pokemondraft/leagueportal/EgressRegressionTest.java) | Six new integration regressions and SQL/payload assertions. |
| [src/test/java/com/pokemondraft/leagueportal/OfficialScheduleServiceTest.java](../src/test/java/com/pokemondraft/leagueportal/OfficialScheduleServiceTest.java) | Update metadata mock contract while retaining fixture/advancement assertions. |
| [src/test/java/com/pokemondraft/leagueportal/ScheduleServiceTest.java](../src/test/java/com/pokemondraft/leagueportal/ScheduleServiceTest.java) | Update metadata mocks while retaining scoring assertions. |
| [src/test/java/com/pokemondraft/leagueportal/TeamBrandingControllerTest.java](../src/test/java/com/pokemondraft/leagueportal/TeamBrandingControllerTest.java) | Provide narrow image query mocks; retain upload and permission assertions. |
| [src/test/java/com/pokemondraft/leagueportal/WeeklyLineupServiceTest.java](../src/test/java/com/pokemondraft/leagueportal/WeeklyLineupServiceTest.java) | Adapt batch/existence mocks; retain carry-forward and lock assertions. |
| [tests/api-request-dedup.mjs](../tests/api-request-dedup.mjs) | New server-free regression for the actual frontend API helper. |
| [tests/egress-browser.mjs](../tests/egress-browser.mjs) | Verify new dashboard route and concurrent versus deliberate page reloads. |

Total: **28 files**. Pre-existing untracked `.agents/` and `skills-lock.json` are unchanged and excluded.
