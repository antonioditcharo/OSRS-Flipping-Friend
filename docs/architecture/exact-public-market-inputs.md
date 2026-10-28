# Exact public-market inputs

28 October 2026. `PublicMarketNumbers.VERSION = PUBLIC_NUMERIC_INPUT_V1` validates raw JSON before
conversion into the existing integer-price/volume model. It is a public-data boundary, not a
financial migration or a new pricing model. Original source rows, account events, learning
identities, acquisition costs, exit context and risk windows are unchanged.

Present prices must be exact positive integers within the supported 32-bit range; side clocks
remain exact positive 64-bit integers. Missing prices/clocks remain missing. Volumes must be
explicit exact nonnegative supported integers, with a representable total. A missing volume is
not observed zero. Item IDs and metadata limits are exact and canonical; ambiguous duplicate
metadata identities cannot supply a limit. Midpoint addition uses a widened intermediate.

Client HTTP/cache/feed readers, companion candidates and sale/buy maintenance, portfolio alerts,
entry stop checks, risk marks, history caches and offline replay use the same decoding rules.
An unsupported quote/aggregate removes only that item at that boundary. One malformed history
bucket refuses that item/resolution as a unit. Cached history cannot revive the contradicted
numeric input; unrelated warm histories survive. Transport failure retains its existing retry
behavior. Raw hosted archive bytes, 64-bit public observations and original timestamps remain
intact. A frozen evaluation refuses unsupported declared inputs before typed deserialization;
it cannot quietly exclude an item that crosses the model boundary after cohort freezing.

A cached READY allocation is rechecked for an explicitly unsupported current quote. The client
may try another already allocated item, at most once per item, obtaining independent final
authority for it. This narrow refusal cannot turn a cash, account, exposure or loss refusal into
permission. It does not change user skips. Physically proven stock with an unsupported quote
retains its exact cost and an item entry pause under CURRENT_ACCOUNT_V3. Valuation uncertainty
does not freeze unrelated verified cash; future clocks, unknown live exposure, unseen quantities,
confirmed losses, funding and source guards retain their existing treatment.

Regression evidence covers mixed valid/oversized/fractional/string rows, missing sides and
volumes, overflowing volume totals, metadata duplicates, large midpoint arithmetic, per-item
cache recovery, cached entry rejection with an eligible neighbour, exact stock preservation,
and valid-hash frozen blobs containing unsupported numbers. Root tests use RuneLite's provided
Gson 2.8.5 as well as the standalone core/companion build. Qualification and runtime activation
remain distinct: these checks do not qualify a pricing challenger or establish live latency.
