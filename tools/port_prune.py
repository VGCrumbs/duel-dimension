"""Compile-and-prune: drop copied files whose dependencies stayed behind.

The import scan is file-local, so a pure file that USES an excluded class
still came across. The compiler is the transitive-closure check; whatever it
rejects goes back to its porting phase, and the removals are recorded so
PORTING.md can list them honestly.
"""
import io

# The Forge project this one ports from: its own folder, its own build,
# left exactly as it is. Nothing here writes to it.
FORGE = "C:/Users/Admin/Desktop/YGO/CrumbyDueling"
import os
import re
import subprocess
import sys

task = sys.argv[1] if len(sys.argv) > 1 else "compileJava"
pruned = []
for round_number in range(12):
    repo = "C:/Users/Admin/Desktop/YGO/CrumbyDuelingFabric"
    result = subprocess.run(["cmd", "/c", repo.replace("/", chr(92)) + chr(92) + "gradlew25.cmd", task, "-q"], cwd=repo,
                            capture_output=True, text=True, encoding="utf-8", errors="replace")
    out = (result.stdout or "") + (result.stderr or "")
    failing = sorted(set(re.findall(r"([A-Z]:[^:{}]+?[.]java):[0-9]+: error".format(chr(10)), out)))
    if result.returncode == 0:
        print("round %d: clean" % round_number)
        break
    if not failing:
        print("round %d: failed with no file errors -- config problem" % round_number)
        print("\n".join(line for line in out.splitlines() if "error" in line.lower())[:2000])
        sys.exit(1)
    for path in failing:
        if os.path.exists(path):
            os.remove(path)
            pruned.append(os.path.relpath(path).replace("\\", "/"))
    print("round %d: pruned %d files" % (round_number, len(failing)))
else:
    sys.exit("did not converge")

io.open("build/prune-%s.txt" % task, "w", encoding="utf-8").write("\n".join(pruned))
print("total pruned: %d" % len(pruned))
for p in pruned:
    print("  ", p)
