"""The debug item: registration, model, lang, tab, and a 16px icon.

The icon is the user-supplied seal itself, reduced. Making up a new graphic for
a debug tool would be inventing art the project did not ask for; the seal is
what the item does, and at 16px the ring reads clearly.
"""
import io, json, collections, os
from PIL import Image

ASSETS = "src/main/resources/assets/dueldimension"
NAME = "orichalcos_debug"

# ---------- icon ----------
seal = Image.open(os.path.join(ASSETS, "textures/misc/orichalcos_seal.png")).convert("RGBA")
icon = seal.resize((16, 16), Image.LANCZOS)
# Nearest-neighbour would shred the fine ring detail into noise at this size;
# LANCZOS keeps the shape, and the alpha stays clean because the source is.
out = os.path.join(ASSETS, "textures/item/16")
os.makedirs(out, exist_ok=True)
icon.save(os.path.join(out, NAME + ".png"))
print("icon:", icon.size, "->", os.path.join(out, NAME + ".png"))

# ---------- item definition + model (26.2 splits these) ----------
io.open(os.path.join(ASSETS, "items", NAME + ".json"), "w", encoding="utf-8", newline="\n").write(
    json.dumps({"model": {"type": "minecraft:model",
                          "model": "dueldimension:item/" + NAME}}, indent=2) + "\n")
io.open(os.path.join(ASSETS, "models/item", NAME + ".json"), "w", encoding="utf-8", newline="\n").write(
    json.dumps({"parent": "item/generated",
                "textures": {"layer0": "dueldimension:item/16/" + NAME}}, indent=4) + "\n")
print("model + item definition written")

# ---------- lang ----------
p = os.path.join(ASSETS, "lang/en_us.json")
d = json.loads(io.open(p, encoding="utf-8").read(), object_pairs_hook=collections.OrderedDict)
d["item.dueldimension." + NAME] = "Seal of Orichalcos (Debug)"
io.open(p, "w", encoding="utf-8", newline="\n").write(
    json.dumps(d, indent=2, ensure_ascii=False) + "\n")
print("lang entry added")

# ---------- registration ----------
p = "src/main/java/de/cas_ual_ty/dueldimension/DdItems.java"
s = io.open(p, encoding="utf-8").read()
anchor = """    public static final SimpleBinderItem SIMPLE_BINDER_27 = register("simple_binder_27",
        properties -> new SimpleBinderItem(properties.stacksTo(1), 6 * 9 * 27));"""
add = anchor + """

    /**
     * Right-click any living thing to run the Seal of Orichalcos sequence on
     * it. A testing aid -- see {@link
     * de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosDebugItem}.
     */
    public static final de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosDebugItem
        ORICHALCOS_DEBUG = register("orichalcos_debug",
            properties -> new de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosDebugItem(
                properties.stacksTo(1)));"""
assert anchor in s, "DdItems anchor"
assert "ORICHALCOS_DEBUG" not in s, "already registered"
io.open(p, "w", encoding="utf-8", newline="\n").write(s.replace(anchor, add, 1))
print("DdItems: ORICHALCOS_DEBUG registered")

# ---------- creative tab ----------
p = "src/main/java/de/cas_ual_ty/dueldimension/DdItemGroup.java"
s = io.open(p, encoding="utf-8").read()
a = "                output.accept(DdItems.SIMPLE_BINDER_3);"
assert a in s and "ORICHALCOS_DEBUG" not in s, "DdItemGroup anchor"
io.open(p, "w", encoding="utf-8", newline="\n").write(
    s.replace(a, "                output.accept(DdItems.ORICHALCOS_DEBUG);\n" + a, 1))
print("DdItemGroup: added to the tab")
