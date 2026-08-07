"""Comments out the Forge handle() methods on ported message records.

They have no Fabric equivalent -- a receiver is registered by direction and
handed the payload and the sender -- so each body has to move to the
registration site. Commented rather than deleted: until it moves, this is the
only record of what the message is FOR.
"""
import io
import os
import re
import sys


def park(path):
    lines = io.open(path, encoding="utf-8").read().split("\n")
    out = []
    i = 0
    parked = 0
    while i < len(lines):
        if re.match(r"\s+public static void handle\(", lines[i]):
            parked += 1
            depth = 0
            started = False
            block = []
            while i < len(lines):
                block.append(lines[i])
                depth += lines[i].count("{") - lines[i].count("}")
                if "{" in lines[i]:
                    started = True
                if started and depth <= 0:
                    break
                i += 1
            out.append("        /*")
            out.append("         * The Forge handler. Fabric registers a receiver by direction")
            out.append("         * and hands it the payload and the sender, so this body belongs")
            out.append("         * at the registration site. Kept here until it moves, because")
            out.append("         * it is the record of what this message is for.")
            out.extend("         * " + b.strip() for b in block)
            out.append("         */")
            i += 1
            continue
        out.append(lines[i])
        i += 1
    io.open(path, "w", encoding="utf-8", newline="\n").write("\n".join(out))
    print("%s: %d handlers parked" % (os.path.basename(path), parked))


if __name__ == "__main__":
    for target in sys.argv[1:]:
        park(target)
