"""The card list draws from the small raw; enlarged previews draw from the large.

Measured on this install:
  raw_small  268x391, ~29KB, all 10,856 cards present
  raw        up to 813x1185, ~150KB, 966 cards present (9%)

The old order tried the full raw FIRST at every size, with the small as a mere
stand-in. So a 128px grid icon, where one exists, was made by decoding an
813x1185 photograph -- about nine times the pixels the icon can show -- and for
the other 91% of cards it fell through to the small anyway. The list was paying
the full-art price for a thumbnail, inconsistently.

Now the source is chosen by the size being asked for. A thumbnail comes from the
small copy, which is present for every card and is already close to the size
wanted; an enlarged preview comes from the full art, which is the only place the
extra detail exists.

The chooser is shared by the readiness gate and the resource pack because the
cache key contains the raw's filename: if the two disagreed about which file to
use, they would look in different places and nothing would ever be a hit. That
exact bug cost a lot of frames earlier today.
"""
import io

def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    if new in s:
        print("  skip (already applied):", label)
        return
    assert old in s, "anchor missing: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("  ok:", label)

H = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/ImageHandler.java"
P = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/DdCardResourcePack.java"

sub(H, """    public static String scaledKey(String imageName, int size, File raw)""",
    """    /**
     * The largest texture still drawn from the small copy of the art.
     * <p>
     * The small raws are 268x391. Anything at or below this can be made from
     * one without visibly losing anything, and the deck editor's 128px grid
     * icons sit well inside it. The 512px previews do not, which is the whole
     * point of the split.
     */
    public static final int SMALL_SOURCE_MAX = 256;

    /**
     * Which file a card's art should be scaled from at a given size.
     * <p>
     * Small for thumbnails, full for enlargements. The small copy exists for
     * every card and is close to thumbnail size already; the full art exists
     * for a fraction of them and is the only place the extra detail lives, so
     * asking for it at thumbnail size means decoding several times the pixels
     * that can ever be shown.
     * <p>
     * <b>Both the readiness gate and the resource pack must call this.</b> The
     * cache key contains the chosen file's name, so a disagreement about which
     * file to use is a disagreement about where the scaled result lives, and
     * nothing would ever be a cache hit.
     *
     * @return the file to scale from; may not exist, which callers already test
     */
    public static File cardSourceFor(String imageName, int size)
    {
        File small = ImageHandler.getSmallCardImageFile(imageName);
        if(size <= SMALL_SOURCE_MAX && small.exists())
        {
            return small;
        }
        File full = ImageHandler.getRawCardImageFile(imageName);
        // The small copy stands in for a full image that has not downloaded,
        // which is most of them; the card sharpens when the full one lands.
        return full.exists() ? full : small;
    }

    public static String scaledKey(String imageName, int size, File raw)""",
    "cardSourceFor")

sub(H, """                File source = raw.exists() ? raw
                    : ImageHandler.getSmallCardImageFile(imageName);""",
    """                // Size decides which copy of the art this comes from; see
                // cardSourceFor. `raw` is still honoured for sets and rarities,
                // which have only the one file each.
                File source = ImageHandler.getSmallCardImageFile(imageName).exists()
                    || ImageHandler.getRawCardImageFile(imageName).exists()
                    ? ImageHandler.cardSourceFor(imageName, imageSize)
                    : raw;""", "gate picks by size")

sub(P, """        // Full card, set, rarity -- then the small copy of the card, LAST, so
        // it is only ever the stand-in. Once the full image lands the same
        // request finds it first and the card sharpens on its next load.
        for(File raw : new File[] {ImageHandler.getRawCardImageFile(image),
            ImageHandler.getRawSetImageFile(image), ImageHandler.getRawRarityImageFile(image),
            ImageHandler.getSmallCardImageFile(image)})""",
    """        // The card's art first, chosen by the size being asked for -- small
        // copy for a thumbnail, full art for an enlargement; see
        // ImageHandler.cardSourceFor, which the readiness gate calls too so the
        // two agree on the cache key. Sets and rarities follow, each having only
        // one file, and are unaffected by the split.
        for(File raw : new File[] {ImageHandler.cardSourceFor(image, size),
            ImageHandler.getRawSetImageFile(image), ImageHandler.getRawRarityImageFile(image)})""",
    "pack picks by size")

print("done")
