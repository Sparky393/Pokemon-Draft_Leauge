# Guest viewing and Mega Y review

Local implementation only. No production connection, migration, commit, push, merge, or deployment was performed. Existing untracked `.agents/` and `skills-lock.json` were left alone.

## Mega forms

`final-ui.js` already registers both X/Y slugs for Charizard and Raichu. `form-ui-fixes.js`, loaded later, selected only the first normal Mega, hiding Y. The fix restores the shared detail renderer: **M clicks cycle base → Mega X → Mega Y → base**. Z Mega and Palafin Hero retain their separate controls. A render revision prevents a slow X response from replacing a more recently requested Y view.

Forms remain attached to the existing Charizard/Raichu draft slots, matching the X forms and existing league rule. No additional Pokémon rows, IDs, prices, draft slots, roster entries, picks, or transaction records are introduced. Their detail controls work wherever the shared detail viewer is used, including draft cards, roster cards, free agents, and revealed schedule cards. Searching a Mega's name finds its base slot once; Y abilities are also included in the existing draft/free-agent ability filter without extra lookups.

| Form | Types | HP / Atk / Def / SpA / SpD / Spe | Ability | BST |
| --- | --- | --- | --- | --- |
| Mega Charizard Y | Fire / Flying | 78 / 104 / 78 / 159 / 115 / 100 | Drought | 634 |
| Mega Raichu Y | Electric | 60 / 100 / 55 / 160 / 80 / 130 | No Guard | 585 |

Names, types, stats, and abilities were checked against [Pokémon Showdown's species data](https://github.com/smogon/pokemon-showdown/blob/master/data/pokedex.ts). Form IDs were checked against [PokeAPI's form catalog](https://github.com/PokeAPI/pokeapi/blob/master/data/v2/csv/pokemon.csv). The Y views use PokeAPI official artwork [Charizard Y, 10035](https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/10035.png) and [Raichu Y, 10305](https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/10305.png). These upstream URLs were opened during source verification; successful in-browser image decoding has not been verified here. The verified Y metadata is local so viewing Y does not depend on a live metadata API. X metadata still follows the existing lookup path without new overrides.

## Guest behavior and backend boundary

“Continue as Guest” appears directly below Log In. Its POST clears any account ID, marks only the HTTP session, rotates the session ID, and returns a DTO with role `GUEST` and null user/team IDs. No database account or persisted role is added. Refresh preserves the guest session; Exit Guest Mode / Sign In logs it out. Successful existing coach/manager login clears the guest marker.

Guest navigation includes Home, Draft Room (board/order/picks and pool), Rosters, Standings/divisions, Schedule/results, Playoffs, and public Transactions. Account, lineup, free-agency bidding, commissioner controls, and personal notifications are hidden. Draft controls remain disabled for guests. The existing responsive layouts are reused. A guest cannot navigate into a private page through `showPage()` either.

`GuestAccessInterceptor` permits only explicitly reviewed public GET/HEAD routes and the login/logout/guest session POSTs. Every other guest API request is denied before controller handling. Future endpoints are private until explicitly reviewed. Existing account-only `AuthService.current()`, password helpers, and manager checks also reject guest sessions, even if a stale account ID is present. Public read handlers use `viewer()`; normal sessions continue to use their existing account identity and permissions.

| GET surface reviewed | Guest policy |
| --- | --- |
| Auth identity | Session-only Guest DTO, no account query |
| Dashboard, teams, rosters, Pokémon pool, draft/status/order | Public existing projections; no personal roster for guests |
| Standings, calendar, schedule, week locks | Public; schedule includes lineup contents only when both teams have submitted |
| Playoff bracket | Computes display from saved data without creating/updating fixtures |
| Settings | Public league settings, no passwords/accounts/bids; absent settings do not trigger inserts |
| Transactions | Existing public history of completed trades, awarded free agents, waiver processing/tiebreaks; no bid table or pending offer reads |
| Own lineup and legacy weekly lineup endpoint | Denied, including unrevealed lineups |
| FAAB bids, account security, manager accounts/reminders/reset preview | Denied |
| Notifications, CSV exports, unknown/new API routes | Denied for guests; relevant public information is available on the regular pages |
| Team logo | Public image read only |

Two existing GET paths have write side effects for normal authenticated users: schedule reads finalize expired lineups, and playoff reads synchronize fixtures. Those original paths remain available to coaches/managers. Guest paths share the rendering/reveal calculation but skip both write operations. A guest's schedule may therefore wait for an authenticated league action to finalize expired lineups, and a guest's bracket may show an unmaterialized fixture as pending. Existing scoring and reveal rules were not changed.

No new polling was introduced. Guest mode skips the existing personal-notification polling. Draft viewing reuses the existing lightweight status polling. Transactions load only when the page is opened. Y metadata and filter aliases add no database queries.

## Files changed

Paths are relative to the repository root.

| File | Reason |
| --- | --- |
| `src/main/java/com/pokemondraft/leagueportal/config/GuestAccessInterceptor.java` | New default-deny guest API boundary and explicit public-read allowlist |
| `src/main/java/com/pokemondraft/leagueportal/services/AuthService.java` | Session-only guest identity, public viewer identity, account-helper protection, clear guest marker on login |
| `src/main/java/com/pokemondraft/leagueportal/controllers/AuthController.java` | Guest entry/session rotation and guest-aware identity read |
| `src/main/java/com/pokemondraft/leagueportal/controllers/ApiController.java` | Public viewer authorization, guest dashboard, read-only settings fallback and playoff path |
| `src/main/java/com/pokemondraft/leagueportal/controllers/LeagueFlowController.java` | Public calendar/schedule authorization and guest read-only lineup reveal path |
| `src/main/java/com/pokemondraft/leagueportal/controllers/LeagueAdministrationController.java` | Allow guests to read public week-lock status |
| `src/main/java/com/pokemondraft/leagueportal/controllers/TeamBrandingController.java` | Allow guests to read team logo images; existing edits still require account authorization |
| `src/main/java/com/pokemondraft/leagueportal/services/WeeklyLineupService.java` | Share batch reveal calculation with a read-only entry point that skips deadline finalization |
| `src/main/java/com/pokemondraft/leagueportal/services/ScheduleService.java` | Share bracket rendering with a read-only entry point that skips fixture synchronization |
| `src/main/resources/static/index.html` | Guest button, public transactions navigation/page, changed-script cache versions |
| `src/main/resources/static/js/app.js` | Guest entry/navigation/logout presentation and public transactions rendering |
| `src/main/resources/static/js/final-ui.js` | Mega form name aliases and Y abilities in existing search/filter controls |
| `src/main/resources/static/js/form-ui-fixes.js` | M cycling for X/Y, verified local Y metadata/artwork, types display, render race guard |
| `src/main/resources/static/js/league-extras.js` | Skip personal-notification requests for guests |
| `src/test/java/com/pokemondraft/leagueportal/AuthServiceTest.java` | Guest session/account isolation and returning to existing coach login |
| `src/test/java/com/pokemondraft/leagueportal/GuestAccessTest.java` | New H2 API permission, private-read, SQL-write, lineup-reveal, playoff, and existing login regression tests |
| `tests/mega-forms.mjs` | Offline form metadata, filter aliases, M cycling, original X lookup, Z/Hero regression coverage |
| `tests/guest-mega-browser.mjs` | Desktop/mobile UI coverage using local static files and intercepted fixture APIs; never connects to a database/server |
| `docs/guest-mega-review.md` | This implementation/validation review and full changed-file inventory |

## Verification and remaining work

- **Passed:** direct Java 21 compilation of all application/test sources using cached dependencies, `javac -proc:none`; no application, Maven plugins, annotation processors, or tests executed.
- **Passed:** Node syntax parsing of the four changed application scripts and both new test scripts.
- **Passed:** `git diff --check`.
- **Runtime tests not run:** the security-audit skill requires an OS-enforced isolated runtime for tests. The local capability probe `sandbox-exec -p '(version 1)(allow default)(deny network*)' /usr/bin/true` failed with `sandbox_apply: Operation not permitted`. No application/test runtime was launched after that failed probe. No runtime pass or preservation claim is made. The existing suite has not been rerun, and desktop/mobile behavior remains unverified at runtime.

In a suitable isolated test environment, run the following against a disposable source copy with an empty/allowlisted environment, no production credentials, no external network, bounded runtime/resources, and scratch-local write access. Java tests explicitly use temporary H2; the browser test intercepts every URL locally. Dependencies must already be available.

```sh
mvn -o -Dtest=GuestAccessTest,AuthServiceTest,AuthControllerTest,DraftServiceTest,WeeklyLineupServiceTest,ScheduleServiceTest,TeamBrandingControllerTest,LeagueAdministrationControllerTest,LeagueWorkflowSmokeTest,DeploymentPersistenceTest,EgressRegressionTest test
node tests/mega-forms.mjs
PLAYWRIGHT_MODULE=/path/to/playwright node tests/guest-mega-browser.mjs
```

The added API tests enumerate mapped mutations and require guest denial before any SQL, check public reads issue only SELECTs even after an expired lineup deadline, and verify that completed regular-season bracket reads do not create fixtures. Other tests cover hidden/revealed lineups, confidential bid denial, session-only identity, existing coach/manager login transitions, and re-entering guest mode after manager login. The browser test covers 1440px/390px navigation, refresh/logout, disabled drafting, no private reads or notification polls, shared-slot search, both Y views, and M cycling. These are authored assertions, not executed results.

**Production database changes required: none.** No SQL or migration needs approval or execution for this implementation. No entity/schema/role enum, initializer, data migration, account, or league rule was changed. Production data has not been accessed or validated. The existing deployment startup behavior was not executed or altered.

Remaining risks are unexecuted runtime regressions, unverified real-device rendering, upstream artwork availability, and the deliberate guest read-only behavior around fixtures/automatic lineup finalization described above. Keep this patch undeployed until the runtime tests and UI review are completed.
