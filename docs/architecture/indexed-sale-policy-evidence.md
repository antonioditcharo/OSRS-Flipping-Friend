# Indexed sale-policy evidence

23 October 2026. Actual verification, merge and installation are recorded in
`local-data/indexed-sale-evidence-20261022/delivery-state.json`.

PR #148 is installed. Fresh logged-in observations subsequently drained the snapshotted outbox
without changing its event payloads, and BUY guidance appeared. Intermittent slow maintenance
remained: a JVM dump showed receipt verification holding trading monitors while looking up
event IDs. SQLite's existing nonblank event-ID partial index was unavailable to those queries
because they omitted its predicates. Repeated propagated proofs amplified the scans.

Receipt verification uses the existing unique event-ID index through its explicit nonblank
predicates. Each immutable first-binding source is looked up once per evidence read, rather than
once per propagated observation. A narrow account-correlation index supports both exact pinned
snapshot selection and duplicate detection. These are ordinary additive runtime indexes; original
source payloads, financial rows, evidence identities and loss anchors remain unchanged.

Canonical issuance, raw account hashes, exact eight-slot read sets, first-binding ordering,
duplicate ambiguity, counter continuity, cancellation/collection and current price/cost/stop
checks retain their existing authority. Missing or malformed evidence still refuses. Sale policy
9, replay 18, evaluator 5 and CURRENT_ACCOUNT_V3 are unchanged.

Active-sales investigation details show copied market-side prices and independent trade ages,
alongside the standing offer's material-change threshold. They explain rejected or stale guidance;
they do not relax sale authority or promise execution. Missing prices remain unknown.

Verification includes actual SQLite query plans and binding/propagation behavior, alongside the
existing canonical receipt and client-to-ledger workflow regressions. Fresh post-update response,
outbox and guidance checks are separate from controlled manual trading, sustained latency and
the remaining release gates in TASKS.md.
