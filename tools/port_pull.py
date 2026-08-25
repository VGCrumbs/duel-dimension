"""Ports whatever the 1.21.1 build is missing, until it stops asking.

The mirror of `phase0_prune.py`. That one compiles `common` and pushes out what
does not belong; this compiles `mc1211` and pulls in what it needs.

The reason it exists: a layer of a port is never the layer you named. Starting
with the twenty-one foundation files -- the entrypoint, the registries, the
networking -- the compiler immediately asks for CardItem, DeckBoxItem,
DuelistEntity and thirty more, because a registry names everything it registers.
Chasing that by hand is a lot of reading a list, finding a file, copying it, and
compiling again.

    python tools/port_pull.py            # until it stops finding new files
    python tools/port_pull.py --dry      # say what it would pull, change nothing

Each round: compile, read the unresolved symbols, find the file that declares
each in mc262, port it with `port_1211.py`'s substitutions, repeat. It stops
when a round pulls nothing -- either because the build compiles, or because what
is left is not a missing FILE but a missing API, which is the part a person has
to do.

Anything already in `common` is skipped: that module is on the classpath
already, so if its type is unresolved the problem is an import or a genuine API
difference, not an absent file. Saying so is more useful than copying a file
that is already there.
"""
import io
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import port_1211

GRADLE = os.path.abspath('gradlew25.cmd')
SRC = 'mc262/src/main/java'
COMMON = 'common/src/main/java'
DST = 'mc1211/src/main/java'

SYMBOL = re.compile(r'^\s*symbol:\s+(?:class|interface|enum)\s+(\w+)', re.M)

#: The OTHER way javac reports a missing type here, and the one that cost a
#: round: a type named by its fully-qualified name inline -- which this codebase
#: does constantly -- is reported as a missing PACKAGE, not a missing symbol.
#: "package DuelMessage does not exist" means the class DuelMessage;
#: "package de.cas_ual_ty.dueldimension.duel.dueldisk does not exist" means the
#: whole package has not been ported.
MISSING_PACKAGE = re.compile(r'package ([\w.]+) does not exist', re.M)

#: Only ours. A missing third-party package is a dependency problem and pulling
#: files cannot fix it.
OURS = 'de.cas_ual_ty.dueldimension'
MAX_ROUNDS = 30


def compile_target():
    done = subprocess.run([GRADLE, '-p', 'mc1211', 'compileJava'],
                          capture_output=True, text=True, errors='replace')
    return done.returncode, done.stdout + done.stderr


def declares(root, name, package=None):
    """The file under `root` that declares this type, or None.

    Matched on the DECLARATION rather than on the file name, because a nested
    or secondary type does not live in a file of its own -- and a name-only
    match would miss it and then pull nothing, which reads as "not portable"
    when the truth is "already coming along with its outer class".
    """
    pattern = re.compile(r'^\s*(?:public\s+|final\s+|abstract\s+|sealed\s+)*'
                         r'(?:class|interface|enum|record)\s+' + re.escape(name) + r'\b', re.M)
    for folder, _, files in os.walk(root):
        for entry in files:
            if not entry.endswith('.java'):
                continue
            path = os.path.join(folder, entry)
            if pattern.search(io.open(path, encoding='utf-8', errors='replace').read()):
                rel = os.path.relpath(path, root).replace(os.sep, '/')
                # A simple name is not unique across this codebase, and matching
                # on it alone hid 104 errors for a round: there is a DuelMessage
                # in common/ocg/msg AND a different one in mc262/duel/network,
                # so "is it in common?" answered yes about the wrong class and
                # the needed file was never pulled.
                if package and not rel.startswith(package + '/'):
                    continue
                return rel
    return None


def main():
    dry = '--dry' in sys.argv
    pulled_total = 0

    for round_number in range(1, MAX_ROUNDS + 1):
        code, output = compile_target()
        if code == 0:
            print('\nmc1211 compiles. %d file(s) pulled in total.' % pulled_total)
            return 0

        wanted = set(SYMBOL.findall(output))
        packages = set()
        for named in MISSING_PACKAGE.findall(output):
            if named.startswith(OURS):
                packages.add(named[len(OURS) + 1:].replace('.', '/'))
            elif '.' not in named:
                # A bare name is a class reached through an FQN whose owner is
                # itself missing; javac calls the prefix a package.
                wanted.add(named)
        wanted = sorted(wanted)
        wanted = list(wanted)
        if not wanted:
            print('\nNo unresolved TYPES left -- %d pulled. What remains is API '
                  'work, not missing files:\n' % pulled_total)
            for line in output.splitlines():
                if 'error:' in line:
                    print('   ' + line.strip()[:150])
            return 1

        pulled_here = []

        # Whole packages first: one missing package is usually several files,
        # and pulling them together saves a round each.
        for package in sorted(packages):
            folder = os.path.join(SRC, OURS.replace('.', '/'), package)
            if not os.path.isdir(folder):
                # Not a package at all: javac says "package a.b.C does not
                # exist" when C is a CLASS reached through a fully-qualified
                # name and C itself is missing. Retry the last segment as a
                # type before giving up on it.
                head, _, tail = package.rpartition('/')
                candidate = os.path.join(SRC, OURS.replace('.', '/'), head, tail + '.java')
                if tail and os.path.exists(candidate):
                    wanted.append(tail)
                else:
                    print('  NOT FOUND in mc262: %s' % package)
                continue
            for entry in sorted(os.listdir(folder)):
                if not entry.endswith('.java'):
                    continue
                rel = '%s/%s/%s' % (OURS.replace('.', '/'), package, entry)
                if os.path.exists(os.path.join(DST, rel)):
                    continue
                if dry:
                    print('  would pull %s' % rel)
                elif port_1211.port(rel):
                    pulled_here.append(rel)

        for name in wanted:
            if declares(COMMON, name):
                print('  in common already: %s  (import or API issue, not a missing file)' % name)
                continue
            rel = declares(SRC, name)
            if not rel:
                print('  NOT FOUND in mc262: %s' % name)
                continue
            if os.path.exists(os.path.join(DST, rel)):
                continue
            if dry:
                print('  would pull %s' % rel)
                continue
            if port_1211.port(rel):
                pulled_here.append(rel)

        if dry:
            return 0
        if not pulled_here:
            print('\nRound %d pulled nothing new. %d in total. What is left needs a '
                  'person:\n' % (round_number, pulled_total))
            for line in output.splitlines():
                if 'error:' in line:
                    print('   ' + line.strip()[:150])
            return 1

        pulled_total += len(pulled_here)
        print('round %d: pulled %d (running total %d)\n' % (round_number, len(pulled_here), pulled_total))

    print('gave up after %d rounds' % MAX_ROUNDS)
    return 1


if __name__ == '__main__':
    sys.exit(main())
