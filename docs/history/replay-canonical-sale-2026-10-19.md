# Offline replay canonical sale increment

19 October 2026. Contract: [canonical sale replay](../architecture/replay-canonical-sale.md).
Installed runtime stays #108; this change is confined to offline `AccountReplay`, its behavioral
tests and documentation. No JAR installation, client closure or live financial write is required.
The updated evaluator is delivered in the locally built companion JAR and retained replay bundle.

Standard Windows clean build succeeds in 81s/18 tasks: 1,732 Java passed/six existing skips
(plugin 752/core 193/companion 793 including skips), all project bytecode Java 11.
All 108 tooling tests pass in 139.269s. The packaged v6 CLI writes and reproduces all twelve
cadence/share scenarios from the retained SCHEMA SMOKE ONLY input. Its retained evaluator hash
is `e2321fe106cb92bd86075b439e551463028bccf78d6125b5facd3bf46ecdeab7`.
These structural scenarios contain no qualified captured market evidence; the active trade
behaviors are covered separately by the regression suite.

Two new tests failed against version 5: the partial purchase's canonical position had no original
exit context, and advice computed after a fifteen-second action delay could immediately place the
changed order without first being displayed. Private regression log is
`local-data/replay-canonical-20261019/regression-before.log`.

Version 6 freezes displayed order/advice time, waits manual action seconds, recaptures current
sources and revalidates before acting. Changed/expired advice consumes a new display/delay;
restart withdraws pending advice. The exact original buy binding reloads from its retained
synthetic canonical source. Canonical acquisition context and transactional final companion sale
authority now govern exits/maintenance. The independent simulation world supplies only physical
quantity/cash and exact accounting; a missing original plan stays unknown. Completed sell orders
and partial disposal updates have separate counts.

Verification receipts live in ignored `local-data/replay-canonical-20261019/`: regression/focused/
standard build/tooling logs, module/Java-bytecode/JAR identities, the twelve-scenario packaged CLI
bundle and exact-head CI/merge metadata. The active behavioral fixtures include partial/restart,
eight-hour original target retention, unknown original context, changed instructions, original
plan and candidate expiry, foreground restart withdrawal and exact 180-GP profit/20-GP tax.

The installed pair was read directly during this work. Its eight recorded slot identities still
match, but the latest account capture is aged (3,862 seconds at that checkpoint); the current plan
correctly returns COMPANION_STATE_UNAVAILABLE. The read is not fresh buy readiness or a sustained
timing measurement. Earlier fresh #108 READY/final-entry receipts remain historical checkpoints.

Remaining requirements stay open: production outbid-buy replacement intent, persisted sale-relist
sequencing, real client-thread/transport acceptance, controlled player outcomes, qualified frozen
market benchmarks at 5m/30m/2h/8h and all chronological learning promotion gates. Schema smoke and
hand-authored active fixtures do not establish measured market returns, actual fills or parity.
No live accounting, historical gaps, risk reserve/anchor, unknown money or learning activation changes.
