# Revalidate work while an editor is open

Opening a matching GE editor previously pinned the visible maintenance card inside SuggestionEngine.
Every refresh returned that object before checking offers, prices or stops, then gave it a new guide
receipt. The five-minute engine timeout was renewed by each capture. This could indefinitely hide a
completed offer, a fresh stop or a failed market feed while appearing to provide current guidance.

The engine no longer keeps an editor pin. Every pass checks current offers, acquisition evidence,
prices and exit policy. StepGuide owns presentation deferral: unchanged freshly evaluated inputs
preserve typing progress; corrections apply to the same trade; unrelated instructions wait behind
the original instruction's bounded receipt. Opening an editor alone cannot suppress new decisions.
Source/account/observation and final companion authority fences still apply before delivery.

A fresh stop SELL or CANCEL for the same item immediately withdraws a foreground BUY/MODIFY_BUY,
clears its queued action/progress and advances the guidance generation used by numeric-input
assistance. An unrelated personal sell quote retains its own context. Stops for other items still
respect bounded manual typing deferral. The accepted stop must commit durable continuation
supersession before prediction evidence or notification; a failed commit withdraws the guide.

Fallback collection of observed completed/cancelled offers and explicit sell-only cancellation run
before the market-data availability check. Neither needs a market price. This does not authorize a
new buy, a loss sale, financial recovery or disposal; unpriced personal sales remain unpriced.
Ordinary sell pricing and all new entries retain their existing market/source/funding/risk guards.

Nine added behavioral tests cover the real plugin capture with an open editor, fresh stop,
completion/cancellation, unsupported feed, price-independent collection/wind-down, unknown-cost
personal sales, bounded unrelated interruptions, personal quote preservation and the real
delivery-to-durability ordering. Five regressions fail against the earlier behavior. Existing
guide lifetime, input, manual confirmation, workflow and replay suites remain part of verification.

See the [delivery receipt](../history/live-revalidation-delivery-2026-10-20.md) and its private
delivery-state.json for actual source, CI, installation and live state. Manual trading, sustained
timing, complete production replay, qualified return comparisons and learning remain separate gates.
