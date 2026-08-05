package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;
import de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The persistent duel screen: field on the left, card-info panel on the
 * right, event log underneath, and whatever the engine is asking rendered as
 * kind-specific controls. Between prompts it stays open showing the live
 * board and "waiting for opponent".
 * <p>
 * All rules knowledge lives server-side; this screen renders what it is given
 * and reports clicks (indices, amounts, an order, or a searched card code).
 */
public class EngineDuelScreen extends Screen
{
    private static final int ROW_HEIGHT = 21;
    private static final int MAX_VISIBLE = 6;

    private final BoardRenderer boardRenderer = new BoardRenderer();

    // MULTI / PLACES selection, SORT order, COUNTERS amounts.
    private final Set<Integer> selected = new LinkedHashSet<>();
    private final List<Integer> sortOrder = new ArrayList<>();
    private int[] counterAmounts = new int[0];

    private EnginePrompt shownPrompt;
    private Button confirmButton;
    private EditBox searchBox;
    private final List<Integer> searchResults = new ArrayList<>();
    private int scroll;
    private boolean answered;
    private int boardCentreX;

    public EngineDuelScreen()
    {
        super(Component.literal("Duel"));
    }

    // ---- lifecycle ----

    @Override
    protected void init()
    {
        rebuildControls();
    }

    @Override
    public void tick()
    {
        // A new prompt (or its disappearance) re-shapes the controls.
        if(DuelClientState.prompt != shownPrompt)
        {
            rebuildControls();
        }
        if(searchBox != null)
        {
            searchBox.tick();
        }
    }

    private void rebuildControls()
    {
        clearWidgets();
        shownPrompt = DuelClientState.prompt;
        answered = false;
        selected.clear();
        sortOrder.clear();
        scroll = 0;
        searchBox = null;
        confirmButton = null;

        int panelLeft = width / 2 + 20;
        int panelWidth = Math.min(240, width - panelLeft - 8);
        boardCentreX = (panelLeft - 8) / 2;

        EnginePrompt prompt = shownPrompt;
        if(prompt != null)
        {
            counterAmounts = new int[prompt.options().size()];
            buildPromptControls(prompt, panelLeft, panelWidth);
        }

        if(DuelClientState.over)
        {
            addRenderableWidget(new Button(panelLeft, height - 26, panelWidth, 20,
                Component.literal("Close"), pressed ->
            {
                DuelClientState.reset();
                onClose();
            }));
        }
        else
        {
            addRenderableWidget(new Button(panelLeft, height - 26, panelWidth, 20,
                Component.literal("Surrender"), pressed ->
                    DuelDimension.channel.sendToServer(new PromptMessages.Surrender())));
        }
    }

    private void buildPromptControls(EnginePrompt prompt, int left, int width)
    {
        int y = 56;

        if(prompt.kind() == EnginePrompt.Kind.DECLARE_CARD)
        {
            searchBox = new EditBox(font, left, y, width, 16, Component.literal("search"));
            searchBox.setResponder(this::updateSearch);
            addRenderableWidget(searchBox);
            y += 20;
            for(int i = 0; i < MAX_VISIBLE; i++)
            {
                int row = i;
                Button button = new Button(left, y + row * ROW_HEIGHT, width, 19, Component.empty(), pressed ->
                {
                    if(row < searchResults.size())
                    {
                        answer(new int[] {0}, searchResults.get(row));
                    }
                });
                button.visible = false;
                addRenderableWidget(button);
            }
            return;
        }

        int visible = Math.min(MAX_VISIBLE, prompt.options().size());
        for(int row = 0; row < visible; row++)
        {
            int index = row + scroll;
            if(index >= prompt.options().size())
            {
                break;
            }
            buildOptionRow(prompt, index, left, y + row * ROW_HEIGHT, width);
        }
        int footer = y + visible * ROW_HEIGHT + 4;

        if(prompt.options().size() > MAX_VISIBLE)
        {
            addRenderableWidget(new Button(left, footer, 50, 18, Component.literal("< Up"),
                pressed -> scrollBy(-MAX_VISIBLE)));
            addRenderableWidget(new Button(left + width - 50, footer, 50, 18, Component.literal("Down >"),
                pressed -> scrollBy(MAX_VISIBLE)));
            footer += 20;
        }

        boolean needsConfirm = prompt.kind() == EnginePrompt.Kind.MULTI && !prompt.isSingleChoice()
            || prompt.kind() == EnginePrompt.Kind.SORT
            || prompt.kind() == EnginePrompt.Kind.COUNTERS
            || prompt.kind() == EnginePrompt.Kind.PLACES && prompt.minSelect() > 1;
        if(needsConfirm)
        {
            confirmButton = new Button(left, footer, width / 2 - 2, 18, Component.literal("Confirm"),
                pressed -> confirm());
            addRenderableWidget(confirmButton);
        }
        if(prompt.cancelable())
        {
            addRenderableWidget(new Button(left + width / 2 + 2, footer, width / 2 - 2, 18,
                Component.literal(prompt.kind() == EnginePrompt.Kind.SORT ? "Keep order" : "Cancel"),
                pressed -> answer(new int[0], 0)));
        }
    }

    private void buildOptionRow(EnginePrompt prompt, int index, int x, int y, int width)
    {
        EnginePrompt.Option option = prompt.options().get(index);
        switch(prompt.kind())
        {
            case COUNTERS ->
            {
                addRenderableWidget(new Button(x, y, 18, 19, Component.literal("-"), pressed ->
                {
                    counterAmounts[index] = Math.max(0, counterAmounts[index] - 1);
                    updateConfirm();
                }));
                addRenderableWidget(new Button(x + width - 18, y, 18, 19, Component.literal("+"), pressed ->
                {
                    counterAmounts[index] = Math.min(option.max(), counterAmounts[index] + 1);
                    updateConfirm();
                }));
            }
            default -> addRenderableWidget(new Button(x, y, width, 19,
                Component.literal(optionLabel(prompt, index)), pressed -> choose(index)));
        }
    }

    private String optionLabel(EnginePrompt.Option option, int index)
    {
        return option.label();
    }

    private String optionLabel(EnginePrompt prompt, int index)
    {
        EnginePrompt.Option option = prompt.options().get(index);
        String prefix = switch(prompt.kind())
        {
            case MULTI, PLACES -> prompt.isSingleChoice() ? "" : (selected.contains(index) ? "[x] " : "[ ] ");
            case SORT ->
            {
                int at = sortOrder.indexOf(index);
                yield at >= 0 ? (at + 1) + ". " : "-. ";
            }
            default -> "";
        };
        String detail = option.detail().isEmpty() ? "" : "  §7" + option.detail();
        return prefix + option.label() + detail;
    }

    // ---- choosing ----

    private void choose(int index)
    {
        EnginePrompt prompt = shownPrompt;
        if(prompt == null || answered)
        {
            return;
        }
        switch(prompt.kind())
        {
            case CHOOSE -> answer(new int[] {index}, 0);
            case MULTI, PLACES ->
            {
                if(prompt.isSingleChoice())
                {
                    answer(new int[] {index}, 0);
                    return;
                }
                if(!selected.remove(index) && selected.size() < prompt.maxSelect())
                {
                    selected.add(index);
                }
                // Zone picks confirm themselves once the count is reached.
                if(prompt.kind() == EnginePrompt.Kind.PLACES && selected.size() == prompt.minSelect())
                {
                    confirm();
                    return;
                }
                refreshLabels();
            }
            case SORT ->
            {
                if(!sortOrder.remove((Integer)index))
                {
                    sortOrder.add(index);
                }
                refreshLabels();
            }
            default ->
            {
            }
        }
    }

    private void confirm()
    {
        EnginePrompt prompt = shownPrompt;
        if(prompt == null || answered)
        {
            return;
        }
        switch(prompt.kind())
        {
            case MULTI, PLACES ->
            {
                if(selected.size() >= prompt.minSelect())
                {
                    answer(selected.stream().mapToInt(Integer::intValue).toArray(), 0);
                }
            }
            case SORT ->
            {
                if(sortOrder.size() == prompt.options().size())
                {
                    // response[i] = click order of option i
                    int[] order = new int[sortOrder.size()];
                    for(int position = 0; position < sortOrder.size(); position++)
                    {
                        order[sortOrder.get(position)] = position;
                    }
                    answer(order, 0);
                }
            }
            case COUNTERS ->
            {
                int total = 0;
                for(int amount : counterAmounts)
                {
                    total += amount;
                }
                if(total == prompt.minSelect())
                {
                    answer(counterAmounts.clone(), 0);
                }
            }
            default ->
            {
            }
        }
    }

    private void answer(int[] chosen, int declaredCode)
    {
        if(answered)
        {
            return;
        }
        answered = true;
        DuelClientState.prompt = null;
        DuelDimension.channel.sendToServer(new PromptMessages.AnswerPrompt(chosen, declaredCode));
        rebuildControls();
    }

    private void updateSearch(String query)
    {
        searchResults.clear();
        if(query.length() < 2)
        {
            refreshSearchButtons();
            return;
        }
        String needle = query.toLowerCase();
        for(Properties properties : DdDatabase.PROPERTIES_LIST.getList())
        {
            if(properties.getName().toLowerCase().contains(needle))
            {
                searchResults.add((int)properties.getId());
                if(searchResults.size() >= MAX_VISIBLE)
                {
                    break;
                }
            }
        }
        refreshSearchButtons();
    }

    private void refreshSearchButtons()
    {
        int at = 0;
        for(var widget : children())
        {
            if(widget instanceof Button button && button.y >= 76 && button.getHeight() == 19)
            {
                if(at < searchResults.size())
                {
                    Properties properties = DdDatabase.PROPERTIES_LIST.get((long)searchResults.get(at));
                    button.setMessage(Component.literal(properties != null ? properties.getName() : "?"));
                    button.visible = true;
                }
                else
                {
                    button.visible = false;
                }
                at++;
            }
        }
    }

    private void refreshLabels()
    {
        rebuildKeepingSelection();
    }

    private void rebuildKeepingSelection()
    {
        Set<Integer> keepSelected = new HashSet<>(selected);
        List<Integer> keepOrder = new ArrayList<>(sortOrder);
        int[] keepAmounts = counterAmounts.clone();
        int keepScroll = scroll;
        EnginePrompt keep = shownPrompt;

        clearWidgets();
        shownPrompt = keep;
        selected.clear();
        selected.addAll(keepSelected);
        sortOrder.clear();
        sortOrder.addAll(keepOrder);
        scroll = keepScroll;

        int panelLeft = width / 2 + 20;
        int panelWidth = Math.min(240, width - panelLeft - 8);
        if(keep != null)
        {
            counterAmounts = keepAmounts;
            buildPromptControls(keep, panelLeft, panelWidth);
        }
        addRenderableWidget(new Button(panelLeft, height - 26, panelWidth, 20,
            Component.literal(DuelClientState.over ? "Close" : "Surrender"), pressed ->
        {
            if(DuelClientState.over)
            {
                DuelClientState.reset();
                onClose();
            }
            else
            {
                DuelDimension.channel.sendToServer(new PromptMessages.Surrender());
            }
        }));
    }

    private void scrollBy(int delta)
    {
        EnginePrompt prompt = shownPrompt;
        if(prompt == null)
        {
            return;
        }
        int max = Math.max(0, prompt.options().size() - MAX_VISIBLE);
        scroll = Math.max(0, Math.min(max, scroll + delta));
        rebuildKeepingSelection();
    }

    private void updateConfirm()
    {
        refreshLabels();
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        EnginePrompt prompt = shownPrompt;
        if(button == 0 && prompt != null && !answered)
        {
            for(BoardRenderer.Hit hit : boardRenderer.hits())
            {
                if(!hit.contains(mouseX, mouseY))
                {
                    continue;
                }
                // Zone selection: clicking a highlighted zone picks it.
                if(prompt.kind() == EnginePrompt.Kind.PLACES)
                {
                    for(int index = 0; index < prompt.options().size(); index++)
                    {
                        if(prompt.options().get(index).zone() == hit.zoneRef() && hit.zoneRef() >= 0)
                        {
                            choose(index);
                            return true;
                        }
                    }
                }
                // Otherwise clicking a card picks the option that names it.
                for(int index = 0; index < prompt.options().size(); index++)
                {
                    if(hit.code() != 0 && prompt.options().get(index).cardCode() == hit.code())
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
        if(shownPrompt != null && shownPrompt.options().size() > MAX_VISIBLE)
        {
            scrollBy(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ---- rendering ----

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(poseStack);
        RenderSystem.setShaderColor(1, 1, 1, 1);

        BoardSnapshot board = DuelClientState.board;
        EnginePrompt prompt = shownPrompt;
        if(prompt != null)
        {
            board = prompt.field();
        }

        // Banner: turn, phase, LP.
        String banner = "Turn " + board.turn() + " — " + phaseName(board.phase())
            + " — " + (board.turnPlayer() == 0 ? "your turn" : "opponent's turn");
        drawCenteredString(poseStack, font, banner, width / 2, 6, 0xFFD700);
        drawCenteredString(poseStack, font,
            "You " + board.self().lifePoints() + " LP   •   Opponent " + board.opponent().lifePoints() + " LP",
            width / 2, 18, 0xFFFFFF);

        // Field.
        Set<Integer> highlights = new HashSet<>();
        if(prompt != null && prompt.kind() == EnginePrompt.Kind.PLACES)
        {
            prompt.options().forEach(option -> highlights.add(option.zone()));
        }
        boardRenderer.setHighlights(highlights);
        boardRenderer.render(poseStack, font, board, boardCentreX == 0 ? width / 4 : boardCentreX, 32, highlights);

        // Right panel title.
        int panelLeft = width / 2 + 20;
        if(prompt != null)
        {
            drawWrapped(poseStack, prompt.title(), panelLeft, 34, Math.min(240, width - panelLeft - 8), 0xFFD700);
        }
        else
        {
            drawCenteredString(poseStack, font,
                DuelClientState.over ? DuelClientState.result : "Waiting for the opponent…",
                panelLeft + Math.min(240, width - panelLeft - 8) / 2, 40, 0xA0A0A0);
        }

        // Log tail, bottom-left.
        int logY = height - 12;
        int shown = 0;
        synchronized(DuelClientState.class)
        {
            var iterator = DuelClientState.log.descendingIterator();
            while(iterator.hasNext() && shown < 6)
            {
                font.draw(poseStack, iterator.next(), 6, logY, 0x909090);
                logY -= 10;
                shown++;
            }
        }

        super.render(poseStack, mouseX, mouseY, partialTick);

        renderHoverInfo(poseStack, mouseX, mouseY);
    }

    /** Card-info panel: art, name, stats, effect text, from the local DB. */
    private void renderHoverInfo(PoseStack poseStack, int mouseX, int mouseY)
    {
        for(BoardRenderer.Hit hit : boardRenderer.hits())
        {
            if(!hit.contains(mouseX, mouseY) || hit.code() == 0)
            {
                continue;
            }
            Properties properties = DdDatabase.PROPERTIES_LIST.get((long)hit.code());
            if(properties == null)
            {
                return;
            }
            int x = width / 2 + 20;
            int y = height / 2 - 20;
            int w = Math.min(240, width - x - 8);

            fill(poseStack, x - 2, y - 2, x + w + 2, height - 30, 0xE0101010);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderTexture(0, properties.getMainImageResourceLocation((byte)0));
            blit(poseStack, x, y, 0, 0, 40, 58, 40, 58);
            font.draw(poseStack, properties.getName(), x + 44, y, 0xFFFFFF);
            drawWrapped(poseStack, properties.getText(), x, y + 62, w, 0xC0C0C0);
            return;
        }
    }

    private void drawWrapped(PoseStack poseStack, String textToDraw, int x, int y, int width, int colour)
    {
        for(var line : font.split(Component.literal(textToDraw), width))
        {
            if(y > height - 40)
            {
                break;
            }
            font.draw(poseStack, line, x, y, colour);
            y += 9;
        }
    }

    private static String phaseName(int phase)
    {
        return switch(phase)
        {
            case OcgConstants.PHASE_DRAW -> "Draw Phase";
            case OcgConstants.PHASE_STANDBY -> "Standby Phase";
            case OcgConstants.PHASE_MAIN1 -> "Main Phase 1";
            case OcgConstants.PHASE_MAIN2 -> "Main Phase 2";
            case OcgConstants.PHASE_END -> "End Phase";
            case 0 -> "…";
            default -> "Battle Phase";
        };
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc()
    {
        // Closing with a prompt pending would block the duel; while waiting
        // it is safe (a new prompt reopens the screen).
        return shownPrompt == null;
    }
}
