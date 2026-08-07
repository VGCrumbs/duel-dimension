"""Removes the leftovers of Forge's networking from a ported message class.

Three things go, and none of them has a Fabric counterpart to replace it
with: the NetworkEvent import, the Supplier import that only existed for the
handler signature, and the `server(context, change)` helper that wrapped every
server-side handler in enqueueWork and getSender. DdNetwork.onServer does
both of those, so the helper has nothing left to do.
"""
import io
import os
import re
import sys


def strip(path):
    s = io.open(path, encoding="utf-8").read()
    s = re.sub(r"^import net\.minecraftforge\.network\.[^\n]*\n", "", s, flags=re.M)
    if "Supplier<" not in re.sub(r"/\*.*?\*/", "", s, flags=re.S):
        s = s.replace("import java.util.function.Supplier;\n", "")

    lines = s.split("\n")
    out = []
    i = 0
    removed = 0
    while i < len(lines):
        if re.match(r"\s+private static void server\(", lines[i]):
            removed += 1
            depth = 0
            started = False
            while i < len(lines):
                depth += lines[i].count("{") - lines[i].count("}")
                if "{" in lines[i]:
                    started = True
                if started and depth <= 0:
                    break
                i += 1
            out.append("    // Forge's server(context, change) helper is gone: it wrapped every")
            out.append("    // handler in enqueueWork and getSender, and DdNetwork.onServer does")
            out.append("    // both, so there was nothing left for it to do.")
            i += 1
            continue
        out.append(lines[i])
        i += 1
    io.open(path, "w", encoding="utf-8", newline="\n").write("\n".join(out))
    print("%s: %d helpers removed" % (os.path.basename(path), removed))


if __name__ == "__main__":
    for target in sys.argv[1:]:
        strip(target)
