"""Correlated diagnostic timing; visibility, source identity and missing stages stay explicit."""
import json
import math
import re
from collections import Counter

ACTIONS = {"BUY", "SELL", "MODIFY_BUY", "MODIFY_SELL", "CANCEL", "COLLECT", "PACK", "UNPACK", "DECANT"}
STAGES = {"request", "captureStarted", "capturePersisted", "workerQueued", "workerStarted", "recoveryFinished",
          "durableAck", "accountPublished", "planFinished", "decisionStarted", "pricingPrepared", "authorityStarted",
          "authorityFinished", "clientDelivery", "presentationStarted", "presentationFinished", "delivery",
          "panelRequested", "edtDelivery", "paintEnd"}


def distribution(values):
    values = sorted(values)
    return {"count": len(values), "p50Ms": values[math.ceil(.5 * len(values))-1] if values else None,
            "p95Ms": values[math.ceil(.95 * len(values))-1] if values else None,
            "maxMs": max(values) if values else None}


class TimingAudit:
    def __init__(self):
        self.records, self.invalid, self.conflicts, self.seen = [], [], [], {}
        self.duplicates = 0

    def add(self, payload, origin):
        try:
            value = json.loads(payload)
            if (value.get("schemaVersion") != 2 or value.get("source") not in {"MAIN", "SELECTED_SALE"}
                    or not isinstance(value.get("trace"), str) or not value["trace"] or len(value["trace"]) > 200
                    or value.get("event") not in {"DELIVERY", "PAINT"}):
                raise ValueError("Unsupported timing identity")
            if value.get("account") is not None and not re.fullmatch(r"rs-[0-9a-f]{64}", value["account"]):
                raise ValueError("Invalid account scope")
            stages = value["stageMicros"]
            if (not isinstance(stages, dict) or stages.get("request") != 0 or not stages.keys() <= STAGES
                    or any(type(v) is not int or abs(v) > 2**53 or (k != "durableAck" and v < 0) for k, v in stages.items())
                    or type(value.get("droppedTelemetryRecords")) is not int or value["droppedTelemetryRecords"] < 0):
                raise ValueError("Invalid monotonic stages")
            # ACK may precede a selected-editor request. Never charge that pre-request interval
            # as a selected-sale computation delay or move the original acknowledgement clock.
            ordered = [stages[k] for k in ["workerQueued", "workerStarted", "authorityFinished", "clientDelivery",
                       "presentationStarted", "presentationFinished", "delivery", "panelRequested", "edtDelivery", "paintEnd"] if k in stages]
            if ordered != sorted(ordered):
                raise ValueError("Reversed timing stages")
            key = (value["trace"], value["event"])
            if key in self.seen:
                if self.seen[key] == value:
                    self.duplicates += 1
                else:
                    self.conflicts.append({**origin, "trace": value["trace"], "event": value["event"]})
                return
            self.seen[key] = value
            self.records.append({**origin, **value})
        except (ValueError, TypeError, KeyError, AttributeError) as error:
            self.invalid.append({**origin, "reason": str(error)})

    def result(self):
        conflicting = {r["trace"] for r in self.conflicts}
        groups = {}
        for row in self.records:
            groups.setdefault(row["trace"], {})[row["event"]] = row
        outcomes, samples, missing = Counter(), [], []
        identities = set()
        for trace, pair in groups.items():
            delivery, paint = pair.get("DELIVERY"), pair.get("PAINT")
            if delivery and paint:
                shared = set(delivery)-{"file", "line", "loggedAt", "event", "droppedTelemetryRecords", "stageMicros"}
                if (any(delivery[k] != paint.get(k) for k in shared)
                        or any(paint["stageMicros"].get(k) != v for k, v in delivery["stageMicros"].items())):
                    conflicting.add(trace)
                    self.conflicts.append({"trace": trace, "event": "PAINT_DELIVERY_MISMATCH"})
            if delivery:
                outcomes[(delivery["source"], delivery.get("action", "NONE"), delivery.get("deliveryOutcome", "UNKNOWN"),
                          paint.get("paintOutcome", "UNMEASURED") if paint else "UNMEASURED")] += 1
            if trace in conflicting or not delivery:
                missing.append({"trace": trace, "reason": "CONFLICT_OR_MISSING_DELIVERY"}); continue
            if delivery.get("deliveryOutcome") != "VISIBLE" or delivery.get("action") not in ACTIONS:
                continue
            row = paint or delivery
            s = row["stageMicros"]
            sample = {"trace": trace, "source": row["source"], "action": row["action"], "paintOutcome": row.get("paintOutcome", "UNMEASURED"),
                      "telemetryLoss": row["droppedTelemetryRecords"], "stageMicros": s}
            for name, start, end in [("cachedRenderMs", "panelRequested", "paintEnd"),
                                     ("postAckPublicationMs", "durableAck", "delivery"),
                                     ("workerQueueMs", "workerQueued", "workerStarted"),
                                     ("authorityMs", "authorityStarted", "authorityFinished"),
                                     ("presentationPersistenceMs", "presentationStarted", "presentationFinished")]:
                if start in s and end in s:
                    sample[name] = (s[end]-s[start])/1000
            if "durableAck" in s:
                sample["eligiblePublicationMs"] = (s["delivery"]-max(0, s["durableAck"]))/1000
            else:
                missing.append({"trace": trace, "reason": "NO_MATCHED_DURABLE_FRAME"})
            if sample["paintOutcome"] != "PAINTED":
                sample.pop("cachedRenderMs", None)
            identity = next((row.get(k) for k in ["recommendation", "offer", "saleSource", "origin"] if row.get(k)), None)
            if identity and row.get("account"):
                key = (trace.split(":", 1)[0], row["account"], row["source"], identity, row["action"], row.get("item"), row.get("quantity"), row.get("price"), row.get("slot"))
                sample["decisionOccurrence"] = "REPEATED" if key in identities else "FIRST_OBSERVED"
                identities.add(key)
            else:
                sample["decisionOccurrence"] = "UNPROVEN_IDENTITY"
            # Delivery alone does not prove that the player could see this presentation.
            # Keep unsuccessful attempts and their outliers, but never let hidden/clipped
            # cards, missing identity or telemetry loss improve successful timing statistics.
            sample["nativeTimingEligible"] = sample["paintOutcome"] == "PAINTED" and sample["telemetryLoss"] == 0
            sample["publicationTimingEligible"] = (sample["nativeTimingEligible"]
                and sample["decisionOccurrence"] != "UNPROVEN_IDENTITY" and "durableAck" in s)
            sample["unmeasuredStages"] = sorted(STAGES - s.keys())
            if "durableAck" in s and "paintEnd" in s:
                sample["eligibleToPaintMs"] = (s["paintEnd"]-max(0, s["durableAck"]))/1000
            samples.append(sample)
        metrics = {}
        for source in ["MAIN", "SELECTED_SALE"]:
            rows = [r for r in samples if r["source"] == source]
            native = [r for r in rows if r["nativeTimingEligible"]]
            eligible = [r for r in rows if r["publicationTimingEligible"]]
            distinct = [r for r in eligible if r["decisionOccurrence"] == "FIRST_OBSERVED"]
            repeated = [r for r in eligible if r["decisionOccurrence"] == "REPEATED"]
            metrics[source] = {"allPresentations": len(rows), "decisionOccurrences": dict(Counter(r["decisionOccurrence"] for r in rows)),
                "cachedRender": distribution([r["cachedRenderMs"] for r in native if "cachedRenderMs" in r]),
                "eligiblePublication": distribution([r["eligiblePublicationMs"] for r in eligible]),
                "postAckPublication": distribution([r["postAckPublicationMs"] for r in eligible]),
                "distinctEligiblePublication": distribution([r["eligiblePublicationMs"] for r in distinct]),
                "repeatedEligiblePublication": distribution([r["eligiblePublicationMs"] for r in repeated]),
                "distinctEligibleToPaint": distribution([r["eligibleToPaintMs"] for r in distinct if "eligibleToPaintMs" in r]),
                "attemptedPublication": distribution([r["eligiblePublicationMs"] for r in rows if "eligiblePublicationMs" in r]),
                "excludedPublicationSamples": len(rows)-len(eligible),
                "overTarget": [r["trace"] for r in rows if r.get("cachedRenderMs", 0) > 100 or r.get("eligiblePublicationMs", 0) > 1000]}
        return {"schemaVersion": 2, "records": self.records, "invalid": self.invalid, "conflicts": self.conflicts,
            "duplicates": self.duplicates, "missingStages": missing, "samples": samples, "metrics": metrics,
            "outcomes": [dict(source=k[0], action=k[1], delivery=k[2], paint=k[3], count=v) for k, v in sorted(outcomes.items())],
            "scope": "Completed visible Swing paint passes, not OS compositor/occlusion proof. Repeats, waits and unmeasured stages retained. Selected-sale eligibility starts no earlier than its request.",
            "acceptancePassed": False}
