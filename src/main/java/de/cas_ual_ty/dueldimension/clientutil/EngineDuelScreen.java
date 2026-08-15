package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.cas_ual_ty.dueldimension.DdDatabase;
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
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
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
 * <p>
 * <b>The board is drawn through {@link BoardPip}, everything else is not.</b>
 * A perspective field is trapezoids and a GUI can only blit axis-aligned
 * rectangles, so the play space goes through a picture-in-picture region where
 * a real {@code PoseStack} and {@code SubmitNodeCollector} exist and
 * {@link FieldQuad} works. This screen is the only place in the board's draw
 * chain that still holds a {@link GuiGraphicsExtractor}, and it uses it for two
 * things: to open that region, and to draw the flat furniture around it — life
 * bars, phase row, sidebar, picker, menus and tooltips, none of which ever
 * needed arbitrary geometry.
 */
public class EngineDuelScreen extends Screen
{
    private static final int SIDEBAR_W = 132;

    /**
     * Dims the board and leaves the card panel alone.
     * <p>
     * Everything that dims this screen dims it because something MODAL has
     * opened over the duel -- and the reason a card is being chosen is almost
     * always written in the panel on the left. Greying out the description at
     * the moment it is being read is greying out the answer to the question
     * being asked: the picker in the middle names seven cards, and which of
     * them is the right one is decided by text that had just been dimmed to
     * make the picker stand out.
     * <p>
     * The panel is a full-height column at the left edge, so this is one
     * rectangle rather than four: start where it ends.
     */
    private void dimBoard(net.minecraft.client.gui.GuiGraphicsExtractor poseStack, int colour)
    {
        poseStack.fill(SIDEBAR_W, 0, width, height, colour);
    }
    private static final int SIDEBAR_PAD = 6;

    /** Width the mute button takes out of the chain button's row. */
    private static final int MUSIC_BUTTON = 22;
    /** Log lines kept in the sidebar's log band. */
    private static final int LOG_LINES = 4;
    /** One line for the card name, under the preview. */
    private static final int NAME_H = 10;
    /** The description band never gets squeezed below this. */
    private static final int MIN_DESCRIPTION_H = 30;
    /** Line pitch of the effect text, inside the sidebar's 0.75 scale. */
    private static final int DESCRIPTION_LINE_H = 8;
    /** Lines the wheel moves the effect text by, in those same units. */
    private static final int DESCRIPTION_SCROLL_STEP = DESCRIPTION_LINE_H * 2;
    /** Width of the description's scroll bar, inside the sidebar's 0.75 scale. */
    private static final int BAR_W = 4;
    /** Slack either side of the bar, so catching it does not need pixel aim. */
    private static final int BAR_GRAB = 3;
    /** However short the window, the preview stays recognisable. */
    private static final int MIN_PREVIEW_H = 40;
    /** The preview card, as a fraction of the sidebar's text column. */
    private static final float PREVIEW_SCALE = 0.7F;
    /** How long the victory or defeat card holds before the world returns. */
    private static final long RESULT_HOLD_MS = 5000;
    /** Let the outcome land before replacing it with the earnings table. */
    private static final long RESULT_STINGER_MS = 1800;
    /** The play space starts this far below the header. */
    private static final int FIELD_DROP = 14;
    private static final int TOP_BAR_H = 48;
    /**
     * Menu row height. ShowMenu uses Scale(21) in the reference
     * (CardCommands.MENU_ROW_HEIGHT keeps that number on record), but at this
     * screen's size those rows sit heavily over the board, so the menu is
     * drawn slimmer.
     */
    private static final int MENU_ROW = 15;
    private static final int LOG_W = 150;
    /** The turn badge reads green on your turn, red on theirs. */
    private static final int TURN_YOURS = 0x4CD964;
    private static final int TURN_THEIRS = 0xFF453A;

    private final BoardRenderer boardRenderer = new BoardRenderer();
    /** Shared with the duel, not owned by the screen: see DuelClientState. */
    private final DuelAnimations animations = DuelClientState.animations;

    /**
     * The running answer lives in {@link DuelSelection}, not here.
     * <p>
     * It is one duel however it is being looked at, so a player part way
     * through choosing three tributes who switches to the board -- or is put
     * there by the camera key -- should find the same two already picked. Two
     * sets meant the second view started over, and the difference was invisible
     * to whoever it happened to.
     * <p>
     * Sort order stays local: only this screen can sort.
     */
    private final List<Integer> sortOrder = new ArrayList<>();
    private int[] counterAmounts = new int[0];

    private EnginePrompt shownPrompt;

    /**
     * The race the duel currently gives the previewed card, or 0.
     * <p>
     * Kept beside {@link #previewCode} rather than derived from it, because a
     * passcode names a CARD and this is a fact about one copy of it standing in
     * one square.
     */
    private long previewRace;

    /**
     * Option indices the card picker is showing, or null when it is closed.
     * <p>
     * Some choices are between cards the player cannot see. Monster Reborn asks
     * for a target in a graveyard, and a graveyard is drawn as a stack — there
     * is nothing on the board to point at, so the choice used to arrive as a
     * list of names in the pile's context menu. This shows the cards.
     */
    private List<Integer> picker;
    private int pickerScroll;
    private EditBox searchBox;
    private final List<Integer> searchResults = new ArrayList<>();
    private final List<Button> searchButtons = new ArrayList<>();

    private int previewCode;
    /**
     * The artwork of the copy being previewed.
     * <p>
     * Carried beside the code rather than looked up from it: which artwork a
     * copy wears is a property of that physical card, and the code alone cannot
     * say which of a deck's three Dark Magicians is being pointed at.
     */
    private byte previewArt;
    /**
     * The options behind one command chosen from a pile, or null.
     * <p>
     * A pile is a stack of face-down cards, so "Special Summon" off the Extra
     * Deck is a verb with no visible subject. When one is picked, these are the
     * cards it could mean, and the picker shows them until one is chosen.
     */
    private List<Integer> pileChoices;
    /** What was picked to get here — "Special Summon" — for the picker's header. */
    private String pileChoicesLabel;
    /** How far the sidebar's effect text is scrolled, in text pixels. */
    private int descriptionScroll;
    /** How far it can scroll. Zero when the text already fits. */
    private int descriptionMaxScroll;
    /** The card {@link #descriptionScroll} belongs to, so a new card starts at the top. */
    private int scrolledCode;
    /** The effect-text well in GUI coordinates, for the wheel to hit-test against. */
    private int descriptionX0;
    private int descriptionY0;
    private int descriptionX1;
    private int descriptionY1;
    /** The scroll bar's track, in GUI coordinates, so it can be clicked. */
    private int barX0;
    private int barY0;
    private int barX1;
    private int barY1;
    /** Height of the thumb, and where in it the drag was taken hold of. */
    private int barThumbH;
    private int barGrabOffset = -1;
    private BoardRenderer.Hit menuAnchor;
    private final List<Button> menuButtons = new ArrayList<>();
    private List<BoardSnapshot.Slot> pileView;
    private String pileViewLabel = "";
    /**
     * How many rows of the open pile are scrolled off the top.
     * <p>
     * A graveyard never needed this — it is a handful of cards and the panel
     * grew to fit. A deck is forty to sixty, which at this tile size is seven
     * rows and runs off the bottom of the screen, so the panel is now capped to
     * what fits and the rest is scrolled to.
     */
    private int pileViewScroll;
    /** How far it can scroll; written by the painter, read by the wheel. */
    private int pileViewMaxScroll;
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
        // The saved mat colour, told to the server so the opponent sees it.
        ClientPlayNetworking.send(
            new PromptMessages.SetPlayMat(DuelClientState.matColourId()));
        rebuild();
    }

    /**
     * Whether the right mouse button is down right now.
     * <p>
     * Asked of the window rather than tracked from click events: the button may
     * already have been held when the prompt arrived, and a press that happened
     * before this screen existed produces no event for it to have seen.
     */
    private boolean rightButtonHeld()
    {
        // Window.getWindow() -- the GLFW handle -- is Window.handle() now.
        return minecraft != null && org.lwjgl.glfw.GLFW.glfwGetMouseButton(
            minecraft.getWindow().handle(),
            org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    @Override
    public void tick()
    {
        // The duel is over: show the result, then give the player the world
        // back on its own rather than making them dismiss it.
        if(DuelClientState.over && DuelClientState.overSince > 0)
        {
            long elapsed = System.currentTimeMillis() - DuelClientState.overSince;
            if(elapsed >= RESULT_STINGER_MS && DuelClientState.hasReward())
            {
                de.cas_ual_ty.dueldimension.shop.DuelRewardMessages.Result reward =
                    DuelClientState.takeReward();
                DuelClientState.reset();
                minecraft.gui.setScreen(
                    new de.cas_ual_ty.dueldimension.clientutil.hub.DuelResultScreen(reward));
                return;
            }
            if(elapsed >= RESULT_HOLD_MS)
            {
                DuelClientState.reset();
                onClose();
                return;
            }
        }
        // Holding the right button waves chain windows through. A long chain
        // asks the same question after every link, and a player who has decided
        // not to respond to any of it should be able to say so once by holding
        // rather than clicking through each in turn. Only skippable windows go:
        // a forced response is not a question, and the core would refuse an
        // empty answer to it.
        EnginePrompt open = shownPrompt;
        if(open != null && open.chainWindow() && open.cancelable() && !answered
            && rightButtonHeld())
        {
            answer(new int[0], 0);
            return;
        }

        // The answer to "View Deck". It arrives whenever the server gets round
        // to it rather than on the click, because unlike every other pile the
        // deck's contents are not in the board packet -- they are asked for.
        // Taken rather than read, so one answer opens the panel exactly once.
        List<BoardSnapshot.Slot> deck = DuelClientState.deckView;
        if(deck != null)
        {
            DuelClientState.deckView = null;
            if(!deck.isEmpty())
            {
                pileView = deck;
                // The same wording the deck's own pile label carries, and the
                // same number: the list IS the count, so the two cannot drift.
                pileViewLabel = "Deck (" + deck.size() + ")";
                pileViewScroll = 0;
            }
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

        // Hand the board back as soon as it can take the question.
        //
        // This screen opens over a world board for the prompts the board
        // cannot answer, and then had no way to leave: answer() ends in
        // rebuild(), Escape is refused while the duel runs, and the act key
        // will not reopen the pointer while a screen exists. A Normal Summon
        // asks for a position, which the board could not answer -- so the
        // first summon of a duel ended world-board play permanently and left
        // the player on the 2D screen for the rest of it.
        //
        // Asked with the OPENER's own predicate, so the thing that closes it
        // and the thing that opens it cannot disagree about which prompts
        // belong to which.
        // Not while a pile's card list is open. That list IS the question as
        // far as the player is concerned -- they asked which cards a verb could
        // mean and are reading the answer -- and the prompt underneath it is an
        // idle prompt the board can answer, so without this the screen handed
        // the board back on the very tick the list appeared.
        if(pileChoices == null
            && de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.locked()
            && !de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.screenPreferred()
            && !DuelClientState.over
            && (DuelClientState.prompt == null
                || PromptOptions.boardCanAnswer(DuelClientState.prompt)))
        {
            onClose();
        }
        // The declare-a-card box used to be ticked from here to blink its
        // cursor. EditBox has no tick() any more -- the blink is driven from
        // the frame counter inside the widget -- so there is nothing left for
        // this screen to drive, not a call that has been dropped.
    }

    private void rebuild()
    {
        clearWidgets();
        menuButtons.clear();
        searchButtons.clear();
        if(DuelClientState.prompt != shownPrompt)
        {
            // sync rather than clear: the board may have started this same
            // answer, and identity is what decides whether it is still ours.
            DuelSelection.sync(DuelClientState.prompt);
            sortOrder.clear();
            menuAnchor = null;
            answered = false;
            // A new question; the old one's card list means nothing now.
            pileChoices = null;
            pileChoicesLabel = null;
        }
        shownPrompt = DuelClientState.prompt;
        searchBox = null;

        EnginePrompt prompt = shownPrompt;
        if(prompt != null)
        {
            counterAmounts = new int[prompt.options().size()];
        }
        List<Integer> hidden = prompt == null ? null : pickableCards(prompt);
        if(hidden == null || !hidden.equals(picker))
        {
            pickerScroll = 0;
        }
        // A pile's command list wins: the player has already said "Special
        // Summon" and is now being asked which card, and that question outlives
        // the rebuild that follows the click.
        picker = pileChoices != null ? pileChoices : hidden;

        // EDOPro keeps the chain toggles next to Surrender; same here.
        // Narrowed to leave the music button room on the same row: both are
        // duel-long preferences and there is no reason to spend two rows of a
        // sidebar the card preview wants on them.
        addRenderableWidget(Button.builder(Component.literal(chainPreference.label()), pressed ->
        {
            chainPreference = chainPreference.next();
            ClientPlayNetworking.send(new PromptMessages.SetChainPreference(chainPreference));
            rebuild();
        }).bounds(SIDEBAR_PAD, height - 44, SIDEBAR_W - SIDEBAR_PAD * 2 - MUSIC_BUTTON, 18)
            .build());

        // Mute. The icon says which state pressing it leaves you in the way
        // every mute button does -- crossed out means the music is off -- and
        // the choice is remembered, so a player who duels in silence does not
        // have to say so again next time.
        de.cas_ual_ty.dueldimension.clientutil.widget.TextureButton music =
            new de.cas_ual_ty.dueldimension.clientutil.widget.TextureButton(
                SIDEBAR_W - SIDEBAR_PAD - MUSIC_BUTTON + 2, height - 44,
                MUSIC_BUTTON - 2, 18, Component.empty(), pressed ->
        {
            DuelMusic.toggleMuted();
            rebuild();
        });
        // Cell 1 of the sheet is the crossed-out speaker, cell 0 the plain one.
        music.setTexture(DuelTextures.MUSIC_ICONS, DuelMusic.muted() ? 16 : 0, 0, 16, 16);
        addRenderableWidget(music);

        // Below the chain row, under the mute it belongs with. A slider needs
        // the full width, and it is the one control here adjusted by feel
        // rather than pressed once.
        addRenderableWidget(new MusicVolumeSlider(SIDEBAR_PAD, height - 24,
            SIDEBAR_W - SIDEBAR_PAD * 2, 16));
        // The mat used to be cycled from here. It is chosen in the Duel Hub
        // (Y) now: a setting reachable from two places has no single source of
        // truth, and mid-duel is the worse of the two moments to offer it.

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
            addRenderableWidget(Button.builder(Component.literal(label), pressed -> choose(index))
                .bounds(x, y, buttonWidth, 18).build());
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
            addRenderableWidget(Button.builder(Component.literal("Confirm"), pressed -> confirm())
                .bounds(rightX - 76, height - 24, 74, 18).build());
        }
        if(prompt.cancelable())
        {
            // Bottom right corner, away from the board and the command menu.
            addRenderableWidget(Button.builder(
                Component.literal(prompt.kind() == EnginePrompt.Kind.SORT ? "Keep order" : "Cancel"),
                pressed -> answer(new int[0], 0))
                .bounds(width - 80, height - 24, 74, 18).build());
        }
        if(prompt.kind() == EnginePrompt.Kind.COUNTERS)
        {
            for(int i = 0; i < prompt.options().size(); i++)
            {
                int index = i;
                int rowX = SIDEBAR_W + 8 + i * 96;
                addRenderableWidget(Button.builder(Component.literal("-"),
                    pressed -> counterAmounts[index] = Math.max(0, counterAmounts[index] - 1))
                    .bounds(rowX, height - 46, 18, 18).build());
                addRenderableWidget(Button.builder(Component.literal("+"),
                    pressed -> counterAmounts[index] = Math.min(
                        prompt.options().get(index).max(), counterAmounts[index] + 1))
                    .bounds(rowX + 52, height - 46, 18, 18).build());
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
            Button button = Button.builder(Component.empty(), pressed ->
            {
                if(row < searchResults.size())
                {
                    answer(new int[] {0}, searchResults.get(row));
                }
            }).bounds(x + 176 + (i % 2) * 130, height - 46 + (i / 2) * 19, 126, 17).build();
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

    /**
     * The options of a prompt that are cards the player cannot already see and
     * click, or null when there are none.
     * <p>
     * A card on the field is pointed at directly, which is the better gesture
     * and already works. A card in a deck, a graveyard, a banished pile or the
     * opponent's hand is not drawn individually anywhere, so choosing one has
     * to be done from a list — and a list of cards beats a list of names.
     * <p>
     * Options carrying a command are verbs, not cards: "Activate", "Summon".
     * Those stay in the context menu they belong to.
     */
    private static List<Integer> pickableCards(EnginePrompt prompt)
    {
        List<Integer> found = new ArrayList<>();
        for(int i = 0; i < prompt.options().size(); i++)
        {
            found.add(i);
        }
        // Ordering a list and counting counters are jobs the board cannot do:
        // the board shows where a card IS, and these ask about something else
        // -- what order it goes in, how many markers come off it. Both open
        // the picker wherever their cards happen to sit, including the field.
        if(prompt.kind() == EnginePrompt.Kind.SORT
            || prompt.kind() == EnginePrompt.Kind.COUNTERS
            || prompt.kind() == EnginePrompt.Kind.POSITION)
        {
            return found.isEmpty() ? null : found;
        }

        for(EnginePrompt.Option option : prompt.options())
        {
            if(option.command() != 0 || option.cardCode() == 0 || !option.hasSlot())
            {
                return null;
            }
            if((option.location() & OcgConstants.LOCATION_ONFIELD) != 0)
            {
                // Anything on the field is chosen by pointing at it.
                return null;
            }
        }
        // Any at all, not two or more. The picker is how a hidden card is
        // SHOWN, so refusing to open it for a single candidate is refusing to
        // show the one card the question is about -- and the answer then had to
        // be given by clicking a stack that names nothing. The board's own
        // chooser opens for one just as readily.
        return found.isEmpty() ? null : found;
    }

    // ---- the card picker ----

    /** Geometry of the picker, worked out once and used to draw and to hit-test. */
    private record PickerLayout(int x, int y, int width, int height, int cardW, int cardH,
        int columns, int rows, int gridX, int gridY, int gap)
    {
    }

    /**
     * The picker's geometry, sized to the window it has to fit in.
     * <p>
     * The old version fixed the card at 62px and capped the grid at three rows
     * without once consulting {@code height}. On a short window — or, far more
     * commonly, at a GUI scale of 3, where a 920px client is only ~270 layout
     * pixels tall — three rows of card plus header and footer came out taller
     * than the screen. The panel was then centred to a negative y, so the top
     * row ran off the top and the footer went off the bottom, taking Confirm
     * and Cancel with it.
     * <p>
     * So height decides how many rows there is room for. And where that leaves
     * a single row of a much longer list, the card shrinks until a second row
     * fits: one row of enormous cards is the shape the bug produced, and it is
     * not an improvement on three small ones.
     */
    private PickerLayout pickerLayout()
    {
        int count = picker.size();
        int gap = 6;
        // A position choice is captioned by its ANSWER ("Face-up Attack"), not
        // by a card name, and 62px cut that to "Face-up Att" with no ellipsis.
        // Only this one kind pays for the extra width.
        int preferred = shownPrompt != null && shownPrompt.kind() == EnginePrompt.Kind.POSITION
            ? 78 : 62;
        int chrome = PICKER_PAD * 2 + PICKER_HEADER + PICKER_FOOTER;
        // Clear of the sidebar rather than centred over it: the sidebar carries
        // the details of the card being pointed at, which is the one thing a
        // player choosing between cards wants to read.
        int room = Math.max(cardRoomFloor(), width - SIDEBAR_W - 40);

        int cardW = preferred;
        int cardH;
        int columns;
        int rows;
        while(true)
        {
            cardH = Math.round(cardW / DuelTextures.CARD_ASPECT);
            int maxColumns = Math.max(1, (room + gap) / (cardW + gap));
            columns = Math.max(1, Math.min(maxColumns, Math.min(count, 8)));
            int rowH = cardH + NAME_LINE + gap;
            int maxRows = Math.max(1, (height - 40 - chrome + gap) / rowH);
            rows = Math.max(1, Math.min(Math.min(3, maxRows),
                (count + columns - 1) / columns));
            // Good enough once a second row fits, or once the list needs only
            // the one, or once shrinking further would make the art unreadable.
            if(rows >= 2 || count <= columns || cardW - 4 < MIN_PICKER_CARD_W)
            {
                break;
            }
            cardW -= 4;
        }

        int gridW = columns * cardW + (columns - 1) * gap;
        int gridH = rows * (cardH + NAME_LINE) + (rows - 1) * gap;
        int panelW = gridW + PICKER_PAD * 2;
        int panelH = gridH + chrome;
        int x = SIDEBAR_W + (width - SIDEBAR_W - panelW) / 2;
        int y = (height - panelH) / 2;
        return new PickerLayout(x, y, panelW, panelH, cardW, cardH, columns, rows,
            x + PICKER_PAD, y + PICKER_PAD + PICKER_HEADER, gap);
    }

    /** Enough for one card even on a window too narrow to deserve one. */
    private static int cardRoomFloor()
    {
        return MIN_PICKER_CARD_W;
    }

    private static final int PICKER_PAD = 10;
    private static final int PICKER_HEADER = 14;
    private static final int PICKER_FOOTER = 22;
    /** Room under each card for its name. */
    private static final int NAME_LINE = 10;
    /** Never shrink a picker card below this; past it the art stops reading. */
    private static final int MIN_PICKER_CARD_W = 30;

    /**
     * True while the picker panel is up and taking the mouse.
     * <p>
     * The three sites that draw it, click it and scroll it each spelled this
     * out; the preview now needs the same answer, and four copies of a
     * condition is three too many for one that decides whether the board
     * underneath is reachable at all.
     */
    private boolean pickerOpen()
    {
        return picker != null && shownPrompt != null && !answered;
    }

    /**
     * The picker: the choosable cards as cards, over a dimmed board.
     * <p>
     * Drawn after everything else and before the tooltips, so it sits over the
     * field it is asking about without hiding what a hovered card is.
     */
    private void renderPicker(GuiGraphicsExtractor poseStack, int mouseX, int mouseY)
    {
        EnginePrompt prompt = shownPrompt;
        if(!pickerOpen())
        {
            return;
        }
        PickerLayout at = pickerLayout();
        int perPage = at.columns() * at.rows();
        int maxScroll = Math.max(0, (picker.size() - 1) / at.columns() - at.rows() + 1);
        pickerScroll = Math.max(0, Math.min(pickerScroll, maxScroll));

        // Dim the board rather than hide it: the question is about the duel,
        // and the player should still be able to see its state.
        dimBoard(poseStack, 0xA0000000);
        de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice.draw(poseStack, de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures.PANEL, at.x(), at.y(), at.width(), at.height());

        String title = pileChoicesLabel != null ? pileChoicesLabel
            : prompt.title() == null || prompt.title().isBlank()
            ? "Select a card" : prompt.title();
        poseStack.text(font, title, at.x() + PICKER_PAD, at.y() + 5, 0xFFF4D089, true);

        for(int cell = 0; cell < perPage; cell++)
        {
            int index = cell + pickerScroll * at.columns();
            if(index >= picker.size())
            {
                break;
            }
            EnginePrompt.Option option = prompt.options().get(picker.get(index));
            int column = cell % at.columns();
            int row = cell / at.columns();
            int cardX = at.gridX() + column * (at.cardW() + at.gap());
            int cardY = at.gridY() + row * (at.cardH() + NAME_LINE + at.gap());
            boolean hovered = mouseX >= cardX && mouseX < cardX + at.cardW()
                && mouseY >= cardY && mouseY < cardY + at.cardH();
            boolean picked = DuelSelection.has(picker.get(index));

            if(picked || hovered)
            {
                de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice.draw(poseStack, de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures.PANEL, cardX - 3, cardY - 3,
                    at.cardW() + 6, at.cardH() + 6,
                    picked ? de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice.SELECTED : de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice.HOVER, 3, 0.9F);
            }

            Properties card = DdDatabase.PROPERTIES_LIST.get((long)option.cardCode());
            // Pointing at a choice reads it in the sidebar, the same as
            // pointing at a card on the field or in a pile view. Without this
            // the player was being asked to choose between cards they had no
            // way to read: the picker covers the board, so the sidebar kept
            // showing whatever was last pointed at underneath it.
            //
            // Only a cell showing its FACE previews. A face-down position cell
            // and a card the database cannot name are both drawn as a back, and
            // the sidebar must not name what a back is hiding.
            //
            // The art is whatever drawPickerCard blits into the cell, so the
            // two ask the same question. Set beside the code rather than left
            // alone: a dressed copy previewed a moment ago on the field would
            // otherwise lend this card its artwork.
            if(hovered && !pickerCardIsBack(prompt, option, card))
            {
                previewCode = option.cardCode();
                // A prompt names cards, not squares, so there is no copy to ask
                // about and the printed type is the only honest answer.
                previewRace = 0L;
                previewArt = (byte)artForOption(option);
            }
            drawPickerCard(poseStack, prompt, option, card, cardX, cardY, at);

            // For a position choice the card is the same every time; the label
            // is the answer, so it leads. Everywhere else the card is the
            // answer and its name is the caption.
            String name = prompt.kind() == EnginePrompt.Kind.POSITION || card == null
                ? option.label() : card.getName();
            String shown = font.plainSubstrByWidth(name == null ? "" : name, at.cardW());
            // Whole pixels: the extractor's text takes ints, where the Forge
            // font took a float and rounded it itself.
            poseStack.text(font, shown, cardX + (at.cardW() - font.width(shown)) / 2,
                cardY + at.cardH() + 2, picked ? 0xFFFFE9B0 : 0xFFC2C9D6, true);

            drawPickerBadge(poseStack, prompt, picker.get(index), cardX, cardY, at);
        }

        int footerY = at.y() + at.height() - PICKER_FOOTER + 5;
        String need = switch(prompt.kind())
        {
            case SORT -> "Click them in order   (" + sortOrder.size()
                + " of " + prompt.options().size() + ")";
            case COUNTERS -> "Click a card to take one off   (" + countersChosen()
                + " of " + prompt.minSelect() + ")";
            // Not a third "Choose a position" -- the header already asks and
            // each cell is labelled with its answer. The one thing the panel
            // never said is WHICH card is being placed, and the translator has
            // been putting that in every option's detail all along with nothing
            // drawing it. Any option will do; they are all the same card.
            case POSITION -> prompt.options().isEmpty() ? ""
                : prompt.options().get(0).detail();
            default -> prompt.isSingleChoice() ? "Click a card"
                : "Choose " + prompt.minSelect()
                    + (prompt.maxSelect() > prompt.minSelect() ? " to " + prompt.maxSelect() : "")
                    + "   (" + DuelSelection.count() + " picked)";
        };
        if(!need.isBlank())
        {
            poseStack.text(font, need, at.x() + PICKER_PAD, footerY, 0xFF9FA6B4, true);
        }

        for(FooterButton button : pickerFooter(prompt, at))
        {
            boolean over = mouseX >= button.x() && mouseX < button.x() + FOOTER_W
                && mouseY >= button.y() && mouseY < button.y() + FOOTER_H;
            de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice.draw(poseStack,
                de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures.BUTTON,
                button.x(), button.y(), FOOTER_W, FOOTER_H,
                !button.enabled() ? 2 : over ? 1 : 0, 3);
            poseStack.text(font, button.label(),
                button.x() + (FOOTER_W - font.width(button.label())) / 2, button.y() + 5,
                button.enabled() ? 0xFFE6EAF2 : 0xFF6A7080, true);
        }
        if(maxScroll > 0)
        {
            String more = "scroll  " + Math.min(picker.size(),
                (pickerScroll + at.rows()) * at.columns()) + " / " + picker.size();
            poseStack.text(font, more,
                at.x() + at.width() - PICKER_PAD - font.width(more), footerY, 0xFF7A8090, true);
        }
    }

    /**
     * The artwork the copy behind a picker option wears.
     * <p>
     * The server decides this and puts it on the option, which is the only
     * answer that works everywhere: a card being picked out of a DECK is in no
     * board snapshot at all — the deck is only counted for the client — so
     * there was nothing here to read, and every deck choice came back wearing
     * its printed artwork. An option carrying 0 has either been deliberately
     * left undressed (a card this player may not identify) or belongs to a
     * prompt that names no location, so the board lookup below stays as the
     * second answer rather than being replaced.
     * <p>
     * The passcode is checked against the slot before the art is believed. A
     * prompt and a board arrive as separate messages, so a card can have moved
     * between them; without that guard a stale sequence would lend one card
     * another's artwork, which is a worse error than simply showing the printed
     * one. The option's own art needs no such check — the server compares the
     * code inside the same call frame that builds the option, with the engine
     * blocked, so the two cannot disagree.
     */
    private int artForOption(EnginePrompt.Option option)
    {
        if(option.art() != 0)
        {
            return option.art();
        }
        BoardSnapshot snapshot = currentBoard();
        if(snapshot == null || option.sequence() < 0)
        {
            return 0;
        }
        BoardSnapshot.Side side = option.controller() == 0
            ? snapshot.self() : snapshot.opponent();
        java.util.List<BoardSnapshot.Slot> pile = switch(option.location())
        {
            case OcgConstants.LOCATION_MZONE -> side.monsters();
            case OcgConstants.LOCATION_SZONE -> side.spells();
            case OcgConstants.LOCATION_HAND -> side.hand();
            case OcgConstants.LOCATION_GRAVE -> side.grave();
            case OcgConstants.LOCATION_REMOVED -> side.banished();
            case OcgConstants.LOCATION_EXTRA -> side.extra();
            default -> java.util.List.of();
        };
        if(option.sequence() >= pile.size())
        {
            return 0;
        }
        BoardSnapshot.Slot slot = pile.get(option.sequence());
        return slot.code() == option.cardCode() ? slot.art() : 0;
    }

    /**
     * One card of the picker, drawn the way its prompt wants it.
     * <p>
     * A position choice shows the same card in each posture it is being offered
     * in — face down, or turned on its side for defence — because the posture
     * IS the question. Everything else draws the card straight.
     */
    private void drawPickerCard(GuiGraphicsExtractor poseStack, EnginePrompt prompt,
        EnginePrompt.Option option, Properties card, int cardX, int cardY, PickerLayout at)
    {
        boolean defence = prompt.kind() == EnginePrompt.Kind.POSITION
            && (option.zone() & OcgConstants.POS_DEFENSE) != 0;

        boolean back = pickerCardIsBack(prompt, option, card);
        Identifier texture = back ? DuelTextures.COVER
            : DuelTextures.cardSmooth(card, (byte)artForOption(option),
                DuelTextures.PREVIEW_CARD_SIZE);

        int drawX = cardX;
        int drawY = cardY;
        int drawW = at.cardW();
        int drawH = at.cardH();

        poseStack.pose().pushMatrix();
        if(defence)
        {
            // Turned a quarter about the cell's own middle, as a defending
            // card lies on the table. rotate() takes radians; the Forge
            // quaternion took degrees.
            poseStack.pose().translate(cardX + at.cardW() / 2F, cardY + at.cardH() / 2F);
            poseStack.pose().rotate((float)Math.toRadians(90D));
            poseStack.pose().translate(-(cardX + at.cardW() / 2F), -(cardY + at.cardH() / 2F));

            // A quarter turn swaps a quad's width and height, so the quad drawn
            // BEFORE the turn has to be the one whose TURNED footprint fits the
            // cell. Turning the full 62x90 card put a 90x62 picture on the same
            // centre: 14px past the cell on either side, over its neighbour's
            // edge and out through the panel. That is the invariant DdBlitUtil
            // spells out -- rotation and bounds agree only on a square region --
            // and it is why every other rotating caller squares up first.
            drawW = Math.round(at.cardW() * DuelTextures.CARD_ASPECT);   // turned HEIGHT
            drawH = at.cardW();                                          // turned WIDTH
            drawX = cardX + (at.cardW() - drawW) / 2;
            drawY = cardY + (at.cardH() - drawH) / 2;
        }
        if(back)
        {
            DdBlitUtil.fullBlit(poseStack, texture, drawX, drawY, drawW, drawH);
        }
        else
        {
            // The UV window is absolute now, not an offset and a span.
            DdBlitUtil.blit(poseStack, texture, drawX, drawY, drawW, drawH,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);
        }
        poseStack.pose().popMatrix();
    }

    /**
     * Whether a picker cell shows the card's back rather than its face.
     * <p>
     * The back is drawn for a face-down position because that is what the card
     * will look like, and for a card the database lacks because it at least
     * keeps the cell the same shape as its neighbours.
     * <p>
     * Asked by the drawing and by the hover preview off the one answer: the
     * sidebar may only ever say what the cell is already showing.
     */
    private static boolean pickerCardIsBack(EnginePrompt prompt, EnginePrompt.Option option,
        Properties card)
    {
        return card == null || (prompt.kind() == EnginePrompt.Kind.POSITION
            && (option.zone() & OcgConstants.POS_FACEDOWN) != 0);
    }

    /**
     * The mark in a card's corner: its place in the order being built, or how
     * many counters are coming off it.
     */
    private void drawPickerBadge(GuiGraphicsExtractor poseStack, EnginePrompt prompt, int optionIndex,
        int cardX, int cardY, PickerLayout at)
    {
        String badge = null;
        if(prompt.kind() == EnginePrompt.Kind.SORT)
        {
            int place = sortOrder.indexOf(optionIndex);
            badge = place < 0 ? null : Integer.toString(place + 1);
        }
        else if(prompt.kind() == EnginePrompt.Kind.COUNTERS)
        {
            badge = counterAmounts[optionIndex] + " / " + prompt.options().get(optionIndex).max();
        }
        if(badge == null)
        {
            return;
        }
        int badgeW = font.width(badge) + 6;
        int badgeX = cardX + at.cardW() - badgeW - 2;
        int badgeY = cardY + 2;
        poseStack.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 11, 0xD0000000);
        poseStack.text(font, badge, badgeX + 3, badgeY + 2, 0xFFFFE9B0, true);
    }

    /** A button along the bottom of the picker. */
    private record FooterButton(String label, int x, int y, boolean enabled, Runnable action)
    {
    }

    private static final int FOOTER_W = 74;
    private static final int FOOTER_H = 16;

    /**
     * Confirm and Cancel, belonging to the picker rather than to the screen.
     * <p>
     * The screen's own pair sit along the bottom edge, which the picker covers.
     * Rather than move them and have their position depend on whether a picker
     * happens to be open, the picker carries its own.
     */
    private List<FooterButton> pickerFooter(EnginePrompt prompt, PickerLayout at)
    {
        List<FooterButton> buttons = new ArrayList<>();
        int y = at.y() + at.height() - FOOTER_H - 3;
        int x = at.x() + at.width() - PICKER_PAD - FOOTER_W;

        if(pileChoices != null)
        {
            // The idle prompt itself is not cancelable -- you must do SOMETHING
            // on your turn -- but changing your mind about WHICH card is always
            // allowed, and returns to the board rather than answering.
            buttons.add(new FooterButton("Back", x, y, true, this::closePileChoices));
            x -= FOOTER_W + 4;
        }
        if(prompt.cancelable())
        {
            buttons.add(new FooterButton(
                prompt.kind() == EnginePrompt.Kind.SORT ? "Keep order" : "Cancel",
                x, y, true, () -> answer(new int[0], 0)));
            x -= FOOTER_W + 4;
        }
        boolean needsConfirm = switch(prompt.kind())
        {
            case MULTI -> !prompt.isSingleChoice();
            case SORT, COUNTERS -> true;
            default -> false;
        };
        if(needsConfirm)
        {
            boolean ready = switch(prompt.kind())
            {
                case SORT -> sortOrder.size() == prompt.options().size();
                case COUNTERS -> countersChosen() == prompt.minSelect();
                default -> DuelSelection.count() >= prompt.minSelect();
            };
            buttons.add(new FooterButton("Confirm", x, y, ready, this::confirm));
        }
        return buttons;
    }

    private int countersChosen()
    {
        int total = 0;
        for(int amount : counterAmounts)
        {
            total += amount;
        }
        return total;
    }

    /** A click on the picker, or false if it fell outside one. */
    private boolean clickPicker(double mouseX, double mouseY)
    {
        EnginePrompt prompt = shownPrompt;
        if(!pickerOpen())
        {
            return false;
        }
        PickerLayout at = pickerLayout();
        for(FooterButton button : pickerFooter(prompt, at))
        {
            if(button.enabled() && mouseX >= button.x() && mouseX < button.x() + FOOTER_W
                && mouseY >= button.y() && mouseY < button.y() + FOOTER_H)
            {
                button.action().run();
                return true;
            }
        }
        for(int cell = 0; cell < at.columns() * at.rows(); cell++)
        {
            int index = cell + pickerScroll * at.columns();
            if(index >= picker.size())
            {
                break;
            }
            int column = cell % at.columns();
            int row = cell / at.columns();
            int cardX = at.gridX() + column * (at.cardW() + at.gap());
            int cardY = at.gridY() + row * (at.cardH() + NAME_LINE + at.gap());
            if(mouseX >= cardX && mouseX < cardX + at.cardW()
                && mouseY >= cardY && mouseY < cardY + at.cardH())
            {
                int option = picker.get(index);
                if(prompt.kind() == EnginePrompt.Kind.COUNTERS)
                {
                    // One more off this card per click, back to none past its
                    // stock: overshooting costs one more click rather than
                    // needing a second gesture nobody was told about.
                    int stock = Math.max(0, prompt.options().get(option).max());
                    counterAmounts[option] = counterAmounts[option] >= stock
                        ? 0 : counterAmounts[option] + 1;
                }
                else
                {
                    choose(option);
                }
                return true;
            }
        }
        // Everything inside the panel is swallowed, so a miss between cards
        // does not fall through to the board underneath it.
        return mouseX >= at.x() && mouseX < at.x() + at.width()
            && mouseY >= at.y() && mouseY < at.y() + at.height();
    }

    // ---- contextual command menu ----

    /**
     * Option indices acting on this exact slot, in ShowMenu's order.
     * <p>
     * The body moved to {@link PromptOptions} when the world board needed the
     * same answer. It stays as a delegate because this class asks it in a dozen
     * places -- what matters is that there is now ONE filter, so a card the
     * screen says you may attack with is a card the board says the same about.
     */
    private List<Integer> optionsFor(BoardRenderer.Hit hit)
    {
        return PromptOptions.optionsFor(shownPrompt, answered,
            hit == null ? null : hit.target());
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
        if(hit.isPile())
        {
            // One row per VERB, not per card. The pile's cards are face down,
            // so three summonable monsters produced three rows all reading
            // "Special Summon" with nothing to tell them apart. Picking the
            // verb now opens the list of cards it could mean -- which EDOPro
            // does even when there is only one, because "Special Summon" on its
            // own never says WHAT.
            java.util.LinkedHashMap<Integer, List<Integer>> byCommand =
                new java.util.LinkedHashMap<>();
            for(int index : actions)
            {
                byCommand.computeIfAbsent(prompt.options().get(index).command(),
                    command -> new ArrayList<>()).add(index);
            }
            for(java.util.Map.Entry<Integer, List<Integer>> group : byCommand.entrySet())
            {
                List<Integer> indices = group.getValue();
                // A selection carries command 0 and no verb of its own, so
                // every card in the stack lands in one group -- and labelling
                // that group with the FIRST card's name told the player they
                // were about to act on one particular monster when the row
                // opens a list of all of them. The stack's own name is the
                // truthful heading, and it is the heading the list keeps.
                String label = group.getKey() == 0 ? hit.label()
                    : prompt.options().get(indices.get(0)).label();
                entries.add(new MenuEntry(label, group.getKey(),
                    () -> openPileChoices(label, indices)));
            }
        }
        else
        {
            for(int index : actions)
            {
                EnginePrompt.Option option = prompt.options().get(index);
                entries.add(new MenuEntry(option.label(), option.command(), () -> choose(index)));
            }
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
                if(DuelClientState.hasReward())
                {
                    de.cas_ual_ty.dueldimension.shop.DuelRewardMessages.Result reward =
                        DuelClientState.takeReward();
                    DuelClientState.reset();
                    minecraft.gui.setScreen(
                        new de.cas_ual_ty.dueldimension.clientutil.hub.DuelResultScreen(reward));
                    return;
                }
                DuelClientState.reset();
                onClose();
            }));
        }
        else
        {
            // Above Surrender, because it is the harmless one and a menu whose
            // first row concedes the duel is a menu people learn not to open.
            // The click only asks -- the deck is not in the board packet, so
            // nothing can be shown until the server answers; see tick().
            entries.add(new MenuEntry("View Deck", 0, () ->
            {
                ClientPlayNetworking.send(new PromptMessages.ViewOwnDeck());
                closeMenu();
            }));
            entries.add(new MenuEntry("Surrender", 0, () ->
            {
                ClientPlayNetworking.send(new PromptMessages.Surrender());
                closeMenu();
            }));
        }
        showMenu(hit, entries);
    }

    /**
     * Shows which cards a pile command could mean, and waits for one.
     * <p>
     * Always, even for a single card: a player told "Special Summon" and then
     * asked for tributes has been asked to pay for something they were never
     * shown.
     */
    /**
     * The same list, asked for from the world board.
     * <p>
     * A pile out there has the same problem it has here -- its cards are face
     * down, so a row reading "Special Summon" never says WHAT -- and the answer
     * is this screen's picker, which is already built for it. The board hands
     * the question over rather than growing a second picker of its own.
     */
    public void showPileChoices(String label, List<Integer> indices)
    {
        openPileChoices(label, indices);
    }

    private void openPileChoices(String label, List<Integer> indices)
    {
        closeMenu();
        pileChoices = List.copyOf(indices);
        pileChoicesLabel = label;
        pickerScroll = 0;
        rebuild();
    }

    /** Backs out of a pile's card list, returning to the board. */
    private boolean closePileChoices()
    {
        if(pileChoices == null)
        {
            return false;
        }
        pileChoices = null;
        pileChoicesLabel = null;
        rebuild();
        return true;
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
        if(menuY < TOP_BAR_H + phaseCellH() + 8)
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
            if(mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight())
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
            // POSITION belongs here and was missing, which meant a position
            // click fell through to the empty `default` below and was dropped:
            // the panel drew, the cell highlighted, and nothing happened. It is
            // always exactly one pick -- the translator builds it min 1, max 1 --
            // so it answers on the click like any other single choice.
            case CHOOSE, POSITION -> answer(new int[] {index}, 0);
            case MULTI, PLACES ->
            {
                if(prompt.isSingleChoice())
                {
                    answer(new int[] {index}, 0);
                    return;
                }
                DuelSelection.toggle(prompt, index);
                if(prompt.kind() == EnginePrompt.Kind.PLACES
                    && DuelSelection.count() == prompt.minSelect())
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
                if(DuelSelection.count() >= prompt.minSelect())
                {
                    answer(DuelSelection.answer(), 0);
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
        // Answered: the deadline is met, so the clock stops rather than
        // running on while the opponent takes their turn.
        DuelClientState.promptShownAt = 0;
        ClientPlayNetworking.send(new PromptMessages.AnswerPrompt(chosen, declaredCode,
            DuelClientState.promptSerial));
        rebuild();
    }

    // ---- input ----

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event)
    {
        int keyCode = event.key();

        // Back to the board. A key binding is not polled while a screen is
        // open, so the swap key has to be read here as well or the trip is
        // one-way: out to the screen and no way home without ending the duel.
        if(de.cas_ual_ty.dueldimension.clientutil.hub.HubKeybinds.DUEL_VIEW.matches(event)
            && de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField.locked())
        {
            de.cas_ual_ty.dueldimension.clientutil.overworld.ClientDuelField
                .toggleScreen(minecraft);
            return true;
        }

        // Escape backs out of whatever is open, in that order.
        if(keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
        {
            if(pileView != null)
            {
                pileView = null;
                return true;
            }
            if(closePileChoices())
            {
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
            if(DuelSelection.count() > 0 || !sortOrder.isEmpty())
            {
                DuelSelection.clear();
                sortOrder.clear();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    /**
     * Leaving the duel screen.
     * <p>
     * Tells the server the player has their character back, which is what the
     * Seal of Orichalcos waits for. Without this the server had no idea the
     * screen had closed and every seal death sat out its full fallback timer.
     */
    @Override
    public void onClose()
    {
        ClientPlayNetworking.send(
            new de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosMessages.LeftDuel());
        super.onClose();
    }

    /**
     * Takes hold of the description's scroll bar.
     *
     * @return whether the bar took this click
     */
    private boolean grabDescriptionBar(double mouseX, double mouseY)
    {
        if(descriptionMaxScroll <= 0 || barY1 <= barY0
            || mouseX < barX0 - BAR_GRAB || mouseX >= barX1 + BAR_GRAB
            || mouseY < barY0 || mouseY >= barY1)
        {
            return false;
        }
        int thumbY = barY0 + Math.round((barY1 - barY0 - barThumbH)
            * (descriptionScroll / (float)descriptionMaxScroll));
        // Grabbing the thumb keeps the offset, so it does not jump under the
        // cursor. Clicking the track anywhere else centres the thumb there,
        // which is what every other scroll bar does.
        barGrabOffset = mouseY >= thumbY && mouseY < thumbY + barThumbH
            ? (int)(mouseY - thumbY) : barThumbH / 2;
        dragDescriptionBar(mouseY);
        return true;
    }

    /** Scrubs the text to wherever the thumb has been dragged. */
    private void dragDescriptionBar(double mouseY)
    {
        int travel = barY1 - barY0 - barThumbH;
        if(travel <= 0)
        {
            descriptionScroll = 0;
            return;
        }
        double top = mouseY - barGrabOffset - barY0;
        descriptionScroll = Math.clamp(
            Math.round(top / travel * descriptionMaxScroll), 0, descriptionMaxScroll);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,
        double dragX, double dragY)
    {
        if(barGrabOffset >= 0)
        {
            dragDescriptionBar(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event)
    {
        barGrabOffset = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)
    {
        // The pile view first, for the same reason mouseClicked tests it first:
        // it is DRAWN over the picker and the two panels are centred on the same
        // point, so the wheel belongs to whichever is on top. A deck is the one
        // pile too tall to fit, and this is how the rest of it is reached.
        if(pileView != null && pileViewMaxScroll > 0)
        {
            pileViewScroll = Math.clamp(pileViewScroll - (int)Math.signum(delta),
                0, pileViewMaxScroll);
            return true;
        }
        // A graveyard can hold more cards than the picker shows at once.
        if(pickerOpen())
        {
            pickerScroll = Math.max(0, pickerScroll - (int)Math.signum(delta));
            return true;
        }
        // Reading a long effect. Anywhere at all, not just over the sidebar:
        // the card being read is the one the pointer is resting on out on the
        // FIELD, so requiring the cursor to come back to the sidebar meant
        // letting go of the thing you were reading about to read it. The picker
        // above has already had its chance at the wheel and nothing else on this
        // screen wants it, so there is nothing for this to be confused with.
        if(descriptionMaxScroll > 0)
        {
            descriptionScroll = Math.clamp(
                descriptionScroll - (int)Math.signum(delta) * DESCRIPTION_SCROLL_STEP,
                0, descriptionMaxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
        boolean doubleClick)
    {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        // The scroll bar first: it sits over the sidebar, which the board hit
        // test would otherwise happily claim.
        if(button == 0 && grabDescriptionBar(mouseX, mouseY))
        {
            return true;
        }
        // The pile view before the picker, because it is DRAWN over the picker
        // (renderPileView runs after renderPicker) and both panels are centred
        // on the same point, so they overlap almost completely. openPile is
        // reachable while a picker is up -- a click on a graveyard outside the
        // panel falls through to the board -- and with the picker tested first
        // a click in the overlap selected the picker card hidden UNDER the pile
        // view while the sidebar was reading out the pile card on top of it:
        // pointing at one card and choosing another, which is the one thing the
        // hover preview must never allow. The panel on top takes the click, and
        // its own header has been promising "click to close" all along.
        if(button == 0 && pileView != null)
        {
            pileView = null;
            return true;
        }
        if(button == 0 && clickPicker(mouseX, mouseY))
        {
            return true;
        }
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
            if(DuelSelection.count() > 0 || !sortOrder.isEmpty())
            {
                DuelSelection.clear();
                sortOrder.clear();
                return true;
            }
        }
        if(super.mouseClicked(event, doubleClick))
        {
            return true; // a widget (including a menu entry) took it
        }
        if(button == 0)
        {
            BoardRenderer.Hit hit = boardRenderer.hitAt(mouseX, mouseY, candidate ->
            {
                int priority = optionsFor(candidate).isEmpty() ? 0 : 4;
                if(candidate.code() != 0)
                {
                    priority += 2;
                }
                if(candidate.isPile() && candidate.count() > 0)
                {
                    priority++;
                }
                return priority;
            });
            if(hit != null)
            {
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
                }
                else if(actions.size() == 1 && !isCardCommand(actions.get(0))
                    && !hit.isPile() && !inChainWindow())
                {
                    // Selecting a card for a prompt ("pick a target") stays one
                    // click: there is nothing to choose between.
                    //
                    // NOT for a stack. "There is nothing to choose between" is
                    // true of a card lying on the field, which the player can
                    // see; it is false of one inside a graveyard, which they
                    // cannot. A single-target Monster Reborn committed the
                    // revival the instant the pile was clicked, without ever
                    // naming the monster it brought back. PromptOptions says
                    // this outright -- "a stack never answers on the click" --
                    // and the world board has always obeyed it; this screen
                    // had its own weaker test and did not.
                    choose(actions.get(0));
                    return true;
                }
                else if(!actions.isEmpty())
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
    /**
     * Is the duel asking whether to respond right now?
     * <p>
     * A chain window names cards rather than commands, so every one of its
     * options carries command 0 and reads to the test below as an innocent
     * selection -- and one click then SETS OFF A TRAP, which is the most
     * irreversible thing a duel can be made to do by accident. PromptOptions
     * has refused this since the board learned to answer prompts, with EDOPro's
     * own confirmation as the reason; this screen had no equivalent and fired.
     */
    private boolean inChainWindow()
    {
        return shownPrompt != null && shownPrompt.chainWindow();
    }

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
        pileViewScroll = 0;
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

    /**
     * The screen, drawn in two vocabularies.
     * <p>
     * Everything the player points at that is <em>flat</em> -- life bars, phase
     * row, sidebar, hint banner, marks, picker, result card, pile view -- is
     * blitted straight onto the extractor, exactly as before. The play space
     * cannot be: every zone is a trapezoid, so the board, the animations over it
     * and the equip links go inside a {@link BoardPip} region where a real
     * {@code PoseStack} and {@code SubmitNodeCollector} exist.
     * <p>
     * <b>The region is the whole window, not the board's own rectangle.</b>
     * {@code BoardPip} moves the origin to the region's top-left, so a region
     * the size of the window makes region coordinates and screen coordinates the
     * same number -- and that matters far more here than the pixels saved. The
     * hit rectangles this screen tests the mouse against, the anchor the command
     * menu opens at, the selection marks and the aim pointer's target are all in
     * screen coordinates and are read outside the region; a smaller region would
     * put a translation between them and every one of those sites. Two of the
     * things drawn inside also refuse to fit a board-sized box: a reveal is laid
     * across the middle of the <em>window</em>, and the aim pointer follows the
     * cursor wherever it goes.
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor poseStack, int mouseX, int mouseY, float partialTick)
    {
        // The dim Forge drew, not vanilla's modern background.
        //
        // Screen.renderBackground in 1.19.2 was a gradient over the world;
        // extractBackground in 26.2 BLURS, and the blur is a once-per-frame
        // resource -- asking for it here threw "Can only blur once per frame"
        // and took the client down the moment a duel opened. A duel screen
        // covers the window with its own field anyway, so the blur was never
        // wanted; this is the gradient the Forge original actually produced.
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
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
        // Below the phase bar, whatever height the phase bar turned out to be.
        // Its cells are sized from the window's width, so on a wide window the
        // case grows past the authored TOP_BAR_H and was drawn straight over
        // the top of the table -- the board looked stretched upwards because
        // its top was underneath the instruments.
        int fieldTop = Math.max(TOP_BAR_H + FIELD_DROP,
            PHASE_BAR_Y + phaseCellH() + phasePadY() + FIELD_DROP);
        int fieldWidth = width - SIDEBAR_W;
        // Down to the last few pixels: the wide margin here left a dead band
        // between the hand and the bottom edge and crushed the board above it.
        int fieldHeight = height - fieldTop - 6;

        // Playback is advanced on the client tick, not here, so it keeps its
        // pace even while this screen is closed. Rendering only draws it.
        long now = System.currentTimeMillis();

        // Settle where the board is BEFORE anything reads hits(). The painter
        // below only draws this layout, and it runs after every extract has
        // returned -- so leaving the hit rectangles to it would mean the hover
        // and the selection marks worked off the previous frame's board.
        //
        // mouseClicked is a GLFW callback and fires between frames, so it can
        // never be same-frame; what this buys it is the layout of the frame the
        // player actually clicked on rather than the one before it.
        boardRenderer.layout(font, board, fieldLeft, fieldTop, fieldWidth, fieldHeight, highlights);

        // Hover picks the preview card and opens that card's command menu.
        BoardRenderer.Hit hovered = boardRenderer.hitAt(mouseX, mouseY,
            hit -> hit.code() != 0 ? 1 : 0);
        // ...unless a panel is over the board. The picker and the pile view
        // both swallow the mouse, so a card they happen to be covering is not
        // something the player is pointing at -- and since the board is hit
        // tested before either of them draws, letting it write the preview
        // would pull the sidebar off the card being pointed at inside the
        // panel every time the cursor crossed a gap between two cells.
        if(hovered != null && hovered.code() != 0 && !pickerOpen() && pileView == null)
        {
            previewCode = hovered.code();
            previewRace = de.cas_ual_ty.dueldimension.clientutil.CardFacts.liveRace(
                hovered.controller(), hovered.location(), hovered.sequence());
            previewArt = (byte)hovered.art();
        }
        // EDOPro opens the command menu on click, not on hover; hovering only
        // drives the card preview.

        // Aiming: after clicking Attack, the sword tracks the mouse until the
        // target is chosen -- sampled six times a second, so it snaps rather
        // than glides. The sample is taken here rather than inside the painter
        // because it is a frame's worth of mouse position, and the painter runs
        // once per frame either way.
        boolean aiming = aimZone >= 0 && shownPrompt != null && !answered
            && isTargetSelection(shownPrompt);
        if(aiming)
        {
            // 30 updates a second: smooth enough to track the cursor without
            // redrawing the pointer every frame.
            long bucket = now / 33;
            if(bucket != aimBucket)
            {
                aimBucket = bucket;
                aimX = mouseX;
                aimY = mouseY;
            }
        }

        // hovered is written in the loop above, so it cannot be captured; the
        // equip links need the same hit the preview used.
        BoardRenderer.Hit equipFrom = hovered;
        BoardPip.draw(poseStack, 0, 0, width, height, (PoseStack pose, SubmitNodeCollector collector) ->
        {
            // Call order is draw order again from here; see FieldQuad.resetLayers.
            FieldQuad.resetLayers();
            boardRenderer.render(pose, collector);

            animations.renderMoves(pose, collector, boardRenderer, boardRenderer.projection(), now);
            animations.renderAttacks(pose, collector, boardRenderer.projection(), now);
            animations.renderOverlays(pose, collector, boardRenderer.projection(), now);
            animations.renderShatters(pose, collector, boardRenderer.projection(), now);
            animations.renderTosses(pose, collector, font, boardRenderer.projection(), now);
            animations.renderFlips(pose, collector, boardRenderer.projection(), now);
            animations.renderReveals(pose, collector, font, width, height, now);

            if(aiming)
            {
                animations.renderAim(pose, collector, boardRenderer.projection(), aimZone,
                    aimX, aimY);
            }

            // Drawn straight after the board so the link sits over the cards but
            // under every panel, the way EDOPro's own equip mark does. It is
            // geometry too, so it stays inside the region rather than being
            // flattened into a blit outside it.
            boardRenderer.drawEquipLinks(pose, collector, board, equipFrom);
        });

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
            poseStack.fill(first.getX() - 2, first.getY() - 2, first.getX() + first.getWidth() + 2,
                first.getY() + menuButtons.size() * MENU_ROW, 0xC0000000);
        }

        super.extractRenderState(poseStack, mouseX, mouseY, partialTick);
        renderPicker(poseStack, mouseX, mouseY);
        renderWaiting(poseStack);
        renderResult(poseStack);

        if(pileView != null)
        {
            renderPileView(poseStack, mouseX, mouseY);
        }
        if(hovered != null && hovered.isPile())
        {
            // A tooltip belongs to the frame now rather than to whoever drew
            // it, and it is asked for last so nothing described later covers it.
            poseStack.setTooltipForNextFrame(font, Component.literal(hovered.label()),
                mouseX, mouseY);
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
     * The phase indicator's geometry, matching build/gen_phase_assets.py --
     * change one and regenerate the other. The bar is half the height it was:
     * a 10px key in a 16px case rather than 20 in 28.
     * <p>
     * The two paddings are deliberately different. PAD_X has to clear the
     * shell's angled nose so neither end key sits inside the slope, while
     * PAD_Y only sets how much case shows above and below; tying them together
     * is what made the first half-height pass look lopsided.
     */
    /** The authored proportions; the bar scales from these to fit the board. */
    private static final int PHASE_CELL_W_BASE = 50;
    private static final int PHASE_CELL_H_BASE = 10;
    private static final int PHASE_PAD_X_BASE = 8;
    private static final int PHASE_PAD_Y_BASE = 3;
    /** Never so small it cannot be read, nor so wide it dwarfs the board. */
    private static final int PHASE_CELL_W_MIN = 40;
    private static final int PHASE_CELL_W_MAX = 110;
    /** How much of the board's width the bar spans. */
    private static final float PHASE_FILL = 0.86F;

    /**
     * Key width for this window: the board's width shared between six keys,
     * held between the bounds above. Everything else scales from it, so the
     * bar keeps the shape it was drawn at whatever it is stretched to.
     */
    private int phaseCellW()
    {
        int available = Math.round((width - SIDEBAR_W) * PHASE_FILL) - PHASE_PAD_X_BASE * 2;
        return Math.max(PHASE_CELL_W_MIN,
            Math.min(PHASE_CELL_W_MAX, available / PHASE_NAMES.length));
    }

    private int phaseCellH()
    {
        return Math.max(6, Math.round(phaseCellW() * (float)PHASE_CELL_H_BASE / PHASE_CELL_W_BASE));
    }

    private int phasePadX()
    {
        return Math.round(phaseCellW() * (float)PHASE_PAD_X_BASE / PHASE_CELL_W_BASE);
    }

    private int phasePadY()
    {
        return Math.max(2, Math.round(phaseCellH() * (float)PHASE_PAD_Y_BASE / PHASE_CELL_H_BASE));
    }
    /**
     * The phase row sits below the life bars, tucked up under the turn badge:
     * the "your turn" label that used to occupy this space is gone (the badge's
     * colour says whose turn it is), so the bar can sit higher.
     */
    private static final int PHASE_BAR_Y = 26;

    private int phaseBarLeft()
    {
        int barW = PHASE_NAMES.length * phaseCellW();
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
            addRenderableWidget(new SlimPhaseButton(x + i * phaseCellW(), PHASE_BAR_Y,
                phaseCellW(), phaseCellH(), Component.literal(PHASE_NAMES[i]), i,
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
            // Button's public constructor is gone; the protected one wants a
            // narration supplier, and this button says exactly what it reads.
            super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
            this.command = command;
            this.anchor = anchor;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor poseStack, int mouseX, int mouseY, float partialTick)
        {
            boolean hovered = isHoveredOrFocused();
            int x = getX();
            int y = getY();
            poseStack.fill(x, y, x + width, y + height, hovered ? 0xF0473A22 : 0xE01A1A1E);
            poseStack.fill(x, y, x + width, y + 1, hovered ? 0xFFFFD700 : 0x60FFD700);

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
                int size = height - 4;
                DdBlitUtil.fullBlit(poseStack, icon, x + 3, y + 2, size, size);
                textX = x + 6 + size;
            }
            poseStack.text(font, getMessage(), textX, y + (height - 8) / 2,
                hovered ? 0xFFFFFFCC : 0xFFE8E8E8, false);
        }
    }

    /**
     * The duel's music volume.
     * <p>
     * Its own control rather than a link to the game's sliders: the duel is
     * where a player notices the music is too loud against the effect sounds,
     * and sending them to the options menu mid-duel to fix it is the wrong
     * answer. It scales the mod's track only; Minecraft's own Jukebox slider
     * still applies on top, as it does to everything.
     */
    private static class MusicVolumeSlider extends net.minecraft.client.gui.components.AbstractSliderButton
    {
        MusicVolumeSlider(int x, int y, int w, int h)
        {
            super(x, y, w, h, Component.empty(), DuelMusic.volume());
            updateMessage();
        }

        @Override
        protected void updateMessage()
        {
            setMessage(Component.literal("Music  " + Math.round(value * 100F) + "%"));
        }

        @Override
        protected void applyValue()
        {
            DuelMusic.setVolume((float)value);
        }

        /**
         * Silent. A slider that clicks on every step of a drag is noise, and
         * the thing being dragged is already the feedback.
         */
        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
        }
    }

    /** A flat, compact button so the phase row reads as one strip. */
    /**
     * A phase the core is offering, drawn as the same cased button as the
     * labels so the row reads as one piece of hardware.
     */
    private class SlimPhaseButton extends Button
    {
        private final int index;

        SlimPhaseButton(int x, int y, int w, int h, Component label, int index, OnPress onPress)
        {
            super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
            this.index = index;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor poseStack, int mouseX, int mouseY, float partialTick)
        {
            BoardSnapshot board = currentBoard();
            // Pointing at a reachable phase previews it as lit.
            int state = isHoveredOrFocused() ? PHASE_LIT : phaseState(board, index);
            drawPhaseCell(poseStack, getX(), getY(), width, height, index, state,
                board.turnPlayer() == 0);
        }

        /**
         * The duel's own phase-change sound, not Minecraft's button click.
         * <p>
         * Button plays UI_BUTTON_CLICK from here and this never overrode it, so
         * changing phase in a Yu-Gi-Oh duel sounded like pressing a button in
         * the options menu.
         */
        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
            sounds.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                de.cas_ual_ty.dueldimension.DdSounds.PHASE_CHANGE, 1F, 1F));
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
    private void renderHintBanner(GuiGraphicsExtractor poseStack, EnginePrompt prompt)
    {
        if(prompt == null || answered || prompt.title() == null || prompt.title().isBlank())
        {
            return;
        }
        if(picker != null)
        {
            // The modal panel is up and carries the same title in its header.
            // The banner exists to caption a choice made ON the board, where
            // there is no panel to put a heading on.
            return;
        }
        String text = prompt.title();
        if(prompt.maxSelect() > 1)
        {
            text = text + "  (" + DuelSelection.count() + "/" + prompt.maxSelect() + ")";
        }
        if(prompt.chainWindow() && prompt.cancelable())
        {
            // Named, because holding a button is not a thing anyone tries by
            // accident and a long chain is exactly when it is worth knowing.
            text = text + "   [hold right-click to pass]";
        }

        int textWidth = font.width(text);
        int centre = Math.round(boardRenderer.tableCentreX());
        int left = Math.max(SIDEBAR_W + 6, centre - textWidth / 2 - 6);
        int right = Math.min(width - 6, left + textWidth + 12);
        int top = TOP_BAR_H + 2;

        poseStack.fill(left, top, right, top + 14, 0xD0101014);
        poseStack.fill(left, top, right, top + 1, 0x80FFD700);
        poseStack.fill(left, top + 13, right, top + 14, 0x80FFD700);
        poseStack.centeredText(font, text, (left + right) / 2, top + 3, 0xFFFFE066);
    }

    /**
     * The victory or defeat card, over the settled board.
     * <p>
     * A stinger rather than a screen in its own right: after
     * {@link #RESULT_STINGER_MS} tick() hands over to {@code DuelResultScreen},
     * which is where the outcome is actually reported. {@link #RESULT_HOLD_MS}
     * is only the fallback for a duel that produced no reward packet -- a
     * spectator, or one that never arrived -- and closes the duel instead.
     */
    /**
     * Says so when the duel is not waiting on you.
     * <p>
     * Without this a turn spent watching the opponent think is indistinguishable
     * from a duel that has silently stopped -- the board simply sits there and
     * nothing says whose move it is.
     * <p>
     * Shown only when it is genuinely THEIR turn and nothing is being asked of
     * this player. During your own turn the engine also pauses between actions,
     * and a banner appearing in those gaps would flicker on and off while you
     * play, which is worse than saying nothing.
     */
    private void renderWaiting(GuiGraphicsExtractor poseStack)
    {
        if(DuelClientState.over || DuelClientState.prompt != null || pickerOpen())
        {
            return;
        }
        BoardSnapshot board = currentBoard();
        if(board == null || board == BoardSnapshot.EMPTY || board.turnPlayer() == 0)
        {
            return;
        }
        String text = "Waiting for opponent";
        int textW = font.width(text);
        int boxW = textW + 16;
        int left = SIDEBAR_W + (width - SIDEBAR_W - boxW) / 2;
        int top = TOP_BAR_H + 6;
        poseStack.fill(left, top, left + boxW, top + 16, 0xA0101014);
        poseStack.text(font, text, left + 8, top + 4, 0xFFC2C9D6, true);
    }

    private void renderResult(GuiGraphicsExtractor poseStack)
    {
        if(!DuelClientState.over)
        {
            return;
        }
        dimBoard(poseStack, 0xC0000000);

        String outcome = DuelClientState.result == null ? "" : DuelClientState.result.trim();
        boolean won = outcome.equalsIgnoreCase("Victory");
        boolean drew = outcome.equalsIgnoreCase("Draw");
        String headline = won ? "VICTORY" : drew ? "DRAW" : "DEFEAT";
        int colour = won ? 0xFFD700 : drew ? 0xFFC2C9D6 : 0xFF4C4C;
        int bandH = 60;
        int bandTop = height / 2 - bandH / 2;

        poseStack.fill(0, bandTop, width, bandTop + bandH, 0xB0101014);
        poseStack.fill(0, bandTop, width, bandTop + 1, 0xC0000000 | colour);
        poseStack.fill(0, bandTop + bandH - 1, width, bandTop + bandH, 0xC0000000 | colour);

        poseStack.pose().pushMatrix();
        // The GUI matrix is 2D now; the z of the old three-argument scale had
        // nothing to scale.
        poseStack.pose().scale(3F, 3F);
        // The headline carries the same colour as the band's edges, which are
        // filled with an alpha of their own; as text it needs its own.
        // Centred in the band on both axes. The y used to be a fixed +12,
        // which sat the headline high on purpose to leave room for a second
        // line underneath it; that line is gone, so the offset that made sense
        // beside it now just looks off-centre. Derived from the band and the
        // font instead of being another number to keep in step by hand.
        float scale = 3F;
        int textTop = bandTop + Math.round((bandH - font.lineHeight * scale) / 2F);
        poseStack.centeredText(font, headline, Math.round(width / 2F / scale),
            Math.round(textTop / scale), 0xFF000000 | colour);
        poseStack.pose().popMatrix();

        // No countdown. The result screen takes over at RESULT_STINGER_MS and
        // says everything this banner was counting towards, so a "returning
        // in five" that vanished after under two seconds was telling the
        // player something that was not going to happen.
    }

    /** Idle: the phase can be reached from here. */
    private static final int PHASE_IDLE = 0;
    /** Lit: the phase the duel is in, letters glowing. */
    private static final int PHASE_LIT = 1;
    /** Disabled: unreachable, greyed and dimmed back into the case. */
    private static final int PHASE_DISABLED = 2;

    /**
     * One key of the phase indicator, taken from the PNG atlas: six columns by
     * three rows, the row chosen by state. Blue while you hold the turn, red
     * while the opponent does.
     */
    private void drawPhaseCell(GuiGraphicsExtractor poseStack, int x, int y, int w, int h, int index,
        int state, boolean yourTurn)
    {
        // Keep the sample points half a source texel inside this atlas cell.
        // Sampling exactly on a shared edge can borrow the bright outline from
        // the following phase while the cell is scaled, leaving a white pixel
        // in that phase's bay.
        float uInset = 0.5F / 1200F;
        float vInset = 0.5F / 120F;
        DdBlitUtil.blit(poseStack, yourTurn ? DuelTextures.PHASE_BLUE : DuelTextures.PHASE_RED,
            x, y, w, h,
            index / (float)PHASE_NAMES.length + uInset, state / 3F + vInset,
            (index + 1) / (float)PHASE_NAMES.length - uInset,
            (state + 1) / 3F - vInset, DdBlitUtil.NO_TINT);
    }

    /**
     * Which row a phase key takes. Being the current phase wins over being
     * offered: the core never offers a jump to the phase you are already in,
     * so reading "no button" as unreachable would grey out the live phase.
     */
    private int phaseState(BoardSnapshot board, int index)
    {
        if(isCurrentPhase(board, index))
        {
            return PHASE_LIT;
        }
        return phaseOptionFor(shownPrompt, PHASE_VALUES[index]) >= 0 ? PHASE_IDLE : PHASE_DISABLED;
    }

    /**
     * True only for a prompt that asks the player to pick a card on the board
     * -- slot options carrying no command. The aim pointer must not outlive
     * its question: a direct attack produces no target selection at all, so
     * the sword used to linger over whatever prompt came next.
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

    /** True for the phase the duel is actually in. */
    private static boolean isCurrentPhase(BoardSnapshot board, int index)
    {
        return board.phase() == PHASE_VALUES[index]
            || (PHASE_VALUES[index] == OcgConstants.PHASE_BATTLE
                && board.phase() > OcgConstants.PHASE_MAIN1
                && board.phase() < OcgConstants.PHASE_MAIN2);
    }

    /**
     * The phase indicator: a chrome case with six colour-coded bays. Blue while
     * you hold the turn, red while the opponent does; the current phase lights
     * up. Every part of it is a PNG under textures/duel, so the look can be
     * changed without touching this code.
     */
    private void renderPhaseBar(GuiGraphicsExtractor poseStack, BoardSnapshot board)
    {
        int x = phaseBarLeft();
        boolean yourTurn = board.turnPlayer() == 0;

        // The housing first, sitting a little proud of the bays.
        DdBlitUtil.fullBlit(poseStack, DuelTextures.PHASE_CASE,
            x - phasePadX(), PHASE_BAR_Y - phasePadY(),
            PHASE_NAMES.length * phaseCellW() + phasePadX() * 2, phaseCellH() + phasePadY() * 2);

        for(int i = 0; i < PHASE_NAMES.length; i++)
        {
            // A phase the core is offering gets a real button, added in
            // buildPhaseBar; this draws the ones that are only labels, which
            // is where an unreachable phase greys out.
            if(phaseOptionFor(shownPrompt, PHASE_VALUES[i]) < 0)
            {
                drawPhaseCell(poseStack, x + i * phaseCellW(), PHASE_BAR_Y, phaseCellW(),
                    phaseCellH(), i, phaseState(board, i), yourTurn);
            }
        }

        drawTurnClock(poseStack, x - phasePadX());
    }

    /** Under this much left, the clock reads as a warning rather than a fact. */
    private static final long CLOCK_WARN_MS = 60_000L;
    /** Clear of the case's angled nose, so the two do not read as one part. */
    private static final int CLOCK_GAP = 6;

    /**
     * The countdown to the answer deadline, at the left end of the phase bar.
     * <p>
     * It counts the clock the duel actually keeps: HumanResponseSource gives a
     * player TIMEOUT_MINUTES to answer the question in front of them, and the
     * run ends when that expires. So the clock restarts with each question,
     * which is what a player watching it will see -- it is not a budget for the
     * whole turn.
     */
    private void drawTurnClock(GuiGraphicsExtractor poseStack, int caseLeft)
    {
        if(shownPrompt == null || DuelClientState.promptShownAt == 0 || DuelClientState.over)
        {
            return; // nothing is being asked, so nothing is running out
        }
        long limit = java.util.concurrent.TimeUnit.MINUTES.toMillis(
            de.cas_ual_ty.dueldimension.ocg.prompt.HumanResponseSource.TIMEOUT_MINUTES);
        long left = limit - (System.currentTimeMillis() - DuelClientState.promptShownAt);
        // Never below zero: the abort lands on its own, and a clock counting
        // upwards past the deadline would say the opposite of what it means.
        long seconds = Math.max(0, (left + 999) / 1000);
        String clock = String.format("%d:%02d", seconds / 60, seconds % 60);
        int colour = left <= CLOCK_WARN_MS ? 0xFFC1362F : 0xFFFFFFFF;
        // Right-aligned onto the case, so a digit rolling over from two to one
        // does not walk the clock sideways.
        poseStack.centeredText(font, clock,
            caseLeft - CLOCK_GAP - font.width(clock) / 2,
            PHASE_BAR_Y + (phaseCellH() - font.lineHeight) / 2, colour);
    }

    /**
     * Life points across the top with the turn count between them, as the
     * reference client shows it. Centred on the table rather than the window:
     * the frustum is off-centre by design, so window-centred headers sit
     * visibly left of the board.
     */
    private void renderTopBar(GuiGraphicsExtractor poseStack, BoardSnapshot board)
    {
        int badgeW = 34;
        int left = SIDEBAR_W + 10;
        int right = width - 10;
        // Both players get the same bar: a wider one for you would misread as
        // an advantage at a glance.
        int barW = Math.max(60, (right - left - badgeW - 12) / 2);
        int badgeLeft = left + barW + 6;

        long now = System.currentTimeMillis();
        boolean yourTurn = board.turnPlayer() == 0;
        drawLifeBar(poseStack, left, 6, barW, DuelClientState.selfName, board.self().lifePoints(), 0xFF3FA34D,
            animations.lifePointState(0, board.self().lifePoints(), now), yourTurn, now);
        drawLifeBar(poseStack, right - barW, 6, barW, DuelClientState.opponentName,
            board.opponent().lifePoints(), 0xFFC1362F,
            animations.lifePointState(1, board.opponent().lifePoints(), now), !yourTurn, now);

        // Whose turn it is, said with colour instead of words: the badge and
        // the active player's bar carry it, so the label underneath (which the
        // phase row kept colliding with) is gone.
        int badgeTop = 4;
        int turnColour = yourTurn ? TURN_YOURS : TURN_THEIRS;
        poseStack.fill(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + 17, 0xC0101014);
        poseStack.fill(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + 1, 0xC0000000 | turnColour);
        poseStack.fill(badgeLeft, badgeTop + 16, badgeLeft + badgeW, badgeTop + 17, 0xC0000000 | turnColour);
        // TURN_YOURS/TURN_THEIRS are bare RGB, because the fills above pick
        // their own alpha for them. Text has to bring one or it draws nothing.
        poseStack.centeredText(font, Integer.toString(Math.max(1, board.turn())),
            badgeLeft + badgeW / 2, badgeTop + 5, 0xFF000000 | turnColour);
    }

    /**
     * EDOPro draws a frame texture (lpf.png, a fixed 200x20 source) and fills
     * it procedurally; lp.png is never drawn. Same here.
     */
    /** How much lighter the top of a life bar is than its colour, and darker the bottom. */
    private static final float LIFE_BAR_TOP = 1.35F;
    private static final float LIFE_BAR_BOTTOM = 0.62F;

    /**
     * The same colour, scaled, with its alpha kept.
     * <p>
     * Clamped per channel rather than scaled as a whole, so brightening a
     * colour that is already near full in one channel deepens its hue instead
     * of overflowing it.
     */
    private static int shade(int argb, float factor)
    {
        int alpha = argb >>> 24;
        int red = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
        int green = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
        int blue = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private void drawLifeBar(GuiGraphicsExtractor poseStack, int x, int y, int barW, String name, int lifePoints,
        int colour, DuelAnimations.LifePointState change, boolean active, long now)
    {
        int barH = 13;
        if(active)
        {
            // A slow breath around the bar of whoever is playing. Subtle enough
            // to ignore, bright enough to answer "whose turn is it" at a glance.
            // Three shells, each a pixel wider and fainter than the last, so
            // the edge fades out instead of stopping dead.
            float pulse = 0.30F + 0.18F * (float)Math.sin(now / 420D);
            float[] fade = {1F, 0.5F, 0.22F};
            for(int shell = 0; shell < fade.length; shell++)
            {
                int e = shell + 1;
                int glow = (Math.round(pulse * fade[shell] * 255) << 24) | 0xFFFFFF;
                poseStack.fill(x - e, y - e, x + barW + e, y - e + 1, glow);
                poseStack.fill(x - e, y + barH + e - 1, x + barW + e, y + barH + e, glow);
                poseStack.fill(x - e, y - e, x - e + 1, y + barH + e, glow);
                poseStack.fill(x + barW + e - 1, y - e, x + barW + e, y + barH + e, glow);
            }
        }
        int shownLifePoints = change.displayedLifePoints();
        int filled = lifeBarFill(barW, shownLifePoints);
        int targetFilled = lifeBarFill(barW, change.targetLifePoints());
        poseStack.fill(x + 2, y + 2, x + barW - 2, y + barH - 2, 0xFF101010);
        // Lit from above: the fill is brighter than its base colour at the top
        // and darker at the bottom, so the bar reads as a rounded surface
        // rather than a flat block. Procedural like the flat fill it replaces
        // -- EDOPro fills the frame procedurally too (see above), and a PNG
        // could not carry a colour that is chosen at runtime.
        poseStack.fillGradient(x + 2, y + 2, x + 2 + filled, y + barH - 2,
            shade(colour, LIFE_BAR_TOP), shade(colour, LIFE_BAR_BOTTOM));
        if(change.whiteAlpha() > 0 && filled != targetFilled)
        {
            int alpha = Math.round(change.whiteAlpha() * 255) << 24;
            int whiteLeft = Math.min(filled, targetFilled);
            int whiteRight = Math.max(filled, targetFilled);
            poseStack.fill(x + 2 + whiteLeft, y + 2, x + 2 + whiteRight, y + barH - 2,
                alpha | 0xFFFFFF);
        }

        // Straight, not through CardRenderUtil. The life-point frame is not card
        // art -- it is one shipped PNG, always on screen while a duel runs --
        // and the bind call it used to go through became a no-op the moment
        // textures stopped being global state. Now that the same call routes
        // card art through CardImageManager, sending a UI texture down it would
        // hand back the "unknown card" placeholder for the first frame or two
        // and then let the card LRU evict the frame off the HUD. This is
        // EDOPro's own rule: async iff the count is unbounded and driven by
        // what the player is looking at (image_manager.cpp:738-773).
        DdBlitUtil.fullBlit(poseStack, DuelTextures.LP_FRAME, x, y, barW, barH);

        poseStack.text(font, name, x + 5, y + 3, 0xFFFFFFFF, false);
        String value = Integer.toString(shownLifePoints);
        poseStack.text(font, value, x + barW - font.width(value) - 5, y + 3, 0xFFFFFFFF, false);
    }

    private static int lifeBarFill(int barW, int lifePoints)
    {
        return Math.max(0, Math.min(barW - 4, Math.round((barW - 4) * lifePoints / 8000F)));
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
    private void renderSidebar(GuiGraphicsExtractor poseStack)
    {
        poseStack.fill(0, 0, SIDEBAR_W, height, 0xD0101010);

        Properties card = previewCode == 0 ? null : DdDatabase.PROPERTIES_LIST.get((long)previewCode);
        if(card == null)
        {
            poseStack.centeredText(font, "Point at a card", SIDEBAR_W / 2, height / 2, 0xFF707070);
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

        // Sample the card out of its letterboxed square, or it stretches.
        DdBlitUtil.blit(poseStack,
            DuelTextures.cardSmooth(card, previewArt, DuelTextures.PREVIEW_CARD_SIZE),
            imageX, SIDEBAR_PAD, imageW, imageH,
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);

        // The name sits on its own plate under the art, the type line on an
        // accent chip, and the effect text in a bordered well -- the reference
        // layout's structure, drawn from scratch.
        int y = SIDEBAR_PAD + imageH + 4;
        List<net.minecraft.util.FormattedCharSequence> nameLines =
            font.split(Component.literal(card.getName()), textWidth - 4);
        int plateH = nameLines.size() * 9 + 4;
        poseStack.fill(SIDEBAR_PAD - 2, y - 2, SIDEBAR_W - SIDEBAR_PAD + 2, y + plateH - 2, 0xFF14161A);
        poseStack.fill(SIDEBAR_PAD - 2, y + plateH - 2, SIDEBAR_W - SIDEBAR_PAD + 2, y + plateH - 1,
            0xFFB08A2A);
        for(var line : nameLines)
        {
            poseStack.text(font, line, SIDEBAR_PAD + 2, y, 0xFFFFD700, false);
            y += 9;
        }
        y += 4;

        int descriptionBottom = logTop() - 4;

        List<Component> header = sidebarHeader(card, previewRace);
        poseStack.pose().pushMatrix();
        poseStack.pose().scale(0.75F, 0.75F);
        int scaledX = Math.round(SIDEBAR_PAD / 0.75F);
        int scaledY = Math.round(y / 0.75F) + 2;
        // The text spans the SIDEBAR, not the card image above it. It used to be
        // imageW, and imageW is only 70% of the column -- the preview is
        // deliberately small to give its height back to the description -- so
        // the words stopped a third of the way short of the panel edge and wrapped
        // far more than they needed to. The +2 border is what the well draws
        // outside this, so the span leaves room for it.
        int scaledW = Math.round((SIDEBAR_W - SIDEBAR_PAD) / 0.75F) - scaledX - 2;
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
                    poseStack.fill(scaledX - 2, scaledY - 2, scaledX + scaledW + 2, scaledY + 8,
                        0xFF1B222B);
                    poseStack.text(font, line, scaledX + 2, scaledY, 0xFFFFC864, false);
                }
                else
                {
                    poseStack.text(font, line, scaledX + 2, scaledY, 0xFFB0B0B0, false);
                }
                scaledY += 10;
            }
            typeChip = false;
        }
        scaledY += 2;
        // The effect text in its own well.
        int wellTop = scaledY - 3;
        poseStack.fill(scaledX - 2, wellTop, scaledX + scaledW + 2, limit + 3, 0xC0101318);
        poseStack.fill(scaledX - 2, wellTop, scaledX + scaledW + 2, wellTop + 1, 0x33FFFFFF);

        // The well scrolls, because a duel sidebar is not tall enough for the
        // cards that need reading most. It used to stop at the bottom of the
        // band and drop the rest, so a long effect ended mid-sentence with no
        // way to see the clause that decides whether you can play it.
        List<net.minecraft.util.FormattedCharSequence> textLines =
            font.split(Component.literal(card.getText()), scaledW - 8);
        int viewH = Math.max(DESCRIPTION_LINE_H, limit + DESCRIPTION_LINE_H - scaledY);
        descriptionMaxScroll =
            Math.max(0, textLines.size() * DESCRIPTION_LINE_H - viewH);
        if(previewCode != scrolledCode)
        {
            // A different card starts at the top rather than inheriting the
            // offset of whatever was pointed at before it.
            scrolledCode = previewCode;
            descriptionScroll = 0;
        }
        descriptionScroll = Math.min(descriptionScroll, descriptionMaxScroll);

        // Where the wheel has to be for this to be the thing that scrolls.
        // The pose is scaled, the mouse is not, so this is the one place the
        // two spaces have to be reconciled by hand.
        descriptionX0 = Math.round((scaledX - 2) * 0.75F);
        descriptionY0 = Math.round(wellTop * 0.75F);
        descriptionX1 = Math.round((scaledX + scaledW + 2) * 0.75F);
        descriptionY1 = Math.round((limit + 3) * 0.75F);

        // Clipped rather than line-skipped, so a partly visible line at either
        // edge is cut off cleanly and the text reads as one moving column.
        // enableScissor transforms by the current pose itself, so these are
        // the same coordinates everything else here is drawn in.
        poseStack.enableScissor(scaledX - 2, wellTop + 1, scaledX + scaledW + 2, limit + 3);
        int textY = scaledY - descriptionScroll;
        for(var line : textLines)
        {
            if(textY + DESCRIPTION_LINE_H > wellTop && textY < limit + DESCRIPTION_LINE_H)
            {
                poseStack.text(font, line, scaledX + 2, textY, 0xFFA8AEB4, false);
            }
            textY += DESCRIPTION_LINE_H;
        }
        poseStack.disableScissor();

        // A thumb, and only when there is somewhere to scroll to -- otherwise
        // every short card grows a scrollbar that does nothing.
        if(descriptionMaxScroll > 0)
        {
            int trackX = scaledX + scaledW;
            int trackTop = wellTop + 1;
            int trackH = limit + 3 - trackTop;
            int thumbH = Math.max(6, Math.round(
                trackH * (float)viewH / (textLines.size() * DESCRIPTION_LINE_H)));
            int thumbY = trackTop + Math.round((trackH - thumbH)
                * (descriptionScroll / (float)descriptionMaxScroll));
            // Wide enough to hit. Two pixels inside a 0.75 scale is a pixel and
            // a half on screen, which is a bar you can see but not catch.
            poseStack.fill(trackX, trackTop, trackX + BAR_W, trackTop + trackH, 0x50000000);
            poseStack.fill(trackX, thumbY, trackX + BAR_W, thumbY + thumbH, 0xFFB08A2A);

            // In GUI coordinates for the mouse, which does not live in the
            // 0.75 pose everything above is drawn in.
            barX0 = Math.round(trackX * 0.75F);
            barY0 = Math.round(trackTop * 0.75F);
            barX1 = Math.round((trackX + BAR_W) * 0.75F);
            barY1 = Math.round((trackTop + trackH) * 0.75F);
            barThumbH = Math.max(1, Math.round(thumbH * 0.75F));
        }
        else
        {
            barY1 = barY0;   // nothing to grab
        }
        poseStack.pose().popMatrix();
    }

    /**
     * Compact card facts for the duel sidebar.
     * <p>
     * A monster's race/species (Spellcaster, Dragon, Warrior...) normally
     * lives in {@code addMonsterTextHeader}, not {@code addHeader}. The old
     * sidebar used only the latter, so it showed "Normal Monster" and stats
     * while dropping one of the card's rules-relevant classifications.
     */
    static List<Component> sidebarHeader(Properties card)
    {
        return sidebarHeader(card, 0L);
    }

    /** The same, with the duel's own answer for what this card currently is. */
    static List<Component> sidebarHeader(Properties card, long liveRace)
    {
        return de.cas_ual_ty.dueldimension.clientutil.CardFacts.of(card, liveRace);
    }

    /**
     * The duel log lives in the sidebar, where EDOPro keeps its Log tab.
     * Drawing it over the field put text across the cards.
     */
    private void renderLog(GuiGraphicsExtractor poseStack)
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
        poseStack.fill(SIDEBAR_PAD, top, SIDEBAR_W - SIDEBAR_PAD, top + 1, 0x40FFFFFF);

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
            poseStack.text(font, line, x, y, 0xFF8A8A8A, false);
            y += 9;
        }
    }

    private void renderSelectionMarks(GuiGraphicsExtractor poseStack)
    {
        for(BoardRenderer.Hit hit : boardRenderer.hits())
        {
            for(int index : optionsFor(hit))
            {
                String mark = DuelSelection.has(index) ? "✔"
                    : sortOrder.contains(index) ? Integer.toString(sortOrder.indexOf(index) + 1) : null;
                if(mark != null)
                {
                    poseStack.fill(hit.x(), hit.y(), hit.x() + 10, hit.y() + 10, 0xC0000000);
                    poseStack.text(font, mark, hit.x() + 2, hit.y() + 1, 0xFF00FF66, false);
                }
            }
        }
    }

    private void renderPileView(GuiGraphicsExtractor poseStack, int mouseX, int mouseY)
    {
        int cardW = 34;
        int cardH = Math.round(cardW / DuelTextures.CARD_ASPECT);
        int columns = Math.max(1, Math.min(10, (width - SIDEBAR_W - 40) / (cardW + 4)));
        int rows = (pileView.size() + columns - 1) / columns;
        // The panel used to grow to whatever the pile needed, which was fine
        // while the only piles were graveyards. A forty-card deck is seven rows
        // and ran a hundred and fifty pixels off the bottom of the screen, so
        // the height is capped at what fits under the top bar and the remainder
        // is scrolled to.
        int visibleRows = Math.max(1, Math.min(rows,
            (height - TOP_BAR_H - 30) / (cardH + 4)));
        // Floored at zero, because visibleRows has a floor of 1 and rows does
        // not: an EMPTY pile is nought rows against one visible, and a negative
        // maximum makes the clamp below throw IllegalArgumentException rather
        // than draw an empty panel. Both callers currently refuse to open an
        // empty pile, so this is the guard that keeps a third one from turning
        // a blank panel into a crash.
        pileViewMaxScroll = Math.max(0, rows - visibleRows);
        pileViewScroll = Math.clamp(pileViewScroll, 0, pileViewMaxScroll);
        int panelW = columns * (cardW + 4) + 8;
        int panelH = visibleRows * (cardH + 4) + 26;
        int left = SIDEBAR_W + (width - SIDEBAR_W - panelW) / 2;
        int top = Math.max(TOP_BAR_H, (height - panelH) / 2);

        poseStack.fill(left, top, left + panelW, top + panelH, 0xF0100010);
        poseStack.centeredText(font, pileViewLabel
                + (pileViewMaxScroll > 0 ? " — scroll to see more, click to close" : " — click to close"),
            left + panelW / 2, top + 6, 0xFFFFD700);

        int first = pileViewScroll * columns;
        int last = Math.min(pileView.size(), first + visibleRows * columns);
        for(int i = first; i < last; i++)
        {
            BoardSnapshot.Slot slot = pileView.get(i);
            int cell = i - first;
            int x = left + 4 + (cell % columns) * (cardW + 4);
            int y = top + 20 + (cell / columns) * (cardH + 4);
            Properties card = slot.code() == 0 ? null : DdDatabase.PROPERTIES_LIST.get((long)slot.code());
            // Pointing at a card in the pile reads it in the sidebar, the same
            // as pointing at one on the field.
            if(slot.code() != 0 && mouseX >= x && mouseX < x + cardW + 4
                && mouseY >= y && mouseY < y + cardH + 4)
            {
                previewCode = slot.code();
                // Straight off the slot here: a pile view carries its own
                // cards, so the copy being pointed at is right there rather
                // than somewhere on the board to be looked up.
                previewRace = slot.race();
                previewArt = (byte)slot.art();
                DdBlitUtil.fullBlit(poseStack, DuelTextures.SLOT_ACTIVE,
                    x - 1, y - 1, cardW + 2, cardH + 2);
            }
            if(card == null)
            {
                DdBlitUtil.fullBlit(poseStack, DuelTextures.COVER, x, y, cardW, cardH);
            }
            else
            {
                DdBlitUtil.blit(poseStack,
                    DuelTextures.cardSmooth(card, (byte)slot.art(), DuelTextures.FIELD_CARD_SIZE),
                    x, y, cardW, cardH,
                    DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                    DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);
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
