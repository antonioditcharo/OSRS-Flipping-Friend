# Panel continuity and prepared historical inputs

26 October 2026. Trading authority, financial identities and storage formats are unchanged.

## Delivery and rendering

An editor can close without an offer callback when the player abandons it. Observe that boundary
on the client tick as well as GE widget closure. Apply still-valid deferred guidance, preserve
its original lifetime, and request a fresh current-state calculation immediately. An observation
that supersedes a running plan also explicitly schedules its successor. Log whether a proposal
is visible, deferred by the editor, paused or replaced, rather than calling every proposal delivered.
This is diagnostic evidence, not proof that the player read or followed an action.

Coalesce queued Swing refreshes; each paint reads the latest guide state. Do not coalesce or omit
financial observations. Unchanged instruction text and selected cadence/risk controls are not
rewritten. Cache the two pause/play resources per panel, and reject delayed item-image callbacks
after the displayed item changes. Preserve real price, quantity, forecast and control changes.

Selected-sale quotes refresh after two seconds while the same valid quote remains visible. A
waiting result retries after one second. The existing five-second cache and absolute authority
expiry remain ceilings, with one verification in flight. A changed account/editor/quantity or
actual refusal immediately withdraws the price. Background refresh cannot renew an old receipt.

## Prepared source inputs

`TradeGapEvidence.Index` retains only causal scope derived from the complete validated immutable
canonical prefix. Warm reads process appended rows. Changed accounts, external edits, rollback,
replacement/shrinking prefixes or changed/removed gaps reset it. A newly discovered gap on an
already processed source also rebuilds: an earlier rejected EMPTY must not have cleared its
episode. Invalid/foreign/duplicate sources still make scope unknown. Scope never prices exposure
or authorizes entry. Results cannot mutate the retained map or item sets.

`SalePolicyReceiptStore.InputIndex` prepares receipt/origin/binding identities from the same
prefix. It caches no receipt authority. Each sale transaction still checks retained issuance,
the exact original source and settings hash, eight-slot read set and durable binding order.
Indexed origin/binding lookups cannot read past that transaction's validated source cursor.
Optional preparation failure affects sale qualification; it cannot break unrelated canonical
loading. Current cash, slots, holdings, market evidence, exact cost/tax and risk remain fresh.

Background GE-history support explicitly uses `event_log_invalid_source_json`. On a frozen
183,995-event account, SQLite otherwise chose the larger identity index: the no-invalid-row
check took 25,335 ms versus 0.254 ms with the partial index, with identical results. These are
single component observations, not live latency percentiles. Supported stores create the index
before registration. Missing indexes refuse optional support; they do not reinstate the full scan
or clear any uncertainty. No source or financial row is modified by these changes.

## Verification and remaining acceptance

Regression coverage includes tail-only and evolving-gap parity, rejected clears, invalid sources,
fresh receipt refusal after preparation, source cursor fences, editor abandonment and expiry,
superseded plans, selected-sale background refresh/expiry, latest-state paint bursts, unchanged
text/control rendering and late icons. A native SQL work-budget test includes 15,000 valid
nonterminal source IDs and reproduces the previous query's scan.

Require the standard clean Java/tooling checks and both exact-head CI platforms, old/new frozen
financial/risk comparisons and supported paired backup/rollback checks. Source tests do not prove
live guidance timing. Fresh visible guidance, sustained offer/sale/account latency and a manual
cancel/late-fill/collect/relist/restart loop remain separately required after installation.
