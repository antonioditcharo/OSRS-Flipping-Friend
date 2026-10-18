# Parity increment delivery — 17 October 2026

PR [#83](https://github.com/antonioditcharo/OSRS-Flipping-Friend/pull/83), final source
e411953684017d04bbd2763f5d21798617ce9ea6, merged at
b04a8d189f9d1f6f5914558739c1d72d04ef8a3b. The primary checkout was fast-forwarded cleanly.
Historical main is unchanged. Read [the implementation contract](../architecture/parity-account-evaluation.md)
for active paths, incomplete production replay, model-release gates and player acceptance steps.

## Verified and installed

Standard Windows clean build: 53 seconds / 18 tasks / exit 0; 1,590 Java passed, six existing
skips, zero failures/errors. Python: 103 passed in 93.696 seconds. Both exact final-head CI jobs
passed in run 39091255683: Ubuntu 117568304358 and Windows 117568304211.

| Installed artifact | Bytes | Project classes | SHA-256 |
| --- | ---: | ---: | --- |
| Plugin | 511,340 | 228 | `4f1d8fe3ac0dddd2821703cf3592ac00fa78ec3c549a8e4bca40426406dfd61c` |
| Companion | 17,974,824 | 486 | `80e7dbd13a9fcef831acfbaa26b0419552ef931c9bf060e91bb61ffad6822f0a` |

All project classes target Java 11 (major 55). Both installed hashes match the verified manifest.
Supported `apply-update.ps1 -Unattended -SkipBuild` verified backup 20261017-185122-b93e2a1e,
including three companion and 22 plugin account files, artifacts, manifest and task export.
The wrapper printed successful paired verification and companion readiness. Its outer tool session
remained open, then returned native exit 0 during the next ordinary companion lifecycle transition.
Independent native status, compatibility and scoped GET pipeline each exit 0.
RuneLite API/release 1.13.2 matches. Lifecycle=1; task XML and SDDL compare exactly unchanged.
Caller was not elevated. No game input, financial replay/repair, risk reset or account reassignment.

## Frozen offline evidence

The retained training bundle has 11,598 sources/cursor 13,221 and six eligible outcomes, all
ambiguous. Its manifest SHA-256 is
`003c8d207f1462215fe7455413c8223ee630032d419ee939ff945bae12bad3b6`.
An explicitly scoped read-only later capture has 16,593 sources/cursor 18,794 and seven eligible
outcomes (one known, six ambiguous), manifest
`ff25e494742d2dc70606cbe1436b2f2fad36cce924100bc3dbfd1778b0a3913d`.
Chronological validation has one new known outcome and paired Brier change [0, 0]. The model stays
HOLD; all-unknown fitting retained neutral parameters. These CLI artifacts retain the earlier
12829c7 evaluator/fitter JAR and are not misattributed to the later installed build.

The packaged replay CLI ran/verified all twelve cadence/share scenarios against an empty-history
schema smoke fixture. Zero actions is expected. This is not captured market evidence or a
profitability benchmark. Unit fixtures additionally prove exact tax/cost conservation, shared
slots/capital, partial volume, attendance, restart, conservative terminal marks and determinism.

## Post-login and limits

Supported Start launched one developer-mode RuneLite client; side-loading is logged. Owner
confirmed login at GE. Native scoped pipeline exits 0: 32 sale updates, 18 unresolved, zero profit
ranges at the first sample. No unknown cost/profit was backfilled. A later read-only transaction
against the installed JAR proves a fresh authenticated eight-slot read, eight free slots, finite
exposure, 1,283 retained gaps and zero unbounded gap scopes. The current plan still refuses buys
because the durable uncertainty reserve exceeds its loss allowance. No controlled trade, final
eligible entry, sustained timing, live UI or external notification acceptance is inferred.

Private artifacts stay in `local-data/parity-evidence-20261017/`: build-verification-final.json,
update/status/compatibility/pipeline/launch logs, task metadata, scoped HTTP/probe responses,
training/validation/model and replay-smoke bundles. The original build-verification.json refers
to the earlier packaged CLI build and is preserved separately. No external webhook test was sent.
