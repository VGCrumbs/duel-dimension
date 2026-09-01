package de.cas_ual_ty.dueldimension.clientutil.hub;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Buttons and tabs drawn from the hub's own textures.
 * <p>
 * Vanilla's {@code Button} paints itself from the widgets atlas and fills a
 * rectangle behind its label. Both are overridden here so the only thing the
 * game draws is the text; every pixel of surface comes from a PNG under
 * {@code textures/gui}.
 * <p>
 * Three things moved since the Forge version, and all three are the GUI going
 * retained-mode. {@code renderButton(PoseStack, ...)} became
 * {@code extractContents(GuiGraphicsExtractor, ...)}: a widget no longer draws
 * itself, it <em>describes</em> itself and the game draws everything later in
 * one pass. {@code x}, {@code y} and {@code width} are private, so the
 * accessors are used. And {@code Button}'s constructor gained a narration
 * argument, which is worth having — a button that says nothing to a screen
 * reader is a button some players cannot use.
 */
public final class HubWidgets
{
    private HubWidgets()
    {
    }

    /** A button whose surface is {@link HubTextures#BUTTON}. */
    public static class TextureButton extends Button
    {
        /** Overrides the label's colour, for a row that is reporting a state. */
        private Integer labelColour;
        /** Shown while hovered, when a button needs to explain itself. */
        private java.util.List<String> tooltip = java.util.List.of();
        /**
         * Whether this button is offered, asked every frame rather than set.
         * <p>
         * For a button whose answer can change without the screen rebuilding.
         * Clear Filters is the case that named this: the deck editor's search
         * box gives it something to clear on every keystroke, and its responder
         * deliberately does NOT rebuild the widgets -- a rebuild there drops the
         * field's focus mid-word -- so the button stayed greyed out over a
         * filter that was plainly on. Same shape as {@code ChipButton}'s lit.
         */
        private java.util.function.BooleanSupplier activeSupplier;

        public TextureButton(int x, int y, int width, int height, Component label, OnPress onPress)
        {
            // DEFAULT_NARRATION reads the label, which is what these buttons
            // want: their surface is decoration and the text is the meaning.
            super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        }

        public void setLabelColour(int colour)
        {
            labelColour = colour;
        }

        /** Lets specialised buttons retain a state colour set by their screen. */
        protected int labelColourOr(int fallback)
        {
            return labelColour == null ? fallback : labelColour;
        }

        public void setActiveSupplier(java.util.function.BooleanSupplier supplier)
        {
            activeSupplier = supplier;
        }

        public void setTooltipLines(java.util.List<String> lines)
        {
            tooltip = lines == null ? java.util.List.of() : lines;
        }

        public java.util.List<String> tooltipLines()
        {
            return tooltip;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick)
        {
            // Answered before the surface is chosen, and written back to
            // `active` so the CLICK agrees with the picture rather than only
            // the paint doing.
            if(activeSupplier != null)
            {
                active = activeSupplier.getAsBoolean();
            }
            int row = !active ? NineSlice.DISABLED
                : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(graphics, HubTextures.BUTTON, getX(), getY(), getWidth(), getHeight(),
                row, 3);
            int colour = labelColour != null ? labelColour
                : active ? isHoveredOrFocused() ? MenuInk.title() : MenuInk.label()
                    : MenuInk.dim();
            drawLabel(graphics, colour);
        }

        void drawLabel(GuiGraphicsExtractor graphics, int colour)
        {
            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
            String text = getMessage().getString();
            graphics.text(font, text, getX() + (getWidth() - font.width(text)) / 2,
                getY() + (getHeight() - 8) / 2, colour, MenuInk.shadow());
        }
    }

    /**
     * A tick box: a small square that is either ticked or not, and a label
     * beside it.
     *
     * <h2>Why not a button that says ON</h2>
     * A button is a thing you press to make something happen. A setting is a
     * state that is either set or not, and reading it off the end of a label --
     * "Destiny Draws: ON" -- means reading a sentence to answer a yes/no. The
     * box answers it before the words are read, and the words then say what the
     * answer is about.
     * <p>
     * The state is a SUPPLIER rather than a stored flag, so a box whose setting
     * is changed from somewhere else is right on the next frame instead of on
     * the next rebuild.
     */
    public static class CheckBox extends TextureButton
    {
        private final java.util.function.BooleanSupplier ticked;

        public CheckBox(int x, int y, int width, int height, Component label,
            java.util.function.BooleanSupplier ticked, OnPress onPress)
        {
            super(x, y, width, height, label, onPress);
            this.ticked = ticked;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick)
        {
            
            int box = Math.min(getHeight(), 14);
            int boxY = getY() + (getHeight() - box) / 2;
            NineSlice.draw(graphics, HubTextures.SLOT, getX(), boxY, box, box);
            if(ticked.getAsBoolean())
            {
                // CHECK is white art meant to be tinted; green is the same
                // "yes, this one" the shops mark a collected tile with.
                int tick = box - 4;
                de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.fullBlit(graphics,
                    HubTextures.CHECK, getX() + 2, boxY + 2, tick, tick,
                    de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.tint(
                        0.49F, 0.89F, 0.55F, 1F));
            }
            // Beside the box and LEFT aligned, not centred in the row: a label
            // that centred itself would drift as the word changed length and
            // would sometimes sit under its own tick.
            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
            String text = getMessage().getString();
            graphics.text(font, text, getX() + box + 6, getY() + (getHeight() - 8) / 2,
                !active ? MenuInk.dim()
                    : isHoveredOrFocused() ? MenuInk.title() : MenuInk.label(),
                MenuInk.shadow());
        }
    }

    /**
     * A button whose label is a picture.
     * <p>
     * The same surface, the same three states and the same tooltip as its
     * parent -- only the middle is a texture instead of a word. That is what
     * lets an action shrink to a square without becoming a mystery: the icon
     * says what it is at a glance and the tooltip says it in words on hover,
     * where a truncated label would say neither.
     * <p>
     * The icon is tinted with the colour the LABEL would have taken, which is
     * why the files are white. So idle, hovered and disabled read exactly as
     * they do across the rest of the hub, from one texture.
     */
    public static class IconButton extends TextureButton
    {
        private final net.minecraft.resources.Identifier icon;
        /** How much of the button's shorter side the icon covers. */
        private static final float FILL = 0.62F;

        public IconButton(int x, int y, int width, int height,
            net.minecraft.resources.Identifier icon, Component narration, OnPress onPress)
        {
            // The narration is still a word: a screen reader cannot read a
            // texture, and DEFAULT_NARRATION reads getMessage().
            super(x, y, width, height, narration, onPress);
            this.icon = icon;
        }

        @Override
        void drawLabel(GuiGraphicsExtractor graphics, int colour)
        {
            int size = Math.round(Math.min(getWidth(), getHeight()) * FILL);
            de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.blit(graphics, icon,
                getX() + (getWidth() - size) / 2, getY() + (getHeight() - size) / 2,
                size, size, 0F, 0F, 1F, 1F, 0xFF000000 | (colour & 0xFFFFFF));
        }
    }

    /**
     * A tab. Selection is a state of the texture rather than a different
     * colour, so the whole strip can be reskinned from one file.
     */
    public static class TabButton extends TextureButton
    {
        private final java.util.function.BooleanSupplier selected;

        public TabButton(int x, int y, int width, int height, Component label,
            java.util.function.BooleanSupplier selected, OnPress onPress)
        {
            super(x, y, width, height, label, onPress);
            this.selected = selected;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick)
        {
            boolean on = selected.getAsBoolean();
            int row = on ? NineSlice.SELECTED
                : isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE;
            NineSlice.draw(graphics, HubTextures.TAB, getX(), getY(), getWidth(), getHeight(),
                row, 3);
            drawLabel(graphics, on ? MenuInk.title() : MenuInk.body());
        }
    }

    /**
     * One fixed-ratio tile from Master Duel's six-column deck selector.
     * <p>
     * The surface, clipped corners, regulation ring and icons are all PNGs;
     * only the live deck name and the regulation word use the font.  The deck
     * case is intentionally one placeholder for now because a profile does not
     * yet carry Master Duel case IDs.
     */
    /**
     * The deck case art's width over its height.
     * <p>
     * Measured from the files rather than chosen: every {@code deck_box_*.png}
     * is 422x512. A case drawn to any other ratio is a stretched case, and it
     * shows -- these are photographs of a box, so the eye knows the shape.
     */
    public static final float DECK_BOX_ASPECT = 422F / 512F;

    /** Where a deck case sits inside its tile, relative to the tile. */
    public record CaseFit(int x, int y, int w, int h)
    {
    }

    /**
     * Fits a deck case into a tile: as large as the room allows, still itself.
     * <p>
     * A static function of the tile so it can be TESTED, and one calculation
     * rather than two. It used to be {@code 0.32 * width} by
     * {@code 0.49 * height} -- two unrelated fractions of two different
     * dimensions, so the case's shape was whatever the tile's shape made it.
     * On the hub's own tile that came out square, stretching a 0.824 case about
     * a fifth too wide, and small with it: floors of 18 and 24 meant a short
     * tile got a case that had stopped shrinking while the tile had not.
     *
     * @param nameBandH what the name is reserving along the bottom
     * @param pad       the margin around the case, top and sides
     */
    public static CaseFit fitDeckCase(int tileW, int tileH, int nameBandH, int pad)
    {
        // The name's band comes off the bottom first and the case takes the
        // rest, which is the same order the deck box shop's tiles use.
        int space = Math.max(1, tileH - nameBandH - pad * 2);
        int h = space;
        int w = Math.round(h * DECK_BOX_ASPECT);
        // Sized by height, so a wide-and-short tile is the one that pushes it
        // out sideways; the clamp keeps the ratio rather than trimming it.
        int maxW = Math.max(1, tileW - pad * 2);
        if(w > maxW)
        {
            w = maxW;
            h = Math.max(1, Math.round(w / DECK_BOX_ASPECT));
        }
        return new CaseFit((tileW - w) / 2, pad + Math.max(0, (space - h) / 2), w, h);
    }

    public static class DeckTileButton extends TextureButton
    {
        private static final float BADGE_TEXT_SCALE = 0.35F;
        /**
         * Clearance under the name, between its descenders and the tile's
         * rounded corner. Two, which is what the frame's curve actually needs.
         */
        private static final int NAME_CLEARANCE = 2;

        /**
         * How big the deck's name is drawn, against the font's own size.
         * <p>
         * Half. A deck tile is a picture of a case with a caption under it, and
         * the caption was competing with the case for the tile -- at full size
         * "White Lightning Attack" got eleven characters and a band a fifth of
         * the tile deep. Half the height is half the band, which the case takes
         * back, and twice the name.
         */
        private static final float NAME_SCALE = 0.5F;
        /**
         * The case's margin from the tile's top and sides.
         * <p>
         * Two rather than three. A deck tile is 68 by 58 and the case is a
         * portrait 0.824, so its height is the only thing that limits it -- the
         * side margin never binds at all, and every unit taken off the top is a
         * unit the case grows by.
         */
        private static final int CASE_PAD = 2;
        private final net.minecraft.resources.Identifier deckBoxTexture;
        private final boolean selected;
        private final boolean addTile;

        public DeckTileButton(int x, int y, int width, int height, Component narration,
            net.minecraft.resources.Identifier deckBoxTexture, boolean selected,
            boolean addTile, OnPress onPress)
        {
            super(x, y, width, height, narration, onPress);
            this.deckBoxTexture = deckBoxTexture;
            this.selected = selected;
            this.addTile = addTile;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick)
        {
            de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.fullBlit(graphics,
                selected ? HubTextures.DECK_TILE_SELECTED : HubTextures.DECK_TILE,
                getX(), getY(), getWidth(), getHeight());

            if(addTile)
            {
                int size = Math.round(Math.min(getWidth(), getHeight()) * 0.50F);
                de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.fullBlit(graphics,
                    HubTextures.ADD_DECK, getX() + (getWidth() - size) / 2,
                    getY() + (getHeight() - size) / 2, size, size,
                    isHoveredOrFocused() ? 0xFFBAFF00
                        : de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.NO_TINT);
            }
            else
            {
                // The name's band comes off the bottom and the case sits above
                // it, clear of the text rather than behind it.
                //
                // The case briefly took the whole tile with the name shadowed
                // over its foot, which is how Master Duel does it and which is
                // bigger -- but the bottom of a case is artwork, and a name
                // laid across it reads as something covering the picture rather
                // than as a label under it.
                CaseFit fit = fitDeckCase(getWidth(), getHeight(), nameBandH(), CASE_PAD);
                de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.fullBlit(graphics,
                    deckBoxTexture, getX() + fit.x(), getY() + fit.y(), fit.w(), fit.h());

                // The regulation badge, off until it means something.
                //
                // It says STANDARD on every tile because nothing chooses it: a
                // deck carries no format, so this is a label that cannot yet be
                // wrong and cannot yet be right. Kept rather than deleted --
                // when a deck knows its format this is where the badge goes,
                // and tinyCentredText below is what draws its word.
                //
                // int badgeW = Math.max(19, Math.round(getWidth() * 0.29F));
                // int badgeH = Math.max(9, Math.round(badgeW * 0.50F));
                // int badgeX = getX() + getWidth() - badgeW - 2;
                // int badgeY = getY() + 2;
                // de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.fullBlit(graphics,
                //     HubTextures.STANDARD_BADGE, badgeX, badgeY, badgeW, badgeH);
                // tinyCentredText(graphics, "STANDARD", badgeX, badgeY, badgeW, badgeH,
                //     0xFF369BFF);

                net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft
                    .getInstance().font;
                // Half size, which also doubles how much of a name fits: the
                // budget handed to plainSubstrByWidth is in TEXT pixels, so it
                // has to be divided by the scale or a name would still be cut
                // at the width it used to be and then drawn half as wide.
                // And by the SNAPPED scale, the one tinyText will draw at:
                // dividing by the wanted 0.5 while drawing at 2/3 would fit a
                // name to a width a third wider than the tile.
                String name = font.plainSubstrByWidth(getMessage().getString(),
                    Math.round((getWidth() - 8) / MenuText.crispScale(NAME_SCALE)));
                tinyText(graphics, name, getX() + getWidth() / 2F,
                    getY() + getHeight() - nameBandH(), NAME_SCALE,
                    labelColourOr(selected ? MenuInk.title() : MenuInk.label()));
            }

            int frameRow = selected ? 2 : isHoveredOrFocused() ? 1 : 0;
            de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.blit(graphics,
                HubTextures.DECK_TILE_FRAME, getX(), getY(), getWidth(), getHeight(),
                0F, frameRow / 3F, 1F, (frameRow + 1) / 3F,
                de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.NO_TINT);
        }

        /**
         * What the deck's name reserves along the bottom of the tile.
         * <p>
         * The line itself plus the clearance under it, asked of the FONT rather
         * than written down as 13. Thirteen was the line plus four, so a tile
         * only 58 tall was holding two units of nothing under the name and
         * charging the case for them -- and a resource pack with a taller font
         * would have had the name run off the bottom instead.
         */
        private static int nameBandH()
        {
            // Asked at the scale the name is actually DRAWN at, which snapping
            // makes slightly larger than NAME_SCALE -- 2/3 rather than 1/2 at a
            // GUI scale of 3. Reserving the unsnapped height would leave the
            // band a pixel short of its own text.
            return Math.round(net.minecraft.client.Minecraft.getInstance().font.lineHeight
                * MenuText.crispScale(NAME_SCALE)) + NAME_CLEARANCE;
        }

        /**
         * Text drawn at a fraction of its size, centred on an x.
         * <p>
         * The scale goes on the POSE, so the coordinates handed to the font are
         * divided by it -- a glyph drawn at half size at screen x lands at 2x in
         * the font's own space. Getting that backwards puts the text off the
         * tile rather than merely in the wrong place, which is how it announces
         * itself.
         */
        private static void tinyText(GuiGraphicsExtractor graphics, String text, float centreX,
            float y, float wantedScale, int colour)
        {
            // SNAPPED TO THE DEVICE PIXEL GRID BEFORE ANYTHING IS MEASURED.
            //
            // A deck's name is the smallest text in the mod and it was the only
            // small text still drawn at a raw fraction: 0.5 of a GUI scale of 3
            // is 1.5 device pixels per GUI pixel, so every glyph straddled a
            // pixel and the whole deck list read as slightly out of focus. The
            // deck editor's card counts and its preview already went through
            // this; the list of decks did not.
            //
            // Snapped FIRST, because the scale is divided into the coordinates
            // below -- snapping afterwards would place the text for one size and
            // draw it at another.
            float scale = MenuText.crispScale(wantedScale);
            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft
                .getInstance().font;
            graphics.pose().pushMatrix();
            graphics.pose().scale(scale, scale);
            graphics.text(font, text,
                Math.round(centreX / scale - font.width(text) / 2F),
                Math.round(y / scale), colour, MenuInk.shadow());
            graphics.pose().popMatrix();
        }

        private static void tinyCentredText(GuiGraphicsExtractor graphics, String text,
            int x, int y, int width, int height, int colour)
        {
            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft
                .getInstance().font;
            float scale = MenuText.crispScale(BADGE_TEXT_SCALE);
            float centreX = x + width / 2F;
            float textX = centreX / scale - font.width(text) / 2F;
            float textY = (y + (height - font.lineHeight * scale) / 2F) / scale;
            graphics.pose().pushMatrix();
            graphics.pose().scale(scale, scale);
            graphics.text(font, text, Math.round(textX), Math.round(textY), colour, MenuInk.shadow());
            graphics.pose().popMatrix();
        }
    }
}
