"""Turns AbstractWidget's now-private x/y/width/height into their accessors.

`port_screen.py` rewrites `this.x`, which is what most screens wrote. A widget
subclass usually writes the bare field instead -- `x`, `y`, `width`, `height` --
and there is no safe regex for that: a local called `x`, a parameter called
`width`, and a field access all look identical.

So this does not guess. It reads javac's own output, which reports the exact
line AND column of every "has private access in AbstractWidget", and rewrites
precisely those. Anything the compiler did not complain about is left alone,
which means a local named `x` cannot be caught by accident.

    ./gradlew25.cmd compileJava 2>&1 | python tools/fix_widget_fields.py

Re-run until it reports nothing: one pass can expose errors the first pass hid.
"""
import io
import re
import sys

ACCESSORS = {"x": "getX()", "y": "getY()", "width": "getWidth()", "height": "getHeight()"}

# javac emits three lines per error: location, the source line, then a caret.
ERROR = re.compile(r"^(?P<path>[A-Za-z]:[^:]*\.java):(?P<line>\d+): error: "
                   r"(?P<field>x|y|width|height) has private access in AbstractWidget")


def main():
    lines = sys.stdin.read().split("\n")
    # path -> list of (line number, column, field)
    hits = {}
    for i, line in enumerate(lines):
        match = ERROR.match(line.strip())
        if not match:
            continue
        # The caret line is two below, and its offset is the column.
        if i + 2 >= len(lines):
            continue
        caret = lines[i + 2]
        column = caret.find("^")
        if column < 0:
            continue
        hits.setdefault(match.group("path"), []).append(
            (int(match.group("line")), column, match.group("field")))

    if not hits:
        print("nothing to fix")
        return

    total = 0
    for path, sites in hits.items():
        src = io.open(path, encoding="utf-8").read().split("\n")
        # Right to left within a line, so earlier columns keep their offsets.
        for number, column, field in sorted(sites, key=lambda s: (-s[0], -s[1])):
            text = src[number - 1]
            # javac's caret is aligned to the source line as IT printed it, and
            # that can sit a character off the raw file (leading whitespace is
            # normalised). So the column is a hint, not a promise: look in a
            # small window around it for the identifier, checking it really is
            # one rather than part of a longer name.
            at = -1
            for candidate in (column, column - 1, column + 1, column - 2, column + 2):
                if candidate < 0 or candidate + len(field) > len(text):
                    continue
                if text[candidate:candidate + len(field)] != field:
                    continue
                before = text[candidate - 1] if candidate > 0 else " "
                after = text[candidate + len(field)] if candidate + len(field) < len(text) else " "
                if before.isalnum() or before in "._$" or after.isalnum() or after in "_$":
                    continue
                at = candidate
                break
            if at < 0:
                print("  skipped %s:%d -- no bare %r near column %d"
                      % (path.rsplit("\\", 1)[-1], number, field, column))
                continue
            src[number - 1] = (text[:at] + ACCESSORS[field] + text[at + len(field):])
            total += 1
        io.open(path, "w", encoding="utf-8", newline="\n").write("\n".join(src))
        print("  %-28s %d" % (path.rsplit("\\", 1)[-1], len(sites)))
    print("rewrote %d field accesses" % total)


if __name__ == "__main__":
    main()
