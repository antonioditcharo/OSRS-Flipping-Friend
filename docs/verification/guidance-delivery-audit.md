# Reproduce guidance-delivery observations

Run the read-only analyzer against explicit client logs, keeping the report private:

```powershell
python tools/analyze_guidance_delivery.py `
  "$env:USERPROFILE\.runelite\logs\client_2026-10-27.0.log" `
  "$env:USERPROFILE\.runelite\logs\client.log" `
  --since '2026-10-27 09:12:26' `
  --output 'local-data/delivery-audit/new-report.json'
```

The timestamp filter uses the log's local wall clock. Elapsed values come only from explicit
monotonic runtime measurements; the analyzer does not subtract wall timestamps or infer frame
acknowledgement from transport success. It hashes the exact byte prefix read from each file.
Undecodable old Windows log bytes remain escaped and their line locations are retained.

The report retains main/editor outcomes, proposed/visible/pending actions, trace identities,
phase durations, all post-ACK observations and individual action outliers over one second.
WAITs cannot improve the action distribution. All instruction types other than WAIT count as
actions. Percentiles use nearest rank. Exact duplicate records with the same trace are counted
separately as duplicate evidence; equal-looking actions with distinct traces remain distinct.
Conflicting traces, invalid timings, legacy/unparsed records and unlinked measurements remain
visible. A main post-ACK measurement links only to a matching visible main outcome with the
same action, generation and revision.

Selected-editor overrides, pending editor actions and supersessions are separate matrix rows.
The report does not classify every SELL-to-WAIT as a dropped sale. An absent trace cannot prove
that an action was proposed or lost. Native cached render and selected-sale post-ACK latency
remain explicitly unmeasured until their own evidence exists. Reports never automatically grant
controlled trading, latency, pricing or learning acceptance, even when a sampled p95 is small.

The root test task declares the source and documentation files read by its boundary/encoding
checks as explicit inputs. A documentation-only edit must rerun these checks rather than reuse
an older compiled-class result. The #195 CI encoding refusal is retained as negative evidence;
the tooling follow-up verifies invalidation after an ordinary valid documentation edit.
