# Cold ledger profile and rejected hash cache

26 October 2026. Runtime remains installed #178. Pricing PR #180 is merged, verified and queued;
see [its receipt](exit-comparison-verification-2026-10-26.md). This checkpoint changes no runtime
code. The owner was away and authorized offline development; manual acceptance remains deferred.

## Reproduced component work

The final #180 Windows/Linux CI passes at `a0e5d2d0cc2a12ec15bc184a3d4eca5988553e7e`,
run `40376426920`. An earlier Windows run failed the existing three-second cold-account API test.
Its complete log is retained. No test timeout or production guard was relaxed. Ten separate
synthetic cold opens on this PC all returned 200/WAIT with the same timeout: 44 ms median,
119 ms maximum. Authentication/account-scope refusals remained correct. These empty disposable
accounts do not reproduce the CI failure or establish live startup/guidance performance.

A separate Java 11 probe reads only the frozen #178 disposable restored account, checking ledger,
planning projections and bounded exposure. An initial first read took 8,202 ms total; eleven later
reads had a 20 ms median and 37 ms maximum. The database's before/after hash is unchanged, and the
financial shape is consistent. These measurements exclude HTTP, durable delivery/ACKs and UI.

A read-only JFR retained 145 execution samples: 125 include `automaticTradeScope`, 69 have
`offerHistory` JSON decoding as their nearest own-code frame, and 19 include original acquisition
proof comparisons in `AcquisitionExitEvidence.read`. Inclusive counts overlap. This identifies
work to investigate; sampling does not establish each operation's exact elapsed-time share.

## Rejected candidate

`ExecutionForecast` has immutable scalar fields. A proposed transient per-instance fingerprint
cache retained the canonical hash algorithm and wire format. Four focused tests passed, including
the existing golden identity, unchanged serialization, ignored forged cache input and concurrent
readers. No new policy, financial event, learning sample or risk anchor was introduced.

The first timing run accidentally copied the primary checkout's old #178 build rather than the
modified worktree artifact. That run is explicitly invalidated and retained. Corrected runs
verified the cache class marker and exact candidate hash before three alternating old/new JVM
pairs against the same read-only account. Candidate SHA-256:
`037b7d4de8db0d035b948831b223ed2472151623a413063239f3e246ec92c734`.

| Pair | Verified #180 cold total (ms) | Candidate cold total (ms) |
|---|---:|---:|
| 1 | 6,707 | 5,608 |
| 2 | 5,311 | 5,058 |
| 3 | 5,138 | 5,633 |

Cold medians were 5,311/5,608 ms. Warm medians were 22.9/22.5 ms. The small sample has inconsistent
directions and cannot establish a reliable improvement. The candidate was rejected; its source
and test changes were restored to #180. Its artifact must not be installed or mistaken for a
release. The rejected patch, invalid/corrected measurements and profile remain in primary
`local-data/forecast-read-cache-20261026` and `local-data/exit-comparison-20261026`.

## Next latency increment

Investigate the cold complete source-prefix decoding before adding another cache. Preserve every
canonical occurrence and exact earliest-source qualification; repeated equal-looking records,
changed proofs and source corruption must remain distinguishable. A bounded derived read or
checkpoint must bind the exact account/source/effect prefix, conservative scope and original plan,
invalidate on external edits, replacement history and rollback, and remain old-runtime readable.
Never omit evidence or clear risk merely to make a cold read look ready.

Measure cold account-open, durable acceptance and post-ACK eligible guidance separately. Preserve
the existing warm path and independently valid guidance while optional reports/recovery run.
Continue the player's sale-maintenance/relist/restart and sustained timing checks when they return.
The [budgeted market-loss implementation map](exit-comparison-verification-2026-10-26.md) is also
still open; this profile grants no new exit or learning authority.
