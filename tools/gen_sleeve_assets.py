"""Writes every asset file the card sleeves need, driven by CardSleevesType.

There are 37 sleeve designs and eight files each before the lang line, which is
296 hand-written files nobody would keep in step with the enum. So the enum is
the source of truth here exactly as it is in DdItems' registration loop: this
reads the constants out of the Java and writes what each one implies.

Per sleeve:

  models/item/sleeves_X.json          the model, art at the default 16px size
  models/item/sleeves_X_{16..1024}.json   one per size the client can switch to
  items/sleeves_X.json                the 26.2 item-model DEFINITION
  lang/en_us.json                     one line, only if it is missing

The definition is the file that did not exist before 1.21.4 and has no
counterpart in the Forge tree. Without it an item has no model at all, which is
the black-and-magenta chequer -- so it is generated here rather than left to
tools/gen_item_definitions.py, which reads DdItems for names spelt out
literally and cannot see a loop.

Formatting mirrors the files already on disk byte for byte, which matters here
more than usual: 256 of these 296 models were already present (the 32 sleeves
this fork inherited), so getting the shape wrong would rewrite 256 correct
files into a cosmetic diff. models/item/sleeves_black.json reads

    {
      "parent": "minecraft:item/generated",
      "textures": {
        "layer0": "dueldimension:item/16/sleeves_black"
      }
    }

and each sized variant parents THAT rather than item/generated, overriding only
the texture -- models/item/sleeves_black_1024.json:

    {
      "parent": "dueldimension:item/sleeves_black",
      "textures": {
        "layer0": "dueldimension:item/1024/sleeves_black"
      }
    }

Both are CRLF, two-space indent, no trailing newline. The definitions follow
items/blanc_card.json instead -- LF, two-space indent, trailing newline:

    {
      "model": {
        "type": "minecraft:model",
        "model": "dueldimension:item/blanc_card"
      }
    }

Run from the repository root:  python tools/gen_sleeve_assets.py
"""
import io
import json
import os
import re

ENUM_JAVA = "src/main/java/de/cas_ual_ty/dueldimension/card/CardSleevesType.java"
ASSETS = "src/main/resources/assets/dueldimension"
MODELS = os.path.join(ASSETS, "models", "item")
ITEMS = os.path.join(ASSETS, "items")
TEXTURES = os.path.join(ASSETS, "textures", "item")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
NAMESPACE = "dueldimension"

SIZES = [16, 32, 64, 128, 256, 512, 1024]

# The size the bare model points at, which is what an item with no size
# preference resolves to. Both trees' existing models use 16.
DEFAULT_SIZE = 16

# Display names that title-casing would get wrong. The resource names keep the
# single 'n' of the art on disk; the English prose does not have to, and the
# Millennium items already in this file spell it with two.
LABELS = {
    "millenium_void": "Millennium Void Sleeves",
    "millenium_voidred": "Millennium Void Red Sleeves",
    "millenium_red": "Millennium Red Sleeves",
    "millenium_blue": "Millennium Blue Sleeves",
    "millenium_white": "Millennium White Sleeves",
}


def sleeve_names():
    """The `name` field of every constant in CardSleevesType, in enum order.

    Read from the source rather than hardcoded so that appending a constant is
    still the only edit a new sleeve needs.
    """
    src = io.open(ENUM_JAVA, encoding="utf-8").read()
    body = src[src.index("{"):src.index(";", src.index("CARD_BACK"))]
    return [m for m in re.findall(r'\b[A-Z][A-Z0-9_]*\("([a-z0-9_]+)"', body)]


def resource_name(name):
    """CardSleevesType.getResourceName(), in Python."""
    return name if name == "card_back" else "sleeves_" + name


def model(resource, size, parent):
    return ("{\r\n"
            '  "parent": "%s",\r\n'
            '  "textures": {\r\n'
            '    "layer0": "%s:item/%d/%s"\r\n'
            "  }\r\n"
            "}") % (parent, NAMESPACE, size, resource)


def write(path, text, newline):
    io.open(path, "w", encoding="utf-8", newline=newline).write(text)


def main():
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(ITEMS, exist_ok=True)

    # CARD_BACK is the absence of sleeves, not a design of them: it registers no
    # item on either tree, so it gets no files here either.
    sleeves = [n for n in sleeve_names() if n != "card_back"]

    models, definitions, missing_textures = 0, 0, []

    for name in sleeves:
        resource = resource_name(name)

        write(os.path.join(MODELS, resource + ".json"),
              model(resource, DEFAULT_SIZE, "minecraft:item/generated"), "")
        models += 1
        for size in SIZES:
            # The variants parent the base model, so the only thing a size says
            # is which texture -- everything else is stated once.
            write(os.path.join(MODELS, "%s_%d.json" % (resource, size)),
                  model(resource, size, "%s:item/%s" % (NAMESPACE, resource)), "")
            models += 1
            if not os.path.isfile(os.path.join(TEXTURES, str(size), resource + ".png")):
                missing_textures.append("%d/%s.png" % (size, resource))

        definition = {"model": {"type": "minecraft:model",
                                "model": "%s:item/%s" % (NAMESPACE, resource)}}
        write(os.path.join(ITEMS, resource + ".json"),
              json.dumps(definition, indent=2) + "\n", "\n")
        definitions += 1

    added = add_lang(sleeves)

    print("sleeves:      %d" % len(sleeves))
    print("models:       %d" % models)
    print("definitions:  %d" % definitions)
    print("lang lines:   %d added" % added)
    print("TOTAL FILES:  %d" % (models + definitions))
    if missing_textures:
        print("MISSING TEXTURES (%d):" % len(missing_textures))
        for t in missing_textures:
            print("   ", t)
    else:
        print("every model's texture exists on disk")


def add_lang(sleeves):
    """Adds the lines that are missing, leaving the rest of the file untouched.

    Rewriting the whole JSON would reorder and reformat a file whose grouping is
    deliberate, so the new lines are spliced in after the last sleeve line
    instead.
    """
    lines = io.open(LANG, encoding="utf-8", newline="\n").read().split("\n")
    present = set()
    last = None
    for i, line in enumerate(lines):
        m = re.match(r'\s*"item\.%s\.(sleeves_[a-z0-9_]+)"' % NAMESPACE, line)
        if m:
            present.add(m.group(1))
            last = i

    fresh = []
    for name in sleeves:
        resource = resource_name(name)
        if resource in present:
            continue
        label = LABELS.get(name) or (name.replace("_", " ").title() + " Sleeves")
        fresh.append('  "item.%s.%s": %s,' % (
            NAMESPACE, resource, json.dumps(label, ensure_ascii=False)))

    if not fresh:
        return 0

    at = (last + 1) if last is not None else 1
    # If the line we are splicing after was the file's LAST entry it carries no
    # comma, and the new lines would then make it invalid JSON. Move the comma
    # rather than assume where in the file the sleeves happen to sit.
    if last is not None and not lines[last].rstrip().endswith(","):
        lines[last] = lines[last].rstrip() + ","
        fresh[-1] = fresh[-1].rstrip(",")
    lines[at:at] = fresh
    io.open(LANG, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
    return len(fresh)


if __name__ == "__main__":
    main()
