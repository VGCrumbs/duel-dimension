"""Applies the mechanical half of the 1.19.2 -> 26.2 / Forge -> Fabric port.

Everything here is a rename or a call-shape change that is the same in every
file, verified against the 26.2 jars with javap. Doing them by hand across
forty thousand lines is how a port acquires a long tail of typos; doing them
here means each rule is written once, reviewed once, and applied everywhere.

What is deliberately NOT here: anything needing a decision. Registries,
networking, capabilities, events and rendering all change shape rather than
spelling, and each gets ported by hand in its own phase. A rule only belongs
in this file if getting it wrong would be a compile error rather than a bug.

    python tools/port_rewrite.py <file-or-directory>...
"""
import io
import os
import re
import sys

# --- renames ----------------------------------------------------------------
# ResourceLocation went back to Identifier. Same class, same package, and the
# constructors became factories.
RENAMES = [
    (r"\bnet\.minecraft\.resources\.ResourceLocation\b", "net.minecraft.resources.Identifier"),
    (r"\bResourceLocation\b", "Identifier"),
]

CALLS = [
    # new Identifier(a, b) -> Identifier.fromNamespaceAndPath(a, b). Two
    # arguments, so the split has to survive nested calls in either half; the
    # comma is found by scanning depth, not by regex.
    ("new Identifier(", "IDENTIFIER_CTOR"),
]

# --- CompoundTag / ListTag getters ------------------------------------------
# Every reader returns Optional now. The `...Or` form is the old behaviour, and
# the defaults below are exactly what the 1.19.2 getters returned for a missing
# key, so a translated call keeps its meaning.
TAG_GETTERS = {
    "getString": '""',
    "getInt": "0",
    "getLong": "0L",
    "getFloat": "0.0F",
    "getDouble": "0.0D",
    "getBoolean": "false",
    "getByte": "(byte) 0",
    "getShort": "(short) 0",
}
# These two have named empties rather than a default argument.
TAG_EMPTIES = {"getCompound": "getCompoundOrEmpty", "getList": "getListOrEmpty"}

# Fields that became accessor methods.
#
# `isClientSide` only exists on Level, so rewriting it on sight is safe.
# `Entity.level` is deliberately NOT here even though it moved the same way: a
# card in this mod has a level too, and the rule rewrote
# `PATREON_001_PROPERTIES.level = 12` into a call to a method that does not
# exist. Its few genuine sites are done by hand. A name this common is not
# worth automating -- the same lesson the tag getters taught.
FIELD_TO_METHOD = ["isClientSide"]

MISC = [
    # contains(key, type) lost its type argument; the type test is now the
    # getter returning an empty Optional.
    (re.compile(r"\.contains\((\"[^\"]*\"|[A-Za-z_][\w.]*)\s*,\s*[^)]+\)"), r".contains(\1)"),
    # getAllKeys was renamed to the Map-ish keySet.
    (re.compile(r"\.getAllKeys\(\)"), ".keySet()"),
]


def split_args(text, start):
    """The two top-level arguments of a call whose '(' is at `start`.

    Scans bracket depth and string literals rather than matching a regex, so
    `new Identifier(MOD_ID, "a/" + b(c, d))` splits in the right place.
    """
    depth = 0
    in_string = False
    escape = False
    comma = -1
    i = start
    while i < len(text):
        ch = text[i]
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
        elif ch == '"':
            in_string = True
        elif ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
            if depth == 0:
                return (text[start + 1:comma], text[comma + 1:i], i) if comma > 0 else None
        elif ch == "," and depth == 1 and comma < 0:
            comma = i
        i += 1
    return None


def rewrite_identifier_ctor(src):
    """new Identifier(a, b) -> factory; new Identifier(a) -> parse."""
    out = src
    while True:
        at = out.find("new Identifier(")
        if at < 0:
            return out
        open_paren = at + len("new Identifier")
        split = split_args(out, open_paren)
        if split is None:
            return out
        left, right, close = split
        if right.strip():
            replacement = "Identifier.fromNamespaceAndPath(%s,%s)" % (left, right)
        else:
            replacement = "Identifier.parse(%s)" % left
        out = out[:at] + replacement + out[close + 1:]


# A receiver worth treating as a tag. getInt and getString are far too common
# to rewrite on sight: the first version of this rule turned SQL's
# ResultSet.getInt("id") into getIntOr("id", 0) and broke the card database
# reader, which had been compiling perfectly well. So it now asks twice -- the
# file must deal in NBT at all, and the receiver must look like a tag.
TAG_RECEIVER = re.compile(r"(?:tag|nbt|compound)\w*$", re.I)
RECEIVER_BEFORE = re.compile(r"([A-Za-z_][\w.]*)$")


def looks_like_tag(src, end):
    """Whether the expression ending at `end` is plausibly a tag."""
    receiver = RECEIVER_BEFORE.search(src[:end])
    if receiver is None:
        return False
    return bool(TAG_RECEIVER.search(receiver.group(1).split(".")[-1]))


def rewrite_tag_getters(src):
    """tag.getX("k") -> tag.getXOr("k", default), and the two named empties.

    Only in files that deal in NBT, and only for tag-shaped receivers.
    """
    if "net.minecraft.nbt." not in src:
        return src
    for name, empty in TAG_EMPTIES.items():
        src = re.sub(r"\.%s\((\"[^\"]*\"|[A-Za-z_][\w.]*)\s*(?:,\s*[^)]+)?\)" % name,
                     r".%s(\1)" % empty, src)
    for name, default in TAG_GETTERS.items():
        # Only single-argument calls: a call that already passes a default is
        # either already ported or is not a tag read at all.
        pattern = re.compile(r"\.%s\((\"[^\"]*\"|[A-Za-z_][\w.]*)\)" % name)

        def replace(match, name=name, default=default):
            if not looks_like_tag(src, match.start()):
                return match.group(0)
            return ".%sOr(%s, %s)" % (name, match.group(1), default)

        src = pattern.sub(replace, src)
    return src


def rewrite_field_accessors(src):
    """obj.level -> obj.level(), but never inside an import or a declaration."""
    out = []
    for line in src.split("\n"):
        if not line.lstrip().startswith(("import ", "package ")):
            for field in FIELD_TO_METHOD:
                # A dot, the name, and something that is not already a call and
                # not a further member access (`server.level.ServerLevel`).
                line = re.sub(r"\.%s\b(?!\s*\()(?!\s*\.)" % field, ".%s()" % field, line)
        out.append(line)
    return "\n".join(out)


def rewrite(path):
    src = io.open(path, encoding="utf-8").read()
    before = src
    for pattern, replacement in RENAMES:
        src = re.sub(pattern, replacement, src)
    src = rewrite_field_accessors(src)
    src = rewrite_identifier_ctor(src)
    src = rewrite_tag_getters(src)
    for pattern, replacement in MISC:
        src = pattern.sub(replacement, src)
    if src != before:
        io.open(path, "w", encoding="utf-8", newline="\n").write(src)
        return True
    return False


def main(targets):
    changed = 0
    seen = 0
    for target in targets:
        if os.path.isfile(target):
            files = [target]
        else:
            files = [os.path.join(f, n) for f, _, ns in os.walk(target)
                     for n in ns if n.endswith(".java")]
        for path in files:
            seen += 1
            if rewrite(path):
                changed += 1
    print("rewrote %d of %d files" % (changed, seen))


if __name__ == "__main__":
    main(sys.argv[1:] or ["src"])
