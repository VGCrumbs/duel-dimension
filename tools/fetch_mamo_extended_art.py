"""Populate MAMO's extended ("full") art from the only source that actually has it.

RUN THIS ONLY IF YOU WANT TO. It is opt-in on purpose and does nothing without
--yes. Read the two caveats first.

WHY A SCRIPT AND NOT SHIPPED FILES

  The card database this mod is built from keys an artwork by PASSCODE. An
  extended art reuses the base card's passcode, so the source structurally cannot
  carry a second picture for it -- see run/ydm_db/alt_art/README.md, which is why
  the alt_art mechanism exists at all.

  The 18 extended arts DO exist as scans on Yugipedia, hosted there under that
  wiki's own fair-use claim for card images. That claim does not travel: bundling
  Konami's card scans into the mod jar or a resource pack is redistribution, and
  a materially different act from a wiki hosting them. So the mod ships this
  fetcher and each user populates their own alt_art/ locally, which is exactly
  the shape the alt_art folder was designed for.

CAVEAT 1 -- THE ART IS JAPANESE. There is no English extended-art scan anywhere.
  MAMO releases 2026-09-04 and Yugipedia has no MAMO card gallery yet; the sole
  English extended art on the wiki is a Konami pre-release render carrying a
  diagonal SAMPLE watermark and a placeholder "xxx/100" passcode, unusable as
  game art. What this fetches is the OCG printing of the identical 18 cards from
  "Limit Over Collection: The Heroes" (LOCH-JP001..018), which map 1:1 onto
  MAMO-EN001..018 by passcode. The card text on the image will be Japanese.

CAVEAT 2 -- ydm_db IS DISPOSABLE. alt_art lives inside ydm_db, which the mod will
  replace wholesale if it re-downloads the database. Re-run this afterwards.

WHAT IT DOES. Writes run/ydm_db/alt_art/<passcode>/1_extended_art_loch_jp.png for
each of the 18. addLocalArtwork sorts a card's alt_art files by name and APPENDS
them, so index 0 stays the printed art and no saved deck changes meaning; the
extended art becomes selectable as an alternate artwork.

Every URL below was verified live (HTTP 200, image/png) before being written
here. Nothing in this list is reconstructed from memory.
"""
import io
import os
import sys
import time
import urllib.request

DB = "run/ydm_db"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")

# passcode, MAMO code, name, Yugipedia file URL (LOCH-JP Ultra Rare, extended art)
ART = [
    (88570003, "MAMO-EN001", "Dark Magician, the Pharaoh's Servant",
     "https://ms.yugipedia.com//e/e1/DarkMagicianthePharaohsServant-LOCH-JP-UR-EA.png"),
    (14965712, "MAMO-EN002", "Kuriboh - Multiply!",
     "https://ms.yugipedia.com//c/c2/KuribohMultiply-LOCH-JP-UR-EA.png"),
    (41350417, "MAMO-EN003", "Dark Magical Curtain",
     "https://ms.yugipedia.com//2/2e/DarkMagicalCurtain-LOCH-JP-UR-EA.png"),
    (87758525, "MAMO-EN004", "Favorite HERO Shining Flare Wingman",
     "https://ms.yugipedia.com//3/3c/FavoriteHEROShiningFlareWingman-LOCH-JP-UR-EA.png"),
    (13243124, "MAMO-EN005", "Favorite HERO Flame Wingman",
     "https://ms.yugipedia.com//7/77/FavoriteHEROFlameWingman-LOCH-JP-UR-EA.png"),
    (40237839, "MAMO-EN006", "Winged Kuriboh Sabatiel LV10",
     "https://ms.yugipedia.com//a/a0/WingedKuribohSabatielLV10-LOCH-JP-UR-EA.png"),
    (76636978, "MAMO-EN007", "Stardust Dragon - Victim Sanctuary",
     "https://ms.yugipedia.com//b/b7/StardustDragonVictimSanctuary-LOCH-JP-UR-EA.png"),
    (13021682, "MAMO-EN008", "Starjunk Synchron",
     "https://ms.yugipedia.com//5/59/StarjunkSynchron-LOCH-JP-UR-EA.png"),
    (49415281, "MAMO-EN009", "Synchro Emergency",
     "https://ms.yugipedia.com//b/bc/SynchroEmergency-LOCH-JP-UR-EA.png"),
    (76504386, "MAMO-EN010", "Number 39: Utopia, Emissary of Light",
     "https://ms.yugipedia.com//a/ad/Number39UtopiaEmissaryofLight-LOCH-JP-UR-EA.png"),
    (12908094, "MAMO-EN011", "Gagaga Magician - Gagaga Magic",
     "https://ms.yugipedia.com//a/af/GagagaMagicianGagagaMagic-LOCH-JP-UR-EA.png"),
    (48393693, "MAMO-EN012", "Gagaga Girl - Cell Phone Subtraction",
     "https://ms.yugipedia.com//f/fc/GagagaGirlCellPhoneSubtraction-LOCH-JP-UR-EA.png"),
    (75787708, "MAMO-EN013", "Odd-Eyes Pendulum Dragon of the Four Heavenly Dragons",
     "https://ms.yugipedia.com//d/d8/OddEyesPendulumDragonFourHeavenlyDragons-LOCH-JP-UR-EA.png"),
    (1186447, "MAMO-EN014", "Horoscope Sorcerer, the Stargazer Magician",
     "https://ms.yugipedia.com//6/60/HoroscopeSorcerertheStargazerMagician-LOCH-JP-UR-EA.png"),
    (48171151, "MAMO-EN015", "Astrograph Sorcerer, the Starfrost Magician",
     "https://ms.yugipedia.com//9/9a/AstrographSorcerertheStarfrostMagician-LOCH-JP-UR-EA.png"),
    (74665150, "MAMO-EN016", "Decode Talker Integration",
     "https://ms.yugipedia.com//7/78/DecodeTalkerIntegration-LOCH-JP-UR-EA.png"),
    (64865, "MAMO-EN017", "Cyberse Code Magician",
     "https://ms.yugipedia.com//9/9c/CyberseCodeMagician-LOCH-JP-UR-EA.png"),
    (37458564, "MAMO-EN018", "Cyberse Contract Witch",
     "https://ms.yugipedia.com//b/b7/CyberseContractWitch-LOCH-JP-UR-EA.png"),
]

# Sorted first, so it lands at artwork index 1, directly after the printed art.
FILENAME = "1_extended_art_loch_jp.png"


def main():
    if "--yes" not in sys.argv:
        print(__doc__)
        print("Nothing was downloaded. Re-run with --yes to actually fetch:")
        print("    python tools/fetch_mamo_extended_art.py --yes")
        return 0

    if not os.path.isdir(f"{DB}/alt_art"):
        print(f"No {DB}/alt_art -- run this from the repository root.")
        return 1

    ok = skipped = failed = 0
    for passcode, code, name, url in ART:
        folder = f"{DB}/alt_art/{passcode}"
        dest = f"{folder}/{FILENAME}"
        if os.path.exists(dest):
            print(f"  {code}  already present, left alone")
            skipped += 1
            continue
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                if r.status != 200:
                    raise IOError(f"HTTP {r.status}")
                ctype = r.headers.get("Content-Type", "")
                if not ctype.startswith("image/"):
                    raise IOError(f"not an image: {ctype}")
                data = r.read()
        except Exception as e:
            print(f"  {code}  FAILED  {e}  <- {url}")
            failed += 1
            continue

        os.makedirs(folder, exist_ok=True)
        io.open(dest, "wb").write(data)
        print(f"  {code}  {len(data):>7} B  -> {dest}")
        ok += 1
        time.sleep(0.5)          # a handful of requests, not a crawl

    print()
    print(f"fetched {ok}, already there {skipped}, failed {failed}, of {len(ART)}")
    if ok:
        print("Restart the game (or reload the database) to pick them up.")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
