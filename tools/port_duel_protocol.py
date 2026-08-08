"""Moves the duel protocol onto 26.2's buffer and codecs.

Three changes, all forced by the same shift: what used to be a method on the
buffer is now a StreamCodec, and a codec that touches the registries needs a
buffer that can reach them.

    FriendlyByteBuf              ->  RegistryFriendlyByteBuf
    buf.writeComponent(c)        ->  ComponentSerialization.STREAM_CODEC.encode(buf, c)
    buf.readComponent()          ->  ComponentSerialization.STREAM_CODEC.decode(buf)
    buf.writeItem(stack)         ->  ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack)
    buf.readItem()               ->  ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)

The OPTIONAL variants are the ones that accept an empty stack, which is what
`writeItem` always did -- the plain STREAM_CODEC throws on empty.

The buffer swap is a plain rename because every use in the duel protocol is a
parameter or a local; nothing constructs one. The payload envelope hands a
RegistryFriendlyByteBuf in, so the type is right at the boundary.
"""
import io
import os
import re

ROOTS = [
    "src/main/java/de/cas_ual_ty/dueldimension/duel/network",
    "src/main/java/de/cas_ual_ty/dueldimension/duel/action",
    "src/main/java/de/cas_ual_ty/dueldimension/duel/playfield",
    "src/main/java/de/cas_ual_ty/dueldimension/duel",
]

CALLS = [
    (re.compile(r"\b(\w+)\.writeComponent\("), r"ComponentSerialization.STREAM_CODEC.encode(\1, "),
    (re.compile(r"\b(\w+)\.readComponent\(\)"), r"ComponentSerialization.STREAM_CODEC.decode(\1)"),
    (re.compile(r"\b(\w+)\.writeItem\("), r"ItemStack.OPTIONAL_STREAM_CODEC.encode(\1, "),
    (re.compile(r"\b(\w+)\.readItem\(\)"), r"ItemStack.OPTIONAL_STREAM_CODEC.decode(\1)"),
]

IMPORTS = {
    "ComponentSerialization": "import net.minecraft.network.chat.ComponentSerialization;",
    "ItemStack.OPTIONAL_STREAM_CODEC": "import net.minecraft.world.item.ItemStack;",
}


def add_import(src, line):
    if line in src:
        return src
    # After the package line, ahead of the existing imports; javac does not
    # care about order and neither does anything else here.
    return re.sub(r"^(package [^\n]+\n\n?)", r"\1" + line + "\n", src, count=1, flags=re.M)


def convert(path):
    src = io.open(path, encoding="utf-8").read()
    before = src

    src = src.replace("FriendlyByteBuf", "RegistryFriendlyByteBuf")
    # The rename above also hits the import and any already-correct name.
    src = src.replace("RegistryRegistryFriendlyByteBuf", "RegistryFriendlyByteBuf")
    src = src.replace("import net.minecraft.network.RegistryFriendlyByteBuf;",
                      "import net.minecraft.network.RegistryFriendlyByteBuf;")

    for pattern, replacement in CALLS:
        src = pattern.sub(replacement, src)

    for marker, line in IMPORTS.items():
        if marker in src:
            src = add_import(src, line)

    if src != before:
        io.open(path, "w", encoding="utf-8", newline="\n").write(src)
        return True
    return False


if __name__ == "__main__":
    seen, changed = 0, 0
    for root in ROOTS:
        for dirpath, _, names in os.walk(root):
            for name in names:
                if name.endswith(".java"):
                    seen += 1
                    changed += 1 if convert(os.path.join(dirpath, name)) else 0
    print("scanned %d files, changed %d" % (seen, changed))
