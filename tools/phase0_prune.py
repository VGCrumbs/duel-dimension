"""Sends back whatever `common` cannot actually compile.

`phase0_split.py` answers "does this file name Minecraft?", which is a property
of the file alone. It cannot answer "does this file only call things that are
also in common?", because that is a property of the whole set -- and the two
come apart constantly. `CustomCards` names no Minecraft class anywhere, and
holds a `CardHolder`, which does.

So the split is a guess and this is the correction: compile the module, read
which files javac rejected, move those back to the platform, compile again.
Each round shrinks the set, so it terminates; a round that rejects nothing means
the set is closed and every remaining file compiles with no Minecraft anywhere
in sight.

That is the same two-step the original Forge port used (`port_scan.py` then
`port_prune.py`) and for the same reason. Run it after moving files:

    python tools/phase0_prune.py

It moves files from common/ back to mc262/ and prints what went and why. It
never moves anything the other way -- widening the shared half is a decision,
not something a compile failure should make on its own.
"""
import io
import os
import re
import shutil
import subprocess
import sys

GRADLE = os.path.abspath('gradlew25.cmd')
COMMON = 'common'
PLATFORM = 'mc262'

#: javac's "path:line: error:" prefix, which is how a rejected file is named.
ERROR = re.compile(r'^(.*\.java):(\d+): error:', re.M)

#: Rounds before giving up. Each one strictly shrinks the set, so hitting this
#: means something is wrong with the loop rather than with the code.
MAX_ROUNDS = 25


def compile_common():
    """javac output for the shared module, however it exited."""
    done = subprocess.run([GRADLE, ':common:compileJava', ':common:compileTestJava', '-q'],
                          capture_output=True, text=True, errors='replace')
    return done.returncode, done.stdout + done.stderr


def rejected(output):
    """The files javac named, as repo-relative paths under common/."""
    out = set()
    for path, _ in ERROR.findall(output):
        path = path.strip().replace('\\', '/')
        marker = '/' + COMMON + '/'
        if marker in path:
            out.add(COMMON + '/' + path.split(marker, 1)[1])
    return out


def send_back(paths):
    """Move a file out of common and into the platform, keeping its package."""
    for path in sorted(paths):
        rel = os.path.relpath(path, COMMON).replace(os.sep, '/')
        dst = os.path.join(PLATFORM, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.move(path, dst)
        print('    <- %s' % rel)


def prune_empty_dirs(root):
    for folder, _, files in os.walk(root, topdown=False):
        if not os.listdir(folder):
            os.rmdir(folder)


def main():
    for round_number in range(1, MAX_ROUNDS + 1):
        code, output = compile_common()
        if code == 0:
            print('\ncommon compiles. %d main, %d test files.'
                  % (count(COMMON + '/src/main/java'), count(COMMON + '/src/test/java')))
            prune_empty_dirs(COMMON)
            return 0
        bad = rejected(output)
        if not bad:
            # Failed for a reason that is not a rejected source file -- a
            # missing dependency, a Gradle problem. Print it rather than
            # looping: moving files cannot fix it.
            print('common failed, but no source file was named. '
                  'This is not something pruning fixes:\n')
            print(output[-4000:])
            return 1
        print('round %d: %d file(s) back to %s' % (round_number, len(bad), PLATFORM))
        send_back(bad)
    print('gave up after %d rounds' % MAX_ROUNDS)
    return 1


def count(root):
    return sum(1 for _, _, fs in os.walk(root) for f in fs if f.endswith('.java'))


if __name__ == '__main__':
    sys.exit(main())
