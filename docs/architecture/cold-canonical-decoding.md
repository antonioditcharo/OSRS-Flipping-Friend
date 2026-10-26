# Bounded cold canonical decoding

26 October 2026. PR #182, source branch `codex/bounded-cold-offer-decoding`; not installed. Installed runtime
remains #178, with verified pricing #180 queued separately. Manual acceptance is deferred while
the owner is away. Maintenance 12/replay 28 and every semantic model/receipt version remain.

## Workflow priorities

First reduce cold account-state work that can delay planning and ordered delivery. Then measure
cold open, durable ACK and eligible guidance separately under real player operation, retaining the
existing cancel/late-fill/collect/relist/restart gates. The next pricing increment remains the
explicit evidence/budget/receipt work for supported loss-versus-hold decisions in the
[implementation map](../history/exit-comparison-verification-2026-10-26.md). This decoder grants no
new trade, loss, accounting, recovery or learning authority.

## Mechanism and preservation

The frozen account has 165,183 canonical observations but only 227 distinct serialized accepted
buy plans and 637 distinct top-level execution forecasts. Repeated decoding creates substantial
work and duplicate immutable objects before original acquisition context can be qualified.

`ColdOfferDecoder` is created only for a complete cold `offerHistory` read. It may reuse an
`ExecutionForecast` or `AcceptedBuyPlanProof` only after decoding the complete identical value and
checking that the immutable value is well formed. These types and their nested capture order/exit
context contain only immutable scalar fields. No mutable receipt maps or source events are shared.
Account, source episode, capture ordering and actual-order matching still run in their existing
authorities. Identical value content cannot establish authentication or ownership.

Every `OfferEvent` is separately decoded. IDs, accounts, quantities, money, sequence, occurrence
order, acceptance/progress and rejection checks remain. No SQL rows are filtered, collapsed,
rewritten or newly executed. Historical gaps, original targets/stops, tax uncertainty, acquisition
cost allocation, risk windows and current cash/eight-slot proof retain their rules.

The adapter copies one nested value through Gson's token reader/writer, retaining duplicate member
order and numeric lexemes, then uses the original delegate. A JSON tree would erase earlier
duplicate members and could turn malformed evidence into a valid claim. Strings, unknown values,
lenient retained syntax, nullable fields and numeric coercions retain the existing reader's
behavior. Invalid optional claims are not reused. Serialization and fingerprints do not change.

The combined cache retains at most 128 entries and 256 Ki characters of key text. An individual
entry exceeds eligibility at 8 Ki characters; inputs above 64 Ki characters immediately use the
existing decoder. If a 128-value trial window has fewer than eight hits, the cache clears and the
rest of that read uses the existing streaming reader. This also bounds repeated overhead on
histories containing mostly unique values. These are cache limits, not observation truncation.

The cache dies with the read, including failure. It cannot survive an account change, rollback,
external modification or replacement prefix. Existing canonical-prefix invalidation remains.
Warm append reads take the original path, and no work is added to the UI/game thread or model
workers. No storage migration, financial writer, event ID or prospective model binding changes.

## Verification so far

Six focused tests cover exact decoded fields, distinct repeated observations, original acquisition
context, duplicate fields in order, earlier malformed duplicates, coercions, unknown/nested values,
lenient strings, non-finite/unsupported claims, large inputs, unique-value fallback and combined
retention bounds. Four canonical-prefix tests retain new tails, external corruption/deletion,
rollback and the separation of financial ACKs from historical rebuilding.

A Java 11 read-only pass compares all 165,183 frozen source records with the existing Gson reader:
every serialized field and occurrence order matches. The frozen database hash is unchanged.
The research artifact is `4e533423f00af925ab05b21e48756bfbab33ef4c3f12640870d2929724345ced`.

| Alternating JVM pair | #180 cold ledger/planning/exposure (ms) | New decoder (ms) |
|---|---:|---:|
| 1 | 7,974 | 5,728 |
| 2 | 5,871 | 4,641 |
| 3 | 5,055 | 4,394 |

The median reduction is 20.96%; all three pairs improve. Warm medians are 21.63/21.36 ms, essentially
unchanged. This is a small component experiment on one frozen account with capped Java 11 JVMs;
it excludes HTTP, durable delivery, current frames and rendering. It does not establish live p95,
instant guidance, multi-user capacity or final resolution of the account-verification wait.

The earlier per-forecast hash cache was rejected for inconsistent timing; it is absent. This pass
reduces repeated value decoding and allocation without changing the fingerprint implementation.

The standard clean build passes 2,419 Java tests (six existing skips) and 121 tooling tests.
Final exact-head Windows/Linux CI, source/artifact hashes, old/new ledger/stock/scope/continuation/
exposure/risk equality and frozen replay reproduction are tracked in PR #182 and private
`verification-receipt.json`. Keep paired backup/rollback and player acceptance gates visible.
Private inputs and receipts are in primary `local-data/cold-offer-decoding-20261026`.
