package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Answers one question: does the two-pass foil glint survive a retained-mode
 * GUI?
 * <p>
 * The trick needs the foil draw to see what the mask draw wrote to the
 * framebuffer's alpha channel. In immediate mode that was guaranteed by the
 * order of the calls. Here the GUI collects everything and draws it later, and
 * is free to reorder or merge — so the guarantee has to be checked rather than
 * assumed, before {@code ZoneWidget}, {@code BoardRenderer} and every card
 * animation are built on it.
 * <p>
 * Three panels, so the answer is visible rather than inferred:
 * <ol>
 * <li><b>Two-pass</b> — the real thing. Correct if the foil appears only near
 *     the cursor and follows it.</li>
 * <li><b>Foil only</b> — the second pass without the first. This is the control:
 *     if panel 1 looks like this, the mask pass did nothing and the batching
 *     broke it.</li>
 * <li><b>Additive</b> — the fallback. No framebuffer read, no cursor tracking,
 *     just a brighter card.</li>
 * </ol>
 * <p>
 * It also draws a fourth panel: a <b>trapezoid</b>, through {@link BoardPip} and
 * {@link FieldQuad}. That is the other thing that had to be proven — a GUI can
 * only blit axis-aligned rectangles, and every zone on a perspective duel field
 * is a trapezoid. If panel 4 shows a tapered quad, the whole board renderer has
 * a foundation.
 * <p>
 * A debug screen, not a feature. It goes when the questions are answered.
 */
public class FoilTestScreen extends Screen
{
    private static final Identifier MASK = Identifier.fromNamespaceAndPath(
        DuelDimension.MOD_ID, "textures/gui/rarity_mask.png");
    /** Stands in for a card: any opaque texture will do to glint over. */
    private static final Identifier CARD = Identifier.fromNamespaceAndPath(
        DuelDimension.MOD_ID, "textures/gui/duel_background.png");
    /** Stands in for a foil layer. */
    private static final Identifier FOIL = Identifier.fromNamespaceAndPath(
        DuelDimension.MOD_ID, "textures/item/rarity_kit.png");

    private static final int SIZE = 140;
    private static final int GAP = 24;

    public FoilTestScreen()
    {
        super(Component.literal("Foil blend test"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        float partialTick)
    {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // Two by two, sized to whatever the window actually is. Four across ran
        // off the right edge and a fourth underneath ran off the bottom; a grid
        // that measures the screen does neither.
        int size = Math.min(SIZE, Math.min((width - GAP * 3) / 2, (height - GAP * 5) / 2));
        int left = (width - (size * 2 + GAP)) / 2;
        int top = (height - (size * 2 + GAP * 3)) / 2 + GAP;

        panel(graphics, left, top, size, "1. two-pass (glint follows cursor)",
            mouseX, mouseY, true, false);
        panel(graphics, left + size + GAP, top, size, "2. foil only (control)",
            mouseX, mouseY, false, false);
        panel(graphics, left, top + size + GAP * 2, size, "3. additive (fallback)",
            mouseX, mouseY, false, true);

        // 4: the quad foundation. Narrower at the top than the bottom -- the
        // shape a zone takes on a tilted field, and the one thing a blit cannot
        // draw.
        int quadX = left + size + GAP;
        int quadY = top + size + GAP * 2;
        graphics.text(font, "4. trapezoid (tapered, not a box)", quadX, quadY - 12,
            0xFFC2C9D6, true);
        BoardPip.draw(graphics, quadX, quadY, quadX + size, quadY + size,
            (poseStack, collector) -> FieldQuad.draw(poseStack, collector, CARD,
                new FieldQuad.Corners(
                    size * 0.25F, 0F,
                    size * 0.75F, 0F,
                    size, size,
                    0F, size)));
    }

    private void panel(GuiGraphicsExtractor graphics, int x, int y, int SIZE, String label,
        int mouseX, int mouseY, boolean withMask, boolean additive)
    {
        graphics.text(font, label, x, y - 12, 0xFFC2C9D6, true);

        // The card underneath, drawn normally.
        DdBlitUtil.fullBlit(graphics, CARD, x, y, SIZE, SIZE);

        if(withMask)
        {
            // Writes alpha only. Nothing should appear from this draw itself;
            // centred on the cursor, which is what makes the glint follow it.
            graphics.blit(FoilPipelines.MASK, MASK,
                mouseX - SIZE / 2, mouseY - SIZE / 2, 0F, 0F, SIZE, SIZE,
                SIZE, SIZE, SIZE, SIZE, DdBlitUtil.NO_TINT);
        }

        graphics.blit(additive ? FoilPipelines.ADDITIVE : FoilPipelines.FOIL, FOIL,
            x, y, 0F, 0F, SIZE, SIZE, SIZE, SIZE, SIZE, SIZE, DdBlitUtil.NO_TINT);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
