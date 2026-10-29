# Frozen execution evaluation

15 October 2026. This is an explicit offline capture, verification and comparison workflow. It
does not fit or promote a challenger, measure net account returns, or change game recommendations.

17 October continuation: the separate [account replay and challenger commands](parity-account-evaluation.md)
now build on these verified frozen prefixes. This evaluator's original freeze/verify/compare
contract and hard runtime promotion fence remain; an offline fit is not model approval.

## Commands and private artifacts

Build the standard tested companion JAR, then run from the repository:

```powershell
.\tools\evaluate-executions.ps1 -Mode Freeze -AccountId rs-<the-exact-64-character-account-hash>
.\tools\evaluate-executions.ps1 -Mode Verify -Capture <capture-directory>
.\tools\evaluate-executions.ps1 -Mode Compare -Capture <earlier-directory> -Validation <later-directory>
```

Account identity is explicit. Freeze accepts only an existing accounts/<account>/flipping-friend.db
whose canonical parent matches that identity. It rejects the old unassigned companion/flipping-friend.db.
The wrapper defaults to the companion's scoped accounts directory, with an explicit Database override
for fixtures. It does not install JARs, start a companion, ask for credentials, close RuneLite or send
game input. Linux can invoke the same ExecutionEvaluationMain class through the built companion
JAR with arguments freeze, verify or compare; the command prints its exact usage on missing arguments.

New exports default to version two (28 October 2026). They stream one complete read-only SQLite
snapshot into ordered offers-NNNNNN.jsonl and gaps-NNNNNN.jsonl chunks, plus evaluation.json and
manifest.json. A chunk holds at most 4 MiB or 1,024 rows. One shared eight-chunk cache serves
both source types. Temporary SQLite indexes hold source identities, reconciliation lineage and
episode state; they are removed on completion/failure. Raw payload strings remain exact.

The 5-GiB bundle budget and 768-MiB free-space reserve fail closed without publishing a manifest.
The helper wrappers use a 1-GiB maximum Java heap. Manifest publication is atomic and last; its
ordered references bind each chunk's ordinal, size, count, first/last cursor and SHA-256, alongside
account, capture clock, both complete cursors, counts, policy/evaluator version and artifact hash.
Missing, reordered, duplicate, corrupted or unlisted files fail verification. Validation streams
chunks and recomputes the original observation/censoring/score mathematics. No financial writes,
repairs, new samples or model promotion are authorized by this envelope change.

Version-one readers and fixture bundles remain supported unchanged: sources.json, evaluation.json
and manifest.json retain their original 256-MiB per-file boundary. Their observations and scores
must equal V2 for the same sources. V2 removes the monolithic serialization that exhausted the
1-GiB heap on the 185,450-row account; the original failure remains in the delivery receipt.

Packaged captures retain the exact executing artifact as evaluator.jar.bin, bound by its hash.
It can reproduce the offline command later with `java -cp <capture>/evaluator.jar.bin com.flippingfriend.companion.ExecutionEvaluationMain verify <capture>`.
Creation never overwrites an existing capture; the manifest is written last. Incomplete directories
are rejected. For version one the explicit per-file read/write limit is 256 MiB; exceeding it fails without silently
truncating a source prefix. The compact HTTP learning/current endpoint remains a bounded diagnostic
tail, not the input to this evaluator. Full exports retain all qualifying episodes, including over 200.

The manifest binds account, capture second, canonical offer cursor, row counts, relevant reconciliation
rows, file hashes, evaluator version and original public-price model version. It records the caller's
declared source revision and, for the packaged command, SHA-256 of the actual executing JAR. Unit-test
class directories are explicitly UNPACKAGED_CLASSES, with no invented JAR identity. The revision is
not independently authenticated. Hashes detect inconsistent bytes and support external anchoring;
they are not signatures or proof that a document existed at its claimed historical timestamp.

Verify needs only the bundle, rechecks hashes/schema/account/order and recomputes the evaluation from
raw frozen rows. Changing an output and rehashing it does not bypass recomputation. Future evaluator
semantics must receive a new version; retain the original artifact for reproducibility. Manifest
identities are the hash of manifest bytes, not a mutable directory name. Personal bundles and raw
source IDs stay ignored in local-data; public receipts contain aggregate outcomes only.

## Read-only source boundaries

SqliteStore.openReadOnly uses an encoded file URI with mode=ro, requires an existing file and runs no
schema initializer. Regression 3b71dec reproduces the prior literal-hash path truncation before its
fix. Disposable fixtures prove missing files are not created and writes are rejected.

One read transaction copies canonical event_log rows in recorded id order and relevant RECONCILE
position_event rows, including source item IDs. The capture time is recorded after the reads. A concurrent WAL commit cannot
mix a newer reconciliation prefix with an older offer prefix. This follows SQLite's
[snapshot isolation](https://www.sqlite.org/isolation.html) and
[read-only URI mode](https://www.sqlite.org/uri.html). No migration, checkpoint, repair, retention,
counted-offer claim or accounting write runs through the capture path.

Column identity/time/type must agree with the raw event. Account scope, durable ordering, original
prediction binding and all existing timing qualifications remain. Reconciliation scope comes from
TradeGapEvidence and recorded canonical source events in the requested account. An unnamed clear
can retain its proven item context; all affected items remain excluded and unrelated forecasts can
remain evaluable. Regression 7115219 reproduces the prior global refusal. Missing or conflicting
source references and unknown causal scope still hold evaluation. An unsupported named gap cannot
be ignored; regression 25f5bec reproduces that omission. Legacy missing forecasts remain missing; no historical row
becomes a newly learned sample. Failure is reported, never repaired in the live database.

## Horizon outcomes and conservative score bounds

For each qualified original leg forecast, use its actual recorded horizon H and full-completion
probability p. Let [L,U] be the conservative completion-duration bounds. A completion with U <= H
is known complete by H. A confirmed non-complete active capture yielding L > H proves incomplete
at H, even if the offer later completes or is canceled. Equality at L is retained as ambiguous.
All other cases are unknown at H, including early cancellations, unknown placement and completion
intervals crossing H. First-unit partial fills do not prove full completion.

The binary Brier loss is (p-y)^2. A known y contributes that exact loss. An unknown y contributes
the interval [min(p^2,(1-p)^2), max(p^2,(1-p)^2)]. Group means retain every eligible unknown in their
denominator. Completion-rate bounds are [known-complete / n, (known-complete + unknown) / n].
No cancellation is silently counted as failure, dropped from the denominator or assigned a fill
time. Reports group buy/sell and exact horizon seconds separately and retain each original forecast
fingerprint. There is no invented checking-cadence label or homogeneous model-configuration claim.

These are direct worst/best-case bounds, not an unbiased censor-adjusted estimator or confidence
interval. Cancellation can depend on advice and market conditions; an independent-censoring model
has not been established. See the [NIST readout/censoring distinction](https://www.itl.nist.gov/div898/handbook/apr/section1/apr131.htm)
and [Kvamme/Borgan's Brier-score caveats](https://www.jmlr.org/papers/v24/19-1030.html). Those references
do not validate an OSRS fill model. Wide bounds are an honest lack of identification, not evidence
that a high-confidence forecast worked.

## Recorded-prefix comparison and remaining gates

Comparison requires the same account, strictly later capture/cursor and byte-equivalent prior offer
and reconciliation rows. Validation uses only offers first recorded beyond the training cursor,
with original advice and first observation strictly after the earlier capture second plus its
one-second uncertainty. Older offers settling later and late arrivals of older advice are excluded.
Training labels come only from the earlier immutable capture, not later outcomes. Same-second,
reversed, foreign or changed prefixes fail. This is recorded-prefix chronology, not proof of exact
server execution timing or historical knowledge reconstructed from observed_at alone.

The baseline is the set of original model claims, not refitted retrospective predictions. No fitted
training artifact, challenger prediction set or account return simulation is created here. All
outputs retain HOLD_FOR_VALIDATION. Source snapshots alone do not qualify a model for promotion.
Issue #56 still requires supported censor-aware fitting, frozen challenger/training artifacts,
held-out calibration and full-account return/loss gates, realistic manual attendance, tax, limits,
partial fills and ending inventory, with rollback. Report 100M members/eight-slot scenarios separately
for 5m, 30m, 2h and 8h. Historical CSVs still lack session/cadence labels; superior GP/hour is unmeasured.

See [forecast identity](immutable-execution-forecasts.md), [observation intervals](execution-observation-intervals.md)
and [HANDOFF.md](../../HANDOFF.md) for actual source, build, CI, capture and installation receipts.
