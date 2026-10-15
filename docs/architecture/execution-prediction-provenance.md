# Prospective execution prediction provenance

15 October 2026. This repairs which prediction accompanies an observed offer. It does not
establish player intent, an exact placement/fill clock, unbiased calibration or higher returns.
Cancellation censoring and chronological promotion gates remain open in
[issue #56](https://github.com/antonioditcharo/OSRS-Flipping-Friend/issues/56).

## Faults reproduced

The publisher previously looked up the latest item/side advice on every outgoing event. A terminal
event could acquire advice published after the offer started, replace the original prediction or
lose it on expiry/restart. A prediction for a different price or quantity could enter the timing
ratio. Refreshing identical order inputs retained the first display timestamp while replacing the
prediction, allowing a newer forecast to borrow an older clock. The companion trusted terminal
claims without checking their original recorded source. Tests reproduce these failures before fixes.

## Client binding and delivery

The existing account-scoped offer-event outbox now saves up to eight slot bindings together with
the outgoing event, in its existing file replacement. There is no additional holdings database or
separate attribution file. A failed write cannot consume a sequence or change a saved binding.
Acknowledgement removes the pending event and retains its offer binding across restart.

Only an authenticated first observation of an open, zero-fill offer can begin a comparable binding.
Its first-seen second must equal that observation, and the prediction must already have been
available within the attribution window. Comparable evidence requires the forecast's recorded
second to strictly precede the first observed offer second. A same-second association can remain
diagnostic for maintenance, but cannot establish that the forecast came first or train timing.
The timestamp of the current forecast is distinct from
the first display of identical order inputs. Matching requires actual price and quantity to equal
the suggested inputs and finite valid prediction values. Divergence retains diagnostic advice
metadata but contributes no comparable prediction. An initially unknown offer stays unknown;
later advice cannot adopt it. Once inputs change, comparability stays invalidated for that episode.
Only authenticated EMPTY releases a slot binding. Account/session/offer identity remains fenced.

Legacy pending records keep their original delivery identities and claims. If no binding section
exists, pending episodes get negative bindings rather than reconstructed original forecasts.
The pre-existing account-scope upgrade is retained; it is not an upgrade of prediction evidence.
Invalid saved identity, account or schema fails closed without overwriting the queue. No live
account file is manually repaired by this work.

These conditions are conservative observational association. Even strictly ordered timestamps and
matching inputs do not prove that the player saw or followed the recommendation, or that first
observation was placement. No automatic game input is used to strengthen the association.

## Companion authority

New offer JSON carries optional prediction-evidence version, original source-event ID, forecast
availability timestamp and comparability flag. Missing legacy metadata does not manufacture a
source. Old events and existing aggregate rows are not rewritten or replayed.

Within the existing canonical acceptance transaction, the companion validates the earliest recorded
source for the account/session/slot/offer episode. It checks the authenticated zero-fill start,
ordered identity, original self-reference, actual versus suggested inputs and forecast timing.
A same-second later heartbeat cannot declare itself the original source. Intervening mismatches or
invalidations cannot restore qualification. The original source prediction is used even when the
terminal payload carries newer advice. The raw terminal JSON is preserved unchanged.

Missing, unsupported or inconsistent optional evidence supplies no paired prediction; real fills,
costs, limits and unknown money still follow the existing accounting rules. Economic contradictions
retain their existing reconciliation exclusions. Source acceptance, accounting, the legacy counted
offer key and statistics still commit or roll back together. Existing claims prevent recounting
after restart or a new terminal heartbeat; no key change or historical backfill occurs.

## Limits and next gate

Existing aggregate statistics remain historical data with their original limitations. Completion
observation may follow an offline interval, cancellation is a censored observation, and the existing
timing ratio does not become a validated waiting-time model through this change. No strategy weight,
calibration formula or model-promotion rule is changed here. Source intervals, censor-aware scoring,
forecast decomposition and frozen-baseline chronological evaluation remain necessary before
learning promotion. Simulated price touches stay separate from real execution evidence.

The contract remains 100M GP, members, eight GE slots and separate 5m/30m/2h/8h attendance scenarios,
including losses, capital limits, remaining inventory and net tax-aware returns. Historical exports
do not supply session clocks or cadence labels.

## Verification

Behavioral tests use fixture account files and disposable SQLite stores, with no live writes,
network requests or game input in the new provenance tests. Coverage includes advice replacement,
late/future/expired advice, manual input mismatch, negative bindings, acknowledgements and restart,
failed file replacement, legacy/scoped queues, invalid schemas, account isolation, original-source
validation, rollback/retry and unchanged financial effects. Standard verification and source/runtime
boundaries are recorded in [HANDOFF.md](../../HANDOFF.md).
