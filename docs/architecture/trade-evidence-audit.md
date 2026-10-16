# Audit retained trade observations without changing accounting

16 October 2026. Developer investigation workflow for issue #70, not a recovery or trading policy.

## Commands

Build the standard tested pair, then use a previously verified complete execution capture:

```powershell
.\tools\audit-trades.ps1 -Capture '<existing frozen capture>'
.\tools\audit-trades.ps1 -Mode Verify -Capture '<same capture>' -Report '<created audit directory>'
```

Audit defaults to a new directory under local-data/trade-audits. An explicit Report must also stay
under local-data; existing directories are never overwritten. This command reads JSON/artifacts,
never a database, service, token or game client. It cannot freeze or assign the unscoped legacy
database. To obtain a new explicitly scoped read-only source capture, use the existing
[freeze workflow](frozen-execution-evaluation.md) first. Its account/order/source-scope checks
remain mandatory; an unsupported or incomplete capture fails instead of becoming clean evidence.

Direct packaged invocation on either supported OS:

```text
java -cp <companion.jar> com.flippingfriend.companion.TradeEvidenceAuditMain audit <capture> <new-report>
java -cp <report>/auditor.jar.bin com.flippingfriend.companion.TradeEvidenceAuditMain verify <capture> <report>
```

Successful creation verifies its own output; Verify rechecks hashes and recomputes the report from
the same source prefix. Both exit zero on success. Status remains OBSERVATION_AUDIT_ONLY. A summary
prints aggregate flag counts and manifest identities; personal row/item/source details stay private.
PowerShell 5.1 preserves native diagnostics and checks the native exit code, including launch failure.

## What the report establishes

Each observed offer identity retains its items, first/last source references, EMPTY observations,
original recorded-gap source IDs and problematic row references. Source observations are processed
in their recorded row order. The audit flags filled initial baselines, missing terminal observations,
identity reappearance after EMPTY, identity changes without EMPTY, terminal-to-active transitions,
regressed counters, conflicting identity metadata, ordering/session/clock conflicts, invalid lifecycle
states and contradictory funding. Full counters in BUYING/SELLING remain valid nonterminal progress.

`maxObservedCumulativeFilled` and `maxObservedCumulativeSpent` describe raw counters once per identity.
Repeated terminal frames do not add those counters together. Conflicting item/side/size/price/slot/
first-seen metadata withholds those maxima rather than comparing different units. These are not
acquisitions, remaining stock, current acquisition cost, tax or profit. A zero-filled first observation
does not prove exact placement time. A terminal then EMPTY does not prove a physical collection or
resolve whether a later matching terminal is a replay or an incompletely observed new order.

The output has authorizesAccounting=false and authorizesTrading=false. No report, including one
without anomaly flags, can release a paused item or alter the durable risk allowance. The underlying
capture does not contain complete financial-effect rows, physical inventory/bank evidence, external
funding, or proof of unobserved disposals. Original accounting and unknown profit stay unchanged.

## Reproducibility and boundaries

An audit contains audit.json, manifest.json and, for a packaged invocation, auditor.jar.bin. The
manifest binds auditor/schema versions, original capture-manifest SHA-256, account, capture second,
source cursor/count, report bytes and actual auditor artifact. The original capture is not modified
or copied into the report. Keep it alongside the report to verify later. Unpackaged unit-test classes
are identified explicitly. Hashes detect inconsistent bytes; they are not signatures or proof of
historical knowledge time. Creation writes the manifest last; incomplete output is rejected.

Verify rejects foreign/changed captures, edited/rehash-adjusted report claims, unsupported manifest
versions, missing/linked/oversized files and a mismatched retained artifact. Recomputed bytes must
match exactly; changing hashes cannot grant accounting authority. Every source row is retained in
the parent bundle and no identity list is truncated. The explicit per-file limit remains 256 MiB.

Tests c3f4936/476b0b8 specify raw-counter/ambiguity and complete-bundle behavior before implementation.
744b75b reproduces a manifest-version validation defect in the new unshipped command before its fix.
Disposable prefix/bundle tests cover the source and output boundaries above. See newest HANDOFF for
actual retained-capture results, standard verification and exact-head CI. This offline tool needs
no client restart or JAR installation to run from the tested build.

## Next recovery work

Use source references to determine which missing terminal, cumulative baseline or identity ambiguity
needs independent evidence. Do not replay a current reducer over old sources into a financial writer,
drop a gap, infer disposal from absence, adopt personal stock or reset the risk window. A future
proposal must demonstrate safe remaining quantity/cost and conservative exposure against the complete
financial ledger. The audit is evidence for that investigation, not its approval or implementation.
