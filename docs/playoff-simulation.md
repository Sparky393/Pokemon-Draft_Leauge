# Corrected playoff seeding and simulations

Work is on `fix/playoff-seeding-simulation`, uncommitted. No merge, push, deployment, or production database connection was performed.

## Minimal application changes

- `ScheduleService.java`: Kanto quarterfinal opponents change from seeds 3/4 to 4/3, with matching placeholders. Both divisions now use 1-v-4 and 2-v-3. Existing winner detection, semifinal ordering, final advancement, score validation, and fixture synchronization are unchanged.
- `season-ui.js`: correct the Kanto explanatory text.
- `index.html`: bump the season script version to `playoff-seeds1`.

## Backend results

The final run of `PlayoffSimulationTest`, `OfficialScheduleServiceTest`, `ScheduleServiceTest`, and `WeekLockServiceTest` passed **643 tests, zero failures/errors/skips**.

The main simulation matrix runs 500 deterministic random regular seasons (valid randomly scored games from the real 80-game schedule), plus all 128 winner combinations across a seven-game playoff. These produce 628 complete playoffs and 4,396 playoff results. Every top-four seed from both divisions becomes champion in at least one case. A further test exports 18 verified playoff scenarios for frontend checks.

Each scenario exercises the real `ScheduleService` against in-memory Mockito repositories. No Spring application, H2, Neon, accounts, rosters, draft picks, or bids are involved. It verifies:

- No quarterfinal fixtures before the final regular-season result.
- Both divisions' actual top seeds pair 1-v-4 and 2-v-3, regardless of repository row order or team IDs.
- Each division's semifinal waits for both quarterfinal winners and orders the surviving teams by seed.
- The final waits for both division winners, including upsets; final scores are saved correctly.
- Regular-season match IDs/opponents/scores, standings, FAAB, and draft positions stay unchanged through the playoffs.
- Repeated bracket reads create no duplicates or rewrites; there are exactly 80 regular games and seven playoff games.
- Existing score validation, week-lock protection, and archival behavior retain their regression coverage.

A negative-control run temporarily used the original `main` service code with the new pairing test. It failed exactly as expected with a missing 1-v-4 Kanto matchup. The corrected source was restored, and the final full run passed.

The first test attempt encountered Mockito self-attachment errors in this environment. Explicitly loading the cached Mockito Java agent at JVM startup resolved them; no repository build configuration or dependency changes were needed.

Example, random seed `20261024`, outcome mask `72`:

| Stage | Kanto | Johto |
| --- | --- | --- |
| Seeds 1–4 | Club 7, Club 2, Club 8, Club 1 | Club 6, Club 3, Club 11, Club 9 |
| Quarterfinals | Club 7 vs Club 1; Club 2 vs Club 8 | Club 6 vs Club 9; Club 3 vs Club 11 |
| Semifinal | Club 7 vs Club 2 | Club 6 vs Club 11 |
| Division winner | Club 7 | Club 6 |

Final: Club 7 vs Club 6. Champion: Club 6.

## Frontend results and limitations

`node tests/playoff-simulation-ui.mjs` passed **540 rendering/permission checks and 126 actual score-save handler executions**. It loads the real `season-ui.js` and team branding code, then uses the backend-generated snapshots with a small DOM double. It checks all displayed team names, scores, pending/ready rounds, explanatory labels, commissioner/coach/guest controls, invalid-score rejection, and locked-round protection. This is executable frontend behavior coverage, not a CSS/layout or real-browser result.

`tests/playoff-simulation-browser.mjs` adds real Playwright coverage at 1440px and 390px, for commissioner and guest. It loads the actual site's HTML/CSS/JS, intercepts all URLs locally, and uses the backend-generated fixtures. It is designed to check 648 bracket screens and 252 commissioner score submissions, plus labels, read-only controls, invalid scores, and page overflow. **It has not passed here:** Chromium launch is blocked by macOS (`bootstrap_check_in ... Permission denied (1100)`). No browser-rendered screenshots or mobile-layout pass is claimed.

Generated artifacts (ignored under `target/`):

- `target/playoff-simulation-snapshots.json`: actual backend bracket responses for 18 complete simulated playoffs, nine stages each.
- `target/playoff-simulation-preview.html`: standalone interactive preview with the real renderer and styles, scenario/stage/role controls, and simulated score saves. No server or database connections. Visual appearance still needs browser review.

## Run locally

From the application directory:

```sh
env -i PATH="$PATH" \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
mvn -o \
-Dtest=PlayoffSimulationTest,OfficialScheduleServiceTest,ScheduleServiceTest,WeekLockServiceTest \
'-DargLine=-Xmx512m -javaagent:/Users/hrudayyerramareddy/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar' \
test

node tests/playoff-simulation-ui.mjs

PLAYWRIGHT_MODULE=/private/tmp/pokemon-browser/node_modules/playwright \
PLAYWRIGHT_BROWSERS_PATH=/private/tmp/pokemon-browser/browsers \
node tests/playoff-simulation-browser.mjs

open target/playoff-simulation-preview.html
```

The standalone preview's “Next stage” advances through the captured real-backend results. In commissioner view, “Save next simulated score” fills and clicks the actual scoring controls with the next expected result. Arbitrary alternative scores are intentionally rejected by the fixture adapter; choose another scenario to inspect other outcomes.

## Files changed

| File | Reason |
| --- | --- |
| `src/main/java/com/pokemondraft/leagueportal/services/ScheduleService.java` | Correct Kanto opponents and seed placeholders |
| `src/main/resources/static/js/season-ui.js` | Correct the frontend pairing description |
| `src/main/resources/static/index.html` | Invalidate the old season script URL |
| `src/test/java/com/pokemondraft/leagueportal/OfficialScheduleServiceTest.java` | Update existing quarterfinal expectations to 1-v-4 / 2-v-3 |
| `src/test/java/com/pokemondraft/leagueportal/PlayoffSimulationTest.java` | Randomized/exhaustive full-playoff tests, regression smoke check, real backend frontend-fixture export |
| `tests/playoff-simulation-ui.mjs` | Execute frontend renderer/save handlers and generate a standalone interactive preview |
| `tests/playoff-simulation-browser.mjs` | Desktop/mobile Playwright coverage for the actual site |
| `docs/playoff-simulation.md` | Results, reproduction instructions, changed-file inventory, and limitations |

## Before any deployment

This is a branch-only correction for review. If incorrect playoff fixtures already exist in production, the existing synchronization code can delete obsolete unplayed fixtures or archive obsolete played fixtures at Week 0. This test did not inspect or change live fixtures. Review that state before deploying; no production migration or cleanup was performed.
