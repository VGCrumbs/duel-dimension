package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.clientutil.Layering;
import de.cas_ual_ty.dueldimension.shop.DuelReward;
import de.cas_ual_ty.dueldimension.shop.DuelRewardMessages;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Pageable Tag-Force-style explanation of a committed duel-point award. */
public final class DuelResultScreen extends Screen
{
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 238;
    private static final int ROWS_PER_PAGE = 7;

    /**
     * This screen does NOT take itself away.
     * <p>
     * It used to close on a five-second timer, on the reasoning that a finished
     * duel should hand back control without a keypress. That was wrong: the
     * payout is the thing the player came for, and a screen that walks off while
     * it is being read is worse than one more click. The result stands until it
     * is dismissed -- {@code Done} or Escape, both the player's own doing.
     */
    private final DuelRewardMessages.Result reward;
    /** 0 is the summary; 1 and above are pages of line items. */
    private int page;

    public DuelResultScreen(DuelRewardMessages.Result reward)
    {
        super(Component.literal("Duel Result"));
        this.reward = reward;
    }

    private int x()
    {
        return (width - Math.min(PANEL_WIDTH, width - 24)) / 2;
    }

    private int y()
    {
        return Math.max(12, (height - Math.min(PANEL_HEIGHT, height - 24)) / 2);
    }

    private int panelWidth()
    {
        return Math.min(PANEL_WIDTH, width - 24);
    }

    private int panelHeight()
    {
        return Math.min(PANEL_HEIGHT, height - 24);
    }

    private int detailPages()
    {
        return Math.max(1, (reward.lines().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    @Override
    protected void init()
    {
        int buttonY = y() + panelHeight() - 29;
        int left = x() + 12;
        int right = x() + panelWidth() - 12;

        if(page == 0)
        {
            addRenderableWidget(new HubWidgets.TextureButton(left, buttonY, 104, 18,
                Component.literal("Bonus Details"), button -> setPage(1)));
        }
        else
        {
            addRenderableWidget(new HubWidgets.TextureButton(left, buttonY, 74, 18,
                Component.literal(page == 1 ? "Summary" : "Previous"), button ->
                    setPage(page == 1 ? 0 : page - 1)));
            if(page < detailPages())
            {
                addRenderableWidget(new HubWidgets.TextureButton(left + 80, buttonY, 58, 18,
                    Component.literal("Next"), button -> setPage(page + 1)));
            }
        }

        addRenderableWidget(new HubWidgets.TextureButton(right - 68, buttonY, 68, 18,
            Component.literal("Done"), button -> onClose()));
    }

    private void setPage(int value)
    {
        page = Math.max(0, Math.min(detailPages(), value));
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        float partialTick)
    {
        // The furniture itself is entirely PNG-backed; this dim only separates
        // the modal result from the already-settled world behind it.
        graphics.fillGradient(0, 0, width, height, 0xD0101018, 0xE0080B12);
        int x = x();
        int y = y();
        int w = panelWidth();
        int h = panelHeight();
        NineSlice.draw(graphics, HubTextures.PANEL, x, y, w, h);

        // THIS SCREEN ALTERNATES PLATES AND WRITING, WHICH IS THE ONE THING
        // 1.21.1 CANNOT ORDER BY ITSELF.
        //
        // A nine-slice is a texture and a heading is a glyph, and GuiGraphics
        // resolves one buffer per type in whatever order it iterates them -- so
        // "panel, then the words on the panel" is not what gets drawn. It is a
        // coin toss per type, settled once per frame, which is why a result
        // screen either looks right or looks like the panel ate its headings,
        // consistently, until something else on the screen changes.
        //
        // Every plate-to-writing boundary below therefore says which side it is
        // on. Four seams on a modal screen costs nothing measurable; getting
        // the DUEL RESULT heading eaten by its own panel costs the screen.
        Layering.above(graphics);

        String title = "DUEL RESULT";
        graphics.text(font, title, x + 14, y + 13, MenuInk.title(), MenuInk.shadow());
        String outcome = reward.outcome().label();
        int outcomeColour = reward.outcome() == DuelReward.Outcome.WIN ? 0xFF7CE38B
            : reward.outcome() == DuelReward.Outcome.LOSS ? 0xFFFF8A80 : MenuInk.label();
        graphics.text(font, outcome, x + w - 14 - font.width(outcome), y + 13,
            outcomeColour, true);

        int bodyX = x + 12;
        int bodyY = y + 35;
        int bodyW = w - 24;
        int bodyH = h - 72;
        Layering.above(graphics);
        NineSlice.draw(graphics, HubTextures.PANEL_INSET, bodyX, bodyY, bodyW, bodyH);
        Layering.above(graphics);
        if(page == 0)
        {
            renderSummary(graphics, bodyX, bodyY, bodyW);
        }
        else
        {
            renderDetails(graphics, bodyX, bodyY, bodyW);
        }

        // The buttons are plates with writing on them too, and they are the
        // last word on this screen: Continue has to be clickable-looking over
        // whatever the body just wrote near it.
        Layering.above(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSummary(GuiGraphicsExtractor graphics, int x, int y, int width)
    {
        String contest = reward.npcDuel() ? "Duelist duel"
            : reward.games() > 1 ? "Match  " + reward.myWins() + " - " + reward.theirWins()
            : "Player duel";
        labelValue(graphics, x, y + 12, width, "Contest", contest, MenuInk.body());
        labelValue(graphics, x, y + 35, width, "Reward",
            "+" + reward.total() + " DP", MenuInk.title());
        // DE on its own row rather than folded into the DP total. They are not
        // the same currency and they are not earned the same way: DP scales with
        // how the duel went, DE is a flat rate for having played one. Adding
        // them together would suggest a exchange rate that does not exist.
        labelValue(graphics, x, y + 58, width, "Energy",
            "+" + reward.duelEnergy() + " DE", 0xFFB6E3A8);
        labelValue(graphics, x, y + 81, width, "Bonuses",
            Integer.toString(reward.lines().size()), MenuInk.label());

        NineSlice.draw(graphics, HubTextures.PANEL, x + 10, y + 103, width - 20, 54);
        graphics.text(font, "DP BALANCE", x + 20, y + 113, 0xFF8791A3, true);
        String balance = reward.previousBalance() + "  +  " + reward.total()
            + "  =  " + reward.newBalance() + " DP";
        graphics.text(font, balance, x + (width - font.width(balance)) / 2,
            y + 134, MenuInk.title(), MenuInk.shadow());
    }

    private void renderDetails(GuiGraphicsExtractor graphics, int x, int y, int width)
    {
        int pages = detailPages();
        String heading = "DP ACQUIRED   " + page + " / " + pages;
        graphics.text(font, heading, x + 10, y + 10, 0xFF8791A3, true);

        int first = (page - 1) * ROWS_PER_PAGE;
        int last = Math.min(reward.lines().size(), first + ROWS_PER_PAGE);
        int rowY = y + 31;
        for(int i = first; i < last; i++)
        {
            DuelReward.Line line = reward.lines().get(i);
            graphics.text(font, line.label(), x + 10, rowY, MenuInk.label(), MenuInk.shadow());
            String amount = "+" + line.amount() + " DP";
            graphics.text(font, amount, x + width - 10 - font.width(amount), rowY,
                MenuInk.title(), MenuInk.shadow());
            rowY += 17;
        }
        if(reward.lines().isEmpty())
        {
            String empty = "No assessment bonuses";
            graphics.text(font, empty, x + (width - font.width(empty)) / 2,
                y + 72, 0xFF8791A3, true);
        }
    }

    private void labelValue(GuiGraphicsExtractor graphics, int x, int y, int width,
        String label, String value, int valueColour)
    {
        graphics.text(font, label, x + 12, y, 0xFF8791A3, true);
        graphics.text(font, value, x + width - 12 - font.width(value), y,
            valueColour, true);
    }

    @Override
    public void onClose()
    {
        if(minecraft != null)
        {
            minecraft.setScreenAndShow(null);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
