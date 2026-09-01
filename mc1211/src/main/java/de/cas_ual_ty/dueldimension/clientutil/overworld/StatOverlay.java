package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSiting;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldTransform;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import net.minecraft.client.gui.Font;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A monster's ATK and DEF, pinned to its card from the front.
 * <p>
 * Screen furniture rather than something lying on the board. Written into the
 * world the caption shares the card's tilt, its distance and its lighting, and
 * a number is exactly the thing that must not: three digits laid flat on a mat
 * eight blocks away and seen at a glancing angle are three smudges. Held
 * upright at a fixed size they are readable from anywhere a duellist can stand,
 * which is the whole job.
 * <p>
 * Where each one goes still comes from the board, through
 * {@link BoardProjection} -- the reverse of the picker's own ray -- so a
 * caption sits over the card it belongs to however the player has walked round
 * it, and cannot drift from the card a click would find.
 * <p>
 * Only while SHIFT is held. The duel screen keeps its stats up permanently
 * because that screen has nothing else to be; out here the board IS the view,
 * and a dozen captions scattered over it turns a table of cards into a
 * spreadsheet. Same key as the card's own text, so one hold answers both
 * questions a duellist has about a card.
 */
public final class StatOverlay
{
    private StatOverlay()
    {
    }

    /**
     * White as printed, and the two colours EDOPro recolours a changed stat
     * with -- its own palette is yellow and pink
     * ({@code DUELFIELD_HIGHER_CARD_ATK}, {@code DUELFIELD_LOWER_CARD_ATK});
     * blue and red are what this project asked for, and are what the duel
     * screen already uses.
     */
    private static final int PLAIN = 0xFFFFFFFF;
    private static final int RAISED = 0xFF66B2FF;
    private static final int LOWERED = 0xFFFF4C4C;
    private static final int SLASH = 0xFFB0B0B0;
    /** Dark enough to read against a bright card, thin enough to see it through. */
    private static final int BOX = 0xB0000000;

    private static final int PAD_X = 3;
    private static final int PAD_Y = 2;

    public static void draw(GuiGraphicsExtractor extractor, Font font, BoardSnapshot board,
        int seat)
    {
        // running(), not locked(): this is drawing, and a watcher holding shift
        // wants the stats of the cards they are watching. See ClientDuelField.
        if(board == null || !ClientDuelField.running() || !ClientDuelField.shiftHeld())
        {
            return;
        }
        FieldSiting siting = ClientDuelField.siting();
        if(siting == null)
        {
            return;
        }
        FieldTransform transform = new FieldTransform(siting);
        int screenW = extractor.guiWidth();
        int screenH = extractor.guiHeight();
        int viewer = Math.max(0, seat);

        // Both halves. Whose cards they are decides nothing here -- a stat is a
        // fact about the board, and a duellist needs the opponent's numbers at
        // least as much as their own.
        drawSide(extractor, font, transform, board.self() == null ? null : board.self().monsters(),
            FieldTransform.controllerFor(viewer, true), screenW, screenH, nearScale());
        drawSide(extractor, font, transform,
            board.opponent() == null ? null : board.opponent().monsters(),
            FieldTransform.controllerFor(viewer, false), screenW, screenH, FAR_SCALE);
    }

    /**
     * How much smaller the FAR row's captions are drawn.
     * <p>
     * The asymmetry is perspective's, not a judgement about whose numbers
     * matter. These captions are deliberately a fixed screen size -- that is the
     * whole point of the class, and what makes a number eight blocks away
     * readable at all -- but the board is seen from one end, so the opponent's
     * five zones are foreshortened into roughly half the screen width the
     * player's five get. Five captions that fit across the near row therefore
     * cannot fit across the far one, and they ran into each other: two of them
     * read as "1000/22000/1000", which is a number that does not exist on the
     * board.
     * <p>
     * A half rather than some fitted fraction, because it is the one factor a
     * bitmap font survives cleanly -- every glyph pixel becomes exactly one
     * screen pixel at half GUI scale, where 0.6 or 0.7 would resample them.
     */
    private static final float FAR_SCALE = 0.5F;

    /**
     * How large the NEAR row's captions are drawn: one device pixel smaller
     * than full.
     *
     * <h2>A step, not a fraction</h2>
     * This was a flat {@code 1F}. The near captions are the ones the player
     * reads constantly and they were bigger than they needed to be, crowding
     * the five zones they sit over -- but shrinking them by an arbitrary
     * fraction would cost exactly what {@link #FAR_SCALE}'s note is about: the
     * font is a bitmap, and a factor that does not put a glyph pixel on a whole
     * screen pixel resamples it into mush.
     * <p>
     * So it steps down by one DEVICE pixel per GUI pixel and no more. At the
     * GUI scale of 3 this client runs at that is 2/3; at 4 it is 3/4; at 1
     * there is no step to take and it stays at full size rather than vanishing.
     * Every one of those still lands a glyph pixel on a whole number of screen
     * pixels, which is the only property that matters here.
     * <p>
     * Asked every frame rather than computed once, because the GUI scale is a
     * setting and a caption baked at the old one would resample the moment it
     * changed.
     */
    private static float nearScale()
    {
        double gui = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        if(gui <= 1D)
        {
            // One device pixel per GUI pixel already: a step down is a step to
            // nothing.
            return 1F;
        }
        return (float)((gui - 1D) / gui);
    }

    private static void drawSide(GuiGraphicsExtractor extractor, Font font,
        FieldTransform transform, List<BoardSnapshot.Slot> monsters, int controller, int screenW,
        int screenH, float scale)
    {
        if(monsters == null)
        {
            return;
        }
        for(int sequence = 0; sequence < monsters.size(); sequence++)
        {
            BoardSnapshot.Slot slot = monsters.get(sequence);
            // Only when the numbers are actually known. The core reports -1 for
            // a value this viewer is not entitled to, and a face-down monster
            // captioned with a confident 0/0 is worse than no caption at all.
            if(slot == null || !slot.present() || slot.faceDown() || slot.code() == 0
                || slot.attack() < 0 || slot.defense() < 0)
            {
                continue;
            }
            FieldLayout.Rect zone = FieldLayout.zone(controller, OcgConstants.LOCATION_MZONE,
                sequence);
            if(zone == null)
            {
                continue;
            }
            // Just off the card's face, so the caption is anchored to the card
            // rather than to the mat under it -- which matters on a board that
            // has been raised or lowered.
            Vec3 world = transform.at(zone.x() + zone.w() / 2F, zone.y() + zone.h() / 2F,
                (CardMesh.THICKNESS + 0.02F) * transform.scale());
            double[] at = BoardProjection.project(world, screenW, screenH);
            if(at == null)
            {
                continue;
            }
            drawLabel(extractor, font, slot, (int)Math.round(at[0]), (int)Math.round(at[1]),
                scale);
        }
    }

    /**
     * @param scale drawn about the caption's own centre, so the anchor
     *              {@link BoardProjection} worked out still lands on the card --
     *              shrinking towards the origin would slide every far caption
     *              towards the top-left corner of the screen instead.
     */
    private static void drawLabel(GuiGraphicsExtractor extractor, Font font,
        BoardSnapshot.Slot slot, int centreX, int centreY, float scale)
    {
        boolean shrunk = scale != 1F;
        if(shrunk)
        {
            extractor.pose().pushMatrix();
            extractor.pose().translate(centreX, centreY);
            extractor.pose().scale(scale, scale);
            extractor.pose().translate(-centreX, -centreY);
        }
        String attack = Integer.toString(slot.attack());
        String defense = Integer.toString(slot.defense());
        int attackColour = colourOf(slot.attack(), slot.baseAttack());
        int defenseColour = colourOf(slot.defense(), slot.baseDefense());

        int width = font.width(attack) + font.width("/") + font.width(defense);
        int left = centreX - width / 2;
        int top = centreY - font.lineHeight / 2;

        extractor.fill(left - PAD_X, top - PAD_Y, left + width + PAD_X,
            top + font.lineHeight + PAD_Y - 1, BOX);

        // Each half coloured on its own, because only one of them is usually
        // the one that changed -- and a caption that recolours both when an
        // equip touches one says something that is not true.
        int x = left;
        extractor.text(font, attack, x, top, attackColour, false);
        x += font.width(attack);
        extractor.text(font, "/", x, top, SLASH, false);
        x += font.width("/");
        extractor.text(font, defense, x, top, defenseColour, false);
        if(shrunk)
        {
            extractor.pose().popMatrix();
        }
    }

    /**
     * Against the card's printed value, which is what the reference compares --
     * {@code GetAtkColor} and {@code GetDefColor} in drawing.cpp. A stat that
     * has not moved is not worth colouring.
     */
    private static int colourOf(int now, int printed)
    {
        return now > printed ? RAISED : now < printed ? LOWERED : PLAIN;
    }
}
