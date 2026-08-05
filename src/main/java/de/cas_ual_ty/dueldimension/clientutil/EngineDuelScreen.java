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
    /** Log lines kept in the sidebar's log band. */
    private static final int LOG_LINES = 4;
    /** One line for the card name, under the preview. */
    private static final int NAME_H = 10;
    /** The description band never gets squeezed below this. */
    private static final int MIN_DESCRIPTION_H = 30;
    /** However short the window, the preview stays recognisable. */
    private static final int MIN_PREVIEW_H = 40;
    /** The preview card, as a fraction of the sidebar's text column. */
    private static final float PREVIEW_SCALE = 0.7F;
    /** How long the victory or defeat card holds before the world returns. */
    private static final long RESULT_HOLD_MS = 5000;
    /** The play space starts this far below the header. */
    private static final int FIELD_DROP = 14;
    private static final int TOP_BAR_H = 48;
    private static final int MENU_ROW = CardCommands.MENU_ROW_HEIGHT;
    private static final int LOG_W = 150;
    /** The turn badge reads green on your turn, red on theirs. */
    private static final int TURN_YOURS = 0x4CD964;
    private static final int TURN_THEIRS = 0xFF453A;

    private final BoardRenderer boardRenderer = new BoardRenderer();
    /** Shared with the duel, not owned by the screen: see DuelClientState. */
    private final DuelAnimations animations = DuelClientState.animations;

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
    /** Attacker zone noted when Attack is clicked; aims on the next prompt. */
    private int pendingAimZone = -1;
    /** The zone the aiming sword is drawn from, -1 when not aiming. */
    private int aimZone = -1;
    /** Where the aim points, sampled from the mouse six times a second. */
    private float aimX;
    private float aimY;
    private long aimBucket = -1;
    /**
     * The duel history is off for now — see {@link #renderLog}. Kept as a field
     * so the band arithmetic still has something to read.
     */
    private final boolean showHistory = false;
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
        // The saved mat choice, told to the server so the opponent sees it.
        DuelDimension.channel.sendToServer(
            new PromptMessages.SetPlayMat(DuelClientState.selfMat.id()));
        rebuild();
    }

    @Override
    public void tick()
    {
        // The duel is over: show the result, then give the player the world
        // back on its own rather than making them dismiss it.
        if(DuelClientState.over && DuelClientState.overSince > 0
            && System.currentTimeMillis() - DuelClientState.overSince >= RESULT_HOLD_MS)
        {
            DuelClientState.reset();
            onClose();
            return;
        }
        // No gate needed here any more: the prompt is only ever set by a
        // zero-length step in the playback queue, behind every event that
        // preceded it, so by the time it changes the board has caught up.
        if(DuelClientState.prompt != shownPrompt)
        {
            aimZone = pendingAimZone;
            pendingAimZone = -1;
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
        // Surrender and Close live on the deck's own menu. This row picks your
        // playmat, which the other duelist sees on your half of the table.
        addRenderableWidget(new Button(SIDEBAR_PAD, height - 24, SIDEBAR_W - SIDEBAR_PAD * 2, 18,
            Component.literal("Mat: " + DuelClientState.selfMat.displayName()), pressed ->
        {
            DuelClientState.selfMat = DuelClientState.selfMat.next();
            DuelClientState.savePlayMat();
            DuelDimension.channel.sendToServer(
                new PromptMessages.SetPlayMat(DuelClientState.selfMat.id()));
            rebuild();
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
        buildPhaseBar(prompt);
    }

    /** Choices with no card to point at: phases, effect options, yes/no. */
    private void buildBottomStrip(EnginePrompt prompt)
    {
        int y = height - 24;
        int x = SIDEBAR_W + 8;
        for(int i = 0; i < prompt.options().size(); i++)
        {
            EnginePrompt.Option option = prompt.options().get(i);
            if(option.hasSlot() || option.zone() >= 0 || CardCommands.isPhaseAction(option.command()))
            {
                continue; // belongs on the field or on the phase bar
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
            // Bottom right corner, away from the board and the command menu.
            addRenderableWidget(new Button(width - 80, height - 24, 74, 18,
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
    /** One row of a contextual menu: a caption, its icon, and what it does. */
    private record MenuEntry(String label, int command, Runnable action)
    {
    }

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
        EnginePrompt prompt = shownPrompt;
        List<MenuEntry> entries = new ArrayList<>();
        for(int index : actions)
        {
            EnginePrompt.Option option = prompt.options().get(index);
            entries.add(new MenuEntry(option.label(), option.command(), () -> choose(index)));
        }
        showMenu(hit, entries);
    }

    /**
     * The deck's own menu. Surrendering belongs to a duel rather than to any
     * prompt, and a permanent button for it sat in the sidebar taking up room;
     * asking the deck for it matches how every other action on this screen is
     * reached — point at the thing, read what it can do.
     */
    private void openDeckMenu(BoardRenderer.Hit hit)
    {
        if(menuAnchor != null && sameSlot(menuAnchor, hit))
        {
            return;
        }
        closeMenu();
        List<MenuEntry> entries = new ArrayList<>();
        if(DuelClientState.over)
        {
            entries.add(new MenuEntry("Close", 0, () ->
            {
                DuelClientState.reset();
                onClose();
            }));
        }
        else
        {
            entries.add(new MenuEntry("Surrender", 0, () ->
            {
                DuelDimension.channel.sendToServer(new PromptMessages.Surrender());
                closeMenu();
            }));
        }
        showMenu(hit, entries);
    }

    private void showMenu(BoardRenderer.Hit hit, List<MenuEntry> entries)
    {
        if(entries.isEmpty())
        {
            return;
        }
        menuAnchor = hit;

        int widest = 70;
        for(MenuEntry entry : entries)
        {
            widest = Math.max(widest, font.width(entry.label()) + 30);
        }
        // Above the card, centred on it: the card stays visible while you
        // choose what to do with it.
        int menuHeight = (entries.size() + 1) * MENU_ROW; // + the Cancel row
        int menuX = Math.max(SIDEBAR_W + 4,
            Math.min(hit.x() + hit.w() / 2 - widest / 2, width - widest - 4));
        int menuY = hit.y() - menuHeight - 4;
        if(menuY < TOP_BAR_H + PHASE_CELL_H + 8)
        {
            menuY = hit.y() + hit.h() + 4; // no room above: fall below instead
        }

        for(int row = 0; row < entries.size(); row++)
        {
            MenuEntry entry = entries.get(row);
            CommandButton button = new CommandButton(menuX, menuY + row * MENU_ROW, widest, MENU_ROW - 2,
                Component.literal(entry.label()), entry.command(), hit, pressed -> entry.action().run());
            menuButtons.add(button);
            addRenderableWidget(button);
        }
        // Always offer a way out of the menu itself.
        CommandButton close = new CommandButton(menuX, menuY + entries.size() * MENU_ROW, widest,
            MENU_ROW - 2, Component.literal("Cancel"), 0, hit, pressed -> closeMenu());
        menuButtons.add(close);
        addRenderableWidget(close);
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
        EnginePrompt aimPrompt = shownPrompt;
        if(aimPrompt != null && index >= 0 && index < aimPrompt.options().size())
        {
            EnginePrompt.Option option = aimPrompt.options().get(index);
            if(option.command() == CardCommands.COMMAND_ATTACK && option.hasSlot())
            {
                // The next prompt is the target choice: aim from this zone.
                pendingAimZone = de.cas_ual_ty.dueldimension.ocg.prompt.DuelEvent.zoneOf(
                    option.controller(), option.location(), option.sequence(), 0);
            }
        }
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
        aimZone = -1;
        if(answered)
        {
            return;
        }
        answered = true;
        closeMenu();
        DuelClientState.prompt = null;
        DuelDimension.channel.sendToServer(new PromptMessages.AnswerPrompt(chosen, declaredCode,
            DuelClientState.promptSerial));
        rebuild();
    }

    // ---- input ----

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        // Escape backs out of whatever is open, in that order.
        if(keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
        {
            if(pileView != null)
            {
                pileView = null;
                return true;
            }
            if(!menuButtons.isEmpty())
            {
                closeMenu();
                return true;
            }
            EnginePrompt prompt = shownPrompt;
            if(prompt != null && prompt.cancelable() && !answered)
            {
                answer(new int[0], 0);
                return true;
            }
            if(!selected.isEmpty() || !sortOrder.isEmpty())
            {
                selected.clear();
                sortOrder.clear();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        // Right-click closes the command menu, as it does in the reference.
        if(button == 1)
        {
            if(pileView != null)
            {
                pileView = null;
                return true;
            }
            if(!menuButtons.isEmpty())
            {
                closeMenu();
                return true;
            }
            // Nothing left to back out of, so right-click answers the prompt
            // itself -- the same order Escape backs out in.
            EnginePrompt prompt = shownPrompt;
            if(prompt != null && prompt.cancelable() && !answered)
            {
                answer(new int[0], 0);
                return true;
            }
            if(!selected.isEmpty() || !sortOrder.isEmpty())
            {
                selected.clear();
                sortOrder.clear();
                return true;
            }
        }
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
                if(actions.isEmpty() && hit.controller() == 0
                    && hit.location() == OcgConstants.LOCATION_DECK)
                {
                    openDeckMenu(hit);
                    return true;
                }
                if(hit.isPile() && actions.isEmpty())
                {
                    if(hit.count() > 0)
                    {
                        openPile(hit);
                        return true;
                    }
                    continue;
                }
                if(actions.size() == 1 && !isCardCommand(actions.get(0)))
                {
                    // Selecting a card for a prompt ("pick a target") stays one
                    // click: there is nothing to choose between.
                    choose(actions.get(0));
                    return true;
                }
                if(!actions.isEmpty())
                {
                    // Anything the card can *do* goes through the menu, even
                    // when there is only one of them. Firing it on click meant
                    // touching a monster silently changed its battle position,
                    // and the reference always opens ShowMenu for a card's
                    // commands rather than acting on the click itself.
                    openMenu(hit);
                    return true;
                }
            }
            closeMenu();
        }
        return false;
    }

    /**
     * True if this option is one of the card's own commands (summon, set,
     * activate, reposition, attack...) rather than a prompt selection. Card
     * commands always deserve a button to press; selections do not.
     */
    private boolean isCardCommand(int index)
    {
        EnginePrompt prompt = shownPrompt;
        return prompt != null && index >= 0 && index < prompt.options().size()
            && prompt.options().get(index).command() != 0;
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

    /**
     * The board the screen draws: always the paced one, advanced only as its
     * events play out.
     * <p>
     * The prompt also carries a settled snapshot ({@code prompt.field()},
     * captured server-side when the core asked its question) and this method
     * used to prefer it. That was the bypass behind every "cards appear before
     * their animation" report: the moment any prompt existed — which on your
     * turn is always — the screen drew the end-state directly and the entire
     * hold-the-board-behind-its-events pipeline was ignored. EDOPro has no
     * equivalent snapshot to leak: its select messages are handled in the same
     * stream as everything else (duelclient.cpp:1702/1778/1976), over card
     * state that its own animations own.
     * <p>
     * The prompt's field is still used as a seed before the first update
     * lands, so the very first question of a duel is not asked over an empty
     * table.
     */
    private BoardSnapshot currentBoard()
    {
        if(DuelClientState.board != BoardSnapshot.EMPTY)
        {
            return DuelClientState.board;
        }
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
        boardRenderer.setMats(DuelClientState.selfMat, DuelClientState.opponentMat);
        boardRenderer.setActionable(hit -> !optionsFor(hit).isEmpty());
        boardRenderer.setCanAttack(hit -> optionsFor(hit).stream().anyMatch(index ->
            prompt != null && prompt.options().get(index).command() == CardCommands.COMMAND_ATTACK));
        // A card the core will let you activate right now, which during a chain
        // window is exactly the set of responses available to you.
        boardRenderer.setCanActivate(hit -> optionsFor(hit).stream().anyMatch(index ->
            prompt != null && prompt.options().get(index).command() == CardCommands.COMMAND_ACTIVATE));

        // EDOPro's frustum is off-centre by design (M[8] = 1/3) so the table
        // sits right of screen centre and leaves room for the card-info column.
        // FieldLayout.fit already cancels that bias and centres the table in
        // whatever box it is handed, so the box is the space actually left over:
        // between the card preview and the right window edge. Handing it the
        // whole window instead centres the table behind the sidebar, which is
        // what made the mat look shoved off to one side.
        int fieldLeft = SIDEBAR_W;
        // The table sits a little below the header. Only the play space moves:
        // the life bars, turn badge and phase row keep their own positions.
        int fieldTop = TOP_BAR_H + FIELD_DROP;
        int fieldWidth = width - SIDEBAR_W;
        // Down to the last few pixels: the wide margin here left a dead band
        // between the hand and the bottom edge and crushed the board above it.
        int fieldHeight = height - fieldTop - 6;
        boardRenderer.render(poseStack, font, board, fieldLeft, fieldTop, fieldWidth, fieldHeight, highlights);

        // Playback is advanced on the client tick, not here, so it keeps its
        // pace even while this screen is closed. Rendering only draws it.
        long now = System.currentTimeMillis();
        animations.renderMoves(poseStack, boardRenderer, boardRenderer.projection(), now);
        animations.renderAttacks(poseStack, boardRenderer.projection(), now);
        animations.renderOverlays(poseStack, boardRenderer.projection(), now);
        animations.renderShatters(poseStack, boardRenderer.projection(), now);

        // Aiming: after clicking Attack, the sword tracks the mouse until the
        // target is chosen -- sampled six times a second, so it snaps rather
        // than glides.
        if(aimZone >= 0 && shownPrompt != null && !answered && isTargetSelection(shownPrompt))
        {
            long bucket = now / 167;
            if(bucket != aimBucket)
            {
                aimBucket = bucket;
                aimX = mouseX;
                aimY = mouseY;
            }
            animations.renderAim(poseStack, boardRenderer.projection(), aimZone, aimX, aimY);
        }

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
        // EDOPro opens the command menu on click, not on hover; hovering only
        // drives the card preview.

        renderTopBar(poseStack, board);
        renderPhaseBar(poseStack, board);
        renderHintBanner(poseStack, prompt);
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
        renderResult(poseStack);

        if(pileView != null)
        {
            renderPileView(poseStack, mouseX, mouseY);
        }
        if(hovered != null && hovered.isPile())
        {
            renderTooltip(poseStack, Component.literal(hovered.label()), mouseX, mouseY);
        }
    }

    /** The six phases EDOPro lists, in order. */
    private static final String[] PHASE_NAMES = {"DP", "SP", "M1", "BP", "M2", "EP"};
    private static final int[] PHASE_VALUES = {OcgConstants.PHASE_DRAW, OcgConstants.PHASE_STANDBY,
        OcgConstants.PHASE_MAIN1, OcgConstants.PHASE_BATTLE, OcgConstants.PHASE_MAIN2, OcgConstants.PHASE_END};

    /**
     * EDOPro's phase row (wPhase): every phase is listed, the current one is
     * marked, and a phase you may jump to is a live button - which is exactly
     * the idle/battle command the core offered.
     */
    /**
     * The phase row, in the reference's own proportions. game.cpp:314 builds
     * {@code wPhase} at {@code Scale(480, 310, 855, 330)} -- 375 by 20 against
     * its 1024-wide base -- holding buttons of {@code Scale(0, 0, 50, 20)}. So
     * a phase button is 50 wide and 20 tall, and the row spans 375. Ours was
     * 26 by 12, both too small and the wrong shape (2.17:1 against 2.5:1).
     */
    private static final int PHASE_CELL_W = 50;
    private static final int PHASE_CELL_H = 20;
    /** The reference's row width, for the gap either side of the buttons. */
    private static final int PHASE_BAR_W = 375;
    /**
     * The phase row sits below the life bars, tucked up under the turn badge:
     * the "your turn" label that used to occupy this space is gone (the badge's
     * colour says whose turn it is), so the bar can sit higher.
     */
    private static final int PHASE_BAR_Y = 26;

    private int phaseBarLeft()
    {
        int barW = PHASE_NAMES.length * PHASE_CELL_W;
        float centre = boardRenderer.tableCentreX();
        if(centre <= SIDEBAR_W + barW / 2F || centre >= width - barW / 2F)
        {
            centre = (SIDEBAR_W + width) / 2F;
        }
        return Math.round(centre) - barW / 2;
    }

    private void buildPhaseBar(EnginePrompt prompt)
    {
        int x = phaseBarLeft();
        for(int i = 0; i < PHASE_NAMES.length; i++)
        {
            int option = phaseOptionFor(prompt, PHASE_VALUES[i]);
            if(option < 0)
            {
                continue; // not reachable: drawn as a label instead
            }
            int index = option;
            addRenderableWidget(new SlimPhaseButton(x + i * PHASE_CELL_W, PHASE_BAR_Y,
                PHASE_CELL_W - 1, PHASE_CELL_H, Component.literal(PHASE_NAMES[i]),
                pressed -> choose(index)));
        }
    }

    /** A menu entry: the action's icon, then its caption. */
    private class CommandButton extends Button
    {
        private final int command;
        private final BoardRenderer.Hit anchor;

        CommandButton(int x, int y, int w, int h, Component label, int command,
            BoardRenderer.Hit anchor, OnPress onPress)
        {
            super(x, y, w, h, label, onPress);
            this.command = command;
            this.anchor = anchor;
        }

        @Override
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
        {
            boolean hovered = isHoveredOrFocused();
            fill(poseStack, x, y, x + width, y + height, hovered ? 0xF0473A22 : 0xE01A1A1E);
            fill(poseStack, x, y, x + width, y + 1, hovered ? 0xFFFFD700 : 0x60FFD700);

            BoardSnapshot board = currentBoard();
            boolean faceDown = false;
            boolean attackPosition = true;
            if(anchor != null && !anchor.isPile())
            {
                BoardSnapshot.Side side = anchor.controller() == 0 ? board.self() : board.opponent();
                List<BoardSnapshot.Slot> zone = anchor.location() == OcgConstants.LOCATION_MZONE
                    ? side.monsters() : side.spells();
                if(anchor.sequence() >= 0 && anchor.sequence() < zone.size())
                {
                    BoardSnapshot.Slot slot = zone.get(anchor.sequence());
                    faceDown = slot.faceDown();
                    attackPosition = !slot.defence();
                }
            }

            var icon = DuelTextures.commandIcon(command, faceDown, attackPosition);
            int textX = x + 5;
            if(icon != null)
            {
                ScreenUtil.white();
                CardRenderUtil.bindMainResourceLocation(icon);
                DdBlitUtil.fullBlit(poseStack, x + 3, y + 2, 15, 15);
                textX = x + 21;
            }
            font.draw(poseStack, getMessage(), textX, y + (height - 8) / 2F,
                hovered ? 0xFFFFCC : 0xE8E8E8);
        }
    }

    /** A flat, compact button so the phase row reads as one strip. */
    private class SlimPhaseButton extends Button
    {
        SlimPhaseButton(int x, int y, int w, int h, Component label, OnPress onPress)
        {
            super(x, y, w, h, label, onPress);
        }

        @Override
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
        {
            // The reference's phase buttons are not textures: game.cpp:316
            // builds them with env->addButton, so Irrlicht's GUI skin draws
            // them -- a flat face with a one-pixel bevel, nothing of
            // Minecraft's stone-slab button art. Reproduced here by hand.
            boolean hovered = isHoveredOrFocused();
            drawPhaseCell(poseStack, x, y, width, height, hovered, false);
            drawCenteredString(poseStack, font, getMessage(), x + width / 2,
                y + (height - 8) / 2, hovered ? 0xFFE066 : 0xE8E8E8);
        }
    }

    /** The option that moves to this phase, or -1. */
    private int phaseOptionFor(EnginePrompt prompt, int phase)
    {
        if(prompt == null)
        {
            return -1;
        }
        int wanted = switch(phase)
        {
            case OcgConstants.PHASE_BATTLE -> CardCommands.PHASE_TO_BATTLE;
            case OcgConstants.PHASE_MAIN2 -> CardCommands.PHASE_TO_MAIN2;
            case OcgConstants.PHASE_END -> CardCommands.PHASE_END_TURN;
            default -> 0;
        };
        if(wanted == 0)
        {
            return -1;
        }
        for(int i = 0; i < prompt.options().size(); i++)
        {
            if(prompt.options().get(i).command() == wanted)
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * The instruction banner, EDOPro's {@code stHintMsg}.
     * <p>
     * The reference sets this text and shows it for every prompt it puts up —
     * ten separate sites in duelclient.cpp, one per selection message — so the
     * player is always told what the core is asking for. Our prompts carried
     * the same text in {@link EnginePrompt#title()} from the beginning and
     * nothing ever drew it, which is the bulk of the missing feedback: an
     * effect would ask for a target and the screen said nothing at all.
     * <p>
     * Selection prompts also show progress, since a prompt wanting two cards
     * looks identical to one wanting one until you know how many you have.
     */
    private void renderHintBanner(PoseStack poseStack, EnginePrompt prompt)
    {
        if(prompt == null || answered || prompt.title() == null || prompt.title().isBlank())
        {
            return;
        }
        String text = prompt.title();
        if(prompt.maxSelect() > 1)
        {
            text = text + "  (" + selected.size() + "/" + prompt.maxSelect() + ")";
        }

        int textWidth = font.width(text);
        int centre = Math.round(boardRenderer.tableCentreX());
        int left = Math.max(SIDEBAR_W + 6, centre - textWidth / 2 - 6);
        int right = Math.min(width - 6, left + textWidth + 12);
        int top = TOP_BAR_H + 2;

        fill(poseStack, left, top, right, top + 14, 0xD0101014);
        fill(poseStack, left, top, right, top + 1, 0x80FFD700);
        fill(poseStack, left, top + 13, right, top + 14, 0x80FFD700);
        drawCenteredString(poseStack, font, text, (left + right) / 2, top + 3, 0xFFE066);
    }

    /**
     * The victory or defeat card. Fills the screen over the settled board for
     * {@link #RESULT_HOLD_MS}, then tick() closes the duel.
     */
    private void renderResult(PoseStack poseStack)
    {
        if(!DuelClientState.over)
        {
            return;
        }
        fill(poseStack, 0, 0, width, height, 0xC0000000);

        boolean won = DuelClientState.won;
        String headline = won ? "VICTORY" : "DEFEAT";
        int colour = won ? 0xFFD700 : 0xFF4C4C;
        int bandTop = height / 2 - 30;

        fill(poseStack, 0, bandTop, width, bandTop + 60, 0xB0101014);
        fill(poseStack, 0, bandTop, width, bandTop + 1, 0xC0000000 | colour);
        fill(poseStack, 0, bandTop + 59, width, bandTop + 60, 0xC0000000 | colour);

        poseStack.pushPose();
        poseStack.scale(3F, 3F, 1F);
        drawCenteredString(poseStack, font, headline, Math.round(width / 2F / 3F),
            Math.round((bandTop + 12) / 3F), colour);
        poseStack.popPose();

        if(DuelClientState.result != null && !DuelClientState.result.isBlank())
        {
            drawCenteredString(poseStack, font, DuelClientState.result, width / 2, bandTop + 44, 0xB0B0B0);
        }

        long left = Math.max(0, RESULT_HOLD_MS
            - (System.currentTimeMillis() - DuelClientState.overSince));
        drawCenteredString(poseStack, font, "Returning in " + (left / 1000 + 1) + "...",
            width / 2, bandTop + 70, 0x7A7A7A);
    }

    /**
     * One cell of the phase row, in the reference's skin-button style: a flat
     * dark face with a one-pixel bevel, pressed-in for the current phase.
     */
    private void drawPhaseCell(PoseStack poseStack, int x, int y, int w, int h,
        boolean hovered, boolean pressed)
    {
        int face = pressed ? 0xFF17171B : hovered ? 0xFF35353C : 0xFF26262C;
        int light = 0xFF5C5C66;
        int dark = 0xFF0E0E12;
        fill(poseStack, x, y, x + w, y + h, face);
        // Bevel: light top and left, dark bottom and right; inverted when
        // pressed, which is how a flat skin shows the active state.
        fill(poseStack, x, y, x + w, y + 1, pressed ? dark : light);
        fill(poseStack, x, y, x + 1, y + h, pressed ? dark : light);
        fill(poseStack, x, y + h - 1, x + w, y + h, pressed ? light : dark);
        fill(poseStack, x + w - 1, y, x + w, y + h, pressed ? light : dark);
    }

    /** Draws the phase row's labels and marks the current phase. */
    private void renderPhaseBar(PoseStack poseStack, BoardSnapshot board)
    {
        int x = phaseBarLeft();
        for(int i = 0; i < PHASE_NAMES.length; i++)
        {
            boolean current = board.phase() == PHASE_VALUES[i]
                || (PHASE_VALUES[i] == OcgConstants.PHASE_BATTLE && board.phase() > OcgConstants.PHASE_MAIN1
                    && board.phase() < OcgConstants.PHASE_MAIN2);
            int cellX = x + i * PHASE_CELL_W;

            if(phaseOptionFor(shownPrompt, PHASE_VALUES[i]) < 0)
            if(phaseOptionFor(shownPrompt, PHASE_VALUES[i]) < 0)
            {
                // Not reachable: no button exists, so this cell is a label.
                drawPhaseCell(poseStack, cellX, PHASE_BAR_Y, PHASE_CELL_W - 1, PHASE_CELL_H,
                    false, current);
                drawCenteredString(poseStack, font, PHASE_NAMES[i],
                    cellX + (PHASE_CELL_W - 1) / 2, PHASE_BAR_Y + (PHASE_CELL_H - 8) / 2,
                    current ? 0xFFE066 : 0x6A6A6A);
            }
            if(current)
            {
                // A gold underline marks where the duel actually is.
                fill(poseStack, cellX, PHASE_BAR_Y + PHASE_CELL_H - 1, cellX + PHASE_CELL_W - 1,
                    PHASE_BAR_Y + PHASE_CELL_H, 0xFFFFD700);
            }
        }
    }

    /**
     * Life points across the top with the turn count between them, as the
     * reference client shows it. Centred on the table rather than the window:
     * the frustum is off-centre by design, so window-centred headers sit
     * visibly left of the board.
     */
    /**
     * Life displays as corner plates, arranged the way Master Duel arranges
     * them -- your plate at the lower left of the board, the opponent's at the
     * upper right -- but drawn entirely by hand here; nothing is copied from
     * that game. Each plate carries the duelist's name, an LP readout with the
     * number at display size, and a thin accent line that depletes with their
     * life. The turn badge stays centred above the field.
     */
    private void renderTopBar(PoseStack poseStack, BoardSnapshot board)
    {
        long now = System.currentTimeMillis();
        boolean yourTurn = board.turnPlayer() == 0;

        drawLifePlate(poseStack, SIDEBAR_W + 8, height - PLATE_H - 6, "You",
            board.self().lifePoints(), 0xFF3FA34D, animations.damageFlash(0, now), yourTurn, now);
        drawLifePlate(poseStack, width - PLATE_W - 8, 4, "Opponent",
            board.opponent().lifePoints(), 0xFFC1362F, animations.damageFlash(1, now), !yourTurn, now);

        // Whose turn it is, said with colour instead of words.
        int badgeW = 34;
        int badgeLeft = Math.round(boardRenderer.tableCentreX()) - badgeW / 2;
        int badgeTop = 4;
        int turnColour = yourTurn ? TURN_YOURS : TURN_THEIRS;
        fill(poseStack, badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + 17, 0xC0101014);
        fill(poseStack, badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + 1, 0xC0000000 | turnColour);
        fill(poseStack, badgeLeft, badgeTop + 16, badgeLeft + badgeW, badgeTop + 17, 0xC0000000 | turnColour);
        drawCenteredString(poseStack, font, Integer.toString(Math.max(1, board.turn())),
            badgeLeft + badgeW / 2, badgeTop + 5, turnColour);
    }

    /**
     * True only for a prompt that asks the player to pick a card on the board
     * -- slot options with no command. The aim pointer must not outlive its
     * question: a direct attack produces no target selection at all, and the
     * sword used to linger over whatever prompt came next.
     */
    private static boolean isTargetSelection(EnginePrompt prompt)
    {
        boolean anySlot = false;
        for(EnginePrompt.Option option : prompt.options())
        {
            if(option.command() != 0)
            {
                return false;
            }
            anySlot |= option.hasSlot();
        }
        return anySlot;
    }

    private static final int PLATE_W = 118;
    private static final int PLATE_H = 34;

    private void drawLifePlate(PoseStack poseStack, int x, int y, String name, int lifePoints,
        int accent, float flash, boolean active, long now)
    {
        if(active)
        {
            // The active duelist's plate breathes, softly and feathered.
            float pulse = 0.18F + 0.10F * (float)Math.sin(now / 420D);
            float[] spread = {1.5F, 3F, 4.5F};
            float[] fade = {1F, 0.55F, 0.28F};
            for(int shell = 0; shell < spread.length; shell++)
            {
                int e = Math.round(spread[shell]);
                int alpha = Math.round(pulse * fade[shell] * 255) << 24;
                fill(poseStack, x - e, y - e, x + PLATE_W + e, y + PLATE_H + e, alpha | 0xFFFFFF);
            }
        }

        fill(poseStack, x, y, x + PLATE_W, y + PLATE_H, 0xE60F1216);
        // The accent runs down the plate's outer edge and under its name.
        fill(poseStack, x, y, x + 2, y + PLATE_H, 0xFF000000 | (accent & 0xFFFFFF));
        fill(poseStack, x + 4, y + 12, x + PLATE_W - 4, y + 13, 0x50FFFFFF);

        font.draw(poseStack, name, x + 6, y + 3, 0xE8E8E8);

        // "LP" small, the number at display size, as the reference layout has it.
        font.draw(poseStack, "LP", x + 6, y + 19, 0x9AA0A8);
        String value = Integer.toString(Math.max(0, lifePoints));
        poseStack.pushPose();
        poseStack.scale(1.5F, 1.5F, 1F);
        font.draw(poseStack, value,
            (x + PLATE_W - 6 - font.width(value) * 1.5F) / 1.5F, (y + 15) / 1.5F, 0xFFFFFF);
        poseStack.popPose();

        // The depletion line: full at 8000, gone at 0.
        int lineW = Math.max(0, Math.min(PLATE_W - 8, Math.round((PLATE_W - 8) * lifePoints / 8000F)));
        fill(poseStack, x + 4, y + PLATE_H - 3, x + 4 + PLATE_W - 8, y + PLATE_H - 2, 0xFF23262B);
        fill(poseStack, x + 4, y + PLATE_H - 3, x + 4 + lineW, y + PLATE_H - 2,
            0xFF000000 | (accent & 0xFFFFFF));

        if(flash > 0)
        {
            int alpha = (int)(flash * 150) << 24;
            fill(poseStack, x, y, x + PLATE_W, y + PLATE_H, alpha | 0xFFFFFF);
        }
    }


    /**
     * The sidebar is split into fixed bands so its sections cannot collide:
     * card image and name, then the card's text, then the log, then the two
     * buttons. Previously the card text ran to {@code height - 50} while the
     * log drew upwards from {@code height - 52}, so on a short window the log
     * printed straight over the card description.
     */
    private int footerTop()
    {
        return height - 48;
    }

    /**
     * How many log lines fit. The log gives way first: on a short window it is
     * better to see fewer log lines than to lose the card text entirely.
     */
    private int logLines()
    {
        if(!showHistory)
        {
            return 0;
        }
        int quarter = (footerTop() - SIDEBAR_PAD) / 4;
        return Math.max(0, Math.min(LOG_LINES, (quarter - 12) / 9));
    }

    /** Top of the log band; the card description must stop above this. */
    private int logTop()
    {
        int lines = logLines();
        return lines == 0 ? footerTop() : footerTop() - (lines * 9 + 12);
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

        // The preview is capped so the name, description and log always fit.
        // At full sidebar width a card is 175px tall, which is most of a short
        // window on a high GUI scale — that is what pushed the description off
        // the bottom and left the log printing over the card and its name.
        int textWidth = SIDEBAR_W - SIDEBAR_PAD * 2;
        // 70% of the column: the preview only has to be recognisable, and the
        // height it gives back goes to the description below it.
        int imageW = Math.round(textWidth * PREVIEW_SCALE);
        int imageH = Math.round(imageW / DuelTextures.CARD_ASPECT);
        int maxImageH = logTop() - SIDEBAR_PAD - NAME_H - MIN_DESCRIPTION_H;
        if(imageH > maxImageH)
        {
            imageH = Math.max(MIN_PREVIEW_H, maxImageH);
            imageW = Math.round(imageH * DuelTextures.CARD_ASPECT);
        }
        int imageX = (SIDEBAR_W - imageW) / 2;

        ScreenUtil.white();
        DuelTextures.bindSmooth(
            DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE));
        // Sample the card out of its letterboxed square, or it stretches.
        DdBlitUtil.blit(poseStack, imageX, SIDEBAR_PAD, imageW, imageH,
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1 - DuelTextures.CARD_U0, DuelTextures.CARD_V1 - DuelTextures.CARD_V0,
            1, 1);

        // The name sits on its own plate under the art, the type line on an
        // accent chip, and the effect text in a bordered well -- the reference
        // layout's structure, drawn from scratch.
        int y = SIDEBAR_PAD + imageH + 4;
        List<net.minecraft.util.FormattedCharSequence> nameLines =
            font.split(Component.literal(card.getName()), textWidth - 4);
        int plateH = nameLines.size() * 9 + 4;
        fill(poseStack, SIDEBAR_PAD - 2, y - 2, SIDEBAR_W - SIDEBAR_PAD + 2, y + plateH - 2, 0xFF14161A);
        fill(poseStack, SIDEBAR_PAD - 2, y + plateH - 2, SIDEBAR_W - SIDEBAR_PAD + 2, y + plateH - 1,
            0xFFB08A2A);
        for(var line : nameLines)
        {
            font.draw(poseStack, line, SIDEBAR_PAD + 2, y, 0xFFD700);
            y += 9;
        }
        y += 4;

        int descriptionBottom = logTop() - 4;

        List<Component> header = new ArrayList<>();
        card.addHeader(header);
        // addHeader leads with the card's name, which is already drawn above
        // in gold; keeping it printed the name twice.
        if(!header.isEmpty())
        {
            header.remove(0);
        }
        poseStack.pushPose();
        poseStack.scale(0.75F, 0.75F, 1F);
        int scaledX = Math.round(SIDEBAR_PAD / 0.75F);
        int scaledY = Math.round(y / 0.75F) + 2;
        int scaledW = Math.round(imageW / 0.75F);
        int limit = Math.round(descriptionBottom / 0.75F);

        boolean typeChip = true;
        for(Component component : header)
        {
            for(var line : font.split(component, scaledW - 8))
            {
                if(scaledY > limit)
                {
                    break;
                }
                if(typeChip)
                {
                    // The [Type / Race] row reads as a chip, like the bracket
                    // bar of the reference layout.
                    fill(poseStack, scaledX - 2, scaledY - 2, scaledX + scaledW + 2, scaledY + 8,
                        0xFF1B222B);
                    font.draw(poseStack, line, scaledX + 2, scaledY, 0xFFC864);
                }
                else
                {
                    font.draw(poseStack, line, scaledX + 2, scaledY, 0xB0B0B0);
                }
                scaledY += 10;
            }
            typeChip = false;
        }
        scaledY += 2;
        // The effect text in its own well.
        int wellTop = scaledY - 3;
        fill(poseStack, scaledX - 2, wellTop, scaledX + scaledW + 2, limit + 3, 0xC0101318);
        fill(poseStack, scaledX - 2, wellTop, scaledX + scaledW + 2, wellTop + 1, 0x33FFFFFF);
        for(var line : font.split(Component.literal(card.getText()), scaledW - 8))
        {
            if(scaledY > limit)
            {
                break;
            }
            font.draw(poseStack, line, scaledX + 2, scaledY, 0xA8AeB4);
            scaledY += 8;
        }
        poseStack.popPose();
    }

    /**
     * The duel log lives in the sidebar, where EDOPro keeps its Log tab.
     * Drawing it over the field put text across the cards.
     */
    private void renderLog(PoseStack poseStack)
    {
        // The duel history is switched off for now. Everything below is left
        // intact, and logLines()/logTop() already collapse the band to nothing,
        // so turning it back on is a matter of restoring the toggle button.
        if(true)
        {
            return;
        }
        int x = SIDEBAR_PAD;
        int top = logTop();
        fill(poseStack, SIDEBAR_PAD, top, SIDEBAR_W - SIDEBAR_PAD, top + 1, 0x40FFFFFF);

        // Newest last, so the log reads downwards and stays inside its band.
        List<String> lines = new ArrayList<>();
        synchronized(DuelClientState.class)
        {
            var iterator = DuelClientState.log.descendingIterator();
            while(iterator.hasNext() && lines.size() < LOG_LINES)
            {
                String line = iterator.next();
                while(font.width(line) > SIDEBAR_W - SIDEBAR_PAD * 2 && line.length() > 4)
                {
                    line = line.substring(0, line.length() - 2);
                }
                lines.add(0, line);
            }
        }
        int y = top + 4;
        for(String line : lines)
        {
            font.draw(poseStack, line, x, y, 0x8A8A8A);
            y += 9;
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

    private void renderPileView(PoseStack poseStack, int mouseX, int mouseY)
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
            // Pointing at a card in the pile reads it in the sidebar, the same
            // as pointing at one on the field.
            if(slot.code() != 0 && mouseX >= x && mouseX < x + cardW + 4
                && mouseY >= y && mouseY < y + cardH + 4)
            {
                previewCode = slot.code();
                DuelTextures.bindSmooth(DuelTextures.SLOT_ACTIVE);
                DdBlitUtil.fullBlit(poseStack, x - 1, y - 1, cardW + 2, cardH + 2);
            }
            ScreenUtil.white();
            if(card == null)
            {
                DuelTextures.bindSmooth(DuelTextures.COVER);
                DdBlitUtil.fullBlit(poseStack, x, y, cardW, cardH);
            }
            else
            {
                DuelTextures.bindSmooth(
                    DuelTextures.card(card, (byte)0, DuelTextures.FIELD_CARD_SIZE));
                DdBlitUtil.blit(poseStack, x, y, cardW, cardH,
                    DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                    DuelTextures.CARD_U1 - DuelTextures.CARD_U0,
                    DuelTextures.CARD_V1 - DuelTextures.CARD_V0, 1, 1);
            }
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

    /**
     * Escape backs out of menus and cancelable prompts (handled in
     * {@link #keyPressed}) but does not tear down the duel.
     * <p>
     * This used to return {@code shownPrompt == null}, so pressing Escape while
     * waiting on the opponent closed the screen outright. Since only a prompt
     * reopened it, the opponent's attacks and summons then played out with no
     * screen to animate them — the sequence looked like it had been cut off by
     * the cancel key. The duel is left with the explicit Close button, which
     * only appears once it is over.
     */
    @Override
    public boolean shouldCloseOnEsc()
    {
        return DuelClientState.over;
    }
}
