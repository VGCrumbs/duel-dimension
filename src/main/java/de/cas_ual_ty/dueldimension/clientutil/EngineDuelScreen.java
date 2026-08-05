package de.cas_ual_ty.dueldimension.clientutil;

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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The duel screen, laid out the way duel sims are: a card preview and info
 * column on the left, the field in the middle, and actions where the action
 * is — hovering a card highlights it and lists what it can do, clicking it
 * opens those actions right at the card. Only choices with no card to point at
 * (phases, effect options, yes/no) get buttons, in a strip along the bottom.
 * <p>
 * All rules knowledge is server-side; this screen renders what it is given and
 * reports clicks.
 */
public class EngineDuelScreen extends Screen
{
    private static final int PREVIEW_W = 116;
    private static final int MENU_W = 104;
    /** ShowMenu stacks entries at Scale(21); we keep the same step. */
    private static final int MENU_ROW = de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.MENU_ROW_HEIGHT;

    private final BoardRenderer boardRenderer = new BoardRenderer();

    private final Set<Integer> selected = new LinkedHashSet<>();
    private final List<Integer> sortOrder = new ArrayList<>();
    private int[] counterAmounts = new int[0];

    private EnginePrompt shownPrompt;
    private EditBox searchBox;
    private final List<Integer> searchResults = new ArrayList<>();

    /** Card shown in the left panel: hovered, else the last one hovered. */
    private int previewCode;
    /** Open per-card action menu, or null. */
    private CardMenu menu;
    /** Pile contents being viewed, or null. */
    private List<BoardSnapshot.Slot> pileView;
    private String pileViewLabel = "";
    private boolean answered;

    private record CardMenu(int x, int y, List<Integer> optionIndices)
    {
    }

    public EngineDuelScreen()
    {
        super(Component.literal("Duel"));
    }

    // ---- lifecycle ----

    @Override
    protected void init()
    {
        rebuild();
    }

    @Override
    public void tick()
    {
        if(DuelClientState.prompt != shownPrompt)
        {
            rebuild();
        }
        if(searchBox != null)
        {
            searchBox.tick();
        }
    }

    private void rebuild()
    {
        clearWidgets();
        if(DuelClientState.prompt != shownPrompt)
        {
            selected.clear();
            sortOrder.clear();
            menu = null;
            answered = false;
        }
        shownPrompt = DuelClientState.prompt;
        searchBox = null;

        EnginePrompt prompt = shownPrompt;
        if(prompt != null)
        {
            counterAmounts = new int[prompt.options().size()];
        }

        // Surrender/close sits in the top-right corner, clear of the field.
        addRenderableWidget(new Button(width - 74, 6, 68, 16,
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

        if(prompt == null)
        {
            return;
        }
        if(prompt.kind() == EnginePrompt.Kind.DECLARE_CARD)
        {
            buildDeclareControls(prompt);
            return;
        }
        buildBottomStrip(prompt);
    }

    /** Buttons for choices that aren't attached to a card on the field. */
    private void buildBottomStrip(EnginePrompt prompt)
    {
        List<Integer> loose = new ArrayList<>();
        for(int i = 0; i < prompt.options().size(); i++)
        {
            EnginePrompt.Option option = prompt.options().get(i);
            boolean onField = option.hasSlot() || option.zone() >= 0;
            if(!onField)
            {
                loose.add(i);
            }
        }

        int y = height - 26;
        int x = PREVIEW_W + 8;
        for(int index : loose)
        {
            String label = prompt.options().get(index).label();
            int buttonWidth = Math.max(60, font.width(label) + 12);
            if(x + buttonWidth > width - 80)
            {
                x = PREVIEW_W + 8;
                y -= 20;
            }
            int optionIndex = index;
            addRenderableWidget(new Button(x, y, buttonWidth, 18, Component.literal(label),
                pressed -> choose(optionIndex)));
            x += buttonWidth + 4;
        }

        boolean needsConfirm = switch(prompt.kind())
        {
            case MULTI -> !prompt.isSingleChoice();
            case SORT, COUNTERS -> true;
            case PLACES -> prompt.minSelect() > 1;
            default -> false;
        };
        if(needsConfirm)
        {
            addRenderableWidget(new Button(width - 160, height - 26, 74, 18,
                Component.literal("Confirm"), pressed -> confirm()));
        }
        if(prompt.cancelable())
        {
            addRenderableWidget(new Button(width - 82, height - 26, 74, 18,
                Component.literal(prompt.kind() == EnginePrompt.Kind.SORT ? "Keep order" : "Cancel"),
                pressed -> answer(new int[0], 0)));
        }
        if(prompt.kind() == EnginePrompt.Kind.COUNTERS)
        {
            int rowY = height - 52;
            for(int i = 0; i < prompt.options().size(); i++)
            {
                int index = i;
                addRenderableWidget(new Button(PREVIEW_W + 8 + i * 92, rowY, 20, 18,
                    Component.literal("-"), pressed ->
                {
                    counterAmounts[index] = Math.max(0, counterAmounts[index] - 1);
                }));
                addRenderableWidget(new Button(PREVIEW_W + 8 + i * 92 + 52, rowY, 20, 18,
                    Component.literal("+"), pressed ->
                {
                    counterAmounts[index] = Math.min(prompt.options().get(index).max(),
                        counterAmounts[index] + 1);
                }));
            }
        }
    }

    private void buildDeclareControls(EnginePrompt prompt)
    {
        int x = PREVIEW_W + 8;
        searchBox = new EditBox(font, x, height - 48, 180, 16, Component.literal("search"));
        searchBox.setResponder(this::updateSearch);
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        for(int i = 0; i < 5; i++)
        {
            int row = i;
            Button button = new Button(x + 186 + (i % 3) * 110, height - 48 + (i / 3) * 19, 106, 17,
                Component.empty(), pressed ->
            {
                if(row < searchResults.size())
                {
                    answer(new int[] {0}, searchResults.get(row));
                }
            });
            button.visible = false;
            addRenderableWidget(button);
        }
    }

    private void updateSearch(String query)
    {
        searchResults.clear();
        if(query.length() >= 2)
        {
            String needle = query.toLowerCase();
            for(Properties properties : DdDatabase.PROPERTIES_LIST.getList())
            {
                if(properties.getName().toLowerCase().contains(needle))
                {
                    searchResults.add((int)properties.getId());
                    if(searchResults.size() >= 5)
                    {
                        break;
                    }
                }
            }
        }
        int at = 0;
        for(var child : children())
        {
            if(child instanceof Button button && button.getMessage().getString().isEmpty() || child instanceof Button
                && ((Button)child).y >= height - 50 && ((Button)child).getWidth() == 106)
            {
                Button button = (Button)child;
                if(at < searchResults.size())
                {
                    Properties properties = DdDatabase.PROPERTIES_LIST.get((long)searchResults.get(at));
                    button.setMessage(Component.literal(properties == null ? "?" : properties.getName()));
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

    // ---- choosing ----

    /** Option indices that act on this exact slot. */
    private List<Integer> optionsFor(BoardRenderer.Hit hit)
    {
        List<Integer> found = new ArrayList<>();
        EnginePrompt prompt = shownPrompt;
        if(prompt == null || answered || hit.isPile())
        {
            return found;
        }
        for(int i = 0; i < prompt.options().size(); i++)
        {
            EnginePrompt.Option option = prompt.options().get(i);
            if(prompt.kind() == EnginePrompt.Kind.PLACES)
            {
                if(hit.zoneRef() >= 0 && option.zone() == hit.zoneRef())
                {
                    found.add(i);
                }
            }
            else if(option.hasSlot() && option.isAt(hit.controller(), hit.location(), hit.sequence()))
            {
                found.add(i);
            }
            else if(!option.hasSlot() && option.cardCode() != 0 && option.cardCode() == hit.code())
            {
                found.add(i); // e.g. position choices, which name a card not a zone
            }
        }
        return found;
    }

    /** ShowMenu's fixed button order: Activate, Summon, SpSummon, MSet, SSet, Repos, Attack, ... */
    private List<Integer> inMenuOrder(List<Integer> optionIndices)
    {
        EnginePrompt prompt = shownPrompt;
        if(prompt == null)
        {
            return optionIndices;
        }
        List<Integer> ordered = new ArrayList<>(optionIndices);
        ordered.sort(java.util.Comparator.comparingInt(index ->
            de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.menuIndex(
                prompt.options().get(index).command())));
        return ordered;
    }

    private void choose(int index)
    {
        EnginePrompt prompt = shownPrompt;
        if(prompt == null || answered)
        {
            return;
        }
        menu = null;
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
                if(prompt.kind() == EnginePrompt.Kind.PLACES && selected.size() == prompt.minSelect())
                {
                    confirm();
                }
            }
            case SORT ->
            {
                if(!sortOrder.remove((Integer)index))
                {
                    sortOrder.add(index);
                }
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
        menu = null;
        DuelClientState.prompt = null;
        DuelDimension.channel.sendToServer(new PromptMessages.AnswerPrompt(chosen, declaredCode));
        rebuild();
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(button == 0 && menu != null)
        {
            for(int row = 0; row < menu.optionIndices().size(); row++)
            {
                int rowY = menu.y() + row * MENU_ROW;
                if(mouseX >= menu.x() && mouseX < menu.x() + MENU_W && mouseY >= rowY && mouseY < rowY + MENU_ROW)
                {
                    choose(menu.optionIndices().get(row));
                    return true;
                }
            }
            menu = null; // clicked away
        }

        if(button == 0 && pileView != null)
        {
            pileView = null;
            return true;
        }

        if(button == 0)
        {
            for(BoardRenderer.Hit hit : boardRenderer.hits())
            {
                if(!hit.contains(mouseX, mouseY))
                {
                    continue;
                }
                if(hit.isPile())
                {
                    openPile(hit);
                    return true;
                }
                List<Integer> actions = optionsFor(hit);
                if(actions.isEmpty())
                {
                    continue;
                }
                actions = inMenuOrder(actions);
                if(actions.size() == 1)
                {
                    choose(actions.get(0)); // one action: no menu needed
                }
                else
                {
                    menu = new CardMenu(Math.min((int)mouseX, width - MENU_W - 4), (int)mouseY, actions);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openPile(BoardRenderer.Hit hit)
    {
        BoardSnapshot board = currentBoard();
        BoardSnapshot.Side side = hit.controller() == 0 ? board.self() : board.opponent();
        pileView = switch(hit.location())
        {
            case OcgConstants.LOCATION_GRAVE -> side.grave();
            case OcgConstants.LOCATION_REMOVED -> side.banished();
            case OcgConstants.LOCATION_EXTRA -> side.extra();
            default -> List.of();
        };
        pileViewLabel = hit.label();
        if(pileView.isEmpty())
        {
            pileView = null;
        }
    }

    // ---- rendering ----

    private BoardSnapshot currentBoard()
    {
        EnginePrompt prompt = shownPrompt;
        return prompt != null ? prompt.field() : DuelClientState.board;
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(poseStack);
        BoardSnapshot board = currentBoard();
        EnginePrompt prompt = shownPrompt;

        Set<Integer> highlights = new java.util.HashSet<>();
        if(prompt != null && prompt.kind() == EnginePrompt.Kind.PLACES)
        {
            prompt.options().forEach(option -> highlights.add(option.zone()));
        }
        boardRenderer.setActionable(hit -> !optionsFor(hit).isEmpty());

        // The field fills everything right of the preview column, leaving room
        // for the header and the bottom button strip.
        int fieldLeft = PREVIEW_W + 6;
        int fieldTop = 32;
        int fieldWidth = width - fieldLeft - 6;
        int fieldHeight = height - fieldTop - 34;
        int fieldCentre = fieldLeft + fieldWidth / 2;
        boardRenderer.render(poseStack, font, board, fieldLeft, fieldTop, fieldWidth, fieldHeight, highlights);

        // Header: turn / phase / prompt title.
        drawCenteredString(poseStack, font, "Turn " + board.turn() + " — " + phaseName(board.phase())
            + " — " + (board.turnPlayer() == 0 ? "your turn" : "opponent's turn"), fieldCentre, 8, 0xFFD700);
        String subtitle = prompt != null ? prompt.title()
            : DuelClientState.over ? DuelClientState.result : "Waiting for the opponent…";
        drawCenteredString(poseStack, font, subtitle, fieldCentre, 20,
            prompt != null ? 0xFFFFFF : 0xA0A0A0);

        // Hover: preview + action hint.
        BoardRenderer.Hit hovered = null;
        for(BoardRenderer.Hit hit : boardRenderer.hits())
        {
            if(hit.contains(mouseX, mouseY))
            {
                hovered = hit;
                if(hit.code() != 0)
                {
                    previewCode = hit.code();
                }
                break;
            }
        }

        renderPreviewPanel(poseStack);
        renderLog(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);

        if(prompt != null && prompt.kind() != EnginePrompt.Kind.CHOOSE)
        {
            renderSelectionMarks(poseStack, prompt);
        }
        if(hovered != null)
        {
            renderHoverTooltip(poseStack, hovered, mouseX, mouseY);
        }
        if(menu != null)
        {
            renderMenu(poseStack, mouseX, mouseY);
        }
        if(pileView != null)
        {
            renderPileView(poseStack);
        }
    }

    /** Left column: big card image, name, stats, effect text. */
    private void renderPreviewPanel(PoseStack poseStack)
    {
        fill(poseStack, 0, 0, PREVIEW_W, height, 0xC0000000);
        if(previewCode == 0)
        {
            drawCenteredString(poseStack, font, "Hover a card", PREVIEW_W / 2, height / 2, 0x808080);
            return;
        }
        Properties card = DdDatabase.PROPERTIES_LIST.get((long)previewCode);
        if(card == null)
        {
            return;
        }
        // Cards are 480x700; drawing a square would squash the art.
        int imageW = PREVIEW_W - 16;
        int imageH = Math.round(imageW / DuelTextures.CARD_ASPECT);
        ScreenUtil.white();
        CardRenderUtil.bindMainResourceLocation(
            DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE));
        DdBlitUtil.fullBlit(poseStack, 8, 8, imageW, imageH);

        int y = imageH + 14;
        for(var line : font.split(Component.literal(card.getName()), PREVIEW_W - 12))
        {
            font.draw(poseStack, line, 6, y, 0xFFD700);
            y += 9;
        }

        List<Component> info = new ArrayList<>();
        card.addHeader(info);
        poseStack.pushPose();
        poseStack.scale(0.75F, 0.75F, 1F);
        int scaledY = (int)(y / 0.75F) + 2;
        for(Component component : info)
        {
            for(var line : font.split(component, (int)((PREVIEW_W - 12) / 0.75F)))
            {
                font.draw(poseStack, line, 8, scaledY, 0xC0C0C0);
                scaledY += 8;
            }
        }
        scaledY += 4;
        for(var line : font.split(Component.literal(card.getText()), (int)((PREVIEW_W - 12) / 0.75F)))
        {
            if(scaledY * 0.75F > height - 12)
            {
                break;
            }
            font.draw(poseStack, line, 8, scaledY, 0x9F9F9F);
            scaledY += 8;
        }
        poseStack.popPose();
    }

    /** Log runs up the right edge, clear of the field and the button strip. */
    private void renderLog(PoseStack poseStack)
    {
        int x = width - 132;
        int y = height - 60;
        int shown = 0;
        synchronized(DuelClientState.class)
        {
            var iterator = DuelClientState.log.descendingIterator();
            while(iterator.hasNext() && shown < 5)
            {
                String line = iterator.next();
                while(font.width(line) > 126 && line.length() > 4)
                {
                    line = line.substring(0, line.length() - 2);
                }
                font.draw(poseStack, line, x, y, 0x808080);
                y -= 9;
                shown++;
            }
        }
    }

    /** Ticks/numbers on cards already picked for a multi-select or sort. */
    private void renderSelectionMarks(PoseStack poseStack, EnginePrompt prompt)
    {
        for(BoardRenderer.Hit hit : boardRenderer.hits())
        {
            for(int index : optionsFor(hit))
            {
                String mark = null;
                if(selected.contains(index))
                {
                    mark = "✔";
                }
                else if(sortOrder.contains(index))
                {
                    mark = Integer.toString(sortOrder.indexOf(index) + 1);
                }
                if(mark != null)
                {
                    fill(poseStack, hit.x(), hit.y(), hit.x() + 10, hit.y() + 10, 0xC0000000);
                    font.draw(poseStack, mark, hit.x() + 2, hit.y() + 1, 0x00FF66);
                }
            }
        }
    }

    private void renderHoverTooltip(PoseStack poseStack, BoardRenderer.Hit hit, int mouseX, int mouseY)
    {
        List<Component> lines = new ArrayList<>();
        if(hit.isPile())
        {
            lines.add(Component.literal(hit.label()));
            if(hit.count() > 0)
            {
                lines.add(Component.literal("Click to view").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
        else
        {
            Properties card = hit.code() == 0 ? null : DdDatabase.PROPERTIES_LIST.get((long)hit.code());
            lines.add(Component.literal(card != null ? card.getName()
                : hit.code() != 0 ? "Card " + hit.code() : hit.label()));
            if(card != null)
            {
                lines.add(Component.literal(hit.label()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
            List<Integer> actions = inMenuOrder(optionsFor(hit));
            EnginePrompt prompt = shownPrompt;
            for(int index : actions)
            {
                EnginePrompt.Option option = prompt.options().get(index);
                String detail = option.detail().isEmpty() ? "" : " — " + option.detail();
                lines.add(Component.literal("• " + option.label() + detail)
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
            }
        }
        renderComponentTooltip(poseStack, lines, mouseX, mouseY);
    }

    private void renderMenu(PoseStack poseStack, int mouseX, int mouseY)
    {
        EnginePrompt prompt = shownPrompt;
        if(prompt == null)
        {
            return;
        }
        int rows = menu.optionIndices().size();
        fill(poseStack, menu.x() - 2, menu.y() - 2, menu.x() + MENU_W + 2, menu.y() + rows * MENU_ROW + 2,
            0xF0100010);
        for(int row = 0; row < rows; row++)
        {
            int index = menu.optionIndices().get(row);
            int rowY = menu.y() + row * MENU_ROW;
            boolean hovered = mouseX >= menu.x() && mouseX < menu.x() + MENU_W
                && mouseY >= rowY && mouseY < rowY + MENU_ROW;
            if(hovered)
            {
                fill(poseStack, menu.x(), rowY, menu.x() + MENU_W, rowY + MENU_ROW, 0x60FFFFFF);
            }
            font.draw(poseStack, prompt.options().get(index).label(), menu.x() + 4, rowY + 4,
                hovered ? 0xFFFFA0 : 0xFFFFFF);
        }
    }

    private void renderPileView(PoseStack poseStack)
    {
        int columns = 8;
        int cardW = 30;
        int cardH = 33;
        int rows = (pileView.size() + columns - 1) / columns;
        int panelW = columns * (cardW + 4) + 8;
        int panelH = rows * (cardH + 4) + 26;
        int left = (width - panelW) / 2;
        int top = (height - panelH) / 2;

        fill(poseStack, left, top, left + panelW, top + panelH, 0xF0100010);
        drawCenteredString(poseStack, font, pileViewLabel + " — click to close", width / 2, top + 6, 0xFFD700);

        for(int i = 0; i < pileView.size(); i++)
        {
            BoardSnapshot.Slot slot = pileView.get(i);
            int x = left + 4 + (i % columns) * (cardW + 4);
            int y = top + 20 + (i / columns) * (cardH + 4);
            Properties card = slot.code() == 0 ? null : DdDatabase.PROPERTIES_LIST.get((long)slot.code());
            ScreenUtil.white();
            CardRenderUtil.bindMainResourceLocation(card == null ? CardRenderUtil.getMainCardBack()
                : card.getMainImageResourceLocation((byte)0));
            DdBlitUtil.fullBlit(poseStack, x, y, cardW, cardH);
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
        return shownPrompt == null;
    }
}
