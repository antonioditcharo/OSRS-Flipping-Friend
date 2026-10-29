"""Read client delivery traces without treating transport, WAITs or missing traces as acceptance."""
import argparse
from collections import Counter
import json
import math
import hashlib
from pathlib import Path
import re
from guidance_timing import TimingAudit

OUTCOME = re.compile(
    r"Guidance outcome: (\w+); proposed (\w+); visible (\w+); pending (\w+); schema 1; source (\w+); "
    r"trace ([^;]+); generation (\d+); revision (\d+); item (-?\d+); quantity (\d+); price (\d+); visible item (-?\d+); headline (.*)")
DELIVERY = re.compile(r"Guidance delivered: (-?\d+) ms from refresh request; capture/durable publication (-?\d+) ms, "
                      r"plan/ledger (-?\d+) ms, decision/delivery (-?\d+) ms; (\w+)")
ACK = re.compile(r"Guidance after verified frame acknowledgement: (-?\d+) ms; generation (\d+); revision (\d+); (\w+)")
ACTIONS = {"BUY", "SELL", "MODIFY_BUY", "MODIFY_SELL", "CANCEL", "COLLECT", "PACK", "UNPACK", "DECANT"}


def distribution(rows, field):
    values = sorted(row[field] for row in rows if row[field] >= 0)
    return {"count": len(values), "p50Ms": values[math.ceil(.5 * len(values)) - 1] if values else None,
            "p95Ms": values[math.ceil(.95 * len(values)) - 1] if values else None,
            "maxMs": max(values) if values else None}


def analyze(paths, since=None):
    timing = TimingAudit()
    outcomes, deliveries, acknowledgements, malformed, conflicts = [], [], [], [], []
    sources, decode_issues = [], []
    seen, measurements = {}, {}
    duplicates = 0
    counts = Counter()
    if len({Path(p).resolve() for p in paths}) != len(paths):
        raise ValueError("Each log file must be supplied once")
    for filename in paths:
        path = Path(filename)
        if not path.is_file() or path.stat().st_size > 256 * 1024**2:
            raise ValueError("Explicit bounded client logs required")
        current = None
        digest, read_bytes = hashlib.sha256(), 0
        with path.open("rb") as source:
            for line_number, raw in enumerate(source, 1):
                read_bytes += len(raw)
                if read_bytes > 256 * 1024**2:
                    raise ValueError("Growing client log exceeds the read budget")
                digest.update(raw)
                try:
                    line = raw.decode("utf-8")
                except UnicodeDecodeError:
                    # Older Windows log writers use platform bytes. Preserve unknown bytes as
                    # escapes rather than invent a charset or discard unrelated valid traces.
                    line = raw.decode("utf-8", errors="backslashreplace")
                    decode_issues.append({"file": str(path), "line": line_number})
                # This is an explicit wall-clock filter, never an elapsed-time calculation.
                if since and line[:19] < since:
                    continue
                origin = {"file": str(path), "line": line_number, "loggedAt": line[:19]}
                if "Guidance timing: " in line:
                    timing.add(line.split("Guidance timing: ", 1)[1], origin)
                    continue
                match = OUTCOME.search(line)
                if match:
                    outcome, proposed, visible, pending, channel, trace, generation, revision, item, quantity, price, shown, headline = match.groups()
                    value = dict(outcome=outcome, proposed=proposed, visible=visible, pending=pending, source=channel,
                                 trace=trace, generation=int(generation), revision=int(revision), item=int(item),
                                 quantity=int(quantity), price=int(price), visibleItem=int(shown), headline=headline.rstrip())
                    current = value
                    if trace in seen:
                        if seen[trace] == value:
                            duplicates += 1
                            continue
                        conflicts.append({**origin, "trace": trace, "previous": seen[trace], "current": value})
                    else:
                        seen[trace] = value
                    outcomes.append({**origin, **value})
                    counts[(channel, outcome, proposed, visible, pending)] += 1
                    continue
                if "Guidance outcome:" in line:
                    malformed.append({**origin, "kind": "UNPARSED_OUTCOME"})
                    current = None
                    continue
                match = DELIVERY.search(line)
                if match:
                    total, publication, plan, delivery, action = match.groups()
                    trace = current["trace"] if current and current["source"] == "MAIN" and current["outcome"] == "VISIBLE" and current["visible"] == action else None
                    value = dict(totalMs=int(total), publicationMs=int(publication), planMs=int(plan), deliveryMs=int(delivery), action=action, trace=trace)
                    target = deliveries
                    kind = "DELIVERY"
                else:
                    match = ACK.search(line)
                    if not match:
                        continue
                    latency, generation, revision, action = match.groups()
                    trace = current["trace"] if current and current["source"] == "MAIN" and current["outcome"] == "VISIBLE" and current["visible"] == action and current["generation"] == int(generation) and current["revision"] == int(revision) else None
                    value = dict(postAckMs=int(latency), generation=int(generation), revision=int(revision), action=action, trace=trace)
                    target = acknowledgements
                    kind = "POST_ACK"
                identity = (kind, trace)
                if trace and identity in measurements:
                    if measurements[identity] == value:
                        duplicates += 1
                        continue
                    conflicts.append({**origin, "trace": trace, "kind": kind, "previous": measurements[identity], "current": value})
                if trace:
                    measurements[identity] = value
                if any(number < 0 for key, number in value.items() if key.endswith("Ms")):
                    malformed.append({**origin, "kind": "INVALID_TIMING", "measurement": value})
                target.append({**origin, **value})
        sources.append({"file": str(path), "readPrefixBytes": read_bytes, "readPrefixSha256": digest.hexdigest()})
    linked = [r for r in acknowledgements if r["trace"] and r["action"] in ACTIONS and r["postAckMs"] >= 0]
    return {
        "schemaVersion": 1,
        "scope": "Recorded client delivery events; repeats may be the same working action. Not controlled trading, execution profit or native paint acceptance.",
        "sinceLocalLogClock": since,
        "sourcePrefixes": sources,
        "logDecoding": "UTF8_WITH_UNDECODABLE_BYTES_ESCAPED", "undecodableLogLines": decode_issues,
        "outcomes": outcomes,
        "matrix": [dict(source=k[0], outcome=k[1], proposed=k[2], visible=k[3], pending=k[4], count=v) for k, v in sorted(counts.items())],
        "saleToWait": [r for r in outcomes if r["proposed"] in {"SELL", "MODIFY_SELL"} and r["visible"] == "WAIT"],
        "delivery": deliveries, "postAck": acknowledgements,
        "duplicateTraceRecords": duplicates, "conflictingTraceRecords": conflicts, "unparsedOrInvalidRecords": malformed,
        "unlinkedMeasurements": sum(r["trace"] is None for r in deliveries + acknowledgements),
        "allDelivery": distribution(deliveries, "totalMs"),
        "linkedMainActionPostAck": distribution(linked, "postAckMs"),
        "postAckOverOneSecond": [r for r in linked if r["postAckMs"] > 1000],
        "cachedNativeRender": "UNMEASURED", "selectedSalePostAck": "UNMEASURED",
        "missingProposalOrDeliveryConclusion": "UNKNOWN: absence of a trace cannot prove a dropped action",
        "acceptancePassed": False,
        "correlatedTiming": timing.result(),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", nargs="+", type=Path)
    parser.add_argument("--since", help="Inclusive local log timestamp YYYY-MM-DD HH:MM:SS; no timezone conversion")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.since and not re.fullmatch(r"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}", args.since):
        parser.error("Invalid local log timestamp")
    if "local-data" not in args.output.resolve().parts or args.output.exists():
        parser.error("A new private local-data output is required")
    result = analyze(args.logs, args.since)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as out:
        json.dump(result, out, indent=2)
        out.write("\n")
    print(json.dumps({"outcomeRecords": len(result["outcomes"]), "saleToWait": len(result["saleToWait"]),
                      "linkedMainActionPostAck": result["linkedMainActionPostAck"], "acceptancePassed": False}))


if __name__ == "__main__":
    main()
