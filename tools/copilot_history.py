"""Read-only ingestion of Flipping Copilot's flip-level CSV export.

These rows are flip summaries, not executions or sessions. Reported profit/tax
remain authoritative source figures; integer average prices are truncated.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import re
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from statistics import median

COLUMNS = (
    "First buy time", "Last sell time", "Account", "Item", "Status", "Bought", "Sold",
    "Avg. buy price", "Avg. sell price", "Tax", "Profit", "Profit ea.",
)
NUMERIC = COLUMNS[5:]
STATUSES = {"BUYING", "SELLING", "FINISHED"}
TRANSACTION_COLUMNS = ("Timestamp", "Account", "Side", "Item", "Quantity", "Paid/Received", "Tax", "Price ea.", "Part of Flip")


def _timestamp(value: str, line: int, field: str) -> datetime | None:
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            raise ValueError("timezone missing")
        return parsed.astimezone(timezone.utc)
    except ValueError as error:
        raise ValueError(f"row {line}: invalid {field}: {error}") from error


def _integer(value: str, line: int, field: str) -> int:
    if not re.fullmatch(r"-?\d+", value or ""):
        raise ValueError(f"row {line}: {field} must be an integer")
    result = int(value)
    if field not in {"Profit", "Profit ea."} and result < 0:
        raise ValueError(f"row {line}: {field} cannot be negative")
    return result


def read_export(path: Path) -> dict:
    """Validate every row before returning an immutable-source-derived import."""
    raw = path.read_bytes()
    reader = csv.DictReader(io.StringIO(raw.decode("utf-8-sig"), newline=""))
    if tuple(reader.fieldnames or ()) != COLUMNS:
        raise ValueError("CSV header must match the Flipping Copilot export schema")
    rows, fingerprints = [], Counter()
    aliases = {}
    for line, source in enumerate(reader, 2):
        if None in source or any(value is None for value in source.values()):
            raise ValueError(f"row {line}: incorrect column count")
        for field in ("Account", "Item", "Status"):
            if not source[field].strip():
                raise ValueError(f"row {line}: {field} is required")
        status = source["Status"]
        if status not in STATUSES:
            raise ValueError(f"row {line}: unsupported status {status!r}")
        values = {field: _integer(source[field], line, field) for field in NUMERIC}
        bought, sold = values["Bought"], values["Sold"]
        opened = _timestamp(source["First buy time"], line, "First buy time")
        closed = _timestamp(source["Last sell time"], line, "Last sell time")
        if opened is None or bought <= 0 or sold > bought:
            raise ValueError(f"row {line}: invalid acquisition or disposal quantity/time")
        if sold > 0 and (closed is None or closed < opened):
            raise ValueError(f"row {line}: sold stock requires a valid sale timestamp")
        if status == "FINISHED" and sold != bought:
            raise ValueError(f"row {line}: finished flip must be fully sold")
        if status != "FINISHED" and sold == bought:
            raise ValueError(f"row {line}: open flip has no remaining stock")
        if sold == 0 and (closed is not None or values["Tax"] or values["Profit"] or values["Profit ea."]):
            raise ValueError(f"row {line}: unsold flip cannot report sale time/tax/profit")
        # Java integer division truncates toward zero, including losses.
        profit_each = (abs(values["Profit"]) // sold) * (-1 if values["Profit"] < 0 else 1) if sold else 0
        if values["Profit ea."] != profit_each:
            raise ValueError(f"row {line}: profit per item does not match reported profit")
        fingerprint = hashlib.sha256(json.dumps(source, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        fingerprints[fingerprint] += 1
        alias = aliases.setdefault(source["Account"], f"account_{len(aliases) + 1}")
        rows.append({
            "source_row": line, "source_row_sha256": fingerprint, "account": alias,
            "account_fingerprint": hashlib.sha256(source["Account"].encode()).hexdigest(),
            "item": source["Item"], "status": status,
            "first_buy_time": opened.isoformat(),
            "last_sell_time": closed.isoformat() if closed else None,
            "bought": bought, "sold": sold, "remaining": bought - sold,
            "average_buy_price_floor": values["Avg. buy price"],
            "average_sell_price_floor": values["Avg. sell price"],
            "reported_tax_gp": values["Tax"], "reported_profit_gp": values["Profit"],
            "holding_seconds": int((closed - opened).total_seconds()) if closed else None,
        })
    if not rows:
        raise ValueError("export contains no flip records")
    return {
        "schema_version": 1,
        "source": {"filename": path.name, "sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw)},
        "rows": rows,
        "quality": {"exact_duplicate_rows": sum(count - 1 for count in fingerprints.values()),
                    "account_count": len(aliases), "row_count": len(rows)},
    }


def summarize(data: dict) -> dict:
    rows = data["rows"]
    finished = [row for row in rows if row["status"] == "FINISHED"]
    opened = [row for row in rows if row["status"] != "FINISHED"]
    accounts, items = defaultdict(list), defaultdict(list)
    for row in rows:
        accounts[row["account"]].append(row)
        items[row["item"]].append(row)

    def metrics(group: list[dict]) -> dict:
        realized = [row for row in group if row["sold"]]
        completed = [row for row in group if row["status"] == "FINISHED"]
        durations = [row["holding_seconds"] / 60 for row in completed]
        return {
            "rows": len(group), "finished_flips": len(completed),
            "reported_profit_gp": sum(row["reported_profit_gp"] for row in group),
            "reported_tax_gp": sum(row["reported_tax_gp"] for row in group),
            "profitable_rows": sum(row["reported_profit_gp"] > 0 for row in realized),
            "losing_rows": sum(row["reported_profit_gp"] < 0 for row in realized),
            "losses_gp": sum(row["reported_profit_gp"] for row in realized if row["reported_profit_gp"] < 0),
            "median_completed_holding_minutes": median(durations) if durations else None,
        }

    # Average prices in the public client are integer floors. For finished flips
    # the exact profit must be inside the corresponding buy/sell total interval.
    rounding_violations = []
    for row in finished:
        quantity = row["sold"]
        approximate = quantity * (row["average_sell_price_floor"] - row["average_buy_price_floor"]) - row["reported_tax_gp"]
        if abs(row["reported_profit_gp"] - approximate) > quantity - 1:
            rounding_violations.append(row["source_row"])
    unsold_floor = sum(row["remaining"] * row["average_buy_price_floor"] for row in opened)
    unsold_upper_exclusive = sum(row["remaining"] * (row["average_buy_price_floor"] + 1) for row in opened)
    return {
        "schema_version": 1, "source": data["source"], "quality": {
            **data["quality"], "status_counts": dict(Counter(row["status"] for row in rows)),
            "finished_profit_outside_average_price_bounds": rounding_violations,
        },
        "coverage": {"first_buy_utc": min(row["first_buy_time"] for row in rows),
                     "last_sale_utc": max((row["last_sell_time"] for row in rows if row["last_sell_time"] is not None), default=None)},
        "totals": metrics(rows), "finished": metrics(finished), "open": metrics(opened),
        "remaining_stock": {"flip_rows": len(opened), "acquisition_cost_floor_gp": unsold_floor,
                            "acquisition_cost_upper_exclusive_gp": unsold_upper_exclusive,
                            "valuation": "cost interval from truncated averages; not current market value"},
        "accounts": {key: metrics(group) for key, group in sorted(accounts.items())},
        "top_items": sorted(({"item": item, **metrics(group)} for item, group in items.items()),
                            key=lambda item: item["reported_profit_gp"], reverse=True)[:10],
        "gp_per_hour": None,
        "cadence_results": {"5m": None, "30m": None, "2h": None, "8h": None},
        "limitations": [
            "Flip summaries are not raw fill events and contain no item IDs or stable flip IDs.",
            "No session duration, attendance/cadence labels, starting equity, or current liquidation prices are supplied.",
            "Profit and tax are Copilot-reported figures; historical taxes are not recomputed at today's rate.",
            "Rounded average prices cannot recover exact acquisition cost or sale proceeds.",
            "Remaining stock has acquisition-cost estimates, not a mark-to-market valuation.",
            "Last-sale groups and holding durations are not session profit/hour or a cash-flow timeline.",
            "Exact duplicate rows are retained and flagged rather than silently removed.",
        ],
    }


def read_transactions(path: Path, flips: dict) -> dict:
    raw = path.read_bytes()
    reader = csv.DictReader(io.StringIO(raw.decode("utf-8-sig"), newline=""))
    if tuple(reader.fieldnames or ()) != TRANSACTION_COLUMNS:
        raise ValueError("CSV header must match the Flipping Copilot transaction schema")
    rows, fingerprints = [], Counter()
    for line, source in enumerate(reader, 2):
        if None in source or any(value is None for value in source.values()):
            raise ValueError(f"transaction row {line}: incorrect column count")
        if source["Side"] not in {"BUY", "SELL"} or source["Part of Flip"] not in {"YES", "NO"}:
            raise ValueError(f"transaction row {line}: invalid side or flip membership")
        if not source["Account"].strip() or not source["Item"].strip():
            raise ValueError(f"transaction row {line}: account and item are required")
        time = _timestamp(source["Timestamp"], line, "Timestamp")
        if time is None:
            raise ValueError(f"transaction row {line}: timestamp is required")
        numbers = {field: _integer(source[field], line, field)
                   for field in ("Quantity", "Paid/Received", "Tax", "Price ea.")}
        if numbers["Quantity"] <= 0 or (source["Side"] == "BUY" and numbers["Tax"] != 0):
            raise ValueError(f"transaction row {line}: invalid quantity or purchase tax")
        fingerprint = hashlib.sha256(json.dumps(source, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        fingerprints[fingerprint] += 1
        rows.append({"source_row": line, "source_row_sha256": fingerprint,
                     "account_fingerprint": hashlib.sha256(source["Account"].encode()).hexdigest(),
                     "timestamp": time.isoformat(), "side": source["Side"], "item": source["Item"],
                     "quantity": numbers["Quantity"], "gross_paid_received_gp": numbers["Paid/Received"],
                     "exported_tax_estimate_gp": numbers["Tax"], "offer_price_gp": numbers["Price ea."],
                     "part_of_flip": source["Part of Flip"] == "YES"})
    if not rows:
        raise ValueError("transaction export contains no records")
    accounts = {row["account_fingerprint"] for row in rows}
    flip_accounts = {row["account_fingerprint"] for row in flips["rows"]}
    cutoff = max((row["last_sell_time"] for row in flips["rows"] if row["last_sell_time"]), default=None)
    comparable = [row for row in rows if row["part_of_flip"] and row["account_fingerprint"] in flip_accounts
                  and cutoff is not None and row["timestamp"] <= cutoff]
    flip_subset = [row for row in flips["rows"] if row["account_fingerprint"] in accounts]
    summary = {
        "source": {"filename": path.name, "sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw)},
        "row_count": len(rows), "account_count": len(accounts),
        "flip_accounts_without_transactions": len(flip_accounts - accounts),
        "side_counts": dict(Counter(row["side"] for row in rows)),
        "part_of_flip_rows": sum(row["part_of_flip"] for row in rows),
        "non_flip_rows": sum(not row["part_of_flip"] for row in rows),
        "exact_duplicate_rows": sum(count - 1 for count in fingerprints.values()),
        "first_execution_utc": min(row["timestamp"] for row in rows),
        "last_execution_utc": max(row["timestamp"] for row in rows),
        "executions_after_flip_export_cutoff": sum(row["timestamp"] > cutoff for row in rows) if cutoff else None,
        "comparable_quantity_check": {
            "cutoff_utc": cutoff,
            "flip_bought": sum(row["bought"] for row in flip_subset),
            "execution_bought": sum(row["quantity"] for row in comparable if row["side"] == "BUY"),
            "flip_sold": sum(row["sold"] for row in flip_subset),
            "execution_sold": sum(row["quantity"] for row in comparable if row["side"] == "SELL"),
        },
        "exported_tax_estimate_gp": sum(row["exported_tax_estimate_gp"] for row in rows),
        "tax_semantics": "Calculated by the export client; not observed historical GE tax.",
        "execution_gp_per_hour": None,
        "limitations": [
            "No transaction IDs, flip IDs, item IDs, offer IDs, or placement/cancellation timestamps are exported.",
            "Identical rows may be separate fills; retain them and flag ambiguous identity.",
            "Part-of-flip records and non-flip records are separate populations.",
            "The exports have different account coverage and cutoffs; do not join solely by item name and time.",
            "The public transaction exporter derives Tax using the current calculator and signed sell quantity.",
            "Derived Tax may differ from stored historical tax, floor rounding, and the per-item cap; it cannot establish net cash flow.",
            "Fill timestamps do not establish session attendance, offer placement time, or checking cadence.",
        ],
    }
    return {"schema_version": 1, "rows": rows, "summary": summary}


def _cell(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace("|", "\\|").replace("\n", " ").replace("\r", " ")


def markdown(report: dict) -> str:
    totals, stock = report["totals"], report["remaining_stock"]
    lines = ["# Copilot historical baseline", "",
        f"Source: `{report['source']['filename']}`; SHA-256 `{report['source']['sha256']}`.", "",
        f"{totals['rows']:,} flip summaries across {report['quality']['account_count']} accounts. "
        f"Copilot reports **{totals['reported_profit_gp']:,} GP net profit** and **{totals['reported_tax_gp']:,} GP tax**.", "",
        f"{report['finished']['finished_flips']:,} completed flips report {report['finished']['reported_profit_gp']:,} GP profit. "
        f"{stock['flip_rows']} open positions report {report['open']['reported_profit_gp']:,} GP profit on already sold units.", "",
        f"Remaining stock has estimated acquisition cost in [{stock['acquisition_cost_floor_gp']:,}, "
        f"{stock['acquisition_cost_upper_exclusive_gp']:,}) GP. This is not liquidation value.", "",
        f"Source coverage: {report['coverage']['first_buy_utc']} to {report['coverage']['last_sale_utc']}.", "",
        "**Account GP/hour and the 5m/30m/2h/8h comparisons remain unavailable:** session hours and cadence labels are absent.", "",
        "| Account alias | Flip records | Reported net profit (GP) | Reported tax (GP) | Losing rows |", "| --- | ---: | ---: | ---: | ---: |"]
    for account, values in report["accounts"].items():
        lines.append(f"| {account} | {values['rows']:,} | {values['reported_profit_gp']:,} | {values['reported_tax_gp']:,} | {values['losing_rows']:,} |")
    lines.extend(["", "| Item | Reported profit (GP) | Flip records |", "| --- | ---: | ---: |"])
    for item in report["top_items"]:
        lines.append(f"| {_cell(item['item'])} | {item['reported_profit_gp']:,} | {item['rows']:,} |")
    quality = report["quality"]
    lines.extend(["", f"Checks: {quality['exact_duplicate_rows']} exact duplicates; "
                  f"{len(quality['finished_profit_outside_average_price_bounds'])} finished profits outside the integer-average bounds.", "",
                  "The importer rejects malformed schema/numbers/times, impossible quantities, inconsistent open/finished state, and inconsistent per-item profit.", ""])
    lines.extend(f"- {limitation}" for limitation in report["limitations"])
    if "transactions" in report:
        tx = report["transactions"]
        lines.extend(["", "## Execution export", "",
            f"{tx['row_count']:,} execution records across {tx['account_count']} account(s): "
            f"{tx['part_of_flip_rows']:,} marked as part of a flip and {tx['non_flip_rows']:,} other records.", "",
            f"{tx['exact_duplicate_rows']} identical rows are retained. "
            f"{tx['flip_accounts_without_transactions']} flip-history account(s) lack a corresponding transaction export. "
            f"{tx['executions_after_flip_export_cutoff']} executions occur after the flip-history cutoff.", "",
            f"Transaction source SHA-256: `{tx['source']['sha256']}`.", "",
            "The transaction CSV's Tax column is an exporter-calculated estimate, not stored historical tax. "
            "The public exporter applies the current tax calculator to signed sell quantities; this can change rounding and cap behavior. "
            "Do not subtract it from historical receipts and call the result observed net profit.", "",
            "Quantity reconciliation for the shared account population through the flip-export cutoff:", "",
            "| Quantity | Flip summaries | Part-of-flip execution rows |", "| --- | ---: | ---: |",
            f"| Bought | {tx['comparable_quantity_check']['flip_bought']:,} | {tx['comparable_quantity_check']['execution_bought']:,} |",
            f"| Sold | {tx['comparable_quantity_check']['flip_sold']:,} | {tx['comparable_quantity_check']['execution_sold']:,} |", ""])
        lines.extend(f"- {limitation}" for limitation in tx["limitations"])
    reference = "https://github.com/cbrewitt/flipping-copilot/blob/cb9251e8f46eb85762401ab1158e4caad272a424/src/main/java/com/flippingcopilot/ui/flipsdialog/"
    lines.extend(["", "Exporter semantics verified against Flipping Copilot's official client at commit "
                  "`cb9251e8f46eb85762401ab1158e4caad272a424`: "
                  f"[integer-average prices and profit per item]({reference}FlipTableUtil.java#L30), "
                  f"[derived transaction tax]({reference}TransactionsPanel.java#L283).", ""])
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--transactions", type=Path)
    arguments = parser.parse_args()
    # Do not allow an output to overwrite the supplied source.
    outputs = [arguments.output_dir / name for name in ("normalized.json", "summary.json", "baseline.md")]
    inputs = [arguments.source] + ([arguments.transactions] if arguments.transactions else [])
    targets = outputs + [arguments.output_dir / "transactions-normalized.json"]
    if any(source.resolve() in [path.resolve() for path in targets] for source in inputs):
        parser.error("output would overwrite the source")
    data = read_export(arguments.source)
    report = summarize(data)
    transactions = read_transactions(arguments.transactions, data) if arguments.transactions else None
    if transactions is not None:
        report["transactions"] = transactions["summary"]
    arguments.output_dir.mkdir(parents=True, exist_ok=True)
    outputs[0].write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    outputs[1].write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    outputs[2].write_text(markdown(report), encoding="utf-8")
    if transactions is not None:
        (arguments.output_dir / "transactions-normalized.json").write_text(json.dumps(transactions, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"rows": report["totals"]["rows"], "profit_gp": report["totals"]["reported_profit_gp"],
                      "sha256": report["source"]["sha256"], "gp_per_hour": None}))


if __name__ == "__main__":
    main()
