# Report and replication work during offer delivery

26 October 2026. Follow-up to installed #176. Source, CI, installation and live timing are separate
gates. This increment changes no trading prices, loss authority, financial identities or risk policy.

## Live evidence

With build work stopped, five-second offer-delivery and performance HTTP timeouts still appeared.
A thread capture found `CompanionService.performance` owning the service monitor while
`SqliteStore.performanceInput` classified trade-review history; `offer` and planning waited on that
same service monitor. A 120-second JFR sampled `TradeReviewRead.classify` prominently, recorded a
10.4-second performance-monitor wait and a 1.87-second offer-monitor wait. These waits are not
measurements of exclusive method execution time. Replication also used a source ownership lookup
without the predicate needed by the retained partial event-ID index.

## Bounded review evidence

Trade-review classification is display metadata, not ownership, accounting, risk or permission.
Its new index advances only when SqliteStore validates an exact immutable canonical append.
It tracks eight predecessor slots and only sources/episodes needed by unresolved review rows.
One validated account match replaces repeated identical account-alphabet regex work; every source
still passes field and exact-account checks. No missing execution or acquisition is fabricated.

Changed financial cursors, live holdings and annotations re-evaluate the small display-row set
without rescanning old observations. Exact row-field changes, rejected-source changes, replacement
history, clock reversal, future observations, external writes and rollback retain conservative
requalification. Removing the whole-snapshot shortcut also lets a future closure become eligible
when its recorded time arrives without requiring another financial effect. Original archive
annotations, source rows, money and risk remain unchanged.

Tail-only list tests fail if cursor changes or validated appends reread the historical prefix.
Other regressions cover purchase evidence, live holdings, rejection, foreign accounts, future
closure, clock reversal, archival/restoration and restart. Invalid evidence grants no archive or
trading permission.

## Indexed replication

Replication's ownership lookup includes `event_id IS NOT NULL AND event_id<>''`, matching the
existing source-identity index. A native SQL instruction budget passes repeated found/missing
production lookups and reproduces the old full scan as an interruption on a disposable fixture.
Blank legacy IDs stay unassigned. Authorization, original event IDs, namespace envelopes,
independent remote checkpoints and pending local financial delivery are unchanged. No new schema,
event replay or server provisioning is required.

## Frozen comparison and release gate

A read-only frozen account has 162,939 canonical observations and 41 review rows. Ten warm
classification cycles simulating report-cursor changes averaged 90.605 ms before and 0.931 ms with
the index. Classification/archive hashes match exactly. Parsing/bootstrap are outside this
component measurement; no financial events or effects were added. It is not live end-to-end
latency or profit evidence. Private receipts: `local-data/hosted-pilot/report-read-{old,new}.json`
in the primary workspace.

The final isolated #177 artifact reproduces the same hash and averages 0.721 ms over ten warm
component cycles. Clean Java passes 2,392 tests with six existing skips; 121 tooling tests,
Java 11 bytecode and exact-head Windows/Linux CI run `40351702015` pass for source
`ee3d9d3026e1b90b75b1b0af73d74d2763fb6604`. Merge
`6dd0e15a7a5f19b97fe33e0921d6851bb78e782e` has the identical tree. Frozen old/new ledger,
stock, scope, continuation, exposure and original risk-window reads match. These source/component
checks do not replace a fresh installation backup, rollback verification or live acceptance.

Reanalysis of the earlier three-minute baseline using one client log counts seven separately
logged slow failed requests: four offer deliveries and three performance reads. Fast requests
under 750 ms are not logged, so the counts are not an attempt denominator or failure rate.
Keep actionable refreshes separate from WAIT delivery and record missing/superseded outcomes as
unmeasured. Private diagnostic: `report-baseline-guidance-with-failures.json`.

Require clean Java/tooling, exact-head Windows/Linux CI, backup/rollback reads, fresh durable
eight-slot evidence and observed guidance before acceptance. Measure request failures as well as
successful delivery times: a timeout may produce no delivered-duration log. The instant-guidance
goal and explicit budgeted market-informed loss policy remain separate unchecked requirements.
The owner confirmed concurrent chart work in the primary checkout. This follow-up is isolated in
a managed worktree; final runtime installation must preserve the combined verified changes.
