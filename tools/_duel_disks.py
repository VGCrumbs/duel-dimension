"""Register the ten duel disks.

Everything else was already across: DuelDiskItem, all ten block-model JSONs and
all ten 16px textures. What was missing was the registration, the per-item
definition files 26.2 splits out of the model, the names, and the creative tab
entries -- so the items existed as code and art that nothing could ever obtain.

Names and order are the Forge tree's, verbatim.
"""
import io, json, collections, os

ASSETS = "src/main/resources/assets/dueldimension"

# (registry name, field name, display name) -- Forge's order and wording
DISKS = [
    ("duel_disk", "DUEL_DISK", "Duel Disk"),
    ("chaos_disk", "CHAOS_DISK", "Chaos Disk"),
    ("academia_disk", "ACADEMIA_DISK", "Academia Disk"),
    ("academia_disk_red", "ACADEMIA_DISK_RED", "Slifer Red Academia Disk"),
    ("academia_disk_blue", "ACADEMIA_DISK_BLUE", "Obelisk Blue Academia Disk"),
    ("academia_disk_yellow", "ACADEMIA_DISK_YELLOW", "Ra Yellow Academia Disk"),
    ("rock_spirit_disk", "ROCK_SPIRIT_DISK", "The Rock Spirit's Duel Disk"),
    ("trueman_disk", "TRUEMAN_DISK", "Trueman's Duel Disk"),
    ("jewel_disk", "JEWEL_DISK", "Jewel Disk"),
    ("kaibaman_disk", "KAIBAMAN_DISK", "Kaibaman's Duel Disk"),
]

# ---------- item definitions (26.2 splits these out of the model) ----------
made = 0
for name, _, _ in DISKS:
    model = os.path.join(ASSETS, "models/item", name + ".json")
    assert os.path.isfile(model), "missing model: " + model
    texture = os.path.join(ASSETS, "textures/item", name + ".png")
    assert os.path.isfile(texture), "missing texture: " + texture
    path = os.path.join(ASSETS, "items", name + ".json")
    if os.path.isfile(path):
        continue
    io.open(path, "w", encoding="utf-8", newline="\n").write(
        json.dumps({"model": {"type": "minecraft:model",
                              "model": "dueldimension:item/" + name}}, indent=2) + "\n")
    made += 1
print("item definitions written:", made)

# ---------- lang ----------
p = os.path.join(ASSETS, "lang/en_us.json")
d = json.loads(io.open(p, encoding="utf-8").read(),
               object_pairs_hook=collections.OrderedDict)
for name, _, display in DISKS:
    d["item.dueldimension." + name] = display
io.open(p, "w", encoding="utf-8", newline="\n").write(
    json.dumps(d, indent=2, ensure_ascii=False) + "\n")
print("lang entries:", len(DISKS))

# ---------- registration ----------
p = "src/main/java/de/cas_ual_ty/dueldimension/DdItems.java"
s = io.open(p, encoding="utf-8").read()
anchor = """    public static final CardBinderItem CARD_BINDER = register("card_binder","""
assert anchor in s, "DdItems anchor"
if "DUEL_DISK" in s:
    print("DdItems: already registered")
else:
    block = ["    // The duel disks. A disk in the OFF hand is what starts a duel --",
             "    // see DuelDiskItem.use -- so they stack to one like the rest of the",
             "    // equipment items.",
             "    public static final de.cas_ual_ty.dueldimension.duel.dueldisk.DuelDiskItem"]
    lines = []
    for i, (name, field, _) in enumerate(DISKS):
        lines.append("    public static final de.cas_ual_ty.dueldimension.duel.dueldisk"
                     ".DuelDiskItem " + field + " =\n"
                     '        register("' + name + '", one(de.cas_ual_ty.dueldimension.duel'
                     ".dueldisk.DuelDiskItem::new));")
    new = ("    // The duel disks. A disk held in the OFF hand is what opens a duel --\n"
           "    // see DuelDiskItem.use -- so they stack to one, as the Forge tree had\n"
           "    // them, and carry its names verbatim.\n"
           + "\n".join(lines) + "\n\n" + anchor)
    s = s.replace(anchor, new, 1)
    io.open(p, "w", encoding="utf-8", newline="\n").write(s)
    print("DdItems: 10 disks registered")

# ---------- creative tab ----------
p = "src/main/java/de/cas_ual_ty/dueldimension/DdItemGroup.java"
s = io.open(p, encoding="utf-8").read()
anchor = "                output.accept(DdItems.CARD_BINDER);"
assert anchor in s, "DdItemGroup anchor"
if "DdItems.DUEL_DISK" in s:
    print("DdItemGroup: already listed")
else:
    adds = "".join("                output.accept(DdItems." + f + ");\n" for _, f, _ in DISKS)
    io.open(p, "w", encoding="utf-8", newline="\n").write(
        s.replace(anchor, adds + anchor, 1))
    print("DdItemGroup: 10 disks added to the tab")
