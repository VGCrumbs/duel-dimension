package de.cas_ual_ty.dueldimension.clientutil.overworld;

import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.FieldLayout;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSiting;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldTransform;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
        if(board == null || !ClientDuelField.locked() || !ClientDuelField.shiftHeld())
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
            FieldTransform.controllerFor(viewer, true), screenW, screenH);
        drawSide(extractor, font, transform,
            board.opponent() == null ? null : board.opponent().monsters(),
            FieldTransform.controllerFor(viewer, false), screenW, screenH);
    }

    private static void drawSide(GuiGraphicsExtractor extractor, Font font,
        FieldTransform transform, List<BoardSnapshot.Slot> monsters, int controller, int screenW,
        int screenH)
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
            drawLabel(extractor, font, slot, (int)Math.round(at[0]), (int)Math.round(at[1]));
        }
    }

    private static void drawLabel(GuiGraphicsExtractor extractor, Font font,
        BoardSnapshot.Slot slot, int centreX, int centreY)
    {
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
