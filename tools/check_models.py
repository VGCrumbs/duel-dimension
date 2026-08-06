"""Reports model faults that are easy to see in game and hard to see in JSON.

Two are checked:

  ZERO-AREA UV. A face whose uv window has no width or no height samples a
  single line of texels -- in practice, exactly the boundary between two of
  them. Texture filtering then mixes the two neighbours and the face renders as
  a speckle of both colours, which looks exactly like z-fighting and is not.
  The stock duel disk had 54 of these: its red parts were written [1,0,1,1]
  where every other face on it uses [n,0,n+1,1] to pick one texel out of a
  palette strip, so they sampled the seam between white and red.

  COINCIDENT FACES. Two faces landing on the same plane with real overlap, which
  is a genuine depth-buffer tie. Reported, not corrected: on a hand-built model
  most of these are interior surfaces that nothing can ever see, and moving
  geometry to chase them changes an artist's work for no gain. Fix these only
  when something is actually seen to flicker.

    python tools/check_models.py src/main/resources/assets/dueldimension/models
"""
import json
import math
import os
import sys


def rotation_key(element):
    rotation = element.get("rotation")
    if not rotation or rotation.get("angle", 0) == 0:
        return ("none",)
    return (rotation["angle"], rotation["axis"], tuple(rotation["origin"]))


def footprint(element):
    """The element's XZ corners once its Y rotation is applied."""
    rotation = element.get("rotation")
    x0, z0 = element["from"][0], element["from"][2]
    x1, z1 = element["to"][0], element["to"][2]
    corners = [(x0, z0), (x1, z0), (x1, z1), (x0, z1)]
    if not rotation or rotation.get("angle", 0) == 0 or rotation.get("axis") != "y":
        return corners
    angle = math.radians(rotation["angle"])
    ox, oz = rotation["origin"][0], rotation["origin"][2]
    cos, sin = math.cos(angle), math.sin(angle)
    return [(ox + (x - ox) * cos - (z - oz) * sin,
             oz + (x - ox) * sin + (z - oz) * cos) for x, z in corners]


def overlaps(first, second):
    for polygon in (first, second):
        for i in range(len(polygon)):
            x1, z1 = polygon[i]
            x2, z2 = polygon[(i + 1) % len(polygon)]
            ax, az = -(z2 - z1), (x2 - x1)
            length = math.hypot(ax, az)
            if length < 1e-9:
                continue
            ax, az = ax / length, az / length
            a = [x * ax + z * az for x, z in first]
            b = [x * ax + z * az for x, z in second]
            if max(a) <= min(b) + 1e-6 or max(b) <= min(a) + 1e-6:
                return False
    return True


def degenerate_uvs(model):
    bad = []
    for index, element in enumerate(model.get("elements") or []):
        for name, face in (element.get("faces") or {}).items():
            uv = face.get("uv")
            if uv and len(uv) == 4 and (uv[0] == uv[2] or uv[1] == uv[3]):
                bad.append((index, name, tuple(uv)))
    return bad


def coincident_faces(model):
    elements = model.get("elements") or []
    ground = [footprint(e) for e in elements]
    found = []
    for i in range(len(elements)):
        for j in range(i + 1, len(elements)):
            a, b = elements[i], elements[j]
            # Every rotation in these models is about Y, and a Y rotation leaves
            # a horizontal face on the Y plane it started on -- so these tie
            # across rotation groups, not only within one.
            if overlaps(ground[i], ground[j]) and any(
                    abs(ya - yb) < 1e-9 for ya in (a["from"][1], a["to"][1])
                    for yb in (b["from"][1], b["to"][1])):
                found.append((i, j, "y"))
            if rotation_key(a) != rotation_key(b):
                continue
            for axis in (0, 2):
                others = [k for k in range(3) if k != axis]
                if not all(min(a["to"][k], b["to"][k]) - max(a["from"][k], b["from"][k]) > 1e-9
                           for k in others):
                    continue
                if any(abs(va - vb) < 1e-9 for va in (a["from"][axis], a["to"][axis])
                       for vb in (b["from"][axis], b["to"][axis])):
                    found.append((i, j, "xz"[axis // 2]))
    return found


def check(path, root):
    with open(path, encoding="utf-8") as handle:
        try:
            model = json.load(handle)
        except ValueError as broken:
            print("  %-46s NOT VALID JSON: %s" % (os.path.relpath(path, root), broken))
            return False
    if not model.get("elements"):
        return True

    uvs = degenerate_uvs(model)
    ties = coincident_faces(model)
    if not uvs and not ties:
        return True

    print("  %s" % os.path.relpath(path, root))
    if uvs:
        windows = sorted({uv for _i, _f, uv in uvs})
        print("     zero-area UV : %d face(s), window(s) %s"
              % (len(uvs), ", ".join(str(list(w)) for w in windows)))
    if ties:
        print("     coincident   : %d face pair(s)" % len(ties))
    return not uvs


if __name__ == "__main__":
    targets = sys.argv[1:] or ["src/main/resources/assets/dueldimension/models"]
    clean = True
    for target in targets:
        print(target)
        if os.path.isdir(target):
            for dirpath, _dirs, files in os.walk(target):
                for name in sorted(files):
                    if name.endswith(".json"):
                        clean &= check(os.path.join(dirpath, name), target)
        else:
            clean &= check(target, os.path.dirname(target))
    print("\nzero-area UVs: %s" % ("none" if clean else "present -- see above"))
    raise SystemExit(0 if clean else 1)
