# Public history cache without whole-cache JSON trees

The passive #200 warm profile on 29 October contains public history encoding/saving among the
sampled warmer work and JVM pauses up to 1.213 seconds. It does not cover the earlier slow editor
quotes or prove the cause of their authority delays. Keep that distinction and the native gates.

Save the existing public-history cache directly through a gzip JSON writer. Keep the same fields,
integer/null values, entry ordering, fetch clocks, TTL filtering and temporary-file replacement.
Read one history entry at a time instead of building a tree for the entire expanded gzip file.
Existing readers must still consume the output. No new cache schema or invalidation policy.

The reader retains the legacy last-root-member behavior, rejects repeated item/resolution keys,
skips unsupported individual histories and drops a truncated or malformed container completely.
An unreadable public cache remains a cold start. Missing or expired history never becomes an
observation, fill, financial repair or permission to trade. Public fetching, model calculations,
current market clocks, account quantities/costs, final authority and financial identities are
unchanged. This optimization adds no network request to a foreground read.

Verify old/new public-cache interchange, exact null/integer JSON, duplicate root/key behavior,
truncation, unsupported numeric values, stale entries and all 600 histories at 288 buckets each.
Run the fixed-heap full-capacity probe and clean Java/tooling/Java 11/exact-head CI checks before
claiming qualification. Keep this candidate separate from #202's frozen startup artifacts and
hour. Runtime installation needs its own relevant release/preservation gates; source alone does
not close native latency or player acceptance. The learner and pricing promotion remain gated.
