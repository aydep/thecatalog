"""Merges tools/descriptions/<lang>/*.json (item id -> text) into the mod's language files as thecatalog.desc.* keys.

Usage: python tools/merge_descriptions.py
Batch files map item ids like "minecraft:iron_ingot" to 3-5 sentences. Existing thecatalog.desc.* keys are replaced.
"""
import collections, glob, json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for lang_dir in sorted(glob.glob(os.path.join(ROOT, "tools", "descriptions", "*"))):
    lang = os.path.basename(lang_dir)
    descriptions = collections.OrderedDict()
    for batch in sorted(glob.glob(os.path.join(lang_dir, "*.json"))):
        for item, text in json.load(open(batch, encoding="utf-8")).items():
            namespace, path = item.split(":")
            descriptions[f"thecatalog.desc.{namespace}.{path.replace('/', '.')}"] = text
    target = os.path.join(ROOT, "src", "main", "resources", "assets", "thecatalog", "lang", f"{lang}.json")
    keys = json.load(open(target, encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
    keys = collections.OrderedDict((k, v) for k, v in keys.items() if not k.startswith("thecatalog.desc."))
    keys.update(descriptions)
    with open(target, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(keys, ensure_ascii=False, indent=2) + "\n")
    print(f"{lang}: {len(descriptions)} descriptions")
