"""Adds the Outfit tab to the Fabric duel hub.

One-shot: run once, then the file is the source of truth. Kept because the
Forge original is the reference and this records exactly which parts were
carried over unchanged and which had to be re-expressed for the retained-mode
GUI (the preview's rectangle, and tooltips becoming the screen's job).
"""
import io

PATH = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/DuelHubScreen.java"

WARDROBE = '''    private int outfitStripX()
    {
        return left + PAD + 6;
    }

    private int outfitTiles()
    {
        return Math.max(1, (WIDTH - PAD * 2 - 12) / TILE_W);
    }

    private static java.util.List<de.cas_ual_ty.dueldimension.duel.outfit.Outfits.Outfit> outfits()
    {
        return de.cas_ual_ty.dueldimension.duel.outfit.Outfits.ALL;
    }

    /**
     * The wardrobe: a row of figures wearing the clothes, the worn one marked.
     * <p>
     * A list of names tells a player nothing about what they are choosing.
     * These are clothes, and "what does it look like" is the only question
     * being asked, so each one is worn by a turning figure rather than written
     * down.
     * <p>
     * What the player is wearing is the server's to say, so a tile asks for a
     * change and the mark follows the profile that comes back.
     */
    private void buildOutfitRows(int bodyTop)
    {
        int across = outfitTiles();
        outfitScroll = Math.max(0, Math.min(outfitScroll, Math.max(0, outfits().size() - across)));

        String worn = EditorState.profile().outfit();
        for(int slot = 0; slot < across && slot + outfitScroll < outfits().size(); slot++)
        {
            de.cas_ual_ty.dueldimension.duel.outfit.Outfits.Outfit outfit =
                outfits().get(slot + outfitScroll);
            boolean on = outfit.id().equals(worn);
            int x = outfitStripX() + slot * TILE_W;
            // The whole tile is the button, with the figure drawn over it: a
            // player picking clothes aims at the clothes.
            HubWidgets.TextureButton tile = new HubWidgets.TextureButton(x, bodyTop + 22,
                TILE_W - 6, TILE_H, Component.literal(""), pressed ->
            {
                EditorState.wear(outfit.id());
                rebuild();
            });
            tile.active = !on;
            if(!outfit.credit().isEmpty())
            {
                // Attribution where somebody choosing it will actually see it.
                tile.setTooltipLines(java.util.List.of(outfit.name(), outfit.credit()));
            }
            addRenderableWidget(tile);
        }

        // ---- the under-skin editor ----
        // A label and two verbs. It was three lines of explanation and two
        // sentences on a button, which is a lot of screen for "the skin under
        // the clothes"; the tooltips still carry the why for anyone who asks.
        int editorY = bodyTop + 22 + TILE_H + 10;
        int labelW = font.width("Underskin") + 8;
        boolean wearing = !worn.isEmpty();

        HubWidgets.TextureButton load = new HubWidgets.TextureButton(
            outfitStripX() + labelW, editorY, 54, 18,
            Component.literal("Load"), pressed -> importUnderSkin());
        load.active = wearing;
        load.setTooltipLines(wearing
            ? java.util.List.of("Import a 64x64 PNG",
                "Your own skin with whatever pokes out from under this outfit removed")
            : java.util.List.of("Only used under an outfit"));
        addRenderableWidget(load);

        HubWidgets.TextureButton reset = new HubWidgets.TextureButton(
            outfitStripX() + labelW + 58, editorY, 54, 18,
            Component.literal("Reset"), pressed ->
        {
            de.cas_ual_ty.dueldimension.clientutil.UnderSkin.clear();
            notice = "";
            rebuild();
        });
        reset.active = de.cas_ual_ty.dueldimension.clientutil.UnderSkin.present();
        reset.setTooltipLines(java.util.List.of("Back to your real skin"));
        addRenderableWidget(reset);
    }

    /**
     * Asks the system for a PNG.
     * <p>
     * Through LWJGL's file dialog, which Minecraft already ships, so the player
     * picks a file the way they would in any other program. If that is missing
     * -- it is a native library, and a native library can be absent -- the
     * known path is read instead and the player is told where it is.
     */
    private void importUnderSkin()
    {
        java.nio.file.Path chosen;
        try
        {
            org.lwjgl.PointerBuffer filters = org.lwjgl.BufferUtils.createPointerBuffer(1);
            filters.put(org.lwjgl.system.MemoryUtil.memUTF8("*.png"));
            filters.flip();
            String path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                "Choose your under-skin (64x64 PNG)", "", filters, "PNG image", false);
            if(path == null)
            {
                return; // cancelled, which is an answer
            }
            chosen = java.nio.file.Path.of(path);
        }
        catch(Throwable unavailable)
        {
            chosen = de.cas_ual_ty.dueldimension.clientutil.UnderSkin.file();
            notice = "No file chooser here; reading " + chosen;
        }
        String refusal = de.cas_ual_ty.dueldimension.clientutil.UnderSkin.importFrom(chosen);
        if(refusal != null)
        {
            notice = refusal;
        }
        rebuild();
    }

    private void outfitPanel(GuiGraphicsExtractor graphics, int bodyTop)
    {
        if(!EditorState.isSynced())
        {
            graphics.text(font, "Waiting for the server...", left + PAD + 10, bodyTop + 10,
                0xFF7A8090, true);
            return;
        }

        int across = outfitTiles();
        String worn = EditorState.profile().outfit();
        // One clock for the whole row, so the figures turn together rather than
        // each starting from whenever its tile happened to be built.
        long time = net.minecraft.Util.getMillis();

        for(int slot = 0; slot < across && slot + outfitScroll < outfits().size(); slot++)
        {
            de.cas_ual_ty.dueldimension.duel.outfit.Outfits.Outfit outfit =
                outfits().get(slot + outfitScroll);
            int x = outfitStripX() + slot * TILE_W;
            boolean on = outfit.id().equals(worn);

            // Inside the tile with room left for the name under it, rather
            // than filling the tile and standing on its own label. A GUI figure
            // is placed by the rectangle it stands in now, not by a point and a
            // scale, so the tile's own box is what it is given.
            int figure = TILE_H - 28;
            OutfitPreview.draw(graphics, x, bodyTop + 22 + 8,
                x + TILE_W - 6, bodyTop + 22 + 8 + figure, figure, outfit, time);

            String name = font.plainSubstrByWidth(outfit.name(), TILE_W - 12);
            graphics.text(font, name, x + (TILE_W - 6 - font.width(name)) / 2,
                bodyTop + 22 + TILE_H - 12, on ? 0xFFF4D089 : 0xFFC2C9D6, true);
        }

        if(outfits().size() > across)
        {
            graphics.text(font, (outfitScroll + 1) + "-"
                    + Math.min(outfits().size(), outfitScroll + across)
                    + " of " + outfits().size(),
                outfitStripX(), bodyTop + 10, 0xFF7A8090, true);
        }

        int editorY = bodyTop + 22 + TILE_H + 10;
        graphics.text(font, "Underskin", outfitStripX(), editorY + 5,
            worn.isEmpty() ? 0xFF6E7686 : 0xFFF4D089, true);
        if(!notice.isEmpty())
        {
            graphics.text(font, font.plainSubstrByWidth(notice, WIDTH - PAD * 2 - 12),
                outfitStripX(), editorY + 22, 0xFFFF8A80, true);
        }
    }

    /**
     * A hovered button's own explanation.
     * <p>
     * Forge's widgets carried a tooltip and drew it themselves. Here a tooltip
     * is something the SCREEN sets for the next frame, so the screen has to be
     * the one to ask which widget the mouse is over -- and it has to happen
     * after the widgets are extracted, or a tooltip set for this frame would be
     * covered by a widget described later.
     */
    private void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        for(net.minecraft.client.gui.components.Renderable renderable : renderables)
        {
            if(!(renderable instanceof HubWidgets.TextureButton button)
                || !button.isHovered() || button.tooltipLines().isEmpty())
            {
                continue;
            }
            java.util.List<Component> lines = button.tooltipLines().stream()
                .map(line -> (Component)Component.literal(line)).toList();
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
    }

    /**
     * The wardrobe scrolls sideways, because it is a row.
     * <p>
     * One tile per notch rather than a pixel offset: the tiles are wide and
     * there is no partial one to reveal, so a notch either shows a different
     * outfit or does nothing.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if(section == Section.OUTFIT)
        {
            int max = Math.max(0, outfits().size() - outfitTiles());
            int moved = Math.max(0, Math.min(max, outfitScroll - (int)Math.signum(scrollY)));
            if(moved != outfitScroll)
            {
                outfitScroll = moved;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

'''

src = io.open(PATH, encoding="utf-8").read()

anchor = ("        // One row per deck, over the names the panel draws. The editor is real\n"
          "        // now, so a deck is something a player can open rather than only read.")
assert anchor in src, "deck-row anchor moved"
src = src.replace(anchor,
                  "        if(section == Section.OUTFIT && EditorState.isSynced())\n"
                  "        {\n"
                  "            buildOutfitRows(top + PAD + TAB_H + 8);\n"
                  "        }\n\n" + anchor)

assert "case DECKS -> deckPanel(graphics, bodyTop);" in src, "switch moved"
src = src.replace("case DECKS -> deckPanel(graphics, bodyTop);",
                  "case DECKS -> deckPanel(graphics, bodyTop);\n"
                  "            case OUTFIT -> outfitPanel(graphics, bodyTop);")

tail = ("    @Override\n"
        "    public boolean isPauseScreen()\n"
        "    {\n"
        "        return false;\n"
        "    }\n"
        "}")
assert tail in src, "tail moved"
src = src.replace(tail, WARDROBE + tail)

sup = "        super.extractRenderState(graphics, mouseX, mouseY, partialTick);\n    }"
assert sup in src, "super call moved"
src = src.replace(sup,
                  "        super.extractRenderState(graphics, mouseX, mouseY, partialTick);\n"
                  "        extractTooltip(graphics, mouseX, mouseY);\n    }", 1)

io.open(PATH, "w", encoding="utf-8", newline="\n").write(src)
print("wardrobe added to", PATH)
