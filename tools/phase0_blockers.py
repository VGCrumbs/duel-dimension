"""Ranks what is holding the shared core back, by how many files each thing costs.

`phase0_split.py` finds files that name no Minecraft class. `phase0_prune.py`
then bounces the ones that do not compile anyway, because naming no Minecraft
class is not the same as calling only things that are also shared. What neither
answers is the useful question: WHICH missing thing is responsible, and how much
would fixing it buy.

That question has a good answer and it is worth having, because the payoffs are
wildly uneven. `DdLog` is the example: `HeadlessDuelRunner` was version-specific
over two calls to `DuelDimension.debug`, and giving the shared half its own
logger -- about ten lines -- moved 23 files across, including every test that
drives a real duel. Blindly promoting and pruning finds that eventually. Asking
javac what it could not resolve finds it immediately.

    python tools/phase0_blockers.py

It promotes every textually-pure file, compiles once, reads the errors, and puts
everything back. Nothing is left moved and nothing is committed -- this only
prints. Act on the ranking, then run split/promote/prune to bank the result.
"""
import collections
import io
import os
import re
import shutil
import subprocess
import sys

GRADLE = os.path.abspath('gradlew25.cmd')
PLATFORM = 'mc262'
COMMON = 'common'
LISTS = ('build/phase0-common.txt', 'build/phase0-common-tests.txt')

#: javac names what it could not resolve on a line of its own after the error.
SYMBOL = re.compile(r'^\s*symbol:\s+(?:class|variable|method)\s+(\w+)', re.M)
PACKAGE = re.compile(r'^(.*\.java):\d+: error: package ([\w.]+) does not exist', re.M)
#: Which file each error belongs to, so a blocker can be counted per FILE
#: rather than per mention -- one file citing a class forty times is one file.
ERROR_AT = re.compile(r'^(.*\.java):(\d+): error:', re.M)


def promote():
    """Every pure candidate into common. Returns what to undo."""
    moved = []
    for listfile in LISTS:
        if not os.path.exists(listfile):
            continue
        for line in io.open(listfile, encoding='utf-8'):
            src = line.strip()
            if not src or not os.path.exists(src):
                continue
            dst = src.replace(PLATFORM + '/', COMMON + '/', 1)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.move(src, dst)
            moved.append((src, dst))
    return moved


def restore(moved):
    for src, dst in reversed(moved):
        if os.path.exists(dst):
            os.makedirs(os.path.dirname(src), exist_ok=True)
            shutil.move(dst, src)


def compile_common():
    done = subprocess.run([GRADLE, ':common:compileJava', ':common:compileTestJava', '-q'],
                          capture_output=True, text=True, errors='replace')
    return done.stdout + done.stderr


def blame(output):
    """Missing symbol -> the set of files that could not resolve it."""
    # Walk the output once, remembering which file the current error is in, so
    # a `symbol:` line is attributed to the file above it rather than counted
    # loose.
    blocked = collections.defaultdict(set)
    current = None
    for line in output.splitlines():
        at = ERROR_AT.match(line)
        if at:
            current = at.group(1).strip().replace('\\', '/')
            missing = PACKAGE.match(line)
            if missing:
                blocked['package ' + missing.group(2)].add(current)
            continue
        found = SYMBOL.match(line)
        if found and current:
            blocked[found.group(1)].add(current)
    return blocked


def main():
    if not any(os.path.exists(f) for f in LISTS):
        print('No candidate lists. Run tools/phase0_split.py first.')
        return 1

    moved = promote()
    print('promoted %d file(s), compiling...' % len(moved))
    try:
        output = compile_common()
    finally:
        restore(moved)
        print('put them all back.\n')

    blocked = blame(output)
    if not blocked:
        print('Nothing unresolved -- every pure file compiles. Promote them for real.')
        return 0

    print('%-34s %s' % ('BLOCKER', 'files it holds back'))
    print('-' * 60)
    for name, files in sorted(blocked.items(), key=lambda kv: -len(kv[1]))[:30]:
        print('%-34s %d' % (name, len(files)))

    total = set()
    for files in blocked.values():
        total |= files
    print('\n%d file(s) blocked by %d distinct missing thing(s).' % (len(total), len(blocked)))
    print('Fix from the top: each one is worth the file count beside it, and the')
    print('counts overlap, so re-run after every fix rather than trusting the sum.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
