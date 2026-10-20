# Pokemon Draft League Portal — Prototype

A functional first-pass prototype for a 16-coach Pokemon Champions draft league.

## What is included

- Login/session system with one commissioner account and 16 coach accounts.
- 16 teams split into Division A and Division B (8 teams each).
- Commissioner can rename teams/coaches, move teams between divisions, change draft order, FAAB, and manual standings placement.
- 10-round draft with optional snake order and automatic roster population.
- Regulation M-C-era Pokemon Champions draft pool. Mega Evolutions are not separate draft slots; draft the underlying Pokemon.
- Every Pokemon gets a deterministic prototype value from $0 to $20. Commissioner can edit every value.
- 10-game regular season schedule:
  - 7 games against every team in the same division.
  - 3 balanced cross-division opponents.
  - commissioner-controlled random seed for the cross-division pairings.
- Standings sorted by Wins -> Differential -> Head-to-Head -> Team, unless commissioner sets a manual rank override.
- Results automatically recalculate wins, losses, and differential.
- Weekly lineup submission: exactly 6 Pokemon from a coach's roster.
- Blind free-agent bids using FAAB.
- Commissioner bid processing (highest bid, then earliest submission as the prototype tie-breaker).
- Trades between teams.
- Transaction history.
- Possible-playoff-seed mode is OFF until only two regular-season weeks remain. The prototype deliberately does not run exhaustive scenario calculations earlier.
- H2 local database now; PostgreSQL driver/config variables are already included for deployment later.

## Prototype accounts

Commissioner:

- Username: `manager`
- Password: `manager`

Coaches:

- Usernames: `coach1`, `coach2`, ... `coach16`
- Password for every coach: `password`

These are intentionally simple prototype credentials. Do not use them for a public production deployment. Proper Spring Security/password hashing should be added before launch.

## Run on your Mac

Requirements:

- Java 21
- Maven

From the project directory:

```bash
mvn spring-boot:run
```

Then open:

```text
http://localhost:8080
```

If port 8080 is already used:

```bash
lsof -i :8080
kill <PID>
```

Or temporarily run on another port:

```bash
PORT=8081 mvn spring-boot:run
```

## Fresh database

The local database is stored in the `data/` folder. If the entity structure changes while you are debugging the prototype and Hibernate complains, stop the server and reset local data:

```bash
rm -rf data
mvn spring-boot:run
```

This resets all prototype teams, picks, results, bids, trades, etc.

## Important prototype limitations

1. Passwords are plain text. Replace with Spring Security + BCrypt before public deployment.
2. Commissioner schedule regeneration deletes current schedule/results. Use it only before the season or when intentionally resetting the schedule.
3. Head-to-head is a simple pairwise tie-breaker. Complex multi-team tie resolution can be improved later.
4. Playoff seed scenario enumeration is intentionally not implemented yet. The backend only exposes whether seed mode is allowed; add exhaustive calculations only for the final two weeks.
5. The Pokemon pool is intended as a Regulation M-C prototype dataset. Verify any edge-case forms against the in-game Regular Roster before the real draft.
6. UI is functional and deliberately basic.
7. Free-agent bid ties use earliest submission after bid amount. Change this if your league has a different tie-breaker.

## Suggested next fixes

Run the full site once and make a list of what you want changed. Recommended order:

1. Team names + division assignments.
2. Draft order and actual draft prices.
3. Visual design.
4. Exact free-agency rules/deadlines.
5. Exact trade approval rules.
6. Playoff qualification/seeding rules.
7. Spring Security.
8. PostgreSQL + deployment.
