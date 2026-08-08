"""Marks the pendulum zones on the board and prints the scales standing in them.

Under MR5 a scale sits in Spell/Trap zone 0 or 4, so those two zones carry the
mark. It is drawn under the card rather than over it: an occupied pendulum zone
already shows what is in it, and the mark is only there to answer "where does a
scale go" on an empty one.

The numbers come from the engine's own QUERY_LSCALE/QUERY_RSCALE, carried on the
slot, not from the printed card -- an effect that moves a scale has to move the
number on screen with it.
"""
import io


def sub(path, old, new, label):
    s = io.open(path, encoding="utf-8").read()
    assert old in s, "anchor missing: " + label
    io.open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
    print("   ok:", label)


T = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/DuelTextures.java"
sub(T, "    /** The field spell zone's own square, marked with a compass rose. */",
    """    /**
     * The mark on a Spell/Trap zone that is also a Pendulum Zone.
     * <p>
     * Under MR5 -- which is the mode every duel here runs -- ocgcore sets
     * DUEL_PZONE without DUEL_SEPARATE_PZONE, so a scale occupies backrow zone
     * 0 or 4 rather than a zone of its own. Nothing on the playmat says which
     * two those are, so the zone says it.
     */
    public static final Identifier PENDULUM_ZONE =
        Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/pendulum_zone.png");

    /** The field spell zone's own square, marked with a compass rose. */""",
    "DuelTextures.PENDULUM_ZONE")

B = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/BoardRenderer.java"

# ---- the two outer backrow zones are the pendulum zones ----
sub(B, """    /** A drawn slot; piles use sequence -1. */""",
    """    /**
     * Whether this Spell/Trap zone is also a Pendulum Zone.
     * <p>
     * Zones 0 and 4, the ends of the backrow. That is where ocgcore puts a
     * scale whenever DUEL_PZONE is set without DUEL_SEPARATE_PZONE, which is
     * every duel this mod runs (MR5). The separate zones FieldLayout still
     * describes at sequence 6 and 7 are the MR3 arrangement and stay empty
     * here, which is why they are not drawn.
     */
    private static boolean isPendulumZone(int location, int sequence)
    {
        return location == OcgConstants.LOCATION_SZONE && (sequence == 0 || sequence == 4);
    }

    /** A drawn slot; piles use sequence -1. */""",
    "isPendulumZone")

# ---- draw the mark, then the scales ----
sub(B, """    private void drawSlot(PoseStack poseStack, SubmitNodeCollector collector, ZonePlan zone)
    {
        BoardSnapshot.Slot slot = zone.slot();
        Hit hit = zone.hit();
        FieldLayout.Rect rect = zone.rect();""",
    """    private void drawSlot(PoseStack poseStack, SubmitNodeCollector collector, ZonePlan zone)
    {
        BoardSnapshot.Slot slot = zone.slot();
        Hit hit = zone.hit();
        FieldLayout.Rect rect = zone.rect();

        // The pendulum mark goes down FIRST, so a card set in the zone covers
        // it. It is a label for an empty zone, not decoration over a card.
        if(isPendulumZone(hit.location(), hit.sequence()))
        {
            float side = Math.min(rect.w(), rect.h()) * 0.72F;
            FieldQuad.drawProjected(poseStack, collector, DuelTextures.PENDULUM_ZONE, projection,
                new FieldLayout.Rect(rect.x() + (rect.w() - side) / 2F,
                    rect.y() + (rect.h() - side) / 2F, side, side),
                2, turnsFor(hit.controller(), false), 0F, 0F, 1F, 1F);
        }""",
    "drawSlot marks the zone")

print("done")
