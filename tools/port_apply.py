"""Applies a batch of find/replace port edits, all or nothing.

The port's remaining work arrives as recipes: "in this file, replace this exact
text with that". Applying those by hand is slow and, worse, silently partial --
a `find` string that does not match does nothing at all, and the only symptom is
an error that was supposed to be fixed still being there among seven hundred
others.

So this checks every edit BEFORE writing anything:

  * the file exists
  * the `find` text appears
  * it appears exactly ONCE

Any failure and nothing is written. A find string that matches twice is rejected
rather than guessed at, because "replace the first one" is a decision the recipe
did not make.

    python tools/port_apply.py edits.json
    python tools/port_apply.py edits.json --dry

The JSON is a list of {file, find, replace, why}. `why` is not used here; it is
carried so the batch reads as a record of what was done and can be reviewed
before it is run.
"""
import io
import json
import os
import sys


def check(edits):
    """Every problem with the batch, rather than the first one."""
    problems = []
    for index, edit in enumerate(edits):
        path = edit.get('file', '').replace('\\', '/')
        if not path:
            problems.append('%d: no file named' % index)
            continue
        if not os.path.exists(path):
            problems.append('%d: %s does not exist' % (index, path))
            continue
        text = io.open(path, encoding='utf-8', errors='replace').read()
        find = edit.get('find', '')
        if not find:
            problems.append('%d: %s: empty find' % (index, path))
            continue
        count = text.count(find)
        if count == 0:
            # By far the most common failure, and the quiet one: a recipe that
            # retyped the source instead of copying it.
            problems.append('%d: %s: find text not present' % (index, path))
        elif count > 1:
            problems.append('%d: %s: find text appears %d times -- ambiguous'
                            % (index, path, count))
    return problems


def apply(edits):
    """Grouped by file so each is read and written once."""
    by_file = {}
    for edit in edits:
        by_file.setdefault(edit['file'].replace('\\', '/'), []).append(edit)

    for path, group in sorted(by_file.items()):
        text = io.open(path, encoding='utf-8', errors='replace').read()
        for edit in group:
            text = text.replace(edit['find'], edit['replace'], 1)
        io.open(path, 'w', encoding='utf-8', newline='\n').write(text)
        print('  %-72s %d edit(s)' % (path, len(group)))
    return len(by_file)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if not args:
        print(__doc__)
        return 0
    edits = json.load(io.open(args[0], encoding='utf-8'))
    if isinstance(edits, dict):
        edits = edits.get('edits', [])

    problems = check(edits)
    if problems:
        print('%d of %d edit(s) cannot be applied. NOTHING was written:\n'
              % (len(problems), len(edits)))
        for problem in problems:
            print('  ' + problem)
        return 1

    if '--dry' in sys.argv:
        print('all %d edit(s) check out; --dry, so nothing written' % len(edits))
        return 0

    files = apply(edits)
    print('\napplied %d edit(s) across %d file(s).' % (len(edits), files))
    return 0


if __name__ == '__main__':
    sys.exit(main())
