"""Pendulum zones on the board, with the scales the engine actually holds.

Under MR5 -- which is the mode every duel here runs -- ocgcore sets DUEL_PZONE
but not DUEL_SEPARATE_PZONE, so a scale sits in Spell/Trap zone 0 or 4 rather
than in a zone of its own. FieldLayout's szone 6 and 7 rects are the MR3
separate-zone layout and stay unused; what was missing is that the two outer
backrow zones are never MARKED as the pendulum zones, and that the scale a card
is currently at is never carried to the client at all.

The scale is asked of the engine (QUERY_LSCALE/QUERY_RSCALE) rather than read
off the printed card, because effects change it -- Sky Iris and the like -- and
a printed value would quietly disagree with the rules the duel is running by.
"""
import io


def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    assert old in s, "anchor missing: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("   ok:", label)


# ---------- 1. ask the engine for the scales ----------
Q = "src/main/java/de/cas_ual_ty/dueldimension/ocg/query/QueryParser.java"
sub(Q, "        | OcgConstants.QUERY_EQUIP_CARD;",
    "        | OcgConstants.QUERY_EQUIP_CARD\n"
    "        // A pendulum card's CURRENT scale, which is not its printed one\n"
    "        // once an effect has moved it.\n"
    "        | OcgConstants.QUERY_LSCALE | OcgConstants.QUERY_RSCALE;",
    "BOARD_FLAGS asks for the scales")

sub(Q, "                case OcgConstants.QUERY_BASE_DEFENSE -> baseDefense = buffer.getInt();",
    "                case OcgConstants.QUERY_BASE_DEFENSE -> baseDefense = buffer.getInt();\n"
    "                case OcgConstants.QUERY_LSCALE -> leftScale = buffer.getInt();\n"
    "                case OcgConstants.QUERY_RSCALE -> rightScale = buffer.getInt();",
    "parseCard reads them")

sub(Q, """                    return new CardView(code, position, type, level, attack, defense,
                        baseAttack, baseDefense, isPublic, false, equip);""",
    """                    return new CardView(code, position, type, level, attack, defense,
                        baseAttack, baseDefense, leftScale, rightScale, isPublic, false, equip);""",
    "parseCard builds with them")

# The two locals the cases above assign.
s = io.open(Q, encoding="utf-8").read()
old = "        int baseDefense = -1;"
assert old in s, "parseCard locals"
s = s.replace(old, old + "\n        // -1 for \"not a pendulum card\", as with the stats.\n"
                         "        int leftScale = -1;\n        int rightScale = -1;", 1)
io.open(Q, "w", encoding="utf-8", newline="\n").write(s)
print("   ok: parseCard locals")

# ---------- 2. CardView carries them ----------
C = "src/main/java/de/cas_ual_ty/dueldimension/ocg/query/CardView.java"
sub(C, """ * @param defense  current DEF, -1 when hidden or not a monster""",
    """ * @param defense  current DEF, -1 when hidden or not a monster
 * @param leftScale  current blue scale, -1 when hidden or not a pendulum card
 * @param rightScale current red scale, -1 when hidden or not a pendulum card""",
    "CardView javadoc")

sub(C, """public record CardView(int code, int position, int type, int level, int attack, int defense,
    int baseAttack, int baseDefense, boolean isPublic, boolean hidden, Equip equip)
{
    public static final CardView HIDDEN = new CardView(0, 0, 0, 0, -1, -1, -1, -1, false, true, null);""",
    """public record CardView(int code, int position, int type, int level, int attack, int defense,
    int baseAttack, int baseDefense, int leftScale, int rightScale,
    boolean isPublic, boolean hidden, Equip equip)
{
    public static final CardView HIDDEN =
        new CardView(0, 0, 0, 0, -1, -1, -1, -1, -1, -1, false, true, null);

    /** Whether the engine gave this card a scale, i.e. it is a pendulum card. */
    public boolean hasScale()
    {
        return leftScale >= 0 && rightScale >= 0;
    }""",
    "CardView fields")

# ---------- 3. the concealment path must blank them too ----------
B = "src/main/java/de/cas_ual_ty/dueldimension/ocg/query/BoardState.java"
sub(B, """        // Keep the position (that a card is set there is public) but strip
        // identity, including the base stats a set card must not reveal.
        return new CardView(0, card.position(), 0, 0, -1, -1, -1, -1, false, true, null);""",
    """        // Keep the position (that a card is set there is public) but strip
        // identity, including the base stats a set card must not reveal -- and
        // the scales, which would name a set pendulum card outright.
        return new CardView(0, card.position(), 0, 0, -1, -1, -1, -1, -1, -1, false, true, null);""",
    "hidden strips the scales")

sub(B, """            : new CardView(card.code(), card.position(), card.type(), card.level(), card.attack(),
                card.defense(), card.baseAttack(), card.baseDefense(), card.isPublic(), card.hidden(),
                new CardView.Equip(controller, equip.location(), equip.sequence()));""",
    """            : new CardView(card.code(), card.position(), card.type(), card.level(), card.attack(),
                card.defense(), card.baseAttack(), card.baseDefense(),
                card.leftScale(), card.rightScale(), card.isPublic(), card.hidden(),
                new CardView.Equip(controller, equip.location(), equip.sequence()));""",
    "relative keeps the scales")

# ---------- 4. across the wire ----------
S = "src/main/java/de/cas_ual_ty/dueldimension/ocg/prompt/BoardSnapshot.java"
sub(S, """    public record Slot(boolean present, int code, boolean faceDown, boolean defence, int attack, int defense,
        int baseAttack, int baseDefense, int overlays, CardView.Equip equip)
    {
        public static final Slot EMPTY = new Slot(false, 0, false, false, 0, 0, 0, 0, 0, null);""",
    """    public record Slot(boolean present, int code, boolean faceDown, boolean defence, int attack, int defense,
        int baseAttack, int baseDefense, int leftScale, int rightScale, int overlays,
        CardView.Equip equip)
    {
        public static final Slot EMPTY =
            new Slot(false, 0, false, false, 0, 0, 0, 0, -1, -1, 0, null);

        /** Whether this slot holds a card the engine gave a pendulum scale. */
        public boolean hasScale()
        {
            return present && !faceDown && leftScale >= 0 && rightScale >= 0;
        }""",
    "Slot carries the scales")

sub(S, """            return new Slot(true, card.code(), !card.isFaceUp(), !card.isAttackPosition(),
                card.attack(), card.defense(), card.baseAttack(), card.baseDefense(), 0, card.equip());""",
    """            return new Slot(true, card.code(), !card.isFaceUp(), !card.isAttackPosition(),
                card.attack(), card.defense(), card.baseAttack(), card.baseDefense(),
                card.leftScale(), card.rightScale(), 0, card.equip());""",
    "Slot.of")

sub(S, """                buffer.writeVarInt(baseDefense + 1);
                buffer.writeVarInt(overlays);""",
    """                buffer.writeVarInt(baseDefense + 1);
                // Shifted like the stats, for the same -1 sentinel.
                buffer.writeVarInt(leftScale + 1);
                buffer.writeVarInt(rightScale + 1);
                buffer.writeVarInt(overlays);""",
    "Slot.write")

sub(S, """            int baseDefense = buffer.readVarInt() - 1;
            int overlays = buffer.readVarInt();""",
    """            int baseDefense = buffer.readVarInt() - 1;
            int leftScale = buffer.readVarInt() - 1;
            int rightScale = buffer.readVarInt() - 1;
            int overlays = buffer.readVarInt();""",
    "Slot.read")

sub(S, """            return new Slot(true, code, faceDown, defence, attack, defense,
                baseAttack, baseDefense, overlays, equip);""",
    """            return new Slot(true, code, faceDown, defence, attack, defense,
                baseAttack, baseDefense, leftScale, rightScale, overlays, equip);""",
    "Slot.read builds")
