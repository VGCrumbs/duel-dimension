package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands;
import de.cas_ual_ty.dueldimension.ocg.prompt.EnginePrompt;
import de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The duel screen, arranged like EDOPro's: a card-image and card-info column
 * down the left, life-point bars across the top, the field in the middle with
 * the hands along its edges, the message log in the lower right, and a
 * contextual command menu that opens at the card you point at.
 * <p>
 * The menu is built from the engine-derived {@link CardCommands} bitmask and
 * stacked in ShowMenu's order; choices with no card to point at (phase
 * changes, effect options, yes/no) get buttons in the bottom strip.
 */
public class EngineDuelScreen extends Screen
{
    private static final int SIDEBAR_W = 132;
    private static final int SIDEBAR_PAD = 6;
    private static final int TOP_BAR_H = 32;
    private static final int MENU_ROW = CardCommands.MENU_ROW_HEIGHT;
    private static final int LOG_W = 150;

    private final BoardRenderer boardRenderer = new BoardRenderer();

    private final Set<Integer> selected = new LinkedHashSet<>();
    private final List<Integer> sortOrder = new ArrayList<>();
    private int[] counterAmounts = new int[0];

    private EnginePrompt shownPrompt;
    private EditBox searchBox;
    private final List<Integer> searchResults = new ArrayList<>();
    private final List<Button> searchButtons = new ArrayList<>();

    private int previewCode;
    private BoardRenderer.Hit menuAnchor;
    private final List<Button> menuButtons = new ArrayList<>();
    private List<BoardSnapshot.Slot> pileView;
    private String pileViewLabel = "";
    private boolean answered;
    private de.cas_ual_ty.dueldimension.ocg.prompt.ChainPreference chainPreference =
        de.cas_ual_ty.dueldimension.ocg.prompt.ChainPreference.DEFAULT;

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
        menuButtons.clear();
        searchButtons.clear();
        if(DuelClientState.prompt != shownPrompt)
        {
            selected.clear();
            sortOrder.clear();
            menuAnchor = null;
            answered = false;
        }
        shownPrompt = DuelClientState.prompt;
        searchBox = null;

        EnginePrompt prompt = shownPrompt;
        if(prompt != null)
        {
            counterAmounts = new int[prompt.options().size()];
        }

        // EDOPro keeps the chain toggles next to Surrender; same here.
        addRenderableWidget(new Button(SIDEBAR_PAD, height - 44, SIDEBAR_W - SIDEBAR_PAD * 2, 18,
            Component.literal(chainPreference.label()), pressed ->
        {
            chainPreference = chainPreference.next();
            DuelDimension.channel.sendToServer(new PromptMessages.SetChainPreference(chainPreference));
            rebuild();
        }));
        addRenderableWidget(new Button(SIDEBAR_PAD, height - 24, SIDEBAR_W - SIDEBAR_PAD * 2, 18,
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
            buildDeclareControls();
            return;
        }
        buildBottomStrip(prompt);
    }

    /** Choices with no card to point at: phases, effect options, yes/no. */
    private void buildBottomStrip(EnginePrompt prompt)
    {
        int y = height - 24;
        int x = SIDEBAR_W + 8;
        for(int i = 0; i < prompt.options().size(); i++)
        {
            EnginePrompt.Option option = prompt.options().get(i);
            if(option.hasSlot() || option.zone() >= 0)
            {
                continue; // this one belongs on the field
            }
            String label = option.label();
            int buttonWidth = Math.max(56, font.width(label) + 12);
            if(x + buttonWidth > width - LOG_W - 12)
            {
                x = SIDEBAR_W + 8;
                y -= 20;
            }
            int index = i;
            addRenderableWidget(new Button(x, y, buttonWidth, 18, Component.literal(label),
                pressed -> choose(index)));
            x += buttonWidth + 4;
        }

        boolean needsConfirm = switch(prompt.kind())
        {
            case MULTI -> !prompt.isSingleChoice();
            case SORT, COUNTERS -> true;
            case PLACES -> prompt.minSelect() > 1;
            default -> false;
        };
        int rightX = width - LOG_W - 16;
        if(needsConfirm)
        {
            addRenderableWidget(new Button(rightX - 76, height - 24, 74, 18,
                Component.literal("Confirm"), pressed -> confirm()));
        }
        if(prompt.cancelable())
        {
            addRenderableWidget(new Button(rightX - 76, height - 44, 74, 18,
                Component.literal(prompt.kind() == EnginePrompt.Kind.SORT ? "Keep order" : "Cancel"),
                pressed -> answer(new int[0], 0)));
        }
        if(prompt.kind() == EnginePrompt.Kind.COUNTERS)
        {
            for(int i = 0; i < prompt.options().size(); i++)
            {
                int index = i;
                int rowX = SIDEBAR_W + 8 + i * 96;
                addRenderableWidget(new Button(rowX, height - 46, 18, 18, Component.literal("-"),
                    pressed -> counterAmounts[index] = Math.max(0, counterAmounts[index] - 1)));
                addRenderableWidget(new Button(rowX + 52, height - 46, 18, 18, Component.literal("+"),
                    pressed -> counterAmounts[index] = Math.min(
                        prompt.options().get(index).max(), counterAmounts[index] + 1)));
            }
        }
    }

    private void buildDeclareControls()
    {
        int x = SIDEBAR_W + 8;
        searchBox = new EditBox(font, x, height - 46, 170, 16, Component.literal("card name"));
        searchBox.setResponder(this::updateSearch);
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        for(int i = 0; i < 4; i++)
        {
            int row = i;
            Button button = new Button(x + 176 + (i % 2) * 130, height - 46 + (i / 2) * 19, 126, 17,
                Component.empty(), pressed ->
            {
                if(row < searchResults.size())
                {
                    answer(new int[] {0}, searchResults.get(row));
                }
            });
            button.visible = false;
            searchButtons.add(button);
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
                    if(searchResults.size() >= searchButtons.size())
                    {
                        break;
                    }
                }
            }
        }
        for(int i = 0; i < searchButtons.size(); i++)
        {
            Button button = searchButtons.get(i);
            boolean has = i < searchResults.size();
            button.visible = has;
            if(has)
            {
                Properties card = DdDatabase.PROPERTIES_LIST.get((long)searchResults.get(i));
                button.setMessage(Component.literal(card == null ? "?" : card.getName()));
            }
        }
    }

    // ---- contextual command menu ----

    /** Option indices acting on this exact slot, in ShowMenu's order. */
    private List<Integer> optionsFor(BoardRenderer.Hit hit)
    {
        List<Integer> found = new ArrayList<>();
        EnginePrompt prompt = shownPrompt;
        if(prompt == null || answered || hit == null)
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
            else if(hit.isPile() && option.hasSlot() && option.controller() == hit.controller()
                && option.location() == hit.location())
            {
                // duelclient.cpp raises deck_act/grave_act/remove_act/extra_act
                // for activations from a pile; the pile is the click target.
                found.add(i);
            }
            else if(!option.hasSlot() && option.cardCode() != 0 && option.cardCode() == hit.code()
                && !hit.isPile())
            {
                found.add(i);
            }
        }
        found.sort(java.util.Comparator.comparingInt(index ->
            CardCommands.menuIndex(prompt.options().get(index).command())));
        return found;
    }

    /** Opens the command menu at a card, as EDOPro's wCmdMenu does. */
    private void openMenu(BoardRenderer.Hit hit)
    {
        if(menuAnchor != null && sameSlot(menuAnchor, hit))
        {
            return;
        }
        closeMenu();
        List<Integer> actions = optionsFor(hit);
        if(actions.isEmpty())
        {
            return;
        }
        menuAnchor = hit;
        EnginePrompt prompt = shownPrompt;

        int widest = 60;
        for(int index : actions)
        {
            widest = Math.max(widest, font.width(prompt.options().get(index).label()) + 14);
        }
        int menuX = Math.min(hit.x() + hit.w() + 2, width - widest - 4);
        int menuY = Math.max(TOP_BAR_H, Math.min(hit.y(), height - actions.size() * MENU_ROW - 6));

        for(int row = 0; row < actions.size(); row++)
        {
            int index = actions.get(row);
            Button button = new Button(menuX, menuY + row * MENU_ROW, widest, MENU_ROW - 2,
                Component.literal(prompt.options().get(index).label()), pressed -> choose(index));
            menuButtons.add(button);
            addRenderableWidget(button);
        }
    }

    private static boolean sameSlot(BoardRenderer.Hit a, BoardRenderer.Hit b)
    {
        return b != null && a.controller() == b.controller() && a.location() == b.location()
            && a.sequence() == b.sequence();
    }

    private void closeMenu()
    {
        menuButtons.forEach(this::removeWidget);
        menuButtons.clear();
        menuAnchor = null;
    }

    private boolean overMenu(double mouseX, double mouseY)
    {
        for(Button button : menuButtons)
        {
            if(mouseX >= button.x && mouseX < button.x + button.getWidth()
                && mouseY >= button.y && mouseY < button.y + button.getHeight())
            {
                return true;
            }
        }
        return false;
    }

    // ---- choosing ----

    private void choose(int index)
    {
        EnginePrompt prompt = shownPrompt;
        if(prompt == null || answered)
        {
            return;
        }
        closeMenu();
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
        closeMenu();
        DuelClientState.prompt = null;
        DuelDimension.channel.sendToServer(new PromptMessages.AnswerPrompt(chosen, declaredCode));
        rebuild();
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(button == 0 && pileView != null)
        {
            pileView = null;
            return true;
        }
        if(super.mouseClicked(mouseX, mouseY, button))
        {
            return true; // a widget (including a menu entry) took it
        }
        if(button == 0)
        {
            for(BoardRenderer.Hit hit : boardRenderer.hits())
            {
                if(!hit.contains(mouseX, mouseY))
                {
                    continue;
                }
                List<Integer> actions = optionsFor(hit);
                if(hit.isPile() && actions.isEmpty())
                {
                    if(hit.count() > 0)
                    {
                        openPile(hit);
                        return true;
                    }
                    continue;
                }
                if(actions.size() == 1)
                {
                    choose(actions.get(0));
                    return true;
                }
                if(!actions.isEmpty())
                {
                    openMenu(hit);
                    return true;
                }
            }
            closeMenu();
        }
        return false;
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

        Set<Integer> highlights = new HashSet<>();
        if(prompt != null && prompt.kind() == EnginePrompt.Kind.PLACES)
        {
            prompt.options().forEach(option -> highlights.add(option.zone()));
        }
        boardRenderer.setActionable(hit -> !optionsFor(hit).isEmpty());

        // EDOPro's frustum is off-centre by design: it pushes the table into
        // the right two thirds and leaves the left for the card-info column.
        // So the camera gets the whole screen, and the sidebar is drawn over
        // the space the projection already reserved.
        int fieldLeft = 0;
        int fieldTop = TOP_BAR_H;
        int fieldWidth = width;
        int fieldHeight = height - fieldTop - 30;
        boardRenderer.render(poseStack, font, board, fieldLeft, fieldTop, fieldWidth, fieldHeight, highlights);

        // Hover picks the preview card and opens that card's command menu.
        BoardRenderer.Hit hovered = null;
        for(BoardRenderer.Hit hit : boardRenderer.hits())
        {
            if(hit.contains(mouseX, mouseY))
            {
                hovered = hit;
                break;
            }
        }
        if(hovered != null && hovered.code() != 0)
        {
            previewCode = hovered.code();
        }
        if(hovered != null && !optionsFor(hovered).isEmpty())
        {
            openMenu(hovered);
        }
        else if(menuAnchor != null && hovered == null && !overMenu(mouseX, mouseY))
        {
            closeMenu();
        }

        renderTopBar(poseStack, board);
        renderSidebar(poseStack);
        renderLog(poseStack);

        if(prompt != null && prompt.kind() != EnginePrompt.Kind.CHOOSE)
        {
            renderSelectionMarks(poseStack);
        }
        if(!menuButtons.isEmpty())
        {
            Button first = menuButtons.get(0);
            fill(poseStack, first.x - 2, first.y - 2, first.x + first.getWidth() + 2,
                first.y + menuButtons.size() * MENU_ROW, 0xC0000000);
        }

        super.render(poseStack, mouseX, mouseY, partialTick);

        if(pileView != null)
        {
            renderPileView(poseStack);
        }
        if(hovered != null && hovered.isPile())
        {
            renderTooltip(poseStack, Component.literal(hovered.label()), mouseX, mouseY);
        }
    }

    /** Life-point bars across the top, as EDOPro shows them. */
    private void renderTopBar(PoseStack poseStack, BoardSnapshot board)
    {
        int barW = Math.max(80, (width - SIDEBAR_W - 60) / 2);
        drawLifeBar(poseStack, SIDEBAR_W + 8, 6, barW, "You", board.self().lifePoints(), 0xFF4CAF50);
        drawLifeBar(poseStack, width - barW - 8, 6, barW, "Opponent", board.opponent().lifePoints(), 0xFFE53935);

        drawCenteredString(poseStack, font, "Turn " + board.turn() + " — " + phaseName(board.phase()),
            SIDEBAR_W + (width - SIDEBAR_W) / 2, 20, 0xFFD700);
    }

    private void drawLifeBar(PoseStack poseStack, int x, int y, int barW, String name, int lifePoints, int colour)
    {
        int filled = Math.max(0, Math.min(barW, Math.round(barW * lifePoints / 8000F)));
        fill(poseStack, x, y, x + barW, y + 11, 0xFF202020);
        fill(poseStack, x, y, x + filled, y + 11, colour);
        font.draw(poseStack, name, x + 3, y + 2, 0xFFFFFF);
        String value = Integer.toString(lifePoints);
        font.draw(poseStack, value, x + barW - font.width(value) - 3, y + 2, 0xFFFFFF);
    }

    /** Left column: card image, then card info — EDOPro's Card info tab. */
    private void renderSidebar(PoseStack poseStack)
    {
        fill(poseStack, 0, 0, SIDEBAR_W, height, 0xD0101010);

        Properties card = previewCode == 0 ? null : DdDatabase.PROPERTIES_LIST.get((long)previewCode);
        if(card == null)
        {
            drawCenteredString(poseStack, font, "Point at a card", SIDEBAR_W / 2, height / 2, 0x707070);
            return;
        }

        int imageW = SIDEBAR_W - SIDEBAR_PAD * 2;
        int imageH = Math.round(imageW / DuelTextures.CARD_ASPECT);
        ScreenUtil.white();
        CardRenderUtil.bindMainResourceLocation(
            DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE));
        DdBlitUtil.fullBlit(poseStack, SIDEBAR_PAD, SIDEBAR_PAD, imageW, imageH);

        int y = SIDEBAR_PAD + imageH + 4;
        for(var line : font.split(Component.literal(card.getName()), imageW))
        {
            font.draw(poseStack, line, SIDEBAR_PAD, y, 0xFFD700);
            y += 9;
        }

        // Type line and effect text at 3/4 scale, so a full card fits above
        // the Surrender button instead of spilling over it.
        List<Component> header = new ArrayList<>();
        card.addHeader(header);
        poseStack.pushPose();
        poseStack.scale(0.75F, 0.75F, 1F);
        int scaledX = Math.round(SIDEBAR_PAD / 0.75F);
        int scaledY = Math.round(y / 0.75F) + 2;
        int scaledW = Math.round(imageW / 0.75F);
        int limit = Math.round((height - 50) / 0.75F);

        for(Component component : header)
        {
            for(var line : font.split(component, scaledW))
            {
                if(scaledY > limit)
                {
                    break;
                }
                font.draw(poseStack, line, scaledX, scaledY, 0xB0B0B0);
                scaledY += 8;
            }
        }
        scaledY += 4;
        for(var line : font.split(Component.literal(card.getText()), scaledW))
        {
            if(scaledY > limit)
            {
                break;
            }
            font.draw(poseStack, line, scaledX, scaledY, 0x909090);
            scaledY += 8;
        }
        poseStack.popPose();
    }

    private void renderLog(PoseStack poseStack)
    {
        int x = width - LOG_W - 4;
        int y = height - 30;
        int shown = 0;
        synchronized(DuelClientState.class)
        {
            var iterator = DuelClientState.log.descendingIterator();
            while(iterator.hasNext() && shown < 3)
            {
                String line = iterator.next();
                while(font.width(line) > LOG_W && line.length() > 4)
                {
                    line = line.substring(0, line.length() - 2);
                }
                font.draw(poseStack, line, x, y, 0x8A8A8A);
                y -= 9;
                shown++;
            }
        }
    }

    private void renderSelectionMarks(PoseStack poseStack)
    {
        for(BoardRenderer.Hit hit : boardRenderer.hits())
        {
            for(int index : optionsFor(hit))
            {
                String mark = selected.contains(index) ? "✔"
                    : sortOrder.contains(index) ? Integer.toString(sortOrder.indexOf(index) + 1) : null;
                if(mark != null)
                {
                    fill(poseStack, hit.x(), hit.y(), hit.x() + 10, hit.y() + 10, 0xC0000000);
                    font.draw(poseStack, mark, hit.x() + 2, hit.y() + 1, 0x00FF66);
                }
            }
        }
    }

    private void renderPileView(PoseStack poseStack)
    {
        int cardW = 34;
        int cardH = Math.round(cardW / DuelTextures.CARD_ASPECT);
        int columns = Math.max(1, Math.min(10, (width - SIDEBAR_W - 40) / (cardW + 4)));
        int rows = (pileView.size() + columns - 1) / columns;
        int panelW = columns * (cardW + 4) + 8;
        int panelH = rows * (cardH + 4) + 26;
        int left = SIDEBAR_W + (width - SIDEBAR_W - panelW) / 2;
        int top = Math.max(TOP_BAR_H, (height - panelH) / 2);

        fill(poseStack, left, top, left + panelW, top + panelH, 0xF0100010);
        drawCenteredString(poseStack, font, pileViewLabel + " — click to close",
            left + panelW / 2, top + 6, 0xFFD700);

        for(int i = 0; i < pileView.size(); i++)
        {
            BoardSnapshot.Slot slot = pileView.get(i);
            int x = left + 4 + (i % columns) * (cardW + 4);
            int y = top + 20 + (i / columns) * (cardH + 4);
            Properties card = slot.code() == 0 ? null : DdDatabase.PROPERTIES_LIST.get((long)slot.code());
            ScreenUtil.white();
            CardRenderUtil.bindMainResourceLocation(card == null ? DuelTextures.COVER
                : DuelTextures.card(card, (byte)0, DuelTextures.FIELD_CARD_SIZE));
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
