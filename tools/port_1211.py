"""Copies a file from the 26.2 platform to the 1.21.1 one, renaming what is safe.

The port is 405 files and most of what separates the two versions is spelling.
Doing that by hand 405 times is how a rename gets missed in one file and found
three layers later; doing it here means the mechanical half is uniform and the
INTERESTING half is what is left over.

    python tools/port_1211.py <path under src/main/java> [...]
    python tools/port_1211.py --list-todo        # what has been ported so far

Paths are given relative to a platform's source root, e.g.

    python tools/port_1211.py de/cas_ual_ty/dueldimension/DdItems.java

WHAT IT WILL NOT DO. Only substitutions that are true in every context go in
SAFE. Anything whose translation depends on what the surrounding code MEANS is
listed in FLAG instead: the file is still copied, and every flagged line is
printed with its number so it can be dealt with deliberately. `getStringOr(k, d)`
is the example -- 1.21.1's `getString(k)` returns the value directly and cannot
express "or this default", so the fix depends on whether the default was ever
reachable. A blind rewrite there compiles and quietly changes behaviour, which is
the worst outcome available.

Nothing is ever overwritten: a file already present in mc1211 is left alone and
reported, because the second copy is usually a mistake and the first copy is
usually the one somebody has already edited.
"""
import io
import os
import re
import sys

SRC = 'mc262/src/main/java'
DST = 'mc1211/src/main/java'

#: True in every context. 26.2 renamed the type; 1.21.1 uses the old name, and
#: the mod is written in Mojang names on both, so this is pure spelling.
SAFE = [
    ('net.minecraft.resources.Identifier', 'net.minecraft.resources.ResourceLocation'),
    (r'\bIdentifier\b', 'ResourceLocation'),
]

#: Substitutions that need a human. Each is (pattern, why).
FLAG = [
    (r'\.get\w+Or\(', '1.21.1 has no *Or accessor family (1.21.5+); the default '
                      'has to go somewhere explicit'),
    (r'\bValueInput\b|\bValueOutput\b', 'entity save/load is CompoundTag in 1.21.1 (ValueInput is 1.21.6+)'),
    (r'\.setId\(', 'Item.Properties.setId is 1.21.2+; 1.21.1 registers differently'),
    (r'\bhurtServer\b', 'hurtServer is 1.21.2+; 1.21.1 has hurt(DamageSource, float)'),
    (r'\bTooltipDisplay\b', 'TooltipDisplay is 1.21.5+; appendHoverText has a different shape'),
    (r'\bRenderPipelines\b|\brendertype\.RenderTypes\b', 'the render pipeline rewrite is 1.21.5+; 1.21.1 uses RenderType'),
    (r'\bGuiGraphicsExtractor\b', 'retained-mode GUI is 26.x; 1.21.1 draws with GuiGraphics'),
    (r'\bSubmitNodeCollector\b', 'no 1.21.1 equivalent; immediate-mode drawing instead'),
    (r'\bItemStackRenderState\b|renderer\.item\.ItemModel|\bSpecialModelRenderer\b',
     'the ItemModel split is 1.21.4+; 1.21.1 has BakedModel + ItemOverrides'),
    (r'\bItemOwner\b', 'ItemOwner is 26.x; 1.21.1 passes the LivingEntity'),
    (r'Commands\.hasPermission', 'named permission checks are 1.21.6+; use hasPermission(int)'),
    (r'\bClickEvent\.\w+\(|\bHoverEvent\.\w+\(', 'sealed ClickEvent/HoverEvent records are 1.21.5+'),
    (r'\bMouseButtonEvent\b|\bKeyEvent\b|\bCharacterEvent\b|\bInputWithModifiers\b',
     'input event records are 26.x; 1.21.1 passes raw ints'),
    (r'SavedDataType|computeIfAbsent\(TYPE', 'Codec-driven SavedData is 1.21.5+'),
]


def port(rel):
    src = os.path.join(SRC, rel)
    dst = os.path.join(DST, rel)
    if not os.path.exists(src):
        print('  MISSING  %s' % rel)
        return False
    if os.path.exists(dst):
        print('  EXISTS   %s  (left alone)' % rel)
        return False

    text = io.open(src, encoding='utf-8').read()
    for pattern, replacement in SAFE:
        text = re.sub(pattern, replacement, text)

    os.makedirs(os.path.dirname(dst), exist_ok=True)
    io.open(dst, 'w', encoding='utf-8', newline='\n').write(text)

    flags = []
    for number, line in enumerate(text.splitlines(), 1):
        for pattern, why in FLAG:
            if re.search(pattern, line):
                flags.append((number, line.strip()[:88], why))
                break

    print('  PORTED   %s%s' % (rel, '' if not flags else '   %d to review' % len(flags)))
    for number, line, why in flags:
        print('      %4d  %s' % (number, line))
        print('            ^ %s' % why)
    return True


def list_todo():
    ported = set()
    for folder, _, files in os.walk(DST):
        for name in files:
            if name.endswith('.java'):
                ported.add(os.path.relpath(os.path.join(folder, name), DST).replace(os.sep, '/'))
    todo = []
    for folder, _, files in os.walk(SRC):
        for name in files:
            if not name.endswith('.java'):
                continue
            rel = os.path.relpath(os.path.join(folder, name), SRC).replace(os.sep, '/')
            if rel not in ported:
                todo.append(rel)
    print('ported %d, remaining %d' % (len(ported), len(todo)))
    return todo


def main():
    args = sys.argv[1:]
    if not args or '--help' in args:
        print(__doc__)
        return 0
    if args[0] == '--list-todo':
        for rel in sorted(list_todo()):
            print('  ' + rel)
        return 0
    done = sum(1 for rel in args if port(rel.replace('\\', '/')))
    print('\n%d of %d file(s) ported.' % (done, len(args)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
