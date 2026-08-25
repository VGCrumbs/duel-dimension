"""Works out which files can live in a version-independent `common` module.

Phase 0 of supporting two Minecraft versions from one tree. The question it
answers is the one the whole plan rests on: how much of this mod does not know
what Minecraft is?

A file qualifies if its CODE names nothing from Minecraft, the loader, LWJGL or
Mixin. Two things make that harder than grepping for imports:

  * this codebase writes fully-qualified names inline all over the place
    (`net.minecraft.client.Minecraft.getInstance()`), so an import-only scan --
    which is what `port_scan.py` does -- badly under-detects. That was fine for
    its job, copying a first slice out of the Forge tree, and is not fine here.

  * the javadoc is dense and talks about Minecraft constantly, by name. Every
    port note in this repo cites the class it replaced. Scanning raw text counts
    `GuiGraphics` in a comment as a dependency, which is how you get a number
    that is confidently wrong in the pessimistic direction.

So comments and string literals are stripped first, and what is left is the
code. Run it for the count:

    python tools/phase0_split.py

It writes nothing and moves nothing. Deciding a file is pure is not the same as
that file COMPILING alone -- it may call something impure that happens to sit in
the same package -- so the list here is a candidate set, and the compiler prunes
it. That is the same two-step `port_scan.py` and `port_prune.py` used.
"""
import io
import os
import re
import sys

#: Scanned for code that could MOVE to the shared core. Points at a platform,
#: because that is where anything not yet shared lives; `common` is the
#: destination and is not scanned. Was the repository root before the split.
SRC = 'mc262/src/main/java'
TEST = 'mc262/src/test/java'

#: Where a promoted file goes. Paths are rewritten from SRC to here.
COMMON = 'common'

#: Anything from these roots means the file belongs to one Minecraft version.
#: `com.mojang.blaze3d` and `com.mojang.math` are Minecraft's own; the rest of
#: `com.mojang` (serialization, datafixers, brigadier) are ordinary libraries
#: that resolve the same on either version -- see RELAXED below.
BANNED = re.compile(r'\b(?:net\.minecraft|net\.minecraftforge|net\.fabricmc'
                    r'|com\.mojang\.blaze3d|com\.mojang\.math'
                    r'|org\.lwjgl|org\.spongepowered)\b')

#: Allowed only in the relaxed count: DataFixerUpper and friends are on Maven
#: Central and are the same artifact for both versions, so a file using Codec
#: could share -- at the cost of `common` taking a dependency of its own.
RELAXED = re.compile(r'\bcom\.mojang\.(?:serialization|datafixers|brigadier|authlib)\b')


def code_of(text):
    """The file with comments and string literals removed.

    Crude but sufficient, and deliberately biased: anything it cannot parse it
    keeps, so a file is called impure rather than pure when in doubt. A false
    "impure" costs one file in the count; a false "pure" costs a compile error
    later and, worse, a number that overstates how easy this is.
    """
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        two = text[i:i + 2]
        if two == '//':
            i = text.find('\n', i)
            if i < 0:
                break
        elif two == '/*':
            end = text.find('*/', i + 2)
            i = n if end < 0 else end + 2
        elif c == '"':
            # Text blocks first: a `"""` opener would otherwise read as an
            # empty string followed by a stray quote.
            if text[i:i + 3] == '"""':
                end = text.find('"""', i + 3)
                i = n if end < 0 else end + 3
            else:
                i = skip_quoted(text, i, '"')
        elif c == "'":
            i = skip_quoted(text, i, "'")
        else:
            out.append(c)
            i += 1
    return ''.join(out)


def skip_quoted(text, i, quote):
    """Past the string or char literal starting at `i`, escapes respected."""
    i += 1
    while i < len(text):
        if text[i] == '\\':
            i += 2
            continue
        if text[i] == quote:
            return i + 1
        if text[i] == '\n':
            # Unterminated: not valid Java, so stop rather than eat the file.
            return i
        i += 1
    return i


def scan(root):
    strict, relaxed, impure = [], [], []
    for folder, _, files in os.walk(root):
        for name in sorted(files):
            if not name.endswith('.java'):
                continue
            path = os.path.join(folder, name).replace(os.sep, '/')
            code = code_of(io.open(path, encoding='utf-8', errors='replace').read())
            if BANNED.search(code):
                impure.append(path)
            elif RELAXED.search(code):
                relaxed.append(path)
            else:
                strict.append(path)
    return strict, relaxed, impure


def report(label, root):
    strict, relaxed, impure = scan(root)
    total = len(strict) + len(relaxed) + len(impure)
    if not total:
        return [], []
    print('%s  (%d files)' % (label, total))
    print('  pure, no library either : %4d  (%d%%)'
          % (len(strict), 100 * len(strict) // total))
    print('  pure but uses DFU/etc   : %4d' % len(relaxed))
    print('  -> shareable, relaxed   : %4d  (%d%%)'
          % (len(strict) + len(relaxed), 100 * (len(strict) + len(relaxed)) // total))
    print('  version-specific        : %4d' % len(impure))
    return strict, relaxed


def main():
    main_strict, main_relaxed = report('main', SRC)
    print()
    test_strict, test_relaxed = report('test', TEST)

    if '--list' in sys.argv:
        print('\n--- candidates for common (main) ---')
        for p in main_strict + main_relaxed:
            print(p)

    with io.open('build/phase0-common.txt', 'w', encoding='utf-8') as out:
        for p in main_strict + main_relaxed:
            out.write(p + '\n')
    with io.open('build/phase0-common-tests.txt', 'w', encoding='utf-8') as out:
        for p in test_strict + test_relaxed:
            out.write(p + '\n')
    print('\nwrote build/phase0-common.txt and build/phase0-common-tests.txt')


if __name__ == '__main__':
    main()
