# Exact public inputs: #195 delivery

Local plugin and companion #195 are installed. Tested source
`44fb16bbb1389d973f8b3b0b721a817629cc266d`, merge
`2d63c2e8e44eaa7a8b302ffef46ea889315daeb7`; both exact-head CI platforms
`40773463909` pass. Clean Windows verification: 2,539 Java tests, six existing skips,
144 tooling, own bytecode 55. Exact-artifact Java 11 passes 73 core/companion and seven
client compatibility/orchestration checks. Source between the clean Java build and release
head changes documentation encoding only. The first CI run failed that encoding check;
original UTF-8 archive bytes and current notes were corrected before the successful run.

Installed 28 October 2026 at 21:53:12 UTC:

- Plugin SHA-256 `34627219524ecd1bde2494f2effc6e5525434e6eae1ad0d7a65759732ccf8a79`.
- Companion SHA-256 `49d921a247cdd3166b555b460702c46b472d407ca82219185d1869b7838933f9`.
- Verified backup `20261028-135259-030281fa`: disposable restoration checks 290 files and one
  explicitly scoped account database. Six supplemental configuration copies restore exactly.
  Every original row/projection in all 12 financial tables agrees with the running installation.
  Previous #190 and new runtimes read the fresh backup's ledger, stock, exposure, scope,
  continuations and original risk window identically. An earlier frozen account also agrees,
  with its original bytes unchanged. No source replay, cost invention or risk reset occurs.

The original #190 listener timed out on both global and scoped three-second health probes.
A thread-dump request did not complete. The first update attempt stopped before replacing
artifacts after Windows denied a native process handle; subsequent checks found that listener
gone. Original JAR/manifest hashes remained intact. Its partial artifact-only backup
`20261028-135114-29febe52` is retained as incomplete, not a verified account backup. A normal
supported retry creates the verified backup above and installs successfully. Task XML/ACLs
remain unchanged. The exit race is handled separately in a tooling follow-up, retaining
refusal for every live/reused PID and unidentified listener.

The new companion responds normally. First reads after approximately one minute of background
preparation: global health 19.86ms, scoped health 29.50ms, action 30.93ms, ledger 97.83ms;
warm scoped health 3.57ms. These are first observed post-preparation reads, not cold-start,
durable-frame, eligible-action or native-render measurements. A 90-second observational JFR
retains 109 execution samples and 266 thread-park events; no player trade occurs in this profile.

The launcher verifies official RuneLite 1.13.4, cached artifacts and the installed API. RuneLite
reopens normally; manual login/GE acceptance remains pending. The owner is away and will verify
at 19:00 local on 28 October; no further login request before then. Initial global market transport
passes. Existing projection warnings remain historical uncertainty, not repaired accounting.

The historical #190 trace audit retains 811 outcome records. Its 13 SELL-to-WAIT records comprise
seven selected-editor overrides, five deferred-editor proposals retaining pending SELL, and one
selected-sale supersession followed by working maintenance in later log lines. The earlier three
old records still lack enough trace detail for definitive attribution. One hundred linked main
action post-ACK measurements have p50 580ms, p95 4,656ms and maximum 12,021ms. Repeated valid
working actions may be represented more than once. This does not pass the one-second target.
Native render and selected-sale post-ACK latency remain unmeasured; missing traces cannot prove
a dropped action. [Repeatable audit](../verification/guidance-delivery-audit.md).

An isolated mixed-workflow soak started at 21:59:40 UTC against frozen #195 Java 11 artifacts.
It FAILED after 3,220.484 seconds (53.7 minutes), after 108 group runs; the final group has
46 passing checks and one SocketException during fixture connection setup. The error was
"No buffer space available (maximum connections reached?)". This does not pass the hour gate.
The full frozen export also FAILED at the 1-GiB heap: 185,450 rows contain 276,793,276 raw
payload bytes, beyond the old 256-MiB cap even before JSON wrapping. No verified output bundle
was published. [Repair contract](../verification/offline-export-and-soak-repair.md).
It cannot replace player-operated sale revision, partial/late fills, cancel/collect/relist,
restart or native chart/timing acceptance. The hosted service remains #194 and the fixed public
capture continues; no hosting responsibility or paid capacity expands in this local release.
Pricing challengers, market-loss authority and learning remain unpromoted.

Private receipts: primary `local-data/optimization-pricing-20261026/exact-market-*`,
`local-data/hosted-pilot/exact-market-backup-proof/restored`, and the explicit `currentDelivery`
section in `local-data/hosted-pilot/delivery-state.json`. Older top-level delivery aliases are
historical. [Input contract](../architecture/exact-public-market-inputs.md).
