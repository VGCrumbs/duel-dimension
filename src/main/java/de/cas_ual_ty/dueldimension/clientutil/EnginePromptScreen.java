package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;
import de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The screen a player answers duel prompts on.
 * <p>
 * The field is drawn at the top with real card art and the legal choices are
 * listed underneath; clicking a card on the field is a shortcut for the option
 * that names it. The option list stays authoritative because it can express
 * every prompt the engine produces, including ones with no card to point at
 * ("go to Battle Phase", "choose an effect").
 * <p>
 * Options arrive pre-labelled from the server, so this screen holds no rules
 * knowledge at all — it shows what it is given and reports clicks back.
 */
public class EnginePromptScreen extends Screen
{
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_VISIBLE = 8;

    private final EnginePrompt prompt;
    private final Set<Integer> selected = new LinkedHashSet<>();
    private final List<Button> optionButtons = new ArrayList<>();

    private final BoardRenderer boardRenderer = new BoardRenderer();

    private Button confirmButton;
    private int scroll;
    private boolean answered;
    private int listTop;

    public EnginePromptScreen(EnginePrompt prompt)
    {
        super(Component.literal(prompt.title()));
        this.prompt = prompt;
    }

    @Override
    protected void init()
    {
        optionButtons.clear();
        // The field is drawn first and the options sit under it; how tall the
        // field is depends on how many zones have cards, so measure it here.
        listTop = 34 + fieldHeight() + 8;
        int buttonWidth = Math.min(300, width - 40);
        int left = (width - buttonWidth) / 2;

        int visible = Math.min(MAX_VISIBLE, prompt.options().size());
        for(int row = 0; row < visible; row++)
        {
            int index = row + scroll;
            EnginePrompt.Option option = prompt.options().get(index);
            Button button = new Button(left, listTop + row * ROW_HEIGHT, buttonWidth, 20,
                Component.literal(label(option, index)), pressed -> choose(index));
            optionButtons.add(button);
            addRenderableWidget(button);
        }

        int footer = listTop + visible * ROW_HEIGHT + 8;

        if(prompt.options().size() > MAX_VISIBLE)
        {
            addRenderableWidget(new Button(left, footer, 60, 20, Component.literal("< Up"),
                pressed -> scrollBy(-MAX_VISIBLE)));
            addRenderableWidget(new Button(left + buttonWidth - 60, footer, 60, 20, Component.literal("Down >"),
                pressed -> scrollBy(MAX_VISIBLE)));
        }

        if(!prompt.isSingleChoice())
        {
            confirmButton = new Button(left + buttonWidth / 2 - 60, footer + 24, 120, 20,
                Component.literal("Confirm"), pressed -> confirm());
            addRenderableWidget(confirmButton);
            updateConfirm();
        }

        if(prompt.cancelable())
        {
            addRenderableWidget(new Button(left + buttonWidth / 2 - 60, footer + 48, 120, 20,
                Component.literal("Cancel"), pressed -> answer(new int[0])));
        }
    }

    /** Height the board will occupy, so the option list can start below it. */
    private int fieldHeight()
    {
        BoardSnapshot field = prompt.field();
        int rows = 0;
        rows += field.opponent().spells().isEmpty() ? 0 : 1;
        rows += field.opponent().monsters().isEmpty() ? 0 : 1;
        rows += field.self().monsters().isEmpty() ? 0 : 1;
        rows += field.self().spells().isEmpty() ? 0 : 1;
        rows += field.self().hand().isEmpty() ? 0 : 1;
        int handLabel = field.self().hand().isEmpty() ? 0 : 10;
        return rows * (BoardRenderer.CARD_HEIGHT + 3) + 28 + handLabel;
    }

    private String label(EnginePrompt.Option option, int index)
    {
        String prefix = prompt.isSingleChoice() ? "" : (selected.contains(index) ? "✔ " : "☐ ");
        String detail = option.detail().isEmpty() ? "" : "  §7(" + option.detail() + ")";
        return prefix + option.label() + detail;
    }

    private void choose(int index)
    {
        if(answered)
        {
            return;
        }
        if(prompt.isSingleChoice())
        {
            answer(new int[] {index});
            return;
        }
        if(!selected.remove(index) && selected.size() < prompt.maxSelect())
        {
            selected.add(index);
        }
        rebuild();
    }

    private void confirm()
    {
        if(selected.size() < prompt.minSelect())
        {
            return;
        }
        answer(selected.stream().mapToInt(Integer::intValue).toArray());
    }

    private void answer(int[] chosen)
    {
        if(answered)
        {
            return;
        }
        answered = true;
        DuelDimension.channel.sendToServer(new PromptMessages.AnswerPrompt(chosen));
        onClose();
    }

    private void scrollBy(int delta)
    {
        int max = Math.max(0, prompt.options().size() - MAX_VISIBLE);
        scroll = Math.max(0, Math.min(max, scroll + delta));
        rebuild();
    }

    private void rebuild()
    {
        clearWidgets();
        init();
    }

    private void updateConfirm()
    {
        if(confirmButton != null)
        {
            confirmButton.active = selected.size() >= prompt.minSelect();
            confirmButton.setMessage(Component.literal("Confirm (" + selected.size() + "/"
                + (prompt.minSelect() == prompt.maxSelect() ? prompt.minSelect() : prompt.maxSelect()) + ")"));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(button == 0 && !answered)
        {
            for(BoardRenderer.Hit hit : boardRenderer.hits())
            {
                if(!hit.contains(mouseX, mouseY))
                {
                    continue;
                }
                // Clicking a card is a shortcut for the option that names it;
                // if no option does, the click is simply not a legal play.
                for(int index = 0; index < prompt.options().size(); index++)
                {
                    if(prompt.options().get(index).cardCode() == hit.code() && hit.code() != 0)
                    {
                        choose(index);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if(prompt.options().size() > MAX_VISIBLE)
        {
            scrollBy(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(poseStack);
        RenderSystem.setShaderColor(1, 1, 1, 1);

        drawCenteredString(poseStack, font, title, width / 2, 12, 0xFFD700);

        boardRenderer.render(poseStack, font, prompt.field(), width / 2, 26);

        super.render(poseStack, mouseX, mouseY, partialTick);

        renderCardTooltip(poseStack, mouseX, mouseY);

        if(!prompt.isSingleChoice())
        {
            updateConfirm();
            String hint = "Pick " + prompt.minSelect()
                + (prompt.minSelect() == prompt.maxSelect() ? "" : "-" + prompt.maxSelect()) + ", then Confirm";
            drawCenteredString(poseStack, font, hint, width / 2, height - 16, 0x808080);
        }
        if(prompt.options().size() > MAX_VISIBLE)
        {
            drawCenteredString(poseStack, font,
                (scroll + 1) + "-" + Math.min(prompt.options().size(), scroll + MAX_VISIBLE)
                    + " of " + prompt.options().size(),
                width / 2, height - 28, 0x808080);
        }
    }

    /** Names the card under the cursor, and marks it when it is playable. */
    private void renderCardTooltip(PoseStack poseStack, int mouseX, int mouseY)
    {
        for(BoardRenderer.Hit hit : boardRenderer.hits())
        {
            if(!hit.contains(mouseX, mouseY) || hit.code() == 0)
            {
                continue;
            }
            List<Component> lines = new ArrayList<>();
            Properties card = DdDatabase.PROPERTIES_LIST.get((long)hit.code());
            lines.add(Component.literal(card != null ? card.getName() : "Card " + hit.code()));
            boolean playable = prompt.options().stream().anyMatch(option -> option.cardCode() == hit.code());
            if(playable)
            {
                lines.add(Component.literal("Click to choose").withStyle(ChatFormatting.GREEN));
            }
            renderComponentTooltip(poseStack, lines, mouseX, mouseY);
            return;
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false; // the duel is running on the server; pausing would be a lie
    }

    @Override
    public boolean shouldCloseOnEsc()
    {
        return false; // an unanswered prompt blocks the duel thread
    }

    /** Shown while it is not our turn to decide. */
    public static Component waiting()
    {
        return Component.literal("Waiting for the opponent...").withStyle(ChatFormatting.GRAY);
    }
}
