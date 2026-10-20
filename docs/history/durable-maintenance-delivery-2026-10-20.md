# Durable maintenance delivery receipt

20 October 2026 (America/Anchorage). Contract: ../architecture/durable-maintenance-delivery.md.
Actual source, exact-head CI, installation, backup, source/financial prefix, original anchor and
post-login evidence are consolidated in private local-data/durable-maintenance-20261020/delivery-state.json.
Read those flags before assuming activation; the earlier installed baseline was PR #121.

Four real-file regressions reproduce the former failure to retry pruned collection evidence,
unsaved replacement visibility, dirty-state loss on profile switch and cached corruption overwrite.
A fifth regression uses the real mid-entry walkthrough: final sale refusal left its old modification
actionable while the fresh successor waited in the editor queue. All fail before their respective fixes. A sixth reproduces tests writing synthetic financial warnings/timings into the real RuneLite log.

The batch requires successful durable continuation commits at foreground acceptance, retries dirty
progress/removals even without new evidence, preserves separate account state and fails closed on
changed documents. Initial uncommitted guidance is withdrawn before recording/notifications; ordinary
episode-local backoff permits unrelated actions. Forced atomic replacement preserves old files and
never promotes incomplete temporary output. Scoped refusals withdraw matching editor instructions
without discarding unrelated personal quotes or a newer offer episode. Test-only console logging prevents synthetic evidence entering the player log. Live timing reads filter the actual client thread. All existing financial,
identity, physical, exact-tax/cost, entry and learning guards remain.

Fifteen new behavioral tests cover failure/retry/restart, actual callbacks, personal unknown-cost
relisting, account isolation, pruned evidence, clear retries, interrupted temporary output, accepted
instruction/notification fences, scoped withdrawal and unrelated-entry availability. Existing
relist/recovery/input/foreground tests remain part of verification.

Standard clean Windows build passes in 93s with 1,819 Java passes/six existing skips. The test-only
logging guard and full plugin rerun pass in 28s, bringing the combined verification to 1,820 passes
(plugin 807/core 200/companion 819 including six skips). All 108 tooling tests pass in 130.285s;
project main bytecode is Java 11. The packaged Java-11 evaluator reproduces all twelve synthetic
5m/30m/2h/8h cases under unchanged replay V9. This is conservation/schema evidence, not a qualified
return benchmark. Artifact identities and actual delivery results are in the consolidated receipt. Live cancel/collect/replace/relist, full controlled trading, sustained
latency, qualified whole-account returns and validated learning remain open. Storage retry cannot
recover progress which never durably committed before power loss; no historical repair is performed.
