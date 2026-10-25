# Pokémon type filter

Local branch: `feature/pokemon-type-filter`. No commit, merge, push, deployment, or production database change.

The Draft Room and free-agent card filters now have a Type dropdown with all 18 standard types. A dual-type Pokémon matches either of its types. Typing comes from the actual species/form shown on the base card, so regional variants retain their own typing; Mega forms still share their existing draft slots rather than adding duplicate entries.

Type combines with search, move, ability, Mega, price/BST sorting, and Clear. Requests that finish after a newer selection cannot overwrite that selection's results. Concurrent metadata lookups share one request. Existing cached Pokémon metadata without types is enriched once on its next successful load, using the same cache key; unrelated cached images are not discarded.

The filter bar uses a wrapping grid on desktop. On mobile, search and Clear span the full width and the other controls use two columns. Sidebar expansion/collapse naturally changes the grid's available width. The card layout and existing draft permissions are unchanged.

No backend code, SQL, polling interval, league records, or accounts were changed. Type metadata comes from existing browser-side PokeAPI lookups, not Neon queries. An uncached or older cached Pokémon may need one metadata lookup; type filtering depends on that service being reachable for unknown types.

## Preview

```sh
node tests/build-draft-preview.mjs
open target/draft-layout-preview.html
```

The existing standalone draft preview has been rebuilt with mock type metadata. Try Fire to see Incineroar and Skeledirge, or Dragon to see Hisuian Goodra. Collapse the sidebar or narrow the window to inspect how the controls fit. This preview never connects to the league database.

## Verification

Passed:

- `node tests/pokemon-type-filter.mjs`: all 18 options, both dual-type slots, draft/free-agent/legacy controls, combined filters, sorting, reset, stale-result protection, cache upgrade and shared metadata requests. All fetches are asserted to go only to the mocked PokeAPI endpoint.
- `node tests/draft-layout-ui.mjs`: existing selection/navigation/scroll/permission behavior.
- `node tests/mega-forms.mjs`: existing Mega X/Y cycling and metadata.
- `node tests/api-request-dedup.mjs`: existing API freshness/request-sharing behavior.
- JavaScript syntax and `git diff --check`.

`tests/draft-layout-browser.mjs` now also checks actual type filtering and control containment at desktop/mobile widths. Real-browser layout remains unverified here because Chromium launch was already blocked by this environment's macOS Mach-port permission restriction. No visual pass is claimed. The backend is unchanged and Java tests were not rerun for this frontend-only patch.

## Files changed

- `src/main/resources/static/js/pokemon-ui.js`: type options, normalized metadata, incremental cache enrichment/shared requests, legacy filter support.
- `src/main/resources/static/js/final-ui.js`: active draft/free-agent filter integration, reset and stale-result guard.
- `src/main/resources/static/css/draft-experience.css`: responsive filter grid.
- `src/main/resources/static/index.html`: changed asset versions.
- `tests/draft-preview-fixture.mjs`: simulated species/form typing for the preview.
- `tests/draft-layout-browser.mjs`: type and responsive-containment checks.
- `tests/pokemon-type-filter.mjs`: executable type/cache/filter regressions.
- `docs/pokemon-type-filter.md`: this implementation and validation note.
