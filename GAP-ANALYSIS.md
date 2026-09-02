# Gap Analysis — 2 September 2026

Working checklist. Full reasoning, evidence and reference tables are in the published
audit page (see the Claude conversation for the link).

**Headline:** the analytical engine is strong. The learning layer the README and PLAN-2M
describe does not exist in the code. `gradlew backtest` measures the superseded engine.
Priorities: P0 blocks everything, P1 required for the product claim, P2 quality, P3 polish.

---

## Phase 0 — Ground truth (half a day)

- [ ] **P0** `git init` + private remote. No `.git` exists. Commit the tree as-is first.
      Add `codebase_dump.txt`, `test_output.txt`, `ml-forecaster/venv|models`, `node_modules`, `.next`, `out` to `.gitignore`.
- [ ] **P0** CI running `./gradlew test` (386 tests currently green) + UI build + pytest.
- [ ] **P0** Delete the 6 Gradle tasks referencing missing classes:
      root `daemon`,`daemonJar` -> `com.flippingfriend.daemon.LearningDaemon`;
      companion `replayJar`->ReplayMain, `trainerJar`->ModelTrainer, `monitorJar`->LearningMonitor.
      Delete stale `companion/build/libs/flipping-friend-{monitor,replay,trainer}.jar`.
- [ ] **P0** Rewrite README + PLAN-2M to describe what exists. Move unbuilt claims to ROADMAP.md.
      Keep the measured-experiment notes verbatim.
- [ ] **P1** Delete root strays: `codebase_dump.txt` (34MB), `test_output.txt` (stale failed build),
      `TextComponent.java` (verbatim RuneLite class, (c) Tomas Slusny, default package, unreferenced), `check.py`.
- [ ] **P2** Replace the two literal NUL bytes in `SqliteStore.java` (offsets 23713, 24217) with `\u0000`.
      `file(1)` currently reports the source as binary.

## Phase 1 — Delete the ghosts, fix the lies (~1 week)

- [ ] **P0** Remove the auto-populate hotkey. `client.setVarcStrValue(VarClientStr.INPUT_TEXT, ...)`
      is autotyping under RuneLite's rejected-features list and input generation under Jagex's macro rules.
      It also contradicts "It never clicks or types for you." Clipboard-on-hotkey is the safe version.
- [ ] **P0** Fix NPE: `SuggestionEngine.java:1141` `prices.add((double) c.getAvgHighPrice())` unboxes null
      whenever a 5m bucket had no instant-buy. Kills the whole refresh cycle, logged as a warning only.
- [ ] **P0** Stop the LSTM collapsing the sell grid. `Scorer.java:149-165` replaces SELL_OFFSETS with one
      unbounded `quotedSell * (1 + momentum)`. Disconnect until Phase 3; when restored it must shift the grid, clamped.
- [ ] **P1** Delete unreferenced classes: `ArbitrageEngine` (0 refs), `JagexPriceClient` (0 refs).
      Wire or delete `MarketFluxIndex.compute()` / `AlertManager.checkMarketFlux()` (never invoked ->
      `AlertOverlay` can only ever render empty).
- [ ] **P1** Resolve dead settings: `shadowTrading` (0 reads), `conservativePricing`/`learningDisabled`/
      `waitAwareSizing` (setters exist, 0 callers), `benchmarkProfitPerFlip` (legacy engine only).
- [ ] **P1** Collapse the two decision engines. Move sell/reprice into the companion; delete `Scorer`,
      `PositionSizer` and the legacy screen. The plugin becomes: read state -> post events -> render.
- [ ] **P1** Point `Backtester` at `CandidateFactory` + `PortfolioOptimizer`. The seam already exists
      (`build(Collection<QuotedItem>, ...)`). Then re-derive every number in PLAN-2M.
- [ ] **P1** One shared User-Agent with a real contact, everywhere. Current strings have no contact;
      the plugin's github URL does not resolve. Join `#api-discussion` on the wiki Discord.
- [ ] **P1** One HTTP client + token-bucket limiter in the companion. Today: 3 OkHttpClients + Python
      `requests`, no shared budget, no 429 handling, no backoff, no ETag.
- [ ] **P2** Remove the duplicated members check — `CandidateFactory.tacticsFor` passes
      `canBuyMembersItems = true` hardcoded.
- [ ] **P2** Reconcile the two TTLs: `PLAN_TTL_SECONDS=150` vs `PortfolioPlan.TTL_SECONDS=90`.
- [ ] **P2** Bound the optimizer search: node + wall-clock budget, tighter bound, cache the board ranking.
- [ ] **P2** Read `buyLimitPublished` downstream. 508 of ~4,652 items have a guessed limit that is
      currently indistinguishable from a published one.
- [ ] **P3** Fix the mixed-scale objective in `SellTimingEngine.bestExit` (two formulas, one `bestRate`).

## Phase 2 — Close the learning loop (2-3 weeks) — highest value

- [ ] **P0** Populate `predictedCompletion`. `CompanionClient.java:150` calls the 5-arg
      `OfferEvent.Builder.recommendation(...)`, which hardcodes 0, so the calibrator has never once
      compared a prediction to an outcome. Delete the 5-arg overload afterwards.
- [ ] **P0** Consume `ExecutionRecorder` output. `settled` and `claimed` are both computed and discarded
      in `CompanionService.offer(...)`. The `paired_*` columns give you LearnedDurations in ~120 lines.
- [ ] **P1** Build the shadow trader. Paper-trade every scored candidate AND every vetoed item; resolve
      against later 5m bars. Keep the two-channel rule: paper -> price behaviour, real fills -> queue speed.
      Then re-derive the VetoThresholds gp table from live evidence.
- [ ] **P1** Rebuild the always-on learner as a scheduled task inside `CompanionMain`, not a 3rd process.
      Delete `install-daemon.ps1`, `uninstall-daemon.ps1`, `3 - Background Learning.bat`.
- [ ] **P1** Wire the model registry that already exists: `model_snapshot`, `saveModel`, `loadModel`,
      `latestModelVersion`, `pruneModels`, `GateReport`, `gate_result`. All built, tested, unused.
- [ ] **P1** Add exploration. Thompson draw for ranking, mean for display —
      `PortfolioCandidate.withDisplayProbability()` exists for exactly this. Target ~10%, log which were exploratory.
- [ ] **P1** Restore forecast-driven exits. `PriceForecast` is imported-unused in `SellTimingEngine`;
      `REACHABLE_QUANTILE`, `MAX_USEFUL_BAND`, `BUCKET_SECONDS` are declared and never read;
      `reachableTarget(...)` was deleted with its test. There is currently no time decay at all.
- [ ] **P1** Fix the Jensen bias in `Calibrator` — its own javadoc names the bug. Pool numerators and
      denominators in every channel; test that a balanced pair returns exactly 1.0.
- [ ] **P2** Reliability diagrams + Brier score for both fill probabilities. Primary model-quality metric.
- [ ] **P2** Learn the capture rate from your own fills instead of setting it by risk appetite (0.35-0.85).

## Phase 3 — Rebuild the ML layer (3-4 weeks)

- [ ] **P0** Eliminate target leakage. `target = price_change.ewm(span=5).shift(-1)` is dominated by
      values inside the feature window. Use forward return over the traded horizon, with a gap.
- [ ] **P0** Normalise inputs. volume ~1e6 vs price_change ~1e-3 fed raw. Log volume, standardise on the
      training fold only, sin/cos for time-of-day, persist the scaler with the weights.
- [ ] **P0** Train with mini-batches. Currently ONE `optimizer.step()` per epoch, <=100 epochs total.
- [ ] **P0** Add a held-out test set and baselines: predict-zero, persistence, and `PriceForecast`.
      If it does not beat `PriceForecast`, it does not ship.
- [ ] **P0** Replace ~4,000 per-item models with one pooled model + item/attribute embeddings.
      Only `lstm_model_4151.pth` exists on disk — the approach has never run at scale.
- [ ] **P1** Predict what the optimizer consumes: (a) fill hazard, (b) exit-price quantiles (pinball
      0.25/0.5/0.75 — drops straight into SellTimingEngine), (c) regime/structural break.
- [ ] **P1** Make LightGBM/XGBoost the baseline the network must beat.
- [ ] **P1** Purged, embargoed walk-forward CV. Report mean AND standard error; refuse effects inside noise.
- [ ] **P1** Ship the model as a residual correction with an earned weight:
      `sigma(logit(analytical) + w * delta)`, w from rolling Brier skill vs the analytical baseline, floored at 0.
- [ ] **P1** Stop `_predict_single` re-fetching from the wiki per item (ignores the payload it was sent;
      ~30 requests / 30s). Read history from the companion's `/v1/market/series` over loopback.
- [ ] **P1** Bind uvicorn to 127.0.0.1, add a shared-secret header, bound the training worker pool.
- [ ] **P2** `LayerNorm` instead of `BatchNorm1d` — removes the `self.eval()`/`self.train()` toggle
      inside `forward()`.
- [ ] **P2** One source of truth for hyperparameters (declared 128/2 layers, constructed 64/1). Delete `model.py`.
- [ ] **P2** Real pytest suite: shapes, no-NaN, leakage assertion, scaler round-trip, golden file.
      Replace `test_ml.py` (prints and sleeps, no assertions, hits the live API).

## Phase 4 — Data foundation and the moat (2 weeks)

- [ ] **P0** Verify and migrate to the v2 API. Docs give `api/v2/osrs` and
      `timeseries(id, lookback in {6h,24h,7d,30d,6m,1y})`; the repo uses v1 with `timestep=`.
      Curl both from your machine first — I could not verify whether v1 still serves.
- [ ] **P0** **Start recording your own price history today.** The companion already downloads every
      price every 60s and discards the previous snapshot. Append to Parquet or SQLite with (item_id, ts).
      This is the only durable advantage over competitors using the same public feed.
- [ ] **P1** Use the 6h/24h (or v2 30d/6m/1y) series in `MarketContext`: long-run range percentile,
      day-of-week seasonality, a real changepoint test instead of a 3-sigma mean shift.
- [ ] **P1** Ingest the game-update/news calendar as an exogenous feature.
- [ ] **P1** Replace `ItemGroups` keyword matching with a measured return-correlation clustering
      (keep keywords as the cold-start prior).
- [ ] **P2** Widen `DEEP_ANALYSIS_LIMIT` (90) only in step with model quality on thin items — 300 failed
      5 of 9 promotion gates.
- [ ] **P2** Staleness ladder: fresh -> last-known-good with visible age -> refuse to advise.
- [ ] **P2** Enrich item metadata from the wiki (category, production chain, equipment slot).

## Phase 5 — Decision engine (2-3 weeks)

- [ ] **P1** Implement buy-limit rotation (PLAN-2M 1.4, still open). `BuyLimitLedger` knows the reset
      times; nothing acts on them.
- [ ] **P1** Use the board's marginal rate as an explicit hurdle rate for taking AND for holding.
- [ ] **P1** Make reprice/cancel forecast-driven, not timer-driven. Explain the decision in the card.
- [ ] **P1** Single source of truth for the risk ladder (`RiskAppetite` vs `RiskProfile` lossCutPct),
      then an integration test that the 15% drawdown freeze actually fires from real offer events.
- [ ] **P2** Make the bench a real queue via sequential re-optimisation, not a second knapsack.
- [ ] **P2** Model partial fills as a first-class outcome (also gives the clean `waitAwareSizing` test).
- [ ] **P2** Re-derive all three risk profiles against the shipping engine. High is currently strictly
      dominated by Moderate (45.7k vs 95.9k/slot-hour) — measured on the wrong engine.
- [ ] **P2** Include uncollected offers in slot accounting; quantify the cost of not collecting.
- [ ] **P3** Explore decanting, sets, production chains, and the alch floor as extra candidate types.

## Phase 6 — Actually hands-off (2 weeks)

- [ ] **P1** Move alerting into the companion so it survives the client closing.
- [ ] **P1** "When to come back" estimate — min(expected fill times, limit resets) + a push at that time.
- [ ] **P1** Return-to-desk queue line: "3 to collect / 1 to reprice / 1 to sell / then 2 new buys".
- [ ] **P1** Test the overlay walkthrough against a live GE. PLAN-2M's own caveat: it has never run.
      Add widget-id regression tests from captured widget trees; log which resolver branch fired.
- [ ] **P2** Serve the dashboard from the companion's `ApiServer`. Delete `dashboard-desktop` and
      `dashboard-api`. Gets phone access + auth for free.
- [ ] **P2** Render `PlanDiagnostics` in the panel instead of "Nothing worth trading right now".
- [ ] **P2** Multi-account: key execution + buy limits by account hash, keep market learning shared.
- [ ] **P3** Session-goal mode (target end time -> shrink horizon -> auto sell-only).
- [ ] **P3** Correct the two false claims in `START HERE.md`; keep the voice.

## Phase 7 — Measurement, dashboard, distribution (ongoing)

- [ ] **P0** Realised profit is computed GROSS OF TAX in `dashboard-api/index.js`. On a 2% margin that is
      close to the entire profit. Use the tax `OfferTracker.accrueSell` already tracks.
- [ ] **P0** Stop fabricating values: `confidence: confidence || 0.8` and
      `reason: c.group || "AI Target identified"`. Render "—".
- [ ] **P1** `/api/history/profit` uses `ORDER BY created_at ASC LIMIT 100` — the OLDEST 100 plans.
- [ ] **P1** Auth + loopback binding for the dashboard API (`jsonwebtoken` is a dependency; no route uses it;
      `cors()` allows every origin; `listen` binds every interface).
- [ ] **P1** Stop recomputing the whole ledger per poll (6 endpoints x 5s, full scans of `event_log`,
      up to 500 JSON.parse per request). Maintain aggregates incrementally; push over SSE.
- [ ] **P1** Realised gp/hr net of tax, from completed flips only, with a CI and sample size, as the single
      headline number. Everything else is diagnostics.
- [ ] **P1** Real A/B harness: feature-flag registry, flag set recorded on every plan, effect size with SE.
- [ ] **P1** Eliminate the developer-mode sideload + `--insecure-write-credentials` path. Plugin Hub is the
      only real distribution route, and removing the autotype is its precondition.
- [ ] **P2** Write the data-disclosure text (wiki API, loopback companion, Discord webhook).
- [ ] **P2** Signed installer for the companion; remove the hardcoded `C:\Program Files\Java\jdk-23` fallback.
- [ ] **P2** `tools/monitor.ps1` only copies the built UI when `dashboard-api/public` does not exist —
      a rebuilt UI never reaches the served copy.
- [ ] **P2** Retitle the Electron window ("AI Learning Monitor" with no learning); hide the empty gate chart.
- [ ] **P2** Chart slot utilisation and decompose idle time (limit reset / player away / nothing cleared / freeze).
- [ ] **P3** Position on "after-tax expectation with a fill model, and a tool that tells you why it said no".
      Do not lead with "AI".
- [ ] **P3** Publish the methodology. The javadoc here is better than anything the competition publishes.
- [ ] **P3** JaCoCo + a coverage floor in CI, and one PIT mutation run against `:core`.

---

## Suggested order

1. Phase 0 (undo button before you delete anything)
2. Phase 1 items 7-9 (compliance risk, silent crash, unbounded price override)
3. Phase 4 "start recording history" — every day you wait is data you cannot get back
4. Phase 2 items 21-22 (two small fixes that switch the feedback loop on)
5. Rest of Phase 1
6. Backtest the real engine, then Phase 5
7. Rest of Phase 2
8. Phase 3 (only once there is a calibrated baseline and labelled data)
9. Phases 6 and 7 in parallel from Phase 5 onward
