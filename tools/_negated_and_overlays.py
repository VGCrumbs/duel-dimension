"""The negated marker, and Slot.overlays finally carrying something.

Chosen over counter badges after checking what the reference actually draws.
EDOPro draws NO counter badge on a field card -- counters appear only in the
hover tooltip -- so a counter overlay would be a house addition needing new art.
These two are parity, and both are nearly built already:

 1. THE NEGATED MARKER. drawing.cpp composites tNegated over any face-up
    on-field card whose status has STATUS_DISABLED (0x1) or STATUS_FORBIDDEN
    (0x4000000). Both values read from ygopro-core/ocgapi_constants.h:136,:161.
    negated.png is ALREADY in this mod, ALREADY declared as DuelTextures.NEGATED
    -- and referenced by exactly zero lines of Java. The art shipped and the
    feature never did. All it needed was QUERY_STATUS in BOARD_FLAGS.

 2. Slot.overlays WAS A LIE. The component exists, is written and read across
    the network for every card on every board update, is populated as a
    hardcoded literal 0 at all three construction sites, and is consulted by
    nothing -- `.overlays()` has zero callers. Every board packet has been
    paying for a field that is always zero and never read. QUERY_OVERLAY_CARD
    (0x10000) is what fills it: the count of Xyz materials under a monster.

Both flags were sitting in OcgConstants unused. The whole change is: ask for
them, parse them, carry them, draw them.
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

O = "src/main/java/de/cas_ual_ty/dueldimension/ocg/OcgConstants.java"
Q = "src/main/java/de/cas_ual_ty/dueldimension/ocg/query/QueryParser.java"
V = "src/main/java/de/cas_ual_ty/dueldimension/ocg/query/CardView.java"
B = "src/main/java/de/cas_ual_ty/dueldimension/ocg/prompt/BoardSnapshot.java"

# ---------- the status bits ----------
sub(O, """    public static final int QUERY_STATUS = 0x80000;""",
    """    public static final int QUERY_STATUS = 0x80000;

    /* --- card status bits, as returned by QUERY_STATUS --- */
    /** Effect negated. ygopro-core/ocgapi_constants.h:136. */
    public static final int STATUS_DISABLED = 0x1;
    /** Effect forbidden outright, a stronger negation. ocgapi_constants.h:161. */
    public static final int STATUS_FORBIDDEN = 0x4000000;""", "status bits")

# ---------- ask for them ----------
sub(Q, """        | OcgConstants.QUERY_LSCALE | OcgConstants.QUERY_RSCALE;""",
    """        | OcgConstants.QUERY_LSCALE | OcgConstants.QUERY_RSCALE
        // Whether the card's effect is negated, which is the one always-visible
        // over-card badge the reference draws.
        | OcgConstants.QUERY_STATUS
        // Xyz materials. Slot has carried an `overlays` field across the network
        // since it was written, populated with a literal 0 and read by nobody;
        // this is the flag that was missing to make it mean something.
        | OcgConstants.QUERY_OVERLAY_CARD;""", "BOARD_FLAGS")

# ---------- parse them ----------
sub(Q, """                case OcgConstants.QUERY_LSCALE -> leftScale = buffer.getInt();""",
    """                case OcgConstants.QUERY_STATUS -> status = buffer.getInt();
                case OcgConstants.QUERY_OVERLAY_CARD ->
                {
                    // u32 count, then that many u32 passcodes. Only the count
                    // is shown -- the materials themselves are drawn as a stack
                    // under the monster, not listed -- and the loop below seeks
                    // to `end` regardless, so the codes are skipped for free.
                    overlays = buffer.getInt();
                }
                case OcgConstants.QUERY_LSCALE -> leftScale = buffer.getInt();""",
    "parse cases")

print("done -- remaining edits are per-file and need their own anchors")
