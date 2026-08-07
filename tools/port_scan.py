"""Copies the loader-agnostic half of the Forge tree into the Fabric fork.

A file comes across only if it imports nothing from Minecraft, Forge or
Mojang's client stack. That deliberately over-excludes -- a file that uses
one Component for a label stays behind for its porting phase -- because the
point of this slice is a core that compiles under a toolchain with no Forge
in it at all.
"""
import io
import os
import re
import shutil

BANNED = re.compile(r"^import (net\.minecraft|net\.minecraftforge|com\.mojang|org\.lwjgl)", re.M)

def pure(path):
    return not BANNED.search(io.open(path, encoding="utf-8").read())

def sweep(src_root, dst_root):
    copied, skipped = [], []
    for folder, _, files in os.walk(src_root):
        for name in files:
            if not name.endswith(".java"):
                continue
            src = os.path.join(folder, name)
            rel = os.path.relpath(src, src_root)
            if pure(src):
                dst = os.path.join(dst_root, rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy2(src, dst)
                copied.append(rel.replace("\\", "/"))
            else:
                skipped.append(rel.replace("\\", "/"))
    return copied, skipped

main_c, main_s = sweep("forge-src/main/java", "src/main/java")
test_c, test_s = sweep("forge-src/test/java", "src/test/java")
print("main: %d copied, %d left for later phases" % (len(main_c), len(main_s)))
print("test: %d copied, %d left" % (len(test_c), len(test_s)))
print("\ncopied packages (main):")
pkgs = {}
for f in main_c:
    pkgs.setdefault(os.path.dirname(f), 0)
    pkgs[os.path.dirname(f)] += 1
for pkg in sorted(pkgs):
    print("  %-70s %d" % (pkg, pkgs[pkg]))
