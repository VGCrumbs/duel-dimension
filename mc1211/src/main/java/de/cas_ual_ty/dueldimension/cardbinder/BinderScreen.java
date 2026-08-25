package de.cas_ual_ty.dueldimension.cardbinder;

import de.cas_ual_ty.dueldimension.clientutil.hub.EditorState;
import de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures;
import de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The binder: how much of every pack the player has collected.
 * <p>
 * A plain {@link Screen} rather than a container screen, because the binder
 * holds nothing. It reads {@link EditorState}'s already-synced collection and
 * the set database, so it opens on the client without asking the server for
 * anything.
 * <p>
 * Rows are generations with their packs indented beneath, each with a bar. The
 * three levels the feature is for — the pack, its generation, and the whole
 * collection — are the row, the heading above it, and the line at the top.
 */
public class BinderScreen extends Screen
{
    private static final int WIDTH = 340;
    private static final int HEIGHT = 220;
    private static final int PAD = 10;
    /** A generation heading and a pack row are the same height; only the inset differs. */
    private static final int ROW_H = 13;
    private static final int BAR_W = 84;
    private static final int BAR_H = 7;
    private static final int PACK_INDENT = 10;
    private static final int BAR_GRAB = 3;

    /**
     * One drawn line: either a generation heading or a pack under it.
     *
     * @param generation the group this belongs to, so a heading knows what it
     *                   toggles and a pack knows what it sits inside
     * @param pack       the pack itself, or null on a heading
     */
    private record Row(String label, float fraction, int held, int total, boolean heading,
        boolean complete, String generation, CollectionProgress.Pack pack)
    {
    }

    private final Screen parent;
    /**
     * Which generations are open.
     * <p>
     * Static so it survives closing and reopening the binder: a player who
     * expands a group, looks at a pack and comes back should find it as they
     * left it. Collapsed by default -- there are a dozen groups and 271 packs,
     * and opening onto all of them at once is a wall rather than a summary.
     */
    private static final java.util.Set<String> EXPANDED = new java.util.HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private CollectionProgress.Summary summary;

    private int left;
    private int top;
    private int scroll;
    private int maxScroll;
    private int visibleRows;
    private int barGrab = -1;

    public BinderScreen(Screen parent)
    {
        super(Component.literal("Collection"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;

        // Worked out once on open, not per frame: it walks every pack in the
        // database against the collection, which is not free and does not
        // change while the screen is up.
        summary = CollectionProgress.summarise(EditorState.isSynced() ? EditorState.trunk() : null);
        rebuildRows();

        visibleRows = Math.max(1, (HEIGHT - PAD * 2 - 46) / ROW_H);
        maxScroll = Math.max(0, rows.size() - visibleRows);
        scroll = Math.clamp(scroll, 0, maxScroll);

        addRenderableWidget(Button.builder(Component.literal("Expand all"), pressed ->
        {
            for(CollectionProgress.Generation generation : summary.generations())
            {
                EXPANDED.add(generation.name());
            }
            rebuildRows();
        }).bounds(left + PAD, top + HEIGHT - PAD - 20, 70, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Collapse all"), pressed ->
        {
            EXPANDED.clear();
            scroll = 0;
            rebuildRows();
        }).bounds(left + PAD + 74, top + HEIGHT - PAD - 20, 74, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, pressed -> onClose())
            .bounds(left + WIDTH - PAD - 60, top + HEIGHT - PAD - 20, 60, 20).build());
    }

    /** Flattens the summary into drawn lines, skipping the packs of closed groups. */
    private void rebuildRows()
    {
        rows.clear();
        for(CollectionProgress.Generation generation : summary.generations())
        {
            rows.add(new Row(generation.name(), generation.fraction(),
                generation.held(), generation.total(), true, false, generation.name(), null));
            if(!EXPANDED.contains(generation.name()))
            {
                continue;
            }
            for(CollectionProgress.Pack pack : generation.packs())
            {
                rows.add(new Row(pack.set().name, pack.fraction(), pack.held(), pack.total(),
                    false, pack.complete(), generation.name(), pack));
            }
        }
        maxScroll = Math.max(0, rows.size() - visibleRows);
        scroll = Math.clamp(scroll, 0, maxScroll);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor poseStack = new GuiGraphicsExtractor(vanillaGraphics);

        // fillGradient, not extractBackground: that one blurs, the blur may run
        // only once a frame, and a screen opened over one that already asked
        // takes the client down. Same choice every other screen here makes.
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        NineSlice.draw(poseStack, HubTextures.PANEL, left, top, WIDTH, HEIGHT);

        int y = top + PAD;
        poseStack.text(font, "Collection", left + PAD, y, 0xFFF4D089, true);

        // The total, on its own line at the top, because it is the one number a
        // player wants without reading anything else.
        String total = summary.held() + " / " + summary.total() + " printings   "
            + percent(summary.fraction());
        poseStack.text(font, total, left + WIDTH - PAD - font.width(total), y, 0xFFC2C9D6, true);
        y += 12;
        bar(poseStack, left + PAD, y, WIDTH - PAD * 2, summary.fraction());
        y += BAR_H + 6;

        String counted = summary.packCount() + " packs   (fixed decks are not counted: "
            + "there is nothing random to collect)";
        poseStack.text(font, counted, left + PAD, y, 0xFF7A8090, true);
        y += 12;

        int listTop = y;
        for(int i = 0; i < visibleRows && i + scroll < rows.size(); i++)
        {
            Row row = rows.get(i + scroll);
            int rowY = listTop + i * ROW_H;
            int labelX = left + PAD + (row.heading() ? 0 : PACK_INDENT);
            int barX = left + WIDTH - PAD - BAR_W;

            String label = row.heading()
                ? (EXPANDED.contains(row.generation()) ? "▾ " : "▸ ") + row.label()
                : row.label();
            label = font.plainSubstrByWidth(label, barX - labelX - font.width("100%") - 12);

            // A row lights up under the cursor so it reads as something you can
            // press, which both kinds of row now are.
            if(mouseY >= rowY - 2 && mouseY < rowY + ROW_H - 2
                && mouseX >= left + PAD && mouseX < barX + BAR_W)
            {
                poseStack.fill(left + PAD - 2, rowY - 2, left + WIDTH - PAD, rowY + ROW_H - 3,
                    0x30FFFFFF);
            }
            poseStack.text(font, label, labelX, rowY, row.heading() ? 0xFFF4D089
                : row.complete() ? 0xFF8AD98A : 0xFFC2C9D6, true);

            String pct = percent(row.fraction());
            poseStack.text(font, pct, barX - font.width(pct) - 6, rowY,
                row.complete() ? 0xFF8AD98A : 0xFF9FA6B4, true);
            bar(poseStack, barX, rowY, BAR_W, row.fraction());
        }

        if(maxScroll > 0)
        {
            scrollbar(poseStack, left + WIDTH - 6, listTop, visibleRows * ROW_H);
        }

        super.render(poseStack.vanilla(), mouseX, mouseY, partialTick);
    }

    /**
     * A progress bar made of the scrollbar's own art.
     * <p>
     * Row 0 of that texture is a trough and row 1 a fill, which is exactly what
     * a bar needs, only lying down. The project's rule is that UI is PNG and
     * only text uses the font, so this is art rather than two rectangles.
     */
    private void bar(GuiGraphicsExtractor poseStack, int x, int y, int width, float fraction)
    {
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, y, width, BAR_H, 0, 2);
        int filled = Math.round(width * Math.clamp(fraction, 0F, 1F));
        if(filled > 0)
        {
            NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, y, Math.max(4, filled), BAR_H,
                1, 2);
        }
    }

    private void scrollbar(GuiGraphicsExtractor poseStack, int x, int y, int height)
    {
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, y, 4, height, 0, 2);
        int thumbH = Math.max(12, height * visibleRows / Math.max(1, rows.size()));
        int thumbY = y + (height - thumbH) * scroll / Math.max(1, maxScroll);
        NineSlice.draw(poseStack, HubTextures.SCROLLBAR, x, thumbY, 4, thumbH, 1, 2);
    }

    private static String percent(float fraction)
    {
        // Floored, not rounded: 99.6% of a pack is not a finished pack, and
        // showing "100%" next to a missing card is the one thing a completion
        // tracker must never do.
        return (int)Math.floor(Math.clamp(fraction, 0F, 1F) * 100F) + "%";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        if(maxScroll > 0)
        {
            scroll = Math.clamp(scroll - (int)Math.signum(delta), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    @Override
    public boolean mouseClicked(de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event,
        boolean doubleClick)
    {
        if(event.button() == 0 && grabBar(event.x(), event.y()))
        {
            return true;
        }
        if(event.button() == 0 && clickRow(event.x(), event.y()))
        {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event,
        double dragX, double dragY)
    {
        if(barGrab >= 0)
        {
            dragBar(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(de.cas_ual_ty.dueldimension.compat.InputEvents.MouseButtonEvent event)
    {
        barGrab = -1;
        return super.mouseReleased(event);
    }

    /**
     * A heading opens or closes its group; a pack opens its card list.
     *
     * @return whether a row took the click
     */
    private boolean clickRow(double mouseX, double mouseY)
    {
        if(mouseX < left + PAD || mouseX >= left + WIDTH - PAD)
        {
            return false;
        }
        int index = (int)Math.floor((mouseY - listTop() + 2) / ROW_H);
        if(index < 0 || index >= visibleRows || index + scroll >= rows.size())
        {
            return false;
        }
        Row row = rows.get(index + scroll);
        if(row.heading())
        {
            if(!EXPANDED.remove(row.generation()))
            {
                EXPANDED.add(row.generation());
            }
            rebuildRows();
            return true;
        }
        minecraft.setScreen(new BinderPackScreen(this, row.pack()));
        return true;
    }

    private boolean grabBar(double mouseX, double mouseY)
    {
        if(maxScroll <= 0)
        {
            return false;
        }
        int x = left + WIDTH - 6;
        int y = listTop();
        int height = visibleRows * ROW_H;
        if(mouseX < x - BAR_GRAB || mouseX >= x + 4 + BAR_GRAB || mouseY < y || mouseY >= y + height)
        {
            return false;
        }
        int thumbH = Math.max(12, height * visibleRows / Math.max(1, rows.size()));
        int thumbY = y + (height - thumbH) * scroll / Math.max(1, maxScroll);
        barGrab = mouseY >= thumbY && mouseY < thumbY + thumbH ? (int)(mouseY - thumbY) : thumbH / 2;
        dragBar(mouseY);
        return true;
    }

    private void dragBar(double mouseY)
    {
        int height = visibleRows * ROW_H;
        int thumbH = Math.max(12, height * visibleRows / Math.max(1, rows.size()));
        int travel = height - thumbH;
        if(travel <= 0)
        {
            scroll = 0;
            return;
        }
        scroll = (int)Math.clamp(Math.round((mouseY - barGrab - listTop()) / travel * maxScroll),
            0, maxScroll);
    }

    /** Where the list starts, which the header's height decides. */
    private int listTop()
    {
        return top + PAD + 12 + BAR_H + 6 + 12;
    }

    @Override
    public void onClose()
    {
        minecraft.setScreen(parent);
    }
}
