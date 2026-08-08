"""Import and export decks as .ydk, the format EDOPro and YGOPro use.

Reading was already here -- YdkDeck.parse handles #main, #extra, !side and
comments, and the starter decks are loaded through it. What was missing is
writing, and any way to reach either from the editor.

The format is not guessed: it is the one the vendored Windbot decks are written
in (.wbsrc/windbot-master/Decks/*.ydk), which are real files produced by real
clients -- a "#created by" line, "#main", "#extra", and "!side" with a bang
rather than a hash, one passcode per line, repeated once per copy.

Files live in ydm_decks/ beside the mod's other game-directory folders
(ydm_binders, ydm_db_images). Import uses the same LWJGL file dialog the
under-skin import uses, so a player picks a file the way they would anywhere
else, and falls back to the folder when that native library is unavailable.
"""
import io


def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    assert old in s, "anchor missing: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("   ok:", label)


# ---------- 1. YdkDeck learns to write itself ----------
Y = "src/main/java/de/cas_ual_ty/dueldimension/ocg/deck/YdkDeck.java"
sub(Y, "    public static YdkDeck load(Path file)",
    '''    /**
     * The deck as a .ydk file's contents.
     * <p>
     * Section markers exactly as the format has them, including {@code !side}
     * with a bang rather than a hash -- that inconsistency is the format's, and
     * a file written with {@code #side} is read by other clients as more of the
     * extra deck. A copy is one line, repeated, which is how every real file
     * writes them.
     */
    public String toYdkText()
    {
        StringBuilder text = new StringBuilder();
        text.append("#created by Duel Dimension\\n");
        text.append("#main\\n");
        main.forEach(code -> text.append(code).append('\\n'));
        text.append("#extra\\n");
        extra.forEach(code -> text.append(code).append('\\n'));
        text.append("!side\\n");
        side.forEach(code -> text.append(code).append('\\n'));
        return text.toString();
    }

    public static YdkDeck load(Path file)''', "YdkDeck.toYdkText")

print("done")
