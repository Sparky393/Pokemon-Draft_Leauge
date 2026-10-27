# October 2026 Java and Node security update

These changes are local and uncommitted. They do not change application source,
Spring dependencies, database configuration, accounts, league rules, or saved data.
They do not run a migration or start the application during the certificate update.

## Applicability

- **eMudhra certificate trust:** applies to TLS clients in both the Maven build and
  Java runtime. Docker services manage their own certificate stores; updating the
  OS bundle does not by itself update Temurin's Java trust store.
  [Render advisory](https://render.com/docs/advisory-emudhra).
- **Node vulnerabilities:** no Node installation or execution is defined in the
  production Docker stages. Node is used by the two GitHub workflows. Those
  workflows now explicitly select patched Node 22.23.4, retaining their Node 22
  major version. This does not change GitHub's internally managed action runtimes.
  [Node security release](https://nodejs.org/en/blog/vulnerability/october-2026-security-releases).
- **PHP2Shell:** no PHP build/runtime or PHP dependency was found in the repository.
  No PHP changes are included.
  [Render PHP advisory](https://render.com/docs/advisory-php2shell).

## Files changed

| File | Change |
| --- | --- |
| `Dockerfile` | Preserve Maven 3.9.11 by copying only its Maven distribution into `eclipse-temurin:21-jdk-noble`. Use `eclipse-temurin:21-jre-noble` at runtime. Run certificate remediation in both stages, before Maven downloads in the builder. |
| `docker/update-ca-trust.sh` | Install updated Ubuntu CA certificates, require the Noble security package minimum, select vendor-listed roots by certificate subject in both stores (independent of file/alias naming), remove their trust, and fail the build with store/subject diagnostics if any remain. |
| `.node-version` | Select Node 22.23.4 for project tooling. This does not install or replace the machine's global Node executable. |
| `.github/workflows/mobile-smoke.yml` | Read Node version from `.node-version`. |
| `.github/workflows/readability-format.yml` | Read the same Node version file. Existing workflow triggers and permissions remain unchanged. |
| `docs/october-2026-security-update.md` | Record scope, validation limits, commands, and failure guidance. |

The certificate package must be at least `20260816ubuntu0.24.04.1`, as specified by
[Ubuntu USN-8944-1](https://ubuntu.com/security/notices/USN-8944-1). Later package
versions are accepted.

The root policy follows the Java vendor's October 24 mitigation:
`emSign Root CA - G1`, `emSign Root CA - G2`, `emSign ECC Root CA - G3`, and
`AAA Certificate Services`. G1 and G2 are additional vendor-recommended removals;
this does not mean that all four roots' private keys were compromised.
[Java vendor guidance](https://ops.java/emudhra).

The Java images remain on major version 21. Their tags follow vendor updates;
no unverified digest or Java patch-specific image tag is invented. Pull fresh
images when building, and record the actual `java -version` output. The explicit
trust-store mitigation also applies if a registry tag's vendor patch lags.

## Validation performed here

- Shell syntax and whitespace checks passed.
- Alias extraction fixtures passed for Oracle-style, Debian-style, and arbitrary
  aliases with spaces. The original alias/filename-based script failed in the
  user's Docker build; selection now uses actual certificate subjects.
- Subject-selection fixtures matched all four vendor-listed roots in compact and
  spaced distinguished names, leaving three unrelated root examples unmatched.
  These checks do not substitute for executing the Linux trust-store update.
- Read-only static configuration checks passed: trust setup precedes Maven
  downloads, both stages use the remediation, both workflows select the version
  file, and application/database source files are unchanged.
- The coding-agent environment cannot access Docker. The user ran the Docker
  checks in their own terminal and supplied these results on October 27, 2026:
  the local image built successfully, the runtime reported Temurin 21.0.12.1+1,
  and a separate keytool check printed `PASS: prohibited Java roots are absent`.
- The user ran the application tests in the prepared Docker builder image with
  networking disabled and an explicit temporary H2 datasource: **749 tests,
  0 failures, 0 errors, 0 skipped; BUILD SUCCESS**, completed at 13:17:50 UTC.
  Initial attempts only failed on missing offline Maven dependencies; no tests ran
  on those attempts. The required provider and launcher were then downloaded in
  separate dependency-preparation builds before the successful offline run.
- The runtime patch number is not a claim that all current Java fixes are installed.
  This image uses the vendor-supported root-removal mitigation for the certificate
  issue. A newer Java patch remains a separate follow-up when its image is available.
- The last observed local Node executable was 24.19.0. A project version file does not
  replace it. With an existing Node version manager, select 22.23.4; for example,
  `nvm install 22.23.4` then `nvm use 22.23.4` if you already use nvm.
- The security review requires isolated test execution. The additional macOS
  sandbox is unavailable here (`sandbox_apply: Operation not permitted`).

## Local build verification

Run these from the portal directory in your own terminal with Docker Desktop
running. These commands build local images and inspect runtime metadata; they
never start the league server or receive production database environment values.

```sh
docker build --pull --no-cache -t leagueportal-security-review .
docker run --rm --network none --entrypoint java leagueportal-security-review -version
docker run --rm --network none --entrypoint dpkg-query leagueportal-security-review -W ca-certificates
docker run --rm --network none --entrypoint keytool leagueportal-security-review -list -cacerts -storepass changeit
```

A successful build must print
`System and Java trust stores updated; prohibited roots are absent.` in both
application stages, and must finish Maven packaging successfully. The image build
keeps the existing `-DskipTests`; packaging success alone is not a test-suite pass.

The following reproduces the dependency preparation and successful offline test
run reported by the user. The preparation builds download Maven test-runner
artifacts without starting the application. The test container has no network,
no host mounts, no forwarded production credentials, and an explicit in-memory
H2 datasource. Its writable filesystem is disposable and removed on exit.

```sh
docker build --target build -t leagueportal-security-tests .
docker build -t leagueportal-security-tests-complete - <<'DOCKER'
FROM leagueportal-security-tests
RUN mvn --batch-mode dependency:get \
    -Dartifact=org.apache.maven.surefire:surefire-junit-platform:3.5.6 \
    && mvn --batch-mode dependency:get \
    -Dartifact=org.junit.platform:junit-platform-launcher:6.0.3
DOCKER
docker run --rm --network none --memory=2g --cpus=2 \
  --entrypoint mvn leagueportal-security-tests-complete \
  -o \
  '-Dspring.datasource.url=jdbc:h2:mem:security-tests;DB_CLOSE_DELAY=-1' \
  -Dspring.datasource.driver-class-name=org.h2.Driver \
  -Dspring.datasource.username=sa \
  -Dspring.datasource.password= \
  '-DargLine=-Xmx512m -javaagent:/root/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar' \
  test
```

If Maven reports another missing offline artifact, prepare it separately with
`dependency:get` in a disposable builder image, then retry with network disabled.
Do not supply production settings or enable network on the test container.

The existing readability workflow triggers on changes to its own YAML file and
can commit formatting automatically. This patch updates that YAML to fix Node;
a future approved push can therefore trigger its existing formatting/commit job.
No workflow was triggered from this local review.

## If a check fails

- **Docker permission/connection error:** start Docker Desktop and run the commands
  from your normal terminal. This is the current blocker for this coding session.
- **Image pull or apt failure:** confirm registry/Ubuntu repository connectivity and
  rebuild with `--pull --no-cache`. Do not remove the minimum certificate-package
  check to permit an older package.
- **`Certificate remediation failed`:** the updated diagnostic names the store and certificate
  that survived. Keep the build blocked and share that non-secret build output for
  a targeted adjustment. Do not disable TLS verification or re-import the root.
- **Node version unavailable:** check the version manager's access to official Node
  releases. Retain the patched project version; do not fall back to an older release.
- **Legitimate TLS connection fails after the update:** the peer may chain to a
  removed root. The remote certificate chain needs correction; do not restore the
  distrusted CA as a workaround.

No production runtime or database was inspected. External Render environment
settings may select a custom Java trust store or add certificates at startup;
those would need separate review. Successful local checks do not establish that
an already-running production image has been patched. Deployment remains a
separate action requiring approval.
