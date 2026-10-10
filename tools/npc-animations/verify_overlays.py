"""Verify a rebuilt cache against its accepted baseline and explicit patch report."""
import argparse
import json
from pathlib import Path

def verify(before, after, report):
    assert before["revision"] == after["revision"] == report["revision"] == 240
    assert before["sequenceDefinitions"] == after["sequenceDefinitions"], "Client animation assets changed"
    assert before["serverSequences"] == after["serverSequences"], "Sequence timing or priorities changed"
    assert before["weapons"] == after["weapons"], "Weapon models or parameters changed"
    expected = {p["id"]: p for p in report["patches"]}
    baseline = {n["id"]: n for n in before["npcs"]}
    candidate = {n["id"]: n for n in after["npcs"]}
    assert baseline.keys() == candidate.keys(), "NPC definitions added or removed"
    changed = 0
    for id, n in baseline.items():
        original = n["definition"]
        actual = candidate[id]["definition"]
        wanted = dict(original)
        if id in expected:
            wanted["paramsRaw"] = dict(original["paramsRaw"] or {})
            for key, symbol in expected[id]["params"].items():
                param = str(before["params"][key])
                assert param not in wanted["paramsRaw"], f"Overwrote existing {n['symbol']} {key}"
                wanted["paramsRaw"][param] = before["sequences"][symbol]
        assert actual == wanted, f"Unexpected definition change: {id} {n['symbol']}"
        assert candidate[id]["models"] == n["models"], f"NPC model changed: {id}"
        changed += actual != original
    assert changed == report["patchedNpcs"], "Patch was not packed or unexpectedly changed coverage"
    return {"revision": 240, "npcDefinitionsChecked": len(baseline), "patchedNpcs": changed,
            "parametersAdded": sum(report["parameters"].values()),
            "statsModelsAndOtherParametersPreserved": True, "clientSequencesAndTimingsPreserved": True}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("before", "after", "report", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    result = verify(*(json.loads(p.read_text()) for p in (args.before, args.after, args.report)))
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result))

if __name__ == "__main__":
    main()
