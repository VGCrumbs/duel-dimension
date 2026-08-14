#!/usr/bin/env python3
"""
pull_yugipedia_set_art.py -- replace low-res ygoprodeck set thumbnails with
full-resolution Yugipedia scans, for the Duel Dimension Minecraft mod.

WHY
    Every set record in run/ydm_db/sets/*.json points its "image" field at
    https://ygoprodeck.com/pics_sets/<CODE>.jpg, which serves thumbnails
    (484 of 550 cached raws are exactly 100 px wide).  Yugipedia hosts the
    original scans of the same products.

WHAT IT DOES
    1. reads every set record in run/ydm_db/sets/*.json
    2. resolves each set to a Yugipedia set page and then to one image file:
         a) SemanticMediaWiki  [[English set prefix::CODE]][[Page type::Set page]]
         b) fallback           [[North American English set prefix::CODE]]
         c) fallback           set name as a page title, verified to be a Set page
       then prop=images on the resolved page + a suffix/region ranking picks the
       file (Booster > BoosterBox > Box > Deck > Promo; Poster/Logo excluded),
       falling back to the page's own `Set image` property.
    3. prop=imageinfo gives the original URL and its true pixel size
    4. compares against the raw file already on disk and installs ONLY when the
       candidate is strictly larger in BOTH dimensions (never a downgrade), and
       only when the aspect ratio is compatible with what is already shipped
    5. installs into the dev cache AND the live Modrinth profile using the exact
       names the mod looks up (ImageHandler.getRawSetImageFile prefers
       raw/<code>.png over raw/<code>.jpg), and deletes the stale processed
       square <size>/<code>.png so the mod regenerates it

CONDUCT
    Yugipedia is a volunteer wiki.  Every request carries a descriptive
    User-Agent with a contact address, requests are strictly sequential, and a
    hard sleep floor of ~1 s (API) / ~2 s (image downloads) is self-imposed.
    The server sends no rate-limit headers at all, so this politeness is the
    only throttle that exists.

USAGE
    python tools/pull_yugipedia_set_art.py --dry-run --limit 40
    python tools/pull_yugipedia_set_art.py --limit 40
    python tools/pull_yugipedia_set_art.py            # full run, resumable

The ledger makes the job resumable: re-running continues where it stopped.
"""

import argparse
import io
import json
import math
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is required:  python -m pip install pillow")

# --------------------------------------------------------------------------
# paths
# --------------------------------------------------------------------------
REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
DEV_SETS_JSON = os.path.join(REPO, "run", "ydm_db", "sets")
DEV_IMG = os.path.join(REPO, "run", "ydm_db_images", "sets")
LIVE_IMG = r"C:/Users/Admin/AppData/Roaming/ModrinthApp/profiles/Duel/ydm_db_images/sets"
DEV_CFG = os.path.join(REPO, "run", "config", "dueldimension-client.json")
# pristine downloads and displaced thumbnails live outside the cache; the mod
# never reads these folders.
SOURCE_DIR = os.path.join(REPO, "run", "ydm_db_images_source", "sets")
BACKUP_DIR = os.path.join(REPO, "run", "ydm_db_images_source", "displaced")
LEDGER = os.path.join(REPO, "run", "ydm_db_images_source", "yugipedia_ledger.json")

API = "https://yugipedia.com/api.php"
UA = ("DuelDimensionMC/1.0 (Duel Dimension Minecraft mod, booster-pack art fetcher; "
      "https://github.com/VGCrumbs/duel-dimension; vgcrumbsyt@gmail.com)")

API_DELAY = 1.1          # seconds between api.php calls
FILE_DELAY = 2.0         # seconds between ms.yugipedia.com file downloads
ASK_BATCH = 10           # SMW `ask` returns ZERO results past ~16 disjuncts
TITLE_BATCH = 50         # action=query accepts 50 titles

# --------------------------------------------------------------------------
# file-name ranking
# --------------------------------------------------------------------------
FILE_RE = re.compile(
    r"^(?:File:)?(?P<code>[A-Za-z0-9]+)-(?P<suf>[A-Za-z]+?)-?(?P<reg>[A-Z]{2})"
    r"(?:-(?P<var>[A-Za-z0-9]+))?\.(?P<ext>png|jpe?g)$")

SUFFIX_RANK = {"booster": 0, "boosterbox": 1, "box": 2, "deck": 3, "promo": 4}
SUFFIX_BANNED = {"poster", "logo", "ad", "sneak", "banner", "artwork", "wallpaper"}
# a set record whose code ends in _B is the box product, _P the pack product
SUFFIX_RANK_BOX = {"box": 0, "boosterbox": 1, "booster": 2, "deck": 3, "promo": 4}
REGION_RANK = {"EN": 0, "NA": 1, "EU": 2, "AE": 3}
NON_ENGLISH_RANK = 50

SYNTH_SUFFIXES = ("_B", "_P", "_SE", "_GE", "_SPE", "_SS", "-L")

_last_api = [0.0]
_last_file = [0.0]
REQUESTS = {"api": 0, "file": 0, "bytes": 0}


def log(msg):
    print(msg, flush=True)


def now_iso():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def normalise(s):
    if not s:
        return ""
    return re.sub(r"[^a-z0-9]+", " ", s.lower().replace("\u2019", "'")).strip()


def chunks(seq, n):
    for i in range(0, len(seq), n):
        yield seq[i:i + n]


# --------------------------------------------------------------------------
# rate-limited HTTP
# --------------------------------------------------------------------------
def _throttle(slot, delay):
    dt = time.time() - slot[0]
    if dt < delay:
        time.sleep(delay - dt)


def http_get(url, slot, delay, tries=3):
    last = None
    for attempt in range(tries):
        _throttle(slot, delay)
        req = urllib.request.Request(
            url, headers={"User-Agent": UA, "Accept-Encoding": "identity"})
        try:
            with urllib.request.urlopen(req, timeout=90) as r:
                data = r.read()
            slot[0] = time.time()
            return data
        except urllib.error.HTTPError as e:
            slot[0] = time.time()
            last = e
            if e.code == 429:
                log("    HTTP 429 -- backing off 60 s")
                time.sleep(60)
            elif 500 <= e.code < 600:
                time.sleep(5 * (attempt + 1))
            else:
                raise
        except Exception as e:                       # noqa: BLE001 - network
            slot[0] = time.time()
            last = e
            time.sleep(5 * (attempt + 1))
    raise last


def api(params):
    params = dict(params)
    params.setdefault("format", "json")
    url = API + "?" + urllib.parse.urlencode(params)
    data = http_get(url, _last_api, API_DELAY)
    REQUESTS["api"] += 1
    return json.loads(data.decode("utf-8"))


def ask(query):
    r = api({"action": "ask", "query": query})
    if "error" in r:
        raise RuntimeError("SMW ask error: %s" % r["error"])
    return r.get("query", {}).get("results", {}) or {}


# --------------------------------------------------------------------------
# set records
# --------------------------------------------------------------------------
def load_sets():
    """Every *.json in run/ydm_db/sets, ordered by filename."""
    named, unnamed = [], []
    for fn in sorted(os.listdir(DEV_SETS_JSON)):
        if not fn.endswith(".json"):
            continue
        p = os.path.join(DEV_SETS_JSON, fn)
        with open(p, encoding="utf-8") as fh:
            d = json.load(fh)
        code = (d.get("code") or "").strip()
        name = (d.get("name") or "").strip()
        rec = {"file": fn, "code": code, "name": name,
               "type": d.get("type"), "image": d.get("image")}
        (named if (name and code) else unnamed).append(rec)
    return named, unnamed


def base_code(code):
    c = code
    for suf in SYNTH_SUFFIXES:
        if c.endswith(suf) and len(c) > len(suf):
            c = c[: -len(suf)]
            break
    return c


def base_name(name):
    return re.sub(r"\s*\((Box|Pack)\)\s*$", "", name).strip()


def wants_box(code):
    return code.endswith("_B")


# --------------------------------------------------------------------------
# local image measurement
# --------------------------------------------------------------------------
def raw_path(root, code, ext):
    return os.path.join(root, "raw", "%s.%s" % (code.lower(), ext))


def current_raw(root, code):
    """(path, (w, h)) of the file the mod would actually load, or (None, None).

    The mod prefers raw/<code>.png over raw/<code>.jpg, and never trusts the
    extension for the format (two shipped .jpg files are really PNG).
    """
    for ext in ("png", "jpg"):
        p = raw_path(root, code, ext)
        if os.path.isfile(p) and os.path.getsize(p) > 0:
            try:
                with Image.open(p) as im:
                    return p, im.size
            except Exception as e:                    # noqa: BLE001
                log("    ! unreadable local raw %s (%s)" % (p, e))
                return p, None
    return None, None


def info_size():
    """setInfoImageSize from the dev client config (the processed square tier)."""
    try:
        with open(DEV_CFG, encoding="utf-8") as fh:
            return int(json.load(fh).get("setInfoImageSize", 256))
    except Exception:                                 # noqa: BLE001
        return 256


# --------------------------------------------------------------------------
# resolution: set -> Yugipedia page
# --------------------------------------------------------------------------
def page_from_ask_results(results, want_name, want_code):
    """Disambiguate multiple SMW hits for one prefix."""
    cands = []
    for title, v in results.items():
        p = v.get("printouts", {})
        prefixes = [str(x) for x in (p.get("English set prefix") or [])]
        prefixes += [str(x) for x in (p.get("North American English set prefix") or [])]
        if want_code and not any(x.strip().upper() == want_code.upper() for x in prefixes):
            continue
        cands.append({
            "title": title,
            "name": (p.get("English name") or [None])[0],
            "image": (p.get("Set image") or [None])[0],
            "prefix": prefixes,
        })
    if not cands:
        return None, "no_candidate"
    for c in cands:                                   # 1. English name matches
        if normalise(c["name"]) == normalise(want_name):
            return c, "name_exact"
    for c in cands:                                   # 2. page title matches
        if normalise(c["title"]) == normalise(want_name):
            return c, "title_exact"
    if len(cands) == 1:                               # 3. unique
        return cands[0], "unique"
    return None, "ambiguous"


ASK_PROPS = ("|?English name|?English set prefix|?North American English set prefix"
             "|?Set image|?Page type")


def resolve_by_prefix(sets, prop, label):
    """SMW ask over <=10 prefixes per request. Returns {code: pagedict}."""
    out = {}
    codes = sorted({base_code(s["code"]) for s in sets})
    by_base = {}
    for s in sets:
        by_base.setdefault(base_code(s["code"]), []).append(s)
    for chunk in chunks(codes, ASK_BATCH):
        q = "[[%s::%s]][[Page type::Set page]]%s|limit=500" % (
            prop, "||".join(chunk), ASK_PROPS)
        try:
            res = ask(q)
        except Exception as e:                        # noqa: BLE001
            log("    ! %s ask failed for %s: %s" % (label, chunk, e))
            continue
        if not res:
            # a non-empty disjunction returning nothing is the documented
            # silent-failure mode of SMW; surface it rather than swallowing it.
            log("    . %s: no set pages for %s" % (label, ",".join(chunk)))
            continue
        for base in chunk:
            for s in by_base.get(base, []):
                if s["code"] in out:
                    continue
                page, how = page_from_ask_results(res, s["name"], base)
                if page:
                    page = dict(page)
                    page["via"] = "%s/%s" % (label, how)
                    out[s["code"]] = page
    return out


def resolve_by_name(sets):
    """Name as a page title, then verified to really be a Set page."""
    out = {}
    want = []
    for s in sets:
        want.append((s, base_name(s["name"])))
    final = {}
    for chunk in chunks(want, TITLE_BATCH):
        titles = [t.strip() for _, t in chunk]
        titles = [t for t in titles if t and "|" not in t and "#" not in t]
        if not titles:
            continue
        try:
            r = api({"action": "query", "titles": "|".join(titles),
                     "redirects": "1", "formatversion": "2"})
        except Exception as e:                        # noqa: BLE001
            log("    ! name lookup failed: %s" % e)
            continue
        q = r.get("query", {})
        norm = {n["from"]: n["to"] for n in q.get("normalized", [])}
        red = {n["from"]: n["to"] for n in q.get("redirects", [])}
        alive = {p["title"] for p in q.get("pages", []) if not p.get("missing")}
        for s, t in chunk:
            t = t.strip()
            cur = red.get(norm.get(t, t), norm.get(t, t))
            if cur in alive:
                final[s["code"]] = (s, cur)
    # verify: only Set pages, and grab Set image at the same time
    titles = sorted({t for _, t in final.values()})
    info = {}
    for chunk in chunks([t for t in titles if "|" not in t and "[" not in t], 8):
        q = "[[%s]]%s|limit=100" % ("||".join(chunk), ASK_PROPS)
        try:
            res = ask(q)
        except Exception as e:                        # noqa: BLE001
            log("    ! name verify failed for %s: %s" % (chunk, e))
            continue
        for title, v in res.items():
            p = v.get("printouts", {})
            ptype = (p.get("Page type") or [None])[0]
            ptype = ptype.get("fulltext") if isinstance(ptype, dict) else ptype
            info[title] = {
                "title": title,
                "name": (p.get("English name") or [None])[0],
                "image": (p.get("Set image") or [None])[0],
                "prefix": [str(x) for x in (p.get("English set prefix") or [])],
                "ptype": ptype,
            }
    for code, (s, title) in final.items():
        page = info.get(title)
        if page and page.get("ptype") == "Set page":
            page = dict(page)
            page["via"] = "name/verified"
            out[code] = page
    return out


def resolve_sets(sets):
    """code -> {title, name, image, prefix, via} for everything resolvable."""
    log("  [1/3] SMW English set prefix (%d sets, %d per request)"
        % (len(sets), ASK_BATCH))
    res = resolve_by_prefix(sets, "English set prefix", "prefix")
    todo = [s for s in sets if s["code"] not in res]
    log("        resolved %d, %d left" % (len(res), len(todo)))
    if todo:
        log("  [2/3] SMW North American English set prefix (%d left)" % len(todo))
        res.update(resolve_by_prefix(todo, "North American English set prefix", "naprefix"))
        todo = [s for s in sets if s["code"] not in res]
        log("        resolved %d, %d left" % (len(res), len(todo)))
    if todo:
        log("  [3/3] set name as page title (%d left)" % len(todo))
        res.update(resolve_by_name(todo))
        todo = [s for s in sets if s["code"] not in res]
        log("        resolved %d, %d left" % (len(res), len(todo)))
    return res


# --------------------------------------------------------------------------
# resolution: page -> file name
# --------------------------------------------------------------------------
def rank_file(fname, code_ok, box):
    """Sort key for a candidate File: title. Returns None to reject."""
    m = FILE_RE.match(fname)
    if not m:
        return None
    suf = m.group("suf").lower()
    if suf in SUFFIX_BANNED:
        return None
    reg = m.group("reg").upper()
    var = (m.group("var") or "").upper()
    table = SUFFIX_RANK_BOX if box else SUFFIX_RANK
    srank = table.get(suf, 9)
    rrank = REGION_RANK.get(reg, NON_ENGLISH_RANK)
    vrank = 0 if var in ("", "1E") else (2 if var == "UE" else 1)
    crank = 0 if code_ok(m.group("code")) else 1
    return (crank, rrank, srank, vrank, len(fname), fname)


MAX_TIER = 8


def pick_files(pages):
    """prop=images on each resolved page, then rank.

    Returns {code: {"tier": [filenames], "file_via": str}} where `tier` holds
    every candidate sharing the best (code-match, region, suffix) rank -- i.e.
    the same product in the same language, differing only by variant
    (-Ver1/-Ver2/-Wave1/-1E...).  Those are then measured and disambiguated by
    aspect ratio, because filename order alone picks wrong: 2013 Collectible
    Tins Wave 1 offers CT10-PromoEN-Ver1 (792x962, one tin) and
    CT10-PromoEN-Wave1 (the landscape group shot the mod actually ships).
    """
    titles = sorted({p["title"] for p in pages.values()})
    listing = {}
    for chunk in chunks(titles, TITLE_BATCH):
        try:
            r = api({"action": "query", "titles": "|".join(chunk),
                     "prop": "images", "imlimit": "max",
                     "redirects": "1", "formatversion": "2"})
        except Exception as e:                        # noqa: BLE001
            log("    ! prop=images failed: %s" % e)
            continue
        q = r.get("query", {})
        red = {n["from"]: n["to"] for n in q.get("redirects", [])}
        for p in q.get("pages", []):
            listing[p["title"]] = [i["title"] for i in p.get("images", [])]
        for src, dst in red.items():
            if dst in listing:
                listing.setdefault(src, listing[dst])
    out = {}
    for code, page in pages.items():
        files = listing.get(page["title"], [])
        base = base_code(code).upper()
        prefixes = {base} | {str(x).strip().upper() for x in page.get("prefix", [])}

        def code_ok(c, _p=prefixes):
            return c.upper() in _p

        ranked = []
        for f in files:
            k = rank_file(f, code_ok, wants_box(code))
            if k is not None:
                ranked.append((k, f))
        ranked.sort()
        if ranked:
            best = ranked[0][0][:3]
            tier = [f for k, f in ranked if k[:3] == best][:MAX_TIER]
            how = "images/rank"
            if ranked[0][0][1] >= NON_ENGLISH_RANK:
                how = "images/rank-non-english"
            out[code] = {"tier": tier, "file_via": how, "candidates": len(ranked)}
        elif page.get("image"):
            f = str(page["image"])
            f = f if f.startswith("File:") else "File:" + f
            # The page's own `Set image` is the last resort and is NOT
            # guaranteed to be product art: Duel Terminal 1 offers exactly one
            # file, DuelTerminal.png, a photo of the arcade cabinet. Only trust
            # it when it follows the <CODE>-<Suffix><REGION> convention.
            if rank_file(f, code_ok, wants_box(code)) is not None:
                out[code] = {"tier": [f], "file_via": "smw/setimage",
                             "candidates": 0}
            else:
                out[code] = {"tier": [], "file_via": "smw/setimage-unverified",
                             "candidates": 0, "unverified": f}
    return out


def choose_from_tier(tier, infos, local_wh):
    """Pick one measured candidate out of a variant tier.

    With a local reference the shipped composition wins (closest aspect ratio,
    largest area as the tiebreak); without one, filename order then area.
    """
    measured = [(f, infos[f]) for f in tier
                if infos.get(f) and infos[f].get("width") and infos[f].get("height")]
    if not measured:
        return None, None, []
    alts = [{"file": f[5:], "w": i["width"], "h": i["height"]} for f, i in measured]
    if len(measured) == 1:
        return measured[0][0], "single", alts
    if local_wh:
        la = local_wh[0] / local_wh[1]
        measured.sort(key=lambda m: (round(abs(math.log((m[1]["width"] / m[1]["height"]) / la)), 3),
                                     -(m[1]["width"] * m[1]["height"])))
        return measured[0][0], "aspect-match", alts
    measured.sort(key=lambda m: (tier.index(m[0]), -(m[1]["width"] * m[1]["height"])))
    return measured[0][0], "rank-order", alts


def file_infos(files):
    """File: title -> {url, width, height, size, mime, sha1}"""
    out = {}
    uniq = sorted(set(files))
    for chunk in chunks(uniq, TITLE_BATCH):
        try:
            r = api({"action": "query", "titles": "|".join(chunk),
                     "prop": "imageinfo", "iiprop": "url|size|mime|sha1",
                     "formatversion": "2"})
        except Exception as e:                        # noqa: BLE001
            log("    ! imageinfo failed: %s" % e)
            continue
        q = r.get("query", {})
        norm = {n["from"]: n["to"] for n in q.get("normalized", [])}
        by_title = {}
        for p in q.get("pages", []):
            if p.get("missing") or not p.get("imageinfo"):
                continue
            ii = p["imageinfo"][0]
            by_title[p["title"]] = {
                "url": ii.get("url"), "width": ii.get("width"),
                "height": ii.get("height"), "bytes": ii.get("size"),
                "mime": ii.get("mime"), "sha1": ii.get("sha1"),
            }
        for t in chunk:
            hit = by_title.get(norm.get(t, t)) or by_title.get(t.replace("_", " "))
            if hit:
                out[t] = hit
    return out


# --------------------------------------------------------------------------
# the gate
# --------------------------------------------------------------------------
def judge(cur_wh, cand_wh):
    """('install'|'skip', reason) -- never replace a good image with a worse one."""
    cw, ch = cand_wh
    if not cw or not ch:
        return "skip", "no_dimensions"
    if cur_wh is None:
        # Nothing cached, so there is no shipped composition to preserve and
        # nothing to downgrade. The suffix ranking already rejected posters and
        # logos, and adjustRawImage fits by HEIGHT (content box size-size/8),
        # so a landscape source is side-cropped exactly as the ygoprodeck
        # landscape thumbnails already are today (CT11 100x62, MP25 300x232).
        return "install", "new_landscape" if cw / ch > 1.05 else "new"
    lw, lh = cur_wh
    if not (cw > lw and ch > lh):
        return "skip", "not_larger"
    ratio = (cw / ch) / (lw / lh)
    if not (0.60 <= ratio <= 1.6667):
        return "skip", "aspect_mismatch"
    return "install", "upgrade"


# --------------------------------------------------------------------------
# install
# --------------------------------------------------------------------------
def target_height(pristine_wh, cur_wh, size, prescale):
    """Height to store in raw/.

    ImageHandler.adjustRawImage scales the raw in ONE bilinear step into a
    content box of (size - size/8) px tall.  A >4x downscale in that step
    aliases badly (measured 21.5 dB PSNR); feeding it exactly 2x the box makes
    it a clean 0.5x step (31.4 dB).  Never go below the raw already on disk and
    never upscale past the pristine source.
    """
    pw, ph = pristine_wh
    if not prescale:
        return ph
    want = 2 * (size - size // 8)
    if cur_wh:
        # Clamp on BOTH axes. Height alone is not enough: a 302x531 local next
        # to a 943x1672 original would prescale to 300x531, i.e. 2 px narrower
        # than what is already shipped -- a downgrade the gate had just ruled out.
        want = max(want, cur_wh[1], -(-cur_wh[0] * ph // pw))
    return max(1, min(ph, want))


def install(code, blob, pristine_wh, cur_path, cur_wh, size, prescale, roots,
            src_ext):
    """Write raw/<code>.png into every root, displace the old .jpg, drop the square."""
    name = code.lower()
    # keep the pristine download outside the cache for a future tier bump
    os.makedirs(SOURCE_DIR, exist_ok=True)
    with open(os.path.join(SOURCE_DIR, "%s.%s" % (name, src_ext)), "wb") as fh:
        fh.write(blob)

    im = Image.open(io.BytesIO(blob))
    im.load()
    if im.mode not in ("RGB", "RGBA", "L"):
        im = im.convert("RGBA")
    th = target_height(pristine_wh, cur_wh, size, prescale)
    if th != im.size[1]:
        tw = max(1, round(im.size[0] * th / im.size[1]))
        im = im.resize((tw, th), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, format="PNG")
    png = buf.getvalue()
    out_wh = im.size

    written = []
    for root in roots:
        rawdir = os.path.join(root, "raw")
        if not os.path.isdir(rawdir):
            continue
        dst = os.path.join(rawdir, name + ".png")
        tmp = dst + ".part"
        with open(tmp, "wb") as fh:
            fh.write(png)
        os.replace(tmp, dst)                       # atomic: a truncated raw
        written.append(dst)                        # would poison the cache
        # the .png now wins the lookup; move the thumbnail out of the way
        old = os.path.join(rawdir, name + ".jpg")
        if os.path.isfile(old):
            os.makedirs(BACKUP_DIR, exist_ok=True)
            tag = "dev" if root == DEV_IMG else "live"
            shutil.move(old, os.path.join(BACKUP_DIR, "%s.%s.jpg" % (name, tag)))
        # drop the stale processed square(s) so the mod regenerates them
        for d in os.listdir(root):
            if d == "raw":
                continue
            sq = os.path.join(root, d, name + ".png")
            if os.path.isfile(sq):
                os.remove(sq)
    return written, out_wh


# --------------------------------------------------------------------------
# ledger
# --------------------------------------------------------------------------
def load_ledger(path):
    if os.path.isfile(path):
        try:
            with open(path, encoding="utf-8") as fh:
                d = json.load(fh)
            d.setdefault("resolve", {})
            d.setdefault("install", {})
            return d
        except Exception as e:                        # noqa: BLE001
            log("  ! ledger unreadable (%s), starting a new one" % e)
    return {"version": 1, "created": now_iso(), "resolve": {}, "install": {}}


def save_ledger(path, led):
    led["updated"] = now_iso()
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(led, fh, indent=1, ensure_ascii=False)
    os.replace(tmp, path)


TERMINAL = {"installed", "skipped_not_larger", "skipped_aspect_mismatch",
            "skipped_no_dimensions", "skipped_unconventional_file",
            "skipped_no_page", "skipped_no_file"}


# --------------------------------------------------------------------------
# safety
# --------------------------------------------------------------------------
def game_running():
    """The cache must not be edited while a client is up: ImageHandler caches
    adjusted images in memory and never re-checks the file once drawn."""
    try:
        out = subprocess.run(["tasklist"], capture_output=True, text=True,
                             timeout=30).stdout.lower()
    except Exception:                                 # noqa: BLE001
        return None
    return [p for p in ("javaw.exe", "java.exe", "minecraft.windows.exe")
            if p in out]


# --------------------------------------------------------------------------
# main
# --------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dry-run", action="store_true",
                    help="resolve and measure, download and write nothing")
    ap.add_argument("--limit", type=int, default=0,
                    help="only process the first N pending sets")
    ap.add_argument("--only", default="", help="comma-separated set codes")
    ap.add_argument("--ledger", default=LEDGER)
    ap.add_argument("--refresh", action="store_true",
                    help="ignore the ledger and redo everything")
    ap.add_argument("--retry-failed", action="store_true", default=True,
                    help="retry entries that failed before (default on)")
    ap.add_argument("--no-prescale", action="store_true",
                    help="store the pristine original in raw/ instead of a "
                         "LANCZOS pre-scale to 2x the mod's content box")
    ap.add_argument("--size", type=int, default=0,
                    help="setInfoImageSize override (default: read from config)")
    ap.add_argument("--dev-only", action="store_true",
                    help="do not touch the live Modrinth profile")
    ap.add_argument("--force", action="store_true",
                    help="write even if a java process looks like a running client")
    args = ap.parse_args()

    size = args.size or info_size()
    roots = [DEV_IMG] + ([] if args.dev_only or not os.path.isdir(LIVE_IMG) else [LIVE_IMG])

    log("Duel Dimension <- Yugipedia set art")
    log("  repo        %s" % REPO)
    log("  dev cache   %s" % DEV_IMG)
    log("  live cache  %s" % (LIVE_IMG if len(roots) > 1 else "(skipped)"))
    log("  ledger      %s" % args.ledger)
    log("  square tier %d px  (content box %d px tall)" % (size, size - size // 8))
    log("  mode        %s" % ("DRY RUN - nothing will be written"
                              if args.dry_run else "LIVE - files will be replaced"))

    if not args.dry_run:
        procs = game_running()
        if procs and not args.force:
            sys.exit("REFUSING TO WRITE: %s running. Close the game/dev client "
                     "(deleting a processed square while the client is up gives a "
                     "permanent missing-texture checkerboard for that set) or pass "
                     "--force." % ", ".join(procs))

    named, unnamed = load_sets()
    log("\n  %d set records: %d named, %d unnamed sub-sets (no art by design, skipped)"
        % (len(named) + len(unnamed), len(named), len(unnamed)))

    led = load_ledger(args.ledger)
    if args.refresh:
        led = {"version": 1, "created": now_iso(), "resolve": {}, "install": {}}

    only = {c.strip().upper() for c in args.only.split(",") if c.strip()}
    pending = []
    for s in named:
        if only and s["code"].upper() not in only:
            continue
        st = led["install"].get(s["code"], {}).get("status")
        if st in TERMINAL:
            continue
        if st == "failed" and not args.retry_failed:
            continue
        pending.append(s)
    log("  %d pending (%d already terminal in the ledger)"
        % (len(pending), len([s for s in named
                              if led["install"].get(s["code"], {}).get("status") in TERMINAL])))
    if args.limit:
        pending = pending[: args.limit]
        log("  --limit %d -> working on %d sets" % (args.limit, len(pending)))
    if not pending:
        log("\nNothing to do.")
        return

    # ---------------- resolve (cached in the ledger) ----------------
    log("\nRESOLVING")
    need = [s for s in pending if s["code"] not in led["resolve"]]
    log("  %d cached from a previous run, %d to look up"
        % (len(pending) - len(need), len(need)))
    if need:
        pages = resolve_sets(need)
        for s in need:
            p = pages.get(s["code"])
            led["resolve"][s["code"]] = {"page": p, "at": now_iso()} if p else \
                {"page": None, "at": now_iso()}
        # file choice
        got = {c: v["page"] for c, v in led["resolve"].items()
               if v.get("page") and c in {s["code"] for s in need}}
        if got:
            log("  picking files (prop=images, %d pages)" % len(got))
            picks = pick_files(got)
            allfiles = sorted({f for p in picks.values() for f in p["tier"]})
            log("  measuring originals (prop=imageinfo, %d files over %d pages)"
                % (len(allfiles), len(picks)))
            infos = file_infos(allfiles)
            for code, pick in picks.items():
                _, cur_wh = current_raw(DEV_IMG, code)
                chosen, how, alts = choose_from_tier(pick["tier"], infos, cur_wh)
                led["resolve"][code].update({
                    "file": chosen, "file_via": pick["file_via"],
                    "tier_via": how, "candidates": pick["candidates"],
                    "unverified": pick.get("unverified"),
                    "alts": alts if len(alts) > 1 else None,
                    "info": infos.get(chosen) if chosen else None})
        save_ledger(args.ledger, led)

    # ---------------- compare + install ----------------
    log("\nCOMPARING%s" % ("" if args.dry_run else " AND INSTALLING"))
    log("  %-10s %-30s %-11s %-11s %s" % ("CODE", "FILE", "ON DISK", "YUGIPEDIA", "VERDICT"))
    tally = {}
    rows = []
    for s in pending:
        code = s["code"]
        r = led["resolve"].get(code, {})
        page = r.get("page")
        cur_path, cur_wh = current_raw(DEV_IMG, code)
        cur_txt = ("%dx%d" % cur_wh) if cur_wh else ("-" if not cur_path else "unreadable")
        # Gate against the LARGEST copy in any cache: the live profile can hold
        # a file the dev tree does not, and installing there must not shrink it.
        gate_wh = cur_wh
        for other in roots[1:]:
            _, owh = current_raw(other, code)
            if owh and (not gate_wh or owh[0] * owh[1] > gate_wh[0] * gate_wh[1]):
                gate_wh = owh
                cur_txt = "%dx%d*" % owh

        if not page:
            status, reason = "skipped_no_page", "no Yugipedia set page"
        elif r.get("unverified"):
            status = "skipped_unconventional_file"
            reason = "only %s, not product art by name" % r["unverified"][5:]
        elif not r.get("file"):
            status, reason = "skipped_no_file", "page has no usable image file"
        elif not r.get("info"):
            status, reason = "skipped_no_dimensions", "imageinfo returned nothing"
        else:
            info = r["info"]
            verdict, why = judge(gate_wh, (info["width"], info["height"]))
            status = "install" if verdict == "install" else "skipped_" + why
            reason = why
        cand = r.get("info")
        cand_txt = ("%dx%d" % (cand["width"], cand["height"])) if cand else "-"
        fname = (r.get("file") or "")[5:]
        rows.append((code, fname, cur_txt, cand_txt, status, reason))

        if status != "install":
            tally[status] = tally.get(status, 0) + 1
            led["install"][code] = {"status": status, "reason": reason,
                                    "local": cur_wh, "gate": gate_wh,
                                    "cand": cand, "at": now_iso()}
            log("  %-10s %-30s %-11s %-11s %s" % (code, fname[:30], cur_txt, cand_txt, reason))
            continue

        if args.dry_run:
            tally["would_install"] = tally.get("would_install", 0) + 1
            gain = ""
            if gate_wh:
                gain = "  (+%.1fx linear)" % (cand["height"] / gate_wh[1])
            log("  %-10s %-30s %-11s %-11s WOULD INSTALL%s%s"
                % (code, fname[:30], cur_txt, cand_txt, gain,
                   "" if reason != "new_landscape" else "  [landscape, no local reference]"))
            continue

        # real download
        try:
            blob = http_get(r["info"]["url"], _last_file, FILE_DELAY)
            REQUESTS["file"] += 1
            REQUESTS["bytes"] += len(blob)
            with Image.open(io.BytesIO(blob)) as probe:
                real_wh = probe.size
            if real_wh != (cand["width"], cand["height"]):
                log("    ! %s: imageinfo said %dx%d, bytes are %dx%d -- re-judging"
                    % (code, cand["width"], cand["height"], real_wh[0], real_wh[1]))
                verdict, why = judge(gate_wh, real_wh)
                if verdict != "install":
                    led["install"][code] = {"status": "skipped_" + why, "reason": why,
                                            "local": cur_wh, "cand": cand, "at": now_iso()}
                    tally["skipped_" + why] = tally.get("skipped_" + why, 0) + 1
                    save_ledger(args.ledger, led)
                    continue
            ext = "png" if (cand.get("mime") == "image/png") else "jpg"
            written, out_wh = install(code, blob, real_wh, cur_path, gate_wh, size,
                                      not args.no_prescale, roots, ext)
            led["install"][code] = {"status": "installed", "reason": reason,
                                    "local": cur_wh, "cand": cand,
                                    "stored": list(out_wh), "files": written,
                                    "at": now_iso()}
            tally["installed"] = tally.get("installed", 0) + 1
            log("  %-10s %-30s %-11s %-11s INSTALLED as %dx%d -> %d file(s)"
                % (code, fname[:30], cur_txt, cand_txt, out_wh[0], out_wh[1], len(written)))
        except Exception as e:                        # noqa: BLE001
            led["install"][code] = {"status": "failed", "reason": repr(e),
                                    "local": cur_wh, "cand": cand, "at": now_iso()}
            tally["failed"] = tally.get("failed", 0) + 1
            log("  %-10s FAILED: %r" % (code, e))
        save_ledger(args.ledger, led)

    if args.dry_run:
        # dry run persists only the API resolution cache, never install state
        clean = {"version": led.get("version", 1), "created": led.get("created"),
                 "resolve": led["resolve"],
                 "install": load_ledger(args.ledger)["install"]}
        save_ledger(args.ledger, clean)
    else:
        save_ledger(args.ledger, led)

    # ---------------- summary ----------------
    log("\nSUMMARY (%d sets this run)" % len(pending))
    for k in sorted(tally):
        log("  %-40s %d" % (k, tally[k]))
    resolved = sum(1 for s in pending if led["resolve"].get(s["code"], {}).get("page"))
    withfile = sum(1 for s in pending if led["resolve"].get(s["code"], {}).get("info"))
    log("  %-40s %d/%d" % ("resolved to a Yugipedia set page", resolved, len(pending)))
    log("  %-40s %d/%d" % ("resolved to a measured file", withfile, len(pending)))
    log("  requests: %d api, %d file downloads, %.1f MB"
        % (REQUESTS["api"], REQUESTS["file"], REQUESTS["bytes"] / 1e6))
    log("  ledger: %s" % args.ledger)


if __name__ == "__main__":
    main()
