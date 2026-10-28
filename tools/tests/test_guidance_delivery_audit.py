"""Delivery evidence keeps waits, refusals, outliers and unknown coverage separate."""
from pathlib import Path
import tempfile
import unittest
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from analyze_guidance_delivery import analyze


class GuidanceDeliveryAuditTest(unittest.TestCase):
    def outcome(self, trace, proposed="SELL", visible="SELL", outcome="VISIBLE", pending="NONE", source="MAIN"):
        return ("2026-10-28 12:00:00 AKDT [Client] INFO plugin - Guidance outcome: " + outcome
                + "; proposed " + proposed + "; visible " + visible + "; pending " + pending
                + "; schema 1; source " + source + "; trace " + trace
                + "; generation 4; revision 8; item 1; quantity 2; price 110; visible item 1; headline Selected trade\n")

    def read(self, text):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "client.log"
            path.write_text(text, encoding="utf-8")
            return analyze([path])

    def test_waits_cannot_hide_action_tail_failures_or_become_native_acceptance(self):
        text = self.outcome("p:MAIN:1")
        text += "Guidance delivered: 1200 ms from refresh request; capture/durable publication 100 ms, plan/ledger 200 ms, decision/delivery 900 ms; SELL\n"
        text += "Guidance after verified frame acknowledgement: 1100 ms; generation 4; revision 8; SELL\n"
        text += self.outcome("p:MAIN:2", proposed="WAIT", visible="WAIT")
        text += "Guidance after verified frame acknowledgement: 10 ms; generation 4; revision 8; WAIT\n"
        result = self.read(text)
        self.assertEqual({"count": 1, "p50Ms": 1100, "p95Ms": 1100, "maxMs": 1100}, result["linkedMainActionPostAck"])
        self.assertEqual(1, len(result["postAckOverOneSecond"]))
        self.assertEqual(100, result["delivery"][0]["publicationMs"])
        self.assertEqual(2, len(result["postAck"]))
        self.assertEqual("UNMEASURED", result["cachedNativeRender"])
        self.assertEqual("UNMEASURED", result["selectedSalePostAck"])
        self.assertFalse(result["acceptancePassed"])

    def test_editor_override_deferred_sale_and_supersession_retain_distinct_evidence(self):
        result = self.read(self.outcome("p:MAIN:1", visible="WAIT", outcome="SELECTED_EDITOR_QUOTE")
                           + self.outcome("p:MAIN:2", visible="WAIT", outcome="DEFERRED_EDITOR", pending="SELL")
                           + self.outcome("p:SELECTED_SALE:3", visible="WAIT", outcome="SUPERSEDED", source="SELECTED_SALE"))
        self.assertEqual(["SELECTED_EDITOR_QUOTE", "DEFERRED_EDITOR", "SUPERSEDED"], [r["outcome"] for r in result["saleToWait"]])
        self.assertEqual("SELL", result["saleToWait"][1]["pending"])
        self.assertEqual(0, result["linkedMainActionPostAck"]["count"])
        self.assertIsNone(result["linkedMainActionPostAck"]["p95Ms"])

    def test_same_looking_legitimate_actions_keep_their_distinct_traces(self):
        first = self.outcome("p:MAIN:1")
        metric = "Guidance after verified frame acknowledgement: 900 ms; generation 4; revision 8; SELL\n"
        result = self.read(first + metric + first + metric + self.outcome("p:MAIN:2") + metric)
        self.assertEqual(2, len(result["outcomes"]))
        self.assertEqual(2, result["linkedMainActionPostAck"]["count"])
        self.assertEqual(2, result["duplicateTraceRecords"])

    def test_conflicts_invalid_clocks_and_missing_trace_linkage_are_not_discarded(self):
        result = self.read("Guidance delivered: 3000 ms from refresh request; capture/durable publication 2900 ms, plan/ledger 50 ms, decision/delivery 50 ms; SELL\n"
                           + self.outcome("p:MAIN:1")
                           + "Guidance after verified frame acknowledgement: -1 ms; generation 4; revision 8; SELL\n"
                           + self.outcome("p:MAIN:1", visible="WAIT", outcome="EXPIRED")
                           + "Guidance outcome: old-format\n"
                           + "Guidance after verified frame acknowledgement: 2000 ms; generation 9; revision 10; SELL\n")
        self.assertEqual(1, len(result["conflictingTraceRecords"]))
        self.assertEqual(2, len(result["unparsedOrInvalidRecords"]))
        self.assertEqual(2, result["unlinkedMeasurements"])
        self.assertEqual(2, len(result["postAck"]))
        self.assertEqual(3000, result["allDelivery"]["maxMs"])
        self.assertEqual(0, result["linkedMainActionPostAck"]["count"])
        self.assertIn("UNKNOWN", result["missingProposalOrDeliveryConclusion"])

    def test_explicit_log_filter_does_not_invent_elapsed_time_from_wall_clocks(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "client.log"
            path.write_text(self.outcome("p:MAIN:1") + self.outcome("p:MAIN:2").replace("12:00:00", "13:00:00"), encoding="utf-8")
            result = analyze([path], "2026-10-28 12:30:00")
            self.assertEqual(["p:MAIN:2"], [r["trace"] for r in result["outcomes"]])
            with self.assertRaises(ValueError):
                analyze([path, path])

    def test_legacy_windows_bytes_are_preserved_without_suppressing_valid_delivery_evidence(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "client.log"
            original = self.outcome("p:MAIN:1").replace("Selected trade", "Selected \u2014 trade").encode("cp1252")
            path.write_bytes(original)
            result = analyze([path])
            self.assertEqual(1, len(result["outcomes"]))
            self.assertIn("\\x97", result["outcomes"][0]["headline"])
            self.assertEqual(1, len(result["undecodableLogLines"]))
            self.assertEqual(len(original), result["sourcePrefixes"][0]["readPrefixBytes"])
            self.assertEqual(__import__("hashlib").sha256(original).hexdigest(), result["sourcePrefixes"][0]["readPrefixSha256"])
            self.assertFalse(result["acceptancePassed"])
