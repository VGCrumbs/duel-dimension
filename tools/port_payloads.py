"""Turns the Forge message records into CustomPacketPayloads.

The Forge messages were already the right shape and nobody planned it that
way: each record carries `static void encode(T, FriendlyByteBuf)` and
`static T decode(FriendlyByteBuf)`, and those are exactly
`StreamMemberEncoder<B,T>.encode(T, B)` and `StreamDecoder<B,T>.decode(B)`.
So the bodies -- which are where the wire format actually lives, and the part
worth not retyping -- come across untouched, and each record gains three
things:

    a Type naming it on the wire,
    a StreamCodec built from the two methods it already has,
    and type() returning the Type.

What this does NOT do is the handlers. `handle(msg, Supplier<Context>)` has no
equivalent: Fabric hands the payload and the player straight to a receiver
registered by direction, so those bodies have to move to the registration
site by hand.

    python tools/port_payloads.py <file>...
"""
import io
import re
import sys


def payload_name(class_name, record_name):
    """The wire name: `lobby_open` from LobbyMessages.OpenLobby."""
    group = re.sub(r"Messages$", "", class_name)
    group = re.sub(r"(?<!^)([A-Z])", r"_\1", group).lower()
    record = re.sub(r"(?<!^)([A-Z])", r"_\1", record_name).lower()
    return "%s_%s" % (group, record)


def record_header(src, at):
    """Where a record's parameter list closes and its body opens.

    Scanned by depth rather than matched by regex: a record header can span
    several lines and can contain generics with their own brackets, and the
    first version of this assumed one line -- which spliced the new members
    into the middle of OpenLobby's parameter list.

    :return: (index of the closing paren, index of the opening brace)
    """
    open_paren = src.index("(", at)
    depth = 0
    i = open_paren
    while i < len(src):
        if src[i] == "(":
            depth += 1
        elif src[i] == ")":
            depth -= 1
            if depth == 0:
                return i, src.index("{", i)
        i += 1
    raise ValueError("unterminated record header at %d" % at)


def port(path):
    src = io.open(path, encoding="utf-8").read()
    match = re.search(r"public (?:final )?class (\w+)", src)
    class_name = match.group(1)

    added = 0
    # Right to left, so each insertion cannot move the offsets of the next.
    for record in reversed(list(re.finditer(r"    public record (\w+)\(", src))):
        name = record.group(1)
        close_paren, brace = record_header(src, record.start())
        wire = payload_name(class_name, name)
        members = (
            "\n        /** Names this message on the wire. */\n"
            "        public static final CustomPacketPayload.Type<%s> TYPE =\n"
            "            DdNetwork.type(\"%s\");\n"
            "\n"
            "        /**\n"
            "         * Built from the encode/decode pair below rather than rewritten\n"
            "         * as a composite: those two methods ARE the wire format, and\n"
            "         * retyping a format is how a port quietly changes one.\n"
            "         */\n"
            "        public static final StreamCodec<RegistryFriendlyByteBuf, %s> CODEC =\n"
            "            CustomPacketPayload.codec(%s::encode, %s::decode);\n"
            "\n"
            "        @Override\n"
            "        public CustomPacketPayload.Type<? extends CustomPacketPayload> type()\n"
            "        {\n"
            "            return TYPE;\n"
            "        }\n" % (name, wire, name, name, name))

        src = (src[:brace + 1] + members + src[brace + 1:])
        src = (src[:close_paren + 1] + " implements CustomPacketPayload"
               + src[close_paren + 1:])
        added += 1

    for needed in ("import net.minecraft.network.RegistryFriendlyByteBuf;",
                   "import net.minecraft.network.codec.StreamCodec;",
                   "import net.minecraft.network.protocol.common.custom.CustomPacketPayload;",
                   "import de.cas_ual_ty.dueldimension.net.DdNetwork;"):
        if needed not in src:
            src = src.replace("import net.minecraft.network.FriendlyByteBuf;",
                              needed + "\nimport net.minecraft.network.FriendlyByteBuf;", 1)

    io.open(path, "w", encoding="utf-8", newline="\n").write(src)
    print("%s: %d records" % (path.replace("\\", "/").split("/")[-1], added))


if __name__ == "__main__":
    for target in sys.argv[1:]:
        port(target)
