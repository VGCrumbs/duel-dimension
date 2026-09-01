"""Replaces the mod's card sleeves with Master Duel's protector catalogue.

Run `build_sleeve_names.py` first; this reads `sleeve_names.json`.

    python tools/import_sleeves.py --backup          # just the backup
    python tools/import_sleeves.py                   # backup, remove, import

<h2>What a sleeve is made of</h2>
Six things, and missing any one of them is a different kind of broken:

  * a constant in `CardSleevesType` (both modules), whose `name` is the id
    everything else is keyed by;
  * a texture per tier under `textures/item/<tier>/sleeves_<id>.png`;
  * a `.mcmeta` beside the big ones, turning on blur and clamp;
  * a base item model and one per tier under `models/item/`;
  * a 26.2 item definition under `mc262/.../items/`;
  * a lang entry, or the shop shows `item.dueldimension.sleeves_<id>`.

<h2>The art</h2>
Master Duel's protector extracts as 512x1024, but the art in it is STRETCHED:
the native is 512x747, a card, and Unity scaled it to the nearest power of two
on import. The game undoes that by drawing it into a card-shaped box. So each
tier is a straight resize into the mod's CARD_U0..V1 window -- 0.602 across by
0.875 down, the same card aspect -- which unsquashes it. Nothing is cropped.
See `canvas`, and MasterDuelDecomp/research/protector-framing.md.

The ladder stops at 512. The source is 512 wide, and once the window's own 0.602
is taken out, a 1024 canvas would be storing a 1.2x upscale -- 163 MB across the
catalogue for detail that is not in the file. See CardSleevesType.MAX_SIZE.

<h2>The size</h2>
Every tier is palettized to 255 colours with FASTOCTREE, which is the only PIL
quantiser that keeps alpha. Master Duel's art is painted rather than flat, so
raw PNGs of it are five times the size of the old dye-coloured sleeves: 56 MB
for the 512 tier alone. Palettized it is 11 MB, and at the size a sleeve is ever
drawn the two are not distinguishable. The deck box import learned this first.
"""
import argparse
import datetime
import io
import json
import os
import re
import shutil
import sys
import zipfile

from PIL import Image

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
NAMES = os.path.join(HERE, "sleeve_names.json")
DESCRIPTIONS = os.path.join(HERE, "sleeve_descriptions.json")
COLOURS = os.path.join(HERE, "sleeve_colors.json")
DUMP = (r"C:\Users\Admin\Desktop\YGO\MasterDuelDecomp\extracted\sleeves"
        r"\assets\resourcesassetbundle\protector")
BACKUPS = os.path.join(HERE, "backup")

SHARED = os.path.join(REPO, "shared", "resources", "assets", "dueldimension")
TEX = os.path.join(SHARED, "textures", "item")
MODELS = os.path.join(SHARED, "models", "item")
LANG = os.path.join(SHARED, "lang", "en_us.json")
ITEMS_262 = os.path.join(REPO, "mc262", "src", "main", "resources", "assets",
                         "dueldimension", "items")
MODULES = ("mc262", "mc1211")

# The window the mod samples a sleeve through, from DuelTextures.
U0, U1, V0, V1 = 0.19922, 0.80078, 0.0625, 0.9375
TIERS = (16, 32, 64, 128, 256, 512)
# Below this a texture is drawn small enough that nearest is sharper than blur,
# which is the convention the existing sleeves already follow.
MCMETA_FROM = 256



def enum_path(mod):
    return os.path.join(REPO, mod, "src/main/java/de/cas_ual_ty/dueldimension/"
                                  "card/CardSleevesType.java")


def sleeves_path(mod):
    return os.path.join(REPO, mod, "src/main/java/de/cas_ual_ty/dueldimension/"
                                   "duel/profile/Sleeves.java")


def slug(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def literal(name):
    """A name as the body of a Java string literal.

    Master Duel names carry quotes -- `Karakuri Barrel mdl 96 "Shinkuro"` is a
    real one -- and pasting one raw closes the literal early and fails the build
    rather than the import.
    """
    return name.replace("\\", "\\\\").replace('"', '\\"')


def constant(ident):
    """The enum constant for an id, which must be a Java identifier.

    Prefixed when the slug starts with a digit: "2024 Card Case" would otherwise
    give `2024_CARD_CASE`, and a constant cannot start with a digit.
    """
    value = ident.upper()
    return "SLEEVE_" + value if value[:1].isdigit() else value


# ---------------------------------------------------------------- catalogue

def catalogue():
    """Every protector that has BOTH a name and art, keyed by its final id.

    Three protector ids share a name with another -- Konami ships two
    "Sky Striker Ace - Raye" sleeves with different art -- so the later id takes
    an `_alt` suffix and an "(Alt)" label. Letting them collide would have the
    second silently overwrite the first's texture, model and constant.
    """
    table = json.load(io.open(NAMES, encoding="utf-8"))["names"]
    art = find_art()
    rows = []
    taken = set()
    for item_id in sorted(table):
        path = art.get(item_id)
        if path is None:
            continue
        name = table[item_id]
        ident = slug(name)
        label = name
        if ident in taken:
            n = 2
            while True:
                candidate = ident + "_alt" if n == 2 else "%s_alt%d" % (ident, n)
                if candidate not in taken:
                    break
                n += 1
            label = "%s (Alt%s)" % (name, "" if n == 2 else " %d" % n)
            ident = candidate
        taken.add(ident)
        rows.append({"item_id": item_id, "id": ident, "label": label, "art": path})
    return rows, sorted(set(table) - set(art))


def find_art():
    """`ProtectorIcon107NNNN.png` for each id, wherever it landed in the dump.

    The masks and focus overlays share the prefix and are not the sleeve, so
    they are excluded by name rather than by guessing at size.

    Some ids also ship a `_2` rendition. The plain one WINS: the suffixed files
    are a minority and there is no id that has only one, so preferring the bare
    name gives the same sleeve every run -- where taking whichever os.walk
    reached first would not.
    """
    plain = {}
    suffixed = {}
    for dirpath, _, files in os.walk(DUMP):
        for f in files:
            if not f.startswith("ProtectorIcon") or not f.endswith(".png"):
                continue
            stem = f.split(" @")[0]
            m = re.fullmatch(r"ProtectorIcon(\d{7})(?:_(\d+))?", stem)
            if not m:
                continue          # _Mask, _Focus, and anything else odd
            (suffixed if m.group(2) else plain)[m.group(1)] = os.path.join(dirpath, f)
    found = dict(suffixed)
    found.update(plain)
    return found


# ------------------------------------------------------------------- backup

def existing_sleeve_files():
    out = []
    for tier in os.listdir(TEX):
        d = os.path.join(TEX, tier)
        if not os.path.isdir(d):
            continue
        for f in os.listdir(d):
            if f.startswith("sleeves_"):
                out.append(os.path.join(d, f))
    for f in os.listdir(MODELS):
        if f.startswith("sleeves_"):
            out.append(os.path.join(MODELS, f))
    if os.path.isdir(ITEMS_262):
        for f in os.listdir(ITEMS_262):
            if f.startswith("sleeves_"):
                out.append(os.path.join(ITEMS_262, f))
    return sorted(out)


def backup():
    """Everything a sleeve is made of, plus the two sources that name them.

    A zip rather than a copied tree: it is one file to keep or delete, and it
    cannot be half-restored by a stray glob.
    """
    os.makedirs(BACKUPS, exist_ok=True)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    path = os.path.join(BACKUPS, "sleeves-%s.zip" % stamp)
    files = existing_sleeve_files()
    for mod in MODULES:
        files.append(enum_path(mod))
        files.append(sleeves_path(mod))
    files.append(LANG)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for f in files:
            if os.path.exists(f):
                z.write(f, os.path.relpath(f, REPO))
    print("backup: %d files -> %s (%.1f MB)"
          % (len(files), os.path.relpath(path, REPO), os.path.getsize(path) / 1048576))
    return path


# ------------------------------------------------------------------- remove

def remove_existing():
    gone = 0
    for f in existing_sleeve_files():
        os.remove(f)
        gone += 1
    print("removed %d sleeve resource files" % gone)

    lang = json.load(io.open(LANG, encoding="utf-8"))
    before = len(lang)
    lang = {k: v for k, v in lang.items()
            if not k.startswith("item.dueldimension.sleeves_")}
    write_lang(lang)
    print("removed %d lang entries" % (before - len(lang)))


def write_lang(lang):
    io.open(LANG, "w", encoding="utf-8", newline="\n").write(
        json.dumps(lang, indent=2, ensure_ascii=False) + "\n")


# ------------------------------------------------------------------- import

def canvas(im, n):
    """One tier of one sleeve: the protector, unsquashed onto a square canvas.

    <h2>The file is stretched, and that is not a mistake in the file</h2>
    `ProtectorIcon107NNNN` extracts as **512 x 1024**, an aspect of 0.5, and the
    art in it is visibly tall: circles are ellipses and the "MASTER DUEL"
    wordmark is narrow. The native art is **512 x 747** -- 0.6854, a card -- and
    Unity scaled it to the nearest power of two on import. Master Duel undoes
    that by drawing it into a `ProtectorImage` RectTransform of 302.4 x 441,
    which is 0.6857.

    So the conversion is a straight non-uniform resize into the card window,
    which divides the height by 1024/747 and puts every circle back. **Nothing
    is cropped**, because there was never any overflow -- the 1:2 shape is
    padding, not composition.

    Cropping it instead, which is what this did first, cut the wordmark and the
    bottom border off every design in the catalogue AND left the remaining art
    still stretched. Both symptoms, one cause.

    Measured in `MasterDuelDecomp/research/protector-framing.md`.
    """
    w = round((U1 - U0) * n)
    h = round((V1 - V0) * n)
    art = im.resize((max(1, w), max(1, h)), Image.LANCZOS)
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    out.alpha_composite(art, (round(U0 * n), round(V0 * n)))
    return out


MCMETA = '{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n'


def write_json(path, obj):
    io.open(path, "w", encoding="utf-8", newline="\n").write(
        json.dumps(obj, indent=2) + "\n")


def import_art(rows):
    for tier in TIERS:
        os.makedirs(os.path.join(TEX, str(tier)), exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(ITEMS_262, exist_ok=True)

    total = 0
    for i, row in enumerate(rows):
        ident = row["id"]
        source = Image.open(row["art"]).convert("RGBA")
        for tier in TIERS:
            out = os.path.join(TEX, str(tier), "sleeves_%s.png" % ident)
            im = canvas(source, tier)
            # FASTOCTREE is the only PIL quantiser that keeps alpha, and the
            # canvas is half transparent, so it is the only one usable here.
            im.quantize(colors=255, method=Image.FASTOCTREE).save(out, optimize=True)
            total += os.path.getsize(out)
            meta = out + ".mcmeta"
            if tier >= MCMETA_FROM:
                io.open(meta, "w", encoding="utf-8", newline="\n").write(MCMETA)

            write_json(os.path.join(MODELS, "sleeves_%s_%d.json" % (ident, tier)),
                       {"parent": "dueldimension:item/sleeves_%s" % ident,
                        "textures": {"layer0": "dueldimension:item/%d/sleeves_%s"
                                               % (tier, ident)}})

        write_json(os.path.join(MODELS, "sleeves_%s.json" % ident),
                   {"parent": "minecraft:item/generated",
                    "textures": {"layer0": "dueldimension:item/16/sleeves_%s" % ident}})
        write_json(os.path.join(ITEMS_262, "sleeves_%s.json" % ident),
                   {"model": {"type": "minecraft:model",
                              "model": "dueldimension:item/sleeves_%s" % ident}})
        if (i + 1) % 25 == 0:
            print("   %d/%d" % (i + 1, len(rows)))
    print("art: %d sleeves x %d tiers, %.1f MB" % (len(rows), len(TIERS), total / 1048576))


def import_lang(rows):
    """The name of every sleeve, and the description of the ones that have one.

    The descriptions are Master Duel's own flavour text, recovered from
    `IDS_ITEMDESC` and matched to item ids by
    `MasterDuelDecomp/scripts/align_protector_descriptions.py` -- see that file
    for why it is an alignment rather than a lookup.

    Only the clean ones are written. Twenty-nine descriptions contain Konami's
    `<card mrk='...'/>` markup, which names a card the sentence is about and
    needs the encrypted card database to resolve; stripping it leaves
    "Protectors depicting advancing." A sleeve with no `.desc` key falls back to
    the shop's generic line, which reads correctly, so the absence costs nothing
    that a mangled sentence would not cost more.
    """
    lang = json.load(io.open(LANG, encoding="utf-8"))
    described = {}
    if os.path.exists(DESCRIPTIONS):
        described = json.load(io.open(DESCRIPTIONS, encoding="utf-8"))["descriptions"]
    written = 0
    for row in rows:
        lang["item.dueldimension.sleeves_%s" % row["id"]] = "%s Sleeves" % row["label"]
        text = described.get(row["item_id"])
        if text:
            # The two lines are kept as two: the first says what the art shows
            # and the second who it is for, and the shop's own wrapping renders
            # the break.
            lang["item.dueldimension.sleeves_%s.desc" % row["id"]] = text.strip()
            written += 1
    write_lang(lang)
    print("lang: %d names, %d descriptions" % (len(rows), written))


# --------------------------------------------------------------------- java

def load_colours():
    """The dominant colour per id, as ints. A missing one falls back to grey.

    Grey rather than a crash: the sampler is a separate step, so a catalogue
    imported before it has run should still build -- the sleeve simply sorts
    into the achromatic group until the colours are sampled.
    """
    if not os.path.exists(COLOURS):
        print("   no sleeve_colors.json -- run tools/sample_sleeve_colors.py")
        return {}
    raw = json.load(io.open(COLOURS, encoding="utf-8"))["colors"]
    return {k: int(v, 16) for k, v in raw.items()}


def rewrite_enum(rows):
    """Replaces the constant list, leaving the class's methods alone.

    CARD_BACK stays and stays FIRST. It is the "no sleeve" default that
    `Sleeves.DEFAULT` names and that an unreadable id falls back to, and its
    index is what `InitSleevesAction` puts on the wire for a bare card.
    """
    colours = load_colours()
    body = []
    body.append("    // The plain card back: no sleeve at all, and what a deck falls back")
    body.append("    // to when its saved sleeve is one this build does not have. See")
    body.append("    // Sleeves.CODEC.")
    body.append('    CARD_BACK("card_back", 0x%06X),'
                % colours.get('card_back', 0x808080))
    body.append("")
    body.append("    // Master Duel's protector catalogue, by its own item ids and its own")
    body.append("    // English names. Generated by tools/import_sleeves.py -- add sleeves")
    body.append("    // by re-running that, not by hand.")
    body.append("    //")
    body.append("    // Appended in id order and never re-ordered, because index == ordinal")
    body.append("    // (see the static block below) and that index is what")
    body.append("    // InitSleevesAction puts on the wire. Nothing on DISK stores an index:")
    body.append("    // Sleeves writes the name, precisely so this list can grow.")
    for i, row in enumerate(rows):
        end = ";" if i == len(rows) - 1 else ","
        body.append('    %s("%s", 0x%06X)%s   // %s'
                    % (constant(row["id"]), row["id"],
                       colours.get(row["item_id"], 0x808080), end, row["item_id"]))

    for mod in MODULES:
        path = enum_path(mod)
        text = io.open(path, encoding="utf-8").read()
        start = text.index("public enum CardSleevesType\n{\n") + len("public enum CardSleevesType\n{\n")
        stop = text.index("\n\n    public static final CardSleevesType[] VALUES", start)
        io.open(path, "w", encoding="utf-8", newline="\n").write(
            text[:start] + "\n".join(body) + text[stop:])
        print("%s: enum rewritten, %d constants" % (mod, len(rows) + 1))


FREE_BLOCK = '''    /**
     * Sleeves nobody has to buy.
     * <p>
     * Just the plain back now. It used to be the back plus the sixteen dye
     * colours, on the reasoning that flat dyed backs were not really art and
     * charging for all sixteen put a wall in front of "my deck looks like mine".
     * Those colours are gone -- the catalogue is Master Duel's protectors, every
     * one of which is drawn art -- so the exception has nothing left to apply
     * to, and the rule it made room for is the whole shop.
     * <p>
     * Free is a <em>rule</em> and not a stored grant: nothing is written to
     * disk, so it cannot be lost, cannot be duplicated by a double grant, and
     * costs a byte of nobody's save file. It is also why a constant appended to
     * the enum later is <b>not</b> free -- new art is paid art unless it is
     * listed here on purpose.
     */
    public static final Set<CardSleevesType> FREE = Collections.unmodifiableSet(EnumSet.of(
        CardSleevesType.CARD_BACK));'''


def rewrite_free():
    for mod in MODULES:
        path = sleeves_path(mod)
        text = io.open(path, encoding="utf-8").read()
        if "CardSleevesType.CARD_BACK));" in text:
            print("%s: FREE already rewritten" % mod)
            continue
        start = text.index("    /**\n     * Sleeves nobody has to buy")
        stop = text.index("CardSleevesType.YELLOW));", start) + len("CardSleevesType.YELLOW));")
        io.open(path, "w", encoding="utf-8", newline="\n").write(
            text[:start] + FREE_BLOCK + text[stop:])
        print("%s: FREE is now the card back alone" % mod)


# --------------------------------------------------------------------- main

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backup", action="store_true",
                        help="take the backup and stop")
    parser.add_argument("--enum-only", action="store_true",
                        help="rewrite the enum from the current name and "
                             "colour tables, leaving art and lang alone")
    parser.add_argument("--art-only", action="store_true",
                        help="redo the textures and models, leaving the enum and "
                             "lang alone")
    args = parser.parse_args()

    rows, missing = catalogue()
    print("%d protectors named and extracted" % len(rows))
    if missing:
        print("%d named with no art in the dump: %s"
              % (len(missing), ", ".join(missing[:10]) + (" ..." if len(missing) > 10 else "")))

    backup()
    if args.backup:
        return
    if not rows:
        raise SystemExit("nothing to import -- has the extraction finished?")

    if args.enum_only:
        rewrite_enum(rows)
        rewrite_free()
        print("done -- enum rewritten")
        return

    if args.art_only:
        import_art(rows)
        print("done -- art rebuilt")
        return

    remove_existing()
    import_art(rows)
    import_lang(rows)
    rewrite_enum(rows)
    rewrite_free()
    print("done -- now run buildAll")


if __name__ == "__main__":
    main()
