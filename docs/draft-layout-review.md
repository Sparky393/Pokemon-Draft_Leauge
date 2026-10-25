# Draft layout review

Branch: `fix/draft-navigation-and-cards`. Uncommitted; no merge, push, deployment, production connection, or migration.

## Review the actual UI locally

```sh
open /Users/hrudayyerramareddy/projects/TDM_Draft_League/Pokemon-Draft_LeaugePortal/target/draft-layout-preview.html
```

The standalone file bundles the actual page, CSS, and JavaScript with a mock fetch adapter. It starts in the Draft Room as the coach on the clock, with 24 completed picks and a sample available pool. No requests are forwarded to a league backend. Artwork images can load from PokeAPI's image repository; all league data and metadata are simulated.

Try:

1. Collapse navigation using the arrow beside Draft League; restore it with Show navigation. The preference survives reloads and applies to desktop and phone layouts.
2. Click an available Pokémon, close its detail view, and check the selected name and price directly beside Draft Selected Pokémon. Try a simulated pick.
3. Drag the persistent horizontal slider above the draft board, or use its left/right buttons. Scroll vertically inside the board: team headers and the Round column stay in place. The native bottom scrollbar is also styled, but native scrollbar visibility still depends on the operating system; the slider is the always-present control.
4. Click Garchomp in the first draft-board cell: the detail price should read $18, matching the simulated pick, instead of the previous invented $0.
5. Check card fit, including Garchomp's unbroken name, then narrow the browser or use device emulation. Select Commissioner or Guest in the preview selector to inspect role-specific controls. Toggle the draft open/closed to check the disabled button state.

The preview can be regenerated with `node tests/build-draft-preview.mjs`. It is ignored under `target/` and is not shipped as production data.

## Implementation

- Navigation can fully collapse, reclaiming sidebar width on desktop and navigation height on mobile. Native buttons expose expanded state, move focus appropriately, and remember the preference in localStorage. No backend requests are involved.
- The selected name and price appear in a live status beside the draft button. Selection is required to enable the button; existing backend turn/budget rules remain authoritative. Closed/finalized drafts and read-only users keep the button disabled. Failed submissions allow retry.
- The board has bounded vertical scrolling with sticky opaque team headers and the existing sticky Round column. A native range control and arrow buttons provide persistent horizontal navigation; resizing the sidebar updates the range. Previous board scroll position survives a draft rerender. Old scroll listeners/observers are disposed when the board is replaced.
- Board cards use an explicit compact height with room for artwork, name, and price. Name/price no longer compete horizontally, so ordinary names do not break arbitrarily to make room for a badge. Shared card-body and detail-card sizing also avoid unnecessary fixed minimum heights/width overflow.
- Completed picks now include a nullable `price` field from a single ID/name/price projection in the existing pick metadata query. Card and popup use the same object. No extra HTTP requests, per-card database queries, polling, schema changes, or writes were added. A real zero displays $0; missing price metadata displays Price unavailable instead of pretending it is zero. This is the saved Pokémon price used by the league, not a new historical-price column.

## Verification

- Passed: **34 Java tests** across DraftPriceViewTest, LeagueMapperTest, DraftServiceTest, EgressRegressionTest, and GuestAccessTest, using explicit disposable in-memory H2 databases or mocks and a cleared environment. No Neon credentials were inherited.
- The new H2 test verifies $18, legitimate $0, and unknown prices; all completed picks share one narrow metadata query, guest draft loading stays at six SELECTs with populated picks, and the read performs no writes.
- Passed: `node tests/draft-layout-ui.mjs`, exercising actual selection/save/retry guards, scroll input/arrows/resize/cleanup, navigation persistence/focus, and price helpers with DOM doubles.
- Passed: existing Mega form tests and API request deduplication/freshness tests.
- Passed: preview fixture smoke checks for actual sample pick prices, simulated drafting, and guest denial; changed JavaScript and bundled preview syntax; `git diff --check`.
- The first new query-budget assertion expected five SELECTs but omitted the existing waiver-status count. It was corrected to the six-query baseline; the final run passed without adding another query.
- **Real-browser visual checks remain pending.** `tests/draft-layout-browser.mjs` covers desktop 1440px and mobile 390px, sidebar width/persistence, selected text, actual prices, sticky header geometry, scrollbar syncing, card containment/name wrapping, simulated drafting, and guest restrictions. Chromium could not launch here because macOS denied its Mach-port bootstrap permission (`Permission denied (1100)`). No screenshots or visual pass are claimed.

To run the browser checks from Terminal after rebuilding the preview:

```sh
PLAYWRIGHT_MODULE=/private/tmp/pokemon-browser/node_modules/playwright \
PLAYWRIGHT_BROWSERS_PATH=/private/tmp/pokemon-browser/browsers \
node tests/draft-layout-browser.mjs
```

## Changed files

| File | Purpose |
| --- | --- |
| `src/main/java/com/pokemondraft/leagueportal/controllers/ApiController.java` | Batch completed-pick names/prices in the existing read |
| `src/main/java/com/pokemondraft/leagueportal/dto/DraftPickDto.java` | Add nullable price to completed picks |
| `src/main/java/com/pokemondraft/leagueportal/mapper/LeagueMapper.java` | Map pick price and preserve unknown fallback |
| `src/main/java/com/pokemondraft/leagueportal/repositories/PokemonRepository.java` | Narrow ID/name/price projection |
| `src/main/resources/static/index.html` | Collapse controls, new assets, changed-script versions |
| `src/main/resources/static/css/draft-experience.css` | Responsive navigation, cards, selection, sticky board, and scrollbar styling |
| `src/main/resources/static/js/navigation-ui.js` | Browser-only navigation state and focus handling |
| `src/main/resources/static/js/draft-live.js` | Correct pick metadata, shared popup object, persistent horizontal controls and scroll preservation |
| `src/main/resources/static/js/final-ui.js` | Selection beside action button, enable/retry guards, unknown-price display |
| `src/main/resources/static/js/form-ui-fixes.js` | Correct price display in the final detail renderer |
| `src/main/resources/static/js/pokemon-ui.js` | Price formatting helper and null-safe badges |
| `src/test/java/com/pokemondraft/leagueportal/DraftPriceViewTest.java` | H2 API price/query-budget/read-only regression |
| `src/test/java/com/pokemondraft/leagueportal/LeagueMapperTest.java` | Price/null mapping regression |
| `tests/mega-forms.mjs` | Supply the new formatting dependency to the existing isolated harness |
| `tests/draft-layout-ui.mjs` | Execute actual UI handlers with DOM doubles |
| `tests/draft-layout-browser.mjs` | Desktop/mobile visual and interaction checks |
| `tests/draft-preview-fixture.mjs` | Shared standalone mock league adapter |
| `tests/build-draft-preview.mjs` | Bundle the actual site into a standalone review page |
| `docs/draft-layout-review.md` | This review, instructions, and verification limits |

Existing untracked `.agents/` and `skills-lock.json` were not included or modified. Production league data and accounts were not accessed or changed.
