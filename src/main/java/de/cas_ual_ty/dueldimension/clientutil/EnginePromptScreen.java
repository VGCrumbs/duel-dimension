package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;
import de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The screen a player answers duel prompts on.
 * <p>
 * Deliberately a plain list rather than a rendered playfield: it can present
 * <em>every</em> prompt type the engine produces, which the playfield widgets
 * cannot yet, so the game is fully playable while the board rendering catches
 * up. Options arrive pre-labelled from the server, so this screen contains no
 * rules knowledge at all — it shows what it is given and reports clicks back.
 */
public class EnginePromptScreen extends Screen
{
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_VISIBLE = 8;

    private final EnginePrompt prompt;
    private final Set<Integer> selected = new LinkedHashSet<>();
    private final List<Button> optionButtons = new ArrayList<>();

    private Button confirmButton;
    private int scroll;
    private boolean answered;

    public EnginePromptScreen(EnginePrompt prompt)
    {
        super(Component.literal(prompt.title()));
        this.prompt = prompt;
    }

    @Override
    protected void init()
    {
        optionButtons.clear();
        int listTop = 60;
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

        int line = 26;
        for(String summary : prompt.board())
        {
            drawCenteredString(poseStack, font, Component.literal(summary).getString(),
                width / 2, line, 0xA0A0A0);
            line += 10;
        }

        super.render(poseStack, mouseX, mouseY, partialTick);

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
