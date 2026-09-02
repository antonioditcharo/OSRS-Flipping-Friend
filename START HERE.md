# Start here

Two files. Run them in order. That is the whole thing.

---

## The first time only

**Double-click `1 - First Time Setup`**

It asks one question — whether you use the Jagex Launcher — and then walks you through the rest,
opening the windows you need and checking each step worked. It takes a couple of minutes.

At the end it puts a **Flipping Friend** shortcut on your desktop.

---

## Every time you want to play

**Double-click `Flipping Friend` on your desktop**

(or `2 - Start Flipping Friend` in this folder — same thing)

The game opens. Log in as normal. Then click the **gold coin icon** in the bar down the right-hand
side of the RuneLite window.

That is it.

---

## What you will see

The panel tells you **one thing to do at a time**. Do that thing, and it tells you the next one.

When you open the Grand Exchange it draws a **green box around whatever you should click**, and
shows the exact price and quantity to type next to it. Every number has a **copy** button in the
panel so you never have to type one out by hand.

It will say things like:

- *Buy 1,000 × Cannonball* — with the price to offer
- *Collect your Rune scimitar* — a finished offer is holding up a slot
- *Your Magic logs offer is too low* — the market moved, cancel and re-place it
- *Sell your Dragon bones* — with the price to ask
- *Nothing worth trading right now* — this is a real answer, not a bug. It would rather say nothing
  than talk you into a bad trade. It keeps looking every few seconds.

**It never clicks or types for you.** You place every offer yourself.

---

## Settings

Click the wrench icon in RuneLite, find **Flipping Friend**. The two worth knowing:

**Risk level** (also on the panel itself)
- *Low* — small, quick, safe trades
- *Moderate* — the default, and the right choice if you are unsure
- *High* — bigger profits, longer waits, and some trades will lose

**How often you check the GE** (also on the panel)
Set this honestly — it changes what you get suggested. Tell it you check every hour and it gives you
fewer, larger, wider-margin offers that should be finished when you come back, instead of quick
flips that sit completed while you are away.

**Account type**
- *Detect automatically* — the default, and correct for almost everyone
- *Free-to-play* — pick this if you have no membership. It then only suggests buying items you can
  actually trade, and plans around 3 Grand Exchange slots instead of 8.

**Want it learning even when you are not playing?** Double-click `3 - Background Learning`. It
installs a small program that studies the market all day and starts with Windows. It never
touches the game or your account — it reads public prices and does maths. Remove it any time
with `tools\uninstall-daemon.ps1`. The Learning panel shows whether it is running.

**Keep learning in the background** — on. The plugin quietly tracks trades it *didn't* place, to see
how they would have turned out, and gets better at judging them. Nothing is placed in game and no
coins move. The **Learning** section at the bottom of the panel shows what it has worked out.

**Also sell items I already own** — off, and you probably want it left off. Your bank is gear and
supplies, not flipping stock, so the plugin ignores it. Anything it buys for you is always tracked.

Note that selling is never restricted by membership. If your membership ran out while you were
holding members' gear, the plugin will still tell you to sell it — that is allowed in game, and it
is often the first thing you should do.

---

## If something goes wrong

**"I don't see the gold coin icon."**
Click the wrench icon, search for *Flipping Friend*, and make sure it is switched on.

**"It only shows the old username-and-password login screen."**
The Jagex login step did not complete. Run `1 - First Time Setup` again.

**"It says nothing is worth trading."**
That is normal, especially on Low risk. Give it a few minutes, or switch to Moderate.

**"RuneLite updated and now something is broken."**
Open `build.gradle`, change the version on the `ext.runeLiteVersion` line to match, then start it
again as usual.

**Getting rid of it.**
Run `tools\cleanup.ps1`. That removes the saved login and tells you how to remove the plugin file.

---

## For the curious

`README.md` has the full picture: how it decides what to trade, how to run the tests, and how to
replay real market history through the decision code.
