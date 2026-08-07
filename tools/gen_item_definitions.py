"""Writes the assets/<ns>/items/*.json that 26.2 needs, one per registered item.

New since 1.21.4 and absent from the Forge tree entirely. An item now has two
files: the model, which says what it looks like (`models/item/x.json`, which
the Forge tree already has), and a *definition*, which says which model to use
and can switch between models on the item's state. Without the definition an
item has no model at all -- which is the black-and-magenta chequer.

The list of items comes from DdItems.java rather than from the models folder:
the models folder has 330 files, most of them for cards and items this fork
has not registered yet, and writing definitions for things that do not exist
would be inventing content rather than porting it.
"""
import io
import json
import os
import re

ITEMS_JAVA = "src/main/java/de/cas_ual_ty/dueldimension/DdItems.java"
OUT = "src/main/resources/assets/dueldimension/items"
MODELS = "src/main/resources/assets/dueldimension/models/item"
NAMESPACE = "dueldimension"


def registered():
    """Every name passed to register() in DdItems."""
    src = io.open(ITEMS_JAVA, encoding="utf-8").read()
    return sorted(set(re.findall(r'register\("([a-z0-9_]+)"', src)))


def main():
    os.makedirs(OUT, exist_ok=True)
    written, missing = [], []
    for name in registered():
        if not os.path.isfile(os.path.join(MODELS, name + ".json")):
            missing.append(name)
            continue
        definition = {"model": {"type": "minecraft:model",
                                "model": "%s:item/%s" % (NAMESPACE, name)}}
        path = os.path.join(OUT, name + ".json")
        io.open(path, "w", encoding="utf-8", newline="\n").write(
            json.dumps(definition, indent=2) + "\n")
        written.append(name)

    print("wrote %d item definitions" % len(written))
    for name in written:
        print("   ", name)
    if missing:
        print("no model in models/item for: %s" % ", ".join(missing))


if __name__ == "__main__":
    main()
