# Agent handoff

Updated 11 November 2026 (America/Anchorage). Read AGENTS.md and TASKS.md.
Default branch: `CoPilot's-Rebuild-from-Main`; `main` is historical.

## Installed runtime

RuneLite **client 1.13.6** / compatible local pair **#210 are installed** at
`2026-11-12T01:35:21.0260978Z` (11 November local). Verified source
`312bdb79464f98793abd36c5b13cad314d7ee4ef`, merge `f22762f2666a42cc9336e6cdd3dd1469b678037d`.
Installed SHA-256:

- Plugin: `099b8655acb2b435bbe6dce1c469b8179d2d496e05fb3ed24ed6c48e4a1ef192`.
- Companion: `7fd5dfa5d73f930139893106c77afab1c2e163a58b52458f29a095bc0de6b319`.

Clean 2,581 Java passes/six existing skips, 202 full tooling checks and 120 actual paired-JAR
Java 11 checks pass; own bytecode is 55. Both exact-source Windows/Linux CI **43134523417** pass.
Earlier dispatches **43096557239 / 43097432645** failed inside GitHub startup with zero jobs/tests;
queued/failed receipts are retained. No source test failure was excluded to obtain the release.

Only CompanionMain and two new diagnostic logger classes differ from #208; every other own
plugin/companion class is identical. Synthetic smoke passes 181.687s, six groups, 546 checks,
477 fixture requests, zero failures, both completion markers/all 1,306 frozen hashes. Its source
manifest retains d037d38; subsequent changes are tooling/tests, with identical production JARs.
This is not a fresh hour or native/trading acceptance. Pricing, risk, accounting and learning stay unchanged.

Supported unattended paired update passes; task XML/ACL and heap policy are unchanged. Fresh backup
**20261111-163510-593016db** restores all **565 files**, with six supplemental configuration restores.
All **12 original financial/source tables** match the fresh restored copy. Unassigned legacy bytes and
old diagnostic log prefixes are preserved. Original backup databases were never opened in SQLite;
only the restored copy and live account were read. Database/WAL hashes remain strict; explicitly
scoped SHM indexes are derived cache. Status, official bootstrap/API/hash CheckOnly and authenticated
GET-only pipeline pass. Pre-login WAIT still requires current cash/slots; 41 unresolved sales and
seven profit ranges remain. No financial replay, synthetic source or risk release.

Primary private receipts: `local-data/storage-retention-20261111/verification-receipt.json` and
supporting logs/proofs. Authoritative index: `local-data/hosted-pilot/delivery-state.json`,
**currentDelivery**. #195 remains code rollback, #186 economic reference; #203/#206/#208 artifacts
and full updater backups are retained. Code rollback never restores old financial data.

## Active storage policy

See [development storage](docs/development-storage.md). Companion stdout/stderr and supported client
console output each keep 8 MiB plus four archives: **120 MiB total**. Unknown files/links are refused;
post-start logging failures discard further diagnostics with one warning, without stopping the app.
The updater preserves former companion.log/.err and uses separate per-launch bootstrap logs.

Normal Start enables one hidden, non-elevated hourly workspace worker when Python is available;
Status reads its latest report and flags errors/staleness. Newly verified disposable restorations
expire after 24h only with unchanged financial/source bytes, an intact VERIFIED original backup,
permanent external receipt and exact registered files/directories. Modified/extra files or directories
pin the copy. Only the named account SHM index is derived cache; databases/WALs are never cache.
Interrupted expiration resumes conservatively. Updater backups and unknown historical folders never expire.

Optional restore/soak work is admitted within 5 GiB scratch / 50 GiB allocated local-data and a
10 GiB free reserve. Soak captures check a 1 GiB budget at 30-second samples and retain failed raw
output. Sealed evidence is only compressed in place with matching SHA-256, at most 2 GiB/eight files
per pass after 24h. Required data can grow legitimately: alerts/refusal of optional work replace deletion.
Existing 703-file compression savings **26,887,397,867 bytes (25.04 GiB)** remain; old failed broad
worktree-build deletion was not retried. Three owner document deletions remain untouched.

## Remaining acceptance and direction

All 81 original requirements remain in [TASKS](TASKS.md) and the
[acceptance inventory](docs/verification/trading-pass-acceptance-inventory.md); 79 remain open,
including one partial. Older-version native/player checklists carry forward to #210.

- Fresh manual login/GE, visible guidance, idle/mixed/sustained MAIN/editor timing and controlled
  revision/cancel/collect/relist/partial-fill/restart remain open. Keep cached paint p95 <=100ms
  and distinct eligible publication p95 <=1s after durable ACK as separate gates.
- Retain #203 MAIN paint 148.129ms, one eligible 900.258ms decision, editor paint 52.163ms/zero
  eligible editor decisions, initial COLLECT 4.977s and 690ms preparation, plus older delays,
  #198 outage/root cause, missing source identities and unobserved live late fills.
- #199/#201 pricing remains shadow; chronological 100M/member/eight-slot **5m/30m/2h/8h** qualification
  and neutral observational learning gates remain open. No profit-superiority claim.
- Hosted #194, disabled recommendations/coordination and $50/month ceiling are unchanged. Prior
  fixed stop **2026-11-08T01:31:16Z** remains; actual hosted stop/completion was not audited here.
- Separate launcher 2.8.0 installer remains manual after its earlier execution-policy rejection;
  do not retry it through another mechanism. Login and all game actions remain manual.

Previous installed #208 and retained detailed failures remain in the
[previous handoff](docs/history/handoff-before-bounded-storage-2026-11-11.md) and linked history.
