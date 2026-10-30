import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from guidance_timing import TimingAudit


class GuidanceTimingTest(unittest.TestCase):
    def row(self, trace="p:MAIN:1", source="MAIN", event="DELIVERY", **values):
        return dict(schemaVersion=2, trace=trace, source=source, event=event, action="SELL", item=1,
                    account="rs-"+"a"*64,
                    quantity=2, price=55, slot=-1, saleSource="origin", deliveryOutcome="VISIBLE", droppedTelemetryRecords=0,
                    stageMicros={"request": 0, "durableAck": 10000, "delivery": 20000}, **values)

    def add(self, audit, row):
        audit.add(json.dumps(row), {"file": "synthetic.log", "line": 1})

    def painted(self, audit, row, **values):
        at = row["stageMicros"]["delivery"]
        paint = dict(row, event="PAINT", paintOutcome="PAINTED", **values)
        paint["stageMicros"] = dict(row["stageMicros"], panelRequested=at+1000, edtDelivery=at+2000, paintEnd=at+3000)
        self.add(audit, paint)

    def test_missing_native_stages_and_waits_are_not_successful_native_samples(self):
        audit = TimingAudit()
        self.add(audit, self.row())
        row = self.row(trace="p:MAIN:2"); row["action"] = "WAIT"
        self.add(audit, row)
        result = audit.result()
        self.assertEqual(1, result["metrics"]["MAIN"]["allPresentations"])
        self.assertEqual(0, result["metrics"]["MAIN"]["cachedRender"]["count"])
        self.assertEqual(2, len(result["outcomes"]))
        self.assertFalse(result["acceptancePassed"])

    def test_correlates_interleaved_paths_keeps_outliers_and_separates_repeats(self):
        audit = TimingAudit()
        first = self.row(); second = self.row(trace="p:MAIN:2")
        second["stageMicros"]["delivery"] = 3_000_000
        self.add(audit, first); self.add(audit, second)
        for row in [second, first]:
            paint = dict(row, event="PAINT", paintOutcome="PAINTED")
            at = row["stageMicros"]["delivery"]
            paint["stageMicros"] = dict(row["stageMicros"], panelRequested=at+1000, edtDelivery=at+2000, paintEnd=at+150_000)
            self.add(audit, paint)
        result = audit.result(); metrics = result["metrics"]["MAIN"]
        self.assertEqual(2990, metrics["eligiblePublication"]["maxMs"])
        self.assertEqual(149, metrics["cachedRender"]["p95Ms"])
        self.assertEqual({"FIRST_OBSERVED": 1, "REPEATED": 1}, metrics["decisionOccurrences"])
        self.assertEqual(2, len(metrics["overTarget"]))

    def test_editor_eligibility_does_not_charge_the_time_before_selection_to_computation(self):
        audit = TimingAudit(); row = self.row(source="SELECTED_SALE")
        row["stageMicros"]["durableAck"] = -1_000_000
        self.add(audit, row); self.painted(audit, row)
        metrics = audit.result()["metrics"]["SELECTED_SALE"]
        self.assertEqual(20, metrics["eligiblePublication"]["maxMs"])
        self.assertEqual(1020, metrics["postAckPublication"]["maxMs"])

    def test_hidden_clipped_and_lost_telemetry_stay_explicit(self):
        audit = TimingAudit()
        for i, outcome in enumerate(["HIDDEN", "MINIMIZED", "CLIPPED", "SUPERSEDED_BEFORE_PAINT"]):
            row = self.row(trace="p:MAIN:"+str(i)); self.add(audit, row)
            paint = dict(row, event="PAINT", paintOutcome=outcome, droppedTelemetryRecords=2)
            paint["stageMicros"] = dict(row["stageMicros"], panelRequested=21000, paintEnd=22000)
            self.add(audit, paint)
        result = audit.result()
        self.assertEqual(0, result["metrics"]["MAIN"]["cachedRender"]["count"])
        self.assertEqual(0, result["metrics"]["MAIN"]["eligiblePublication"]["count"])
        self.assertEqual(4, result["metrics"]["MAIN"]["attemptedPublication"]["count"])
        self.assertTrue(all(r["telemetryLoss"] == 2 for r in result["samples"]))

    def test_repeated_fast_presentations_cannot_hide_a_slow_distinct_decision(self):
        audit = TimingAudit()
        for i in range(101):
            row = self.row(trace="p:MAIN:"+str(i))
            row["stageMicros"]["delivery"] = 3_000_000 if i == 0 else 20_000
            self.add(audit, row); self.painted(audit, row)
        metrics = audit.result()["metrics"]["MAIN"]
        self.assertEqual(10, metrics["eligiblePublication"]["p95Ms"])
        self.assertEqual(2990, metrics["distinctEligiblePublication"]["p95Ms"])
        self.assertEqual(2993, metrics["distinctEligibleToPaint"]["p95Ms"])
        self.assertEqual(100, metrics["repeatedEligiblePublication"]["count"])

    def test_painted_but_lost_telemetry_or_unknown_identity_cannot_qualify_publication(self):
        audit = TimingAudit()
        lost = self.row(); lost["droppedTelemetryRecords"] = 1
        self.add(audit, lost); self.painted(audit, lost)
        unknown = self.row(trace="p:MAIN:2"); unknown.pop("account")
        self.add(audit, unknown); self.painted(audit, unknown)
        missing = self.row(trace="p:MAIN:3"); missing["stageMicros"].pop("durableAck")
        self.add(audit, missing); self.painted(audit, missing)
        result = audit.result(); metrics = result["metrics"]["MAIN"]
        self.assertEqual(3, metrics["excludedPublicationSamples"])
        self.assertEqual(0, metrics["eligiblePublication"]["count"])
        self.assertEqual(2, metrics["cachedRender"]["count"])
        self.assertIn("durableAck", result["samples"][-1]["unmeasuredStages"])
        self.assertFalse(result["acceptancePassed"])

    def test_conflicts_duplicates_and_invalid_clock_are_retained(self):
        audit = TimingAudit(); row = self.row()
        self.add(audit, row); self.add(audit, row)
        changed = dict(row, price=56); self.add(audit, changed)
        bad = self.row(trace="p:MAIN:bad"); bad["stageMicros"]["workerStarted"] = -1
        self.add(audit, bad)
        result = audit.result()
        self.assertEqual(1, result["duplicates"]); self.assertEqual(1, len(result["conflicts"]))
        self.assertEqual(1, len(result["invalid"])); self.assertEqual([], result["samples"])

    def test_a_paint_cannot_borrow_another_order_or_rewrite_its_ack(self):
        for field in ["price", "stageMicros"]:
            audit = TimingAudit(); row = self.row(); self.add(audit, row)
            paint = dict(row, event="PAINT", paintOutcome="PAINTED")
            paint["stageMicros"] = dict(row["stageMicros"], panelRequested=21000, edtDelivery=22000, paintEnd=23000)
            if field == "price":
                paint["price"] = 56
            else:
                paint["stageMicros"]["durableAck"] = 10001
            self.add(audit, paint); result = audit.result()
            self.assertEqual([], result["samples"])
            self.assertEqual("PAINT_DELIVERY_MISMATCH", result["conflicts"][0]["event"])

    def test_identical_event_ids_from_different_accounts_are_independent(self):
        audit = TimingAudit(); first = self.row(); self.add(audit, first)
        second = self.row(trace="p:MAIN:2"); second["account"] = "rs-"+"b"*64; self.add(audit, second)
        unknown = self.row(trace="p:MAIN:3"); unknown.pop("account"); self.add(audit, unknown)
        self.assertEqual(["FIRST_OBSERVED", "FIRST_OBSERVED", "UNPROVEN_IDENTITY"], [r["decisionOccurrence"] for r in audit.result()["samples"]])

    def test_new_edt_entry_separates_queue_card_update_and_remaining_ui(self):
        audit=TimingAudit();row=self.row();self.add(audit,row)
        paint=dict(row,event="PAINT",paintOutcome="PAINTED")
        paint["stageMicros"]=dict(row["stageMicros"],panelRequested=21000,edtStarted=120000,
            cardUpdated=125000,edtDelivery=125000,criticalUiFinished=127000,paintEnd=130000)
        self.add(audit,paint);result=audit.result();self.assertEqual([],result["invalid"])
        sample=result["samples"][0]
        self.assertEqual(99,sample["edtQueueMs"]);self.assertEqual(5,sample["cardUpdateMs"])
        self.assertEqual(2,sample["remainingCriticalUiMs"])

    def test_v3_final_sale_provenance_counts_distinct_orders_without_changing_v2(self):
        audit=TimingAudit()
        for i in range(3):
            row=self.row(trace="p:SELECTED_SALE:"+str(i),source="SELECTED_SALE")
            row.pop("saleSource");row["schemaVersion"]=3
            row["decisionProvenance"]={"version":1,"kind":"SALE_AUTHORITY","account":row["account"],"source":"acquisition"}
            if i==2: row["price"]=56
            self.add(audit,row);self.painted(audit,row)
        result=audit.result();self.assertEqual([],result["invalid"])
        self.assertEqual(["FIRST_OBSERVED","REPEATED","FIRST_OBSERVED"],[r["decisionOccurrence"] for r in result["samples"]])
        self.assertEqual(2,result["metrics"]["SELECTED_SALE"]["distinctEligiblePublication"]["count"])

    def test_foreign_malformed_or_borrowed_sale_provenance_cannot_qualify(self):
        for invalid in ["account","source","kind","version","legacy","collect"]:
            audit=TimingAudit();row=self.row();row.pop("saleSource");row["schemaVersion"]=3
            row["decisionProvenance"]={"version":1,"kind":"SALE_AUTHORITY","account":row["account"],"source":"acquisition"}
            if invalid=="account": row["decisionProvenance"]["account"]="rs-"+"b"*64
            elif invalid=="source": row["decisionProvenance"]["source"]=" "
            elif invalid=="kind": row["decisionProvenance"]["kind"]="CHART"
            elif invalid=="version": row["decisionProvenance"]["version"]=2
            elif invalid=="legacy": row["schemaVersion"]=2
            elif invalid=="collect": row["action"]="COLLECT"
            self.add(audit,row);self.assertEqual(1,len(audit.result()["invalid"]))
            self.assertEqual([],audit.result()["samples"])
        audit=TimingAudit();row=self.row();row.pop("saleSource");row["schemaVersion"]=3
        self.add(audit,row);self.painted(audit,row)
        self.assertEqual("UNPROVEN_IDENTITY",audit.result()["samples"][0]["decisionOccurrence"])
