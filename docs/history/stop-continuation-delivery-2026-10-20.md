# Fresh-stop continuation delivery

20 October 2026, America/Anchorage. Contract:
[fresh stops and retained continuations](../architecture/stop-continuation-priority.md).

Four old-source behavioral regressions reproduce: a saved reprice hides the working partial buy's
stop; the collected partial gets a BUY instead of a stop SELL; an ordinary relist hides another
holding's fresh stop; replay leaves the partial buy/stock stranded after a stop arrives during the
displayed cancellation. Ten new tests cover those boundaries plus stale/future prices, failed
supersession commits, real foreground withdrawal, an earlier ordinary write backoff, collected
replacement/restart and recovered ordinary relist versus another listed sale's fresh stop.

Source, standard build/tooling, packaged V10 twelve-scenario reproduction, exact-head Windows/
Ubuntu CI, installed pair, backup/task/source/financial preservation and native start status are
consolidated in local-data/stop-continuation-20261020/delivery-state.json. Read it for actual delivery
state; source alone does not establish installation or manual acceptance. Logs and personal source
evidence remain private. No databases, historical cooldowns, risk anchors or learning models are
repaired/reset/promoted. #124's tested 1.13.3 compatibility remains included.

Windows clean build passed in 115 seconds: 1,845 Java tests passed, six existing skips and Java 11
bytecode. All 108 tooling tests passed in 108.429 seconds. The packaged Java 11 evaluator reproduces
all twelve synthetic cadence/share cases under V10, including all four separate 5m/30m/2h/8h
cadences. This establishes reproducibility/conservation, not a market-performance or learning gain.

The owner deferred login and game actions until the 20 October home batch after 17:30 local. Do not
request another login before then. Continue independent work, preserve the installed state and
leave all controlled trade-loop, sustained timing, remaining production replay, return and learning
gates visible in TASKS.md. No fresh cash/eight-slot or trading acceptance is claimed while logged out.
