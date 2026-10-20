# Guided cancellations and advice lifetime

The saved offer episode, rather than the visible card, identifies a cancellation performed for a
buy reprice. A card can change, expire or reset before the player follows it. Previously this
created an eight-hour rejection cooldown and the replacement path then cleared the retained plan.
Conversely, a same-item card could exempt a different or uncommitted offer from rejection.

OfferTracker now delivers a copied cancellation observation to the plugin before continuation
checkpointing. BuyRepriceIntent checks the current account's committed original identity and the
shared continuation's exact slot/item/side/price/total/first-seen/episode match. The counter must be
nonregressing and incomplete. Restored commitments work before the first engine refresh; failed
progress writes do not erase an earlier commitment, while a failed initial write cannot establish
one. Reused episodes and foreign accounts cannot borrow the exemption. Collection, exact remainder,
funding, source acknowledgement and final companion entry authority remain separate requirements.
No existing cooldown or financial record is rewritten by this change.

Every actionable foreground instruction now has a 60-second monotonic receipt lifetime, including
local sales, maintenance, cancellations, collections and personal sell quotes without an upstream
expiry. A shorter upstream expiry still wins. Identical freshly validated orders renew their own
receipt without changing the Suggestion object, forecast provenance or entered quantity/price.
Unrelated queued advice cannot renew the active trade. Pending advice retains its receipt time when
the editor closes; expired pending advice cannot become a fresh order. Personal quotes have their
own receipt time and cannot renew the underlying managed instruction. Expired instructions display
a refresh wait; purchase records and durable continuations remain intact.

The final client delivery separately bounds its queue age. A delayed callback cannot grant an old
local decision a new receipt. After three consecutive refresh failures, explicit withdrawal clears
the old tracked/pending/editor instructions instead of queuing the error behind an in-progress sale.
Existing lifecycle/profile/source/revision fences still protect later successful work.

GeInputAssist retains the original receipt captured for the player's numeric-input key. Renewing
identical advice cannot revive an expired queued key. Existing focus/profile/prompt/editor/generation
checks remain; one fresh player key can fill one matching numeric prompt. All game actions remain
manual. No test or agent sends input to the actual game.

Source verification and delivery are recorded in
[the consolidated receipt](../history/guidance-lifetime-delivery-2026-10-20.md). Controlled trading,
sustained latency, qualified return comparisons and learning promotion remain open.
