package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.shop.DuelReward;
import de.cas_ual_ty.dueldimension.shop.DuelRewardMessages;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Pageable Tag-Force-style explanation of a committed duel-point award. */
public final class DuelResultScreen extends Screen
{
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 238;
    private static final int ROWS_PER_PAGE = 7;

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
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(vanillaGraphics);

        // The furniture itself is entirely PNG-backed; this dim only separates
        // the modal result from the already-settled world behind it.
        graphics.fillGradient(0, 0, width, height, 0xD0101018, 0xE0080B12);
        int x = x();
        int y = y();
        int w = panelWidth();
        int h = panelHeight();
        NineSlice.draw(graphics, HubTextures.PANEL, x, y, w, h);

        String title = "DUEL RESULT";
        graphics.text(font, title, x + 14, y + 13, 0xFFF4D089, true);
        String outcome = reward.outcome().label();
        int outcomeColour = reward.outcome() == DuelReward.Outcome.WIN ? 0xFF7CE38B
            : reward.outcome() == DuelReward.Outcome.LOSS ? 0xFFFF8A80 : 0xFFE6EAF2;
        graphics.text(font, outcome, x + w - 14 - font.width(outcome), y + 13,
            outcomeColour, true);

        int bodyX = x + 12;
        int bodyY = y + 35;
        int bodyW = w - 24;
        int bodyH = h - 72;
        NineSlice.draw(graphics, HubTextures.PANEL_INSET, bodyX, bodyY, bodyW, bodyH);
        if(page == 0)
        {
            renderSummary(graphics, bodyX, bodyY, bodyW);
        }
        else
        {
            renderDetails(graphics, bodyX, bodyY, bodyW);
        }

        super.render(graphics.vanilla(), mouseX, mouseY, partialTick);
    }

    private void renderSummary(GuiGraphicsExtractor graphics, int x, int y, int width)
    {
        String contest = reward.npcDuel() ? "Duelist duel"
            : reward.games() > 1 ? "Match  " + reward.myWins() + " - " + reward.theirWins()
            : "Player duel";
        labelValue(graphics, x, y + 12, width, "Contest", contest, 0xFFC2C9D6);
        labelValue(graphics, x, y + 35, width, "Reward",
            "+" + reward.total() + " DP", 0xFFF4D089);
        labelValue(graphics, x, y + 58, width, "Bonuses",
            Integer.toString(reward.lines().size()), 0xFFE6EAF2);

        NineSlice.draw(graphics, HubTextures.PANEL, x + 10, y + 80, width - 20, 54);
        graphics.text(font, "DP BALANCE", x + 20, y + 90, 0xFF8791A3, true);
        String balance = reward.previousBalance() + "  +  " + reward.total()
            + "  =  " + reward.newBalance() + " DP";
        graphics.text(font, balance, x + (width - font.width(balance)) / 2,
            y + 111, 0xFFF4D089, true);
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
            graphics.text(font, line.label(), x + 10, rowY, 0xFFE6EAF2, true);
            String amount = "+" + line.amount() + " DP";
            graphics.text(font, amount, x + width - 10 - font.width(amount), rowY,
                0xFFF4D089, true);
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
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
