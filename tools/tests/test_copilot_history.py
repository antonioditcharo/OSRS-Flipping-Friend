import csv
import tempfile
import unittest
from pathlib import Path
from tools.copilot_history import COLUMNS, TRANSACTION_COLUMNS, markdown, read_export, read_transactions, summarize


class CopilotHistoryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.source = Path(self.temporary.name) / "flips.csv"

    def write(self, *rows):
        with self.source.open("w", encoding="utf-8-sig", newline="") as file:
            writer = csv.writer(file)
            writer.writerow(COLUMNS)
            writer.writerows(rows)

    def finished(self, profit=98, each=49):
        return ["2025-01-01T01:00:00Z", "2025-01-01T02:00:00Z", "player", "Item, with comma",
                "FINISHED", 2, 2, 100, 150, 2, profit, each]

    def test_preserves_historical_tax_and_does_not_invent_gp_per_hour(self):
        self.write(self.finished())
        before = self.source.read_bytes()
        report = summarize(read_export(self.source))
        self.assertEqual(98, report["totals"]["reported_profit_gp"])
        self.assertEqual(2, report["totals"]["reported_tax_gp"])
        self.assertIsNone(report["gp_per_hour"])
        self.assertTrue(all(value is None for value in report["cadence_results"].values()))
        self.assertEqual(before, self.source.read_bytes())
        self.assertNotIn("player", markdown(report))

    def test_open_positions_keep_partial_profit_and_unsold_cost_separate(self):
        self.write(["2026-01-01T00:00:00Z", "2026-01-01T01:00:00Z", "p", "Stock",
                    "SELLING", 10, 4, 100, 150, 12, 188, 47])
        report = summarize(read_export(self.source))
        self.assertEqual(188, report["open"]["reported_profit_gp"])
        self.assertEqual(0, report["finished"]["reported_profit_gp"])
        self.assertEqual(600, report["remaining_stock"]["acquisition_cost_floor_gp"])
        self.assertEqual(606, report["remaining_stock"]["acquisition_cost_upper_exclusive_gp"])

    def test_integer_price_floors_do_not_replace_source_profit(self):
        self.write(self.finished(99, 49))
        report = summarize(read_export(self.source))
        self.assertEqual(99, report["totals"]["reported_profit_gp"])
        self.assertEqual([], report["quality"]["finished_profit_outside_average_price_bounds"])

    def test_losses_use_java_truncation_toward_zero(self):
        self.write(self.finished(-3, -1))
        self.assertEqual(-3, read_export(self.source)["rows"][0]["reported_profit_gp"])

    def test_duplicate_rows_are_preserved_and_flagged(self):
        self.write(self.finished(), self.finished())
        report = summarize(read_export(self.source))
        self.assertEqual(2, report["totals"]["rows"])
        self.assertEqual(1, report["quality"]["exact_duplicate_rows"])

    def test_rejects_missing_timezone_impossible_quantities_and_bad_status(self):
        for index, value in ((0, "2025-01-01T01:00:00"), (6, 3), (4, "INJECTED"), (10, "=1+1")):
            with self.subTest(index=index):
                row = self.finished()
                row[index] = value
                self.write(row)
                with self.assertRaises(ValueError):
                    read_export(self.source)

    def test_buy_only_export_has_no_invented_sale_cutoff(self):
        self.write(["2026-01-01T00:00:00Z", "", "p", "Stock", "BUYING", 1, 0, 100, 0, 0, 0, 0])
        self.assertIsNone(summarize(read_export(self.source))["coverage"]["last_sale_utc"])

    def test_transactions_preserve_duplicate_identity_and_untrusted_tax(self):
        self.write(self.finished())
        transactions = Path(self.temporary.name) / "transactions.csv"
        row = ["2025-01-01T02:00:00Z", "player", "SELL", "Item, with comma", 2, 300, 6, 150, "YES"]
        with transactions.open("w", encoding="utf-8", newline="") as file:
            writer = csv.writer(file)
            writer.writerow(TRANSACTION_COLUMNS)
            writer.writerows([row, row])
        before = transactions.read_bytes()
        data = read_transactions(transactions, read_export(self.source))
        self.assertEqual(1, data["summary"]["exact_duplicate_rows"])
        self.assertEqual(12, data["summary"]["exported_tax_estimate_gp"])
        self.assertEqual(4, data["summary"]["comparable_quantity_check"]["execution_sold"])
        self.assertIsNone(data["summary"]["execution_gp_per_hour"])
        self.assertEqual(before, transactions.read_bytes())


if __name__ == "__main__":
    unittest.main()
