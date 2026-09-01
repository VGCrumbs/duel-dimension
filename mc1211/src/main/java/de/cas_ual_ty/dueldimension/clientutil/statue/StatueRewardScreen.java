package de.cas_ual_ty.dueldimension.clientutil.statue;

import de.cas_ual_ty.dueldimension.clientutil.Layering;

import de.cas_ual_ty.dueldimension.clientutil.hub.MenuInk;
import de.cas_ual_ty.dueldimension.clientutil.hub.HubTextures;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.clientutil.BoardPip;
import de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil;
import de.cas_ual_ty.dueldimension.clientutil.FoilPipelines;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dawn of Destiny reward screen: three podiums, three god statues, pick one.
 *
 * <h2>Where the layout comes from</h2>
 * Not from a table, and not from measuring a screenshot. There is no layout data
 * anywhere on the disc -- {@code .rdata} holds one 640.0 and one 480.0 between
 * them, and {@code .data} holds neither. Every coordinate on this screen is a
 * compiled-in immediate in the screen's own constructor,
 * {@code CRewardScreen::ctor} at VA 0x00025810, which is identified beyond doubt
 * as the only function that calls the 3D scene builder at 0x00100C40 -- the
 * function that loads {@code menu_m_03/10/11/12/13}.
 * <p>
 * So the numbers below are readouts, with the disassembly address for each:
 * <pre>
 * background    menu\2d\menu_01_07.png  (0,0) stretched 640x480   @0x25A38
 *             + menu\2d\menu_01_08.png  (0,0) stretched 640x480   @0x25AD7
 * bottom trim   comm\2d\comm_09_00.png  (0,339) native 640x141    @0x25D7E
 * pointer L     menu\2d\menu_01_06.png  (165,140) native 57x58    @0x25BCD
 * pointer R     the same sprite, u-mirrored, at (418,140)         @0x25C1C
 * message box   548 wide, 3-slice 43/14/32, CENTRED on (320,409)
 * camera        eye (0,9,59) at (0,4,0), 45 degrees horizontal
 * </pre>
 * Both backdrop layers being 320x240 stretched to 640x480 is the whole
 * explanation for the soft-focus look -- it is a 2x bilinear upscale, not a
 * blur and not a filtering mistake.
 *
 * <h2>Why a 640x480 canvas</h2>
 * The game stores a HORIZONTAL field of view and derives the vertical from a
 * fixed 4:3. At any other aspect those two cannot both hold, so the whole screen
 * -- 2D and 3D alike -- is laid out on a 640x480 canvas scaled uniformly and
 * centred. The backdrop alone is stretched over the entire window, so the
 * letterbox margins are more backdrop rather than bars.
 *
 * <p><b>This does not close itself.</b> Same rule as {@code DuelResultScreen} --
 * what the player won is the thing they came to look at, and a screen that walks
 * off while it is being read is worse than one more click.
 */
public final class StatueRewardScreen extends Screen
{
    /**
     * How tall a row of capitals and digits actually is, in pixels.
     * <p>
     * Seven, not the nine of {@code lineHeight} and not the eight of the glyph
     * cell. The vanilla font's baseline sits at y+7 and a capital occupies rows
     * y..y+6; the rest of the cell is the descender gap, which a label like
     * "DE 0" never uses. Anything centring such a row against a container wants
     * this number.
     */
    private static final int INK_HEIGHT = 7;


    private static ResourceLocation gui(String path)
    {
        return ResourceLocation.fromNamespaceAndPath(
            de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID, "textures/gui/statue/" + path);
    }

    /** menu_01_06, the original's gold bevelled pointer. Points LEFT. */
    private static final ResourceLocation ARROW = gui("arrow.png");
    /**
     * menu_01_07 with menu_01_08 composited on top, baked to one file.
     * <p>
     * The two are separate sprites in the game, drawn one after the other at the
     * same place and size but with different values in the sprite's mode field
     * (2 and 12). That enum is NOT decoded. Of the blends it could plausibly be,
     * only adding the second at half strength reproduces the capture: straight
     * alpha would hide the first layer entirely, since menu_01_08 is fully
     * opaque; full additive blows out to white; multiply goes black.
     * <p>
     * Baked rather than composited here because nothing in the constructor
     * animates either layer -- both are placed once at (0,0) and never touched.
     */
    /**
     * The backdrop, as the game has it: TWO layers, not the bake.
     * <p>
     * `menu_01_07` is a dark olive-gold lattice of raised blocks in perspective
     * and `menu_01_08` is a bright cream tile floor, and the reward screen
     * pushes them in that order at 0x25A38 and 0x25AD7, both 320x240 stretched
     * to 640x480. They were baked into one `background.png` while the blend was
     * unknown; it IS the right composite -- measured against these two it is the
     * sum to within 1.7 of 255 -- but a bake cannot have one layer moving inside
     * it, which is what the lattice is supposed to do. So they go back to being
     * two, and the composite is rebuilt at draw time instead of at build time.
     */
    private static final ResourceLocation LATTICE = gui("bg_lattice.png");
    private static final ResourceLocation TILES = gui("bg_tiles.png");
    /** The middle of menu_01_00, the disc's own gold plaque. See drawDone. */
    private static final ResourceLocation DONE = gui("button.png");
    /** comm_09_00, the ornate band across the bottom. Sits flush: 339 + 141 = 480. */
    private static final ResourceLocation TRIM = gui("trim.png");
    /** menu_02_01/02/03 -- the game's own three-piece message window. */
    private static final ResourceLocation DIALOG_TOP = gui("dialog_top.png");
    private static final ResourceLocation DIALOG_BODY = gui("dialog_body.png");
    private static final ResourceLocation DIALOG_BOTTOM = gui("dialog_bottom.png");

    /**
     * The game's own wording. String id 0x6F, pushed at VA 0x001008AD; id 0x70 is
     * "You got new cards!", which is the screen's other state.
     */
    private static final String PROMPT = "Which do you want?";
    /** The screen's other state, string id 0x70, pushed at VA 0x00100A88. */
    private static final String PROMPT_TAKEN = "You got new cards!";

    /**
     * The game's own typeface, out of default.xbe's FONT_BIN.
     * <p>
     * Band <b>2</b> of the three -- cell 24x20, baseline 19.
     * <p>
     * Which band the game uses here is <b>not</b> established: the prompt is
     * pushed as string id 0x6F into a virtual call at VA 0x001008AF, and the
     * font id is a member of the window rather than an immediate, so there is
     * nothing to read. Band 1 was the first guess, on the reasoning that its
     * 259 px in a 548 px window matched the capture; rendered, it came out too
     * large. Band 2 measures 189 px, and its 20 px cell is the one that suits a
     * 43/14/32 window. If it is still off, band 0 is the other candidate at
     * 162 px.
     */
    private static final ResourceLocation DOD_FONT = ResourceLocation.fromNamespaceAndPath(
        de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID, "dod_2");

    // ---- the canvas ----

    private static final float CANVAS_W = 640F;
    private static final float CANVAS_H = 480F;

    // ---- everything on it, in canvas pixels ----

    private static final float ARROW_W = 57F;
    private static final float ARROW_H = 58F;
    private static final float ARROW_LEFT_X = 165F;
    private static final float ARROW_RIGHT_X = 418F;
    private static final float ARROW_Y = 140F;
    /**
     * How far each pointer drifts outward, in canvas pixels.
     * <p>
     * <b>15.0, read out of the animation itself</b> at VA 0x0010025F, which
     * multiplies the wave by the constant at {@code .rdata 0x00427C38}. The
     * 14.698 that used to be here was a live memory sample caught mid-motion --
     * close, and for the same reason not the number.
     * <p>
     * The whole function is at VA 0x00100243: it advances a phase by 0.03 per
     * frame ({@code .rdata 0x00453698}), wraps it at 1.0, folds it into a
     * TRIANGLE wave about 0.5, multiplies by 15, then sets
     * {@code left = 165 - offset} and {@code right = 418 + offset} with y fixed
     * at 140. Those two base positions are {@code .rdata 0x00453694} and
     * {@code 0x00453690} and they match the constructor exactly.
     * <p>
     * Linear, not sinusoidal. The cosine that used to be here was a guess, and
     * it moves visibly differently through the turn.
     */
    /**
     * How far the backdrop drifts, as a fraction of its own height.
     *
     * <h2>Both numbers are the disc's, and neither was guessed</h2>
     * This game animates a texture through `igUvAnimeShader`, and `menu_m_14.igb`
     * — the menu set's effect layer — carries nine of them. Each declares a
     * **2.0 second** loop (slot 13, and slot 14 says 2,000,000,000 ns) stepping
     * at **1/30 s** (slots 15 and 16), driving an `igTextureMatrixAttr` through
     * four key channels. Three of those channels are constants, and one of them
     * is **0.03125** — one thirty-second, in UV.
     * <p>
     * So the cadence and the amplitude are read out of the game's own animation
     * data rather than chosen. On the 320x240 source that is 7.5 px, which the
     * screen's 2x upscale makes 15 — the same 15 the pointers travel.
     *
     * <h2>What IS a choice, said plainly</h2>
     * That this drift is applied to the BACKDROP. The reward screen's own code
     * does not animate it: the constructor stores the two sprites at
     * `[this+0x2c]` and `[this+0x30]` and neither field is read again anywhere
     * in the screen — not the six-state update at `0x100880`, not any of its
     * helpers. That was checked by disassembly, and it is written up in
     * `35 The reward screen, read from the XBE.md`.
     * <p>
     * The last attempt at this drifted a THIRD copy of the tile layer over the
     * composite, which put a second helping of cream on it and flattened the
     * cubes — the opposite of the intent. This drifts the composite ITSELF, so
     * nothing is added, nothing brightens, and the lattice — the part with depth
     * in it — is what visibly moves.
     */
    private static final float BACKDROP_DRIFT = 1F / 32F;

    /** The 2.0 s loop `igUvAnimeShader` declares. See {@link #BACKDROP_DRIFT}. */
    private static final long BACKDROP_CYCLE_MS = 2000L;

    /**
     * How long the tile floor takes to travel its own WIDTH once, in ms.
     * <p>
     * Across, not down. And the tiles, not the lattice: `menu_01_08` is the
     * layer the game pushes second and adds on top, and it is the one that
     * moves -- the lattice behind it is the fixed backdrop the movement is read
     * against. Moving the lattice instead made the fixed thing drift and left
     * the moving thing nailed down, which is the same effect run backwards.
     * <p>
     * It WRAPS rather than folding back, so there is no turning point and no
     * moment where it stalls. The rate is the two measured numbers multiplied
     * out: 1/32 of the texture every 2.0 s, so a full pass takes 32 of those.
     * Sixty-four seconds is slow enough that nobody watches it move and long
     * enough that nobody sees it repeat.
     */
    private static final long TILE_WRAP_MS =
        (long) (BACKDROP_CYCLE_MS / BACKDROP_DRIFT);

    private static final float ARROW_TRAVEL = 15.0F;

    /**
     * One out-and-back, in milliseconds.
     * <p>
     * The phase steps 0.03 per FRAME and wraps at 1.0, so the cycle is 1/0.03 =
     * 33.33 frames. The disc runs at 60 Hz -- the update function multiplies by
     * 16,666,667 ns at VA 0x00100A1C -- which puts the period at 555.6 ms.
     */
    private static final long ARROW_CYCLE_MS = 556L;

    /**
     * How far in front of the statues the interface sits, in GUI depth units.
     * <p>
     * Past {@code BoardPip}'s own push and the depth band inside it, and short
     * of the 400 vanilla reserves for tooltips.
     */
    private static final float UI_FORWARD = 300F;


    /**
     * A reward card at its LARGEST, on the canvas. The proportions are the real
     * card's, 59x86.
     * <p>
     * A ceiling, not a size. A pack is not a fixed number of cards -- the sets on
     * this server range from a few to a dozen -- so a row laid out at a fixed
     * width runs off both edges of the screen as soon as the pack is generous,
     * which is exactly what it did. {@link #cardRow} shrinks to fit and only
     * uses this when there is room.
     */
    private static final float CARD_MAX_W = 110F;
    /**
     * Height per unit width, from the mod's own {@code DuelTextures.CARD_ASPECT}
     * -- which is stored the other way up, as width over height, so this is its
     * reciprocal. Taken from there rather than written out again so a card on
     * this screen cannot drift out of proportion with a card anywhere else.
     */
    private static final float CARD_ASPECT =
        1F / de.cas_ual_ty.dueldimension.clientutil.DuelTextures.CARD_ASPECT;
    /** Between cards, as a fraction of the card's width, so it shrinks with them. */
    private static final float CARD_GAP_FRACTION = 0.125F;
    /** The canvas is 640 wide; this leaves a margin either side. */
    private static final float CARD_ROW_MAX_W = 600F;
    /** The row is centred on this line, so it stays put as the cards resize. */
    private static final float CARD_ROW_CENTRE_Y = 220F;
    /** How long between one card landing and the next. */
    private static final long CARD_DEAL_MS = 260L;

    /**
     * The backdrop's top-down darkening.
     * <p>
     * Alpha only -- the colour is black at both ends, so this is a multiply by
     * (1 - alpha) and nothing else. It runs out three quarters of the way down
     * rather than at the very bottom, because the bottom quarter is the trim and
     * the message window, which have their own art and do not want dimming.
     */
    private static final int VIGNETTE_TOP = 0xB4000000;
    private static final int VIGNETTE_BOTTOM = 0x00000000;
    private static final float VIGNETTE_SPAN = 0.75F;

    private static final float TRIM_Y = 339F;
    private static final float TRIM_W = 640F;
    private static final float TRIM_H = 141F;

    /** Centre-anchored on both axes, which is how the window's position is stored. */
    private static final float WINDOW_CENTRE_X = 320F;
    /**
     * 415, not the disc's 409.
     * <p>
     * The one placement on this screen that is deliberately NOT the readout. Six
     * pixels lower sits the window further into the trim, which is where it
     * looks right here -- the original's trim is a television-safe band and ours
     * runs to the edge of a monitor.
     */
    private static final float WINDOW_CENTRE_Y = 415F;
    private static final float WINDOW_W = 548F;
    private static final float WINDOW_TOP_H = 43F;
    private static final float WINDOW_BODY_H = 14F;
    private static final float WINDOW_BOTTOM_H = 32F;

    /**
     * Minecraft puts every font provider's baseline at {@code y + 7}, whatever
     * that provider's own metrics are -- that is what the {@code ascent} field in
     * the font JSON exists to line up. Band 2's baseline is 19, so the text
     * extends 12 px above the y passed to {@code text()}.
     */
    private static final float MC_BASELINE_IN_LINE = 7F;
    /**
     * Where the prompt's baseline goes, in canvas pixels. Band 2's glyphs run
     * from 19 above the baseline to about 2 below it, so a baseline of 417 puts
     * their visual centre on the window's own centre of 409.
     */
    private static final float PROMPT_BASELINE_Y = 423F;
    /**
     * How far band 2's ink sits above its baseline, at its middle.
     * <p>
     * Its glyphs run from 19 above the baseline to about 2 below, so their
     * visual centre is 8 above it. Used to centre a line on a box rather than
     * on its own baseline.
     */
    private static final float DOD_VISUAL_CENTRE = 8F;
    /**
     * Where "Done" centres, measured rather than assumed.
     * <p>
     * Its ink runs from 13 above the baseline to 0 below -- no descenders in
     * those four letters -- so the middle is 6.5 above, not the 8 that suits a
     * line with a 'y' in it. Guessing 8 here put the label a pixel and a half
     * low, which at the plaque's scale was plainly visible.
     */
    private static final float DONE_INK_CENTRE = 6.5F;

    /**
     * How far the whole arrangement has been turned, in degrees, and where it is
     * heading.
     * <p>
     * Selecting a god IS rotating the carousel. The three podiums sit 120 degrees
     * apart on one circle, so bringing a statue to the front is a turn of a whole
     * step -- there is no separate "selection" to track, and the chosen one is
     * simply whichever is nearest the camera.
     * <p>
     * Neither value is wrapped into 0..360. A step is always exactly +/-120 in the
     * direction pressed, so the difference is never more than one step and easing
     * straight towards the target always takes the short way round. Wrapping would
     * reintroduce the shortest-arc problem it was meant to solve.
     */
    private float sceneYaw;
    private float sceneYawTarget;
    /** When the rotate click last sounded; see {@link #step}. */
    private long lastRotateSoundAt;

    /** The screen does not get a delta time otherwise. */
    private long lastFrameAt = System.currentTimeMillis();

    /**
     * How long one 120-degree step takes.
     * <p>
     * 30 frames at 60 Hz, both read: the 30 is passed to the tween helper at VA
     * 0x00100159, and the frame length is the 16,666,667 ns at 0x00100A1C.
     */
    private static final long ROTATE_MS = 500L;

    /** The shortest gap between two rotate clicks. See {@link #step}. */
    private static final long ROTATE_CLICK_GAP_MS = 100L;

    /** Over how many degrees of the approach the speed eases down. */
    private static final float EASE_DEGREES = 45F;
    /**
     * The slowest it may get, as a fraction of full speed.
     * <p>
     * Without a floor the ease is asymptotic and the last degree takes forever.
     */
    private static final float EASE_FLOOR = 0.18F;

    /** True once a god has been chosen, which is what the screen asks for. */
    private boolean chosen;

    /**
     * The light editor, open or not. Null in a shipped jar -- see
     * {@link StatueLightEditor#available()}.
     */
    private StatueLightEditor lightEditor;

    /**
     * What the choice paid, once the server has said.
     * <p>
     * Empty until then, and the screen shows the statues in the meantime rather
     * than a gap -- the round trip is short but it is not instant, and a screen
     * that blanks while it waits looks broken.
     */
    private List<CardHolder> granted = List.of();

    /**
     * Per granted card, whether the collection did NOT already hold it.
     * <p>
     * The server's answer, taken before it added them -- by the time this screen
     * sees the profile the cards are already in it. Kept in step with
     * {@link #granted}, which skips codes this client's database does not know,
     * so the flag is filtered alongside the card rather than indexed into the
     * packet's own list.
     */
    private List<Boolean> grantedFresh = List.of();

    /**
     * When the cards arrived, for the deal-in.
     * <p>
     * Wall clock rather than a tick count, for the same reason everything else
     * on this screen uses it: a GUI screen is not ticked at a rate its animation
     * can rely on.
     */
    private long grantedAt;

    /** Why the last offering was turned down, or empty. */
    private String refusal = "";

    /** How many cards have had their sound. See {@link #drawGranted}. */
    private int dealtSoFar;

    /** First visible line of the held card's text, and which card it belongs to. */
    private int peekScroll;
    private CardHolder peekOf;

    /**
     * The card the context menu was opened on, and where it sits. Null when the
     * menu is closed.
     */
    private CardHolder menuCard;
    private int menuX;
    private int menuY;

    /** Row geometry, the deck editor's own, so the two menus look like one. */
    private static final int MENU_ROW = 14;
    private static final int MENU_PAD = 7;
    private static final int MENU_EDGE = 5;

    /** How small the held-card panel's text is drawn. See drawHeldCardInfo. */
    private static final float PEEK_TEXT_SCALE = 0.5F;
    /** Its widest, before a narrow window shrinks it. */
    private static final int PEEK_WIDTH = 300;
    /** Gap between the panel and the bottom edge. */
    private static final int PEEK_MARGIN = 6;

    /**
     * The open screen, so a packet can reach it.
     * <p>
     * A static handle rather than a search through the screen stack: the network
     * handler runs before anything else knows a reward is coming, and there is
     * only ever one of these open. Cleared in {@link #removed()} so a packet
     * arriving after the screen has gone finds nothing rather than reviving it.
     */
    private static StatueRewardScreen open;

    private final StatueRenderer renderer = new StatueRenderer();

    public StatueRewardScreen()
    {
        super(Component.literal("Choose your reward"));
        // Every time the screen is opened, so reopening it re-reports rather than
        // needing a restart to ask the question again.
        StatueRenderer.diagnose = true;
        StatueMusic.start();
        open = this;
    }

    /**
     * Puts the close button in the corner.
     * <p>
     * The original has no such thing -- it is a console screen, and B backs out
     * of it. This one is reachable from the shop with a mouse, and Escape alone
     * is not a visible affordance. It is deliberately a plain widget in the
     * corner rather than dressed-up art: the disc has no button graphic for
     * this, and inventing one would be a worse lie than an honest control.
     */
    /**
     * Where the balance plate and the Done plaque go, in canvas pixels.
     * <p>
     * Canvas units rather than screen ones so both keep their proportion to the
     * rest of the screen when the window resizes, but ANCHORED to the window's
     * corners rather than the canvas': the canvas is pillarboxed on a wide
     * monitor and a Done button floating in from the edge looks misplaced, where
     * the trim it sits on reaches the edge.
     */
    private static final float BADGE_W = 74F;
    private static final float BADGE_H = 22F;
    private static final float DONE_W = 116F;
    private static final float DONE_H = 38F;
    private static final float CORNER = 8F;

    /**
     * Stops the music when the screen goes away.
     * <p>
     * Overridden as well as {@link #onClose()} because a screen can be REPLACED
     * without being closed -- Minecraft calls removed() either way. Hooking only
     * onClose leaves the track playing over whatever comes next, which is the
     * sort of thing that only shows up when somebody navigates away in the one
     * way nobody tested.
     */
    @Override
    public void removed()
    {
        StatueMusic.halt();
        if(open == this)
        {
            open = null;
        }
        super.removed();
    }

    // ---- canvas to screen ----

    /** Uniform, so nothing on this screen is ever stretched out of proportion. */
    private float canvasScale()
    {
        return Math.min(width / CANVAS_W, height / CANVAS_H);
    }

    private int canvasLeft()
    {
        return Math.round((width - CANVAS_W * canvasScale()) / 2F);
    }

    private int canvasTop()
    {
        return Math.round((height - CANVAS_H * canvasScale()) / 2F);
    }

    /** A canvas x in screen pixels. */
    private int cx(float x)
    {
        return canvasLeft() + Math.round(x * canvasScale());
    }

    /** A canvas y in screen pixels. */
    private int cy(float y)
    {
        return canvasTop() + Math.round(y * canvasScale());
    }

    /** A canvas length in screen pixels. */
    private int cs(float v)
    {
        return Math.round(v * canvasScale());
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX,
        int mouseY, float partialTick)
    {
        // 26.2 describes itself into a render state; 1.21.1 draws now. The body is
        // unchanged -- it is handed the compatibility surface over the real
        // GuiGraphics, exactly as DuelResultScreen is.
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(vanillaGraphics);
        advance();
        // The music's intro-to-loop handover, on the wall clock. Every frame,
        // because a tick-scheduled handover drifts and leaves a gap; see
        // StatueMusic.
        StatueMusic.pump();

        // The backdrop covers the WHOLE window rather than the canvas, so the
        // letterbox margins either side of a 4:3 canvas on a wide monitor show
        // more backdrop instead of black bars. It is an abstract lattice, so
        // stretching it is unnoticeable in a way that stretching anything else
        // on this screen would not be.
        // THE LATTICE, STILL. It is the backdrop the movement is read against.
        DdBlitUtil.blit(graphics, LATTICE, 0, 0, width, height,
            0F, 0F, 1F, 1F, DdBlitUtil.NO_TINT);
        graphics.vanilla().flush();

        // THE TILES, ADDED AT HALF STRENGTH, SCROLLING SIDEWAYS AND WRAPPING.
        //
        // Additive because that is what the original does: of the plausible
        // blends only "add the second at half strength" reproduces a capture --
        // it cannot be plain alpha, since `01_08` is opaque and would hide the
        // lattice entirely, and full additive blows out while multiply goes
        // black. The tint carries the half; the pipeline carries the add.
        //
        // Two blits split at the seam rather than one with UVs outside [0, 1]:
        // GUI textures clamp, so a window that ran off the edge would smear the
        // last COLUMN of pixels across the screen instead of coming round
        // again. Split, each piece samples only what is there, and the join is
        // exact because the two share an edge.
        float scroll = tileScroll();
        int split = Math.round(width * (1F - scroll));
        FoilPipelines.ADDITIVE.apply();
        // The tail of the texture, filling from the left edge.
        if(split > 0)
        {
            DdBlitUtil.blit(graphics, TILES, 0, 0, split, height,
                scroll, 0F, 1F, 1F, 0xFF808080);
        }
        // And the head of it, coming round on the right.
        if(split < width)
        {
            DdBlitUtil.blit(graphics, TILES, split, 0, width - split, height,
                0F, 0F, scroll, 1F, 0xFF808080);
        }
        graphics.vanilla().flush();
        FoilPipelines.reset();
        // ONE LAYER AT A TIME, AND IT HAS TO BE SAID OUT LOUD.
        //
        // These three -- the baked composite, the drifting tiles over it, and
        // the darkening over both -- are a stack, and a stack only works if it
        // is drawn in order. GuiGraphics does not do that: it fills one buffer
        // per render type and resolves the lot at the next flush, in type
        // order, and two blits of two different textures are two types. So the
        // shimmer was coming out UNDER the composite it is meant to lift, which
        // is the cube effect going missing rather than being subtle.
        //
        // 26.2 never meets this; its extractor keeps submission order. See
        // mc1211/README.md, "GuiGraphics draws in TYPE order".
        graphics.vanilla().flush();

        // NO SHIMMER LAYER, and no second copy of anything. The backdrop moves
        // by being SAMPLED from a moving window -- see BACKDROP_DRIFT -- which
        // is one blit, adds no light, and cannot cover what it sits on.
        //
        // The backdrop is two sprites in the original -- `menu_01_07`, a dark
        // olive-gold lattice of raised blocks in perspective, and `menu_01_08`,
        // a bright cream tile floor -- and BACKGROUND is the two of them baked
        // together, the second added at half strength. That composite is
        // correct: measured against the two layers it ships beside, it is the
        // sum to within 1.7 of 255, which is rounding.
        //
        // Drawing the tiles AGAIN on top, drifting, put a third helping of
        // cream over a composite that already had one, and the lattice -- the
        // cubes, the part with any depth to it -- washed out to a flat grid.
        //
        // The open question that used to sit here -- whether anything outside the
        // constructor animates the backdrop -- has since been ANSWERED, and the
        // answer is no: neither sprite object is touched again anywhere in the
        // screen. What the same investigation did find is that this game
        // animates textures through `igUvAnimeShader`, on a 2.0 s loop with a
        // 1/32 UV amplitude, and those two numbers are what BACKDROP_DRIFT uses.
        // Applying them to the backdrop is a choice; the numbers are not.
        // The backdrop's darkening, heaviest along the top.
        //
        // Black at alpha a over a background IS a multiply: the result is
        // bg * (1 - a). So an ordinary alpha gradient from black to nothing
        // reproduces the original's multiply exactly, and none of the awkwardness
        // of getting a real multiply blend into a GUI pass is needed.
        //
        // Drawn over the backdrop and UNDER the models, because it belongs to the
        // backdrop: the statues are lit by their own rig and darkening them here
        // would be dimming them twice.
        graphics.fillGradient(0, 0, width, Math.round(height * VIGNETTE_SPAN),
            VIGNETTE_TOP, VIGNETTE_BOTTOM);

        // The models fill the WHOLE window, not the 4:3 canvas the 2D uses.
        // The 2D coordinates are the disc's own and only mean anything at 640x480;
        // the camera is fitted, and fitVerticalFov re-derives the lens from the
        // frame box every frame so a resized window reframes instead of cropping.
        // Boxing the 3D into the canvas as well was part of what made the last
        // build look wrong.
        float fov = StatueCamera.fitVerticalFov(StatueScene.CAMERA_EYE,
            StatueScene.CAMERA_TARGET, StatueScene.FRAME_BOX,
            height <= 0 ? 1F : (float)width / height, StatueScene.FRAME_MARGIN);
        // The statues stop being drawn once the cards are up. They are what the
        // question was about, and the question has been answered.
        if(granted.isEmpty())
        {
            BoardPip.draw(graphics, 0, 0, width, height,
            (poseStack, collector) ->
            {
                StatueCamera camera = new StatueCamera(StatueScene.CAMERA_EYE,
                    StatueScene.CAMERA_TARGET, fov, width, height);
                renderer.draw(poseStack, collector, camera, build(), 0);
            });
        }

        // IN FRONT OF THE STATUES, WHICH ARE IN FRONT OF THE BACKDROP.
        //
        // BoardPip pushes what it draws forward so the screen's depth buffer
        // cannot eat it -- see the note there -- which puts the models ahead of
        // everything drawn at the GUI's own z, the interface included. They were
        // standing over the Done button.
        //
        // So the interface is pushed further still: past the pip and its depth
        // band, and short of the 400 vanilla reserves for tooltips, which have
        // to stay on top of this as they do of everything.
        vanillaGraphics.pose().pushPose();
        vanillaGraphics.pose().translate(0F, 0F, UI_FORWARD);

        // The ornate band, flush to the bottom of the canvas. The message window
        // sits INSIDE it -- 339..480 for the trim against 364..454 for the
        // window -- which is why the trim reads as the window's frame and why
        // its medallions land either side rather than behind it.
        drawTrim(graphics);

        drawGranted(graphics);
        // Decided before the window is drawn, because the window is what it
        // replaces: a description panel and a "You got new cards!" box in the
        // same band would sit on top of each other.
        //
        // Gated on SHIFT rather than on there being a description to show. The
        // box used to reappear the moment the cursor slipped off a card, which
        // meant reading along a row made it flicker in and out -- holding shift
        // is the gesture, so holding shift is what clears the band.
        CardHolder peek = shiftHeld() ? hoveredCard(mouseX, mouseY) : null;
        if(!shiftHeld() || granted.isEmpty())
        {
            drawMessageWindow(graphics);
        }

        // Pointers either side of the near statue. menu_01_06 points left, and
        // the game makes the right-hand one by overwriting that sprite's U range
        // only -- a pure horizontal mirror, no rotation and no vertical flip --
        // so the same is done here rather than shipping a second file.
        //
        // Both the drawing and the click test go through arrowRect, so the target
        // cannot drift away from the picture.
        int[] leftRect = arrowRect(true);
        int[] rightRect = arrowRect(false);
        // Before the early return below, not after it. Both belong to the
        // screen rather than to the choosing part of it, and drawing them after
        // a return that fires the moment cards arrive is why the plaque vanished
        // exactly when there was something to click Done about.
        drawBalance(graphics);
        drawDone(graphics, mouseX, mouseY);

        if(!granted.isEmpty())
        {
            // Nothing left to point at.
            //
            // Both go in front of the screen rather than merely after it: the
            // whole interface here is already pushed forward by UI_FORWARD, so
            // "after" buys nothing against the pieces of it drawn at the same
            // depth. See GuiGraphicsExtractor.foreground.
            CardHolder held = peek;
            Layering.foreground(graphics, () -> drawHeldCardInfo(graphics, held));
            Layering.foreground(graphics, () -> drawMenu(graphics, mouseX, mouseY));
            super.render(vanillaGraphics, mouseX, mouseY, partialTick);
            vanillaGraphics.pose().popPose();
            return;
        }
        DdBlitUtil.blit(graphics, ARROW,
            leftRect[0], leftRect[1], leftRect[2], leftRect[3],
            0F, 0F, 1F, 1F, arrowTint(hit(leftRect, mouseX, mouseY)));
        DdBlitUtil.blit(graphics, ARROW,
            rightRect[0], rightRect[1], rightRect[2], rightRect[3],
            1F, 0F, 0F, 1F, arrowTint(hit(rightRect, mouseX, mouseY)));

        if(lightEditor != null)
        {
            lightEditor.draw(graphics, font, width, height);
        }
        super.render(vanillaGraphics, mouseX, mouseY, partialTick);
        vanillaGraphics.pose().popPose();
    }

    /**
     * The ornate band, spanning the whole window rather than the canvas.
     * <p>
     * It is the one element that should reach both edges: pillarboxed with the
     * rest of the canvas it stops short and the screen reads as a picture inside
     * a frame instead of a screen.
     * <p>
     * <b>Stretched horizontally only.</b> Its height is the canvas height it
     * would have had anyway, so the band is exactly as thick as the layout says
     * and 339 + 141 = 480 still puts its bottom flush; only the width is pulled
     * out to the window. An earlier attempt scaled it uniformly and cropped the
     * excess off the top, which kept the medallions round but made the band
     * visibly thicker than the message window it frames.
     */
    private void drawTrim(GuiGraphicsExtractor graphics)
    {
        DdBlitUtil.blit(graphics, TRIM, 0, cy(TRIM_Y), width, cs(TRIM_H),
            0F, 0F, 1F, 1F, DdBlitUtil.NO_TINT);
    }

    /**
     * The cards the choice paid, laid out across the middle of the canvas.
     * <p>
     * They deal in one after another rather than appearing together, on the
     * same 2 Hz-ish beat a pack opening uses elsewhere in the mod: a row that
     * arrives all at once reads as a screenshot, and one that arrives in order
     * reads as a reward.
     * <p>
     * Sized off the canvas rather than the window, so a card is the same
     * fraction of the screen whatever the window is doing. The row is centred on
     * the canvas centre line and sits above the message window, in the space the
     * statues had been occupying -- which is the point: the thing being shown
     * has changed.
     */
    private void drawGranted(GuiGraphicsExtractor graphics)
    {
        if(granted.isEmpty())
        {
            return;
        }
        int shown = (int)Math.min(granted.size(),
            1 + (System.currentTimeMillis() - grantedAt) / CARD_DEAL_MS);

        // One sound per card as it lands, not one per frame while it is up.
        // Tracked by a high-water mark rather than by comparing against the last
        // frame, so a dropped frame that skips a card still gets its sound and a
        // repeated frame does not get a second one.
        while(dealtSoFar < shown)
        {
            dealtSoFar++;
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    de.cas_ual_ty.dueldimension.DdSounds.STATUE_CARD, 1F));
        }

        float[] row = cardRow();
        float w = row[0];
        float h = row[1];
        float left = row[2];
        float gap = w * CARD_GAP_FRACTION;
        float top = CARD_ROW_CENTRE_Y - h / 2F;
        for(int i = 0; i < shown; i++)
        {
            drawFace(graphics, granted.get(i),
                cx(left + i * (w + gap)), cy(top), cs(w), cs(h));
            if(i < grantedFresh.size() && Boolean.TRUE.equals(grantedFresh.get(i)))
            {
                // Over the card's top edge, centred on it. Above rather than on
                // top of the art, and at the badge's own size -- see
                // HubTextures.NEW_BADGE.
                int badgeX = cx(left + i * (w + gap)) + (cs(w) - HubTextures.NEW_BADGE_W) / 2;
                de.cas_ual_ty.dueldimension.clientutil.DdBlitUtil.fullBlit(graphics,
                    HubTextures.NEW_BADGE, badgeX, cy(top) - HubTextures.NEW_BADGE_H - 2,
                    HubTextures.NEW_BADGE_W, HubTextures.NEW_BADGE_H);
            }
        }
    }

    /**
     * The card under the cursor, or null.
     * <p>
     * Only counts cards that have actually dealt in -- a card that has not
     * landed yet is not on screen, and reporting it would let the panel run
     * ahead of the animation.
     */
    private CardHolder hoveredCard(int mouseX, int mouseY)
    {
        if(granted.isEmpty())
        {
            return null;
        }
        int shown = (int)Math.min(granted.size(),
            1 + (System.currentTimeMillis() - grantedAt) / CARD_DEAL_MS);
        float[] row = cardRow();
        float w = row[0];
        float h = row[1];
        float gap = w * CARD_GAP_FRACTION;
        float top = CARD_ROW_CENTRE_Y - h / 2F;
        if(mouseY < cy(top) || mouseY >= cy(top + h))
        {
            return null;
        }
        for(int i = 0; i < shown; i++)
        {
            int x = cx(row[2] + i * (w + gap));
            if(mouseX >= x && mouseX < x + cs(w))
            {
                return granted.get(i);
            }
        }
        return null;
    }

    /**
     * The held card's picture and text, on Shift, in the top-left.
     * <p>
     * Same gesture and same panel as the card binder and the supply screen --
     * {@code CardRenderUtil.renderCardInfo} draws at a fixed corner rather than
     * at the cursor, which is what those already do, so this reads as the same
     * feature rather than a second one that behaves differently.
     * <p>
     * Shift is read from the WINDOW, not from {@code Screen.hasShiftDown()}.
     * That reports the modifier carried by a key event, and holding a modifier
     * while moving the mouse produces no key event at all -- the same reason
     * CardShopScreen's wheel reads it this way.
     */
    /**
     * The held card, arranged the way {@code CardInfoScreen} arranges one.
     *
     * <h2>The arrangement</h2>
     * Name, then the facts, then the effect text in a RECESSED box. The inset is
     * the point rather than decoration: it separates what the card IS -- its
     * classification and stats, which the mod states in its own voice -- from
     * the card's own words, and the info screen and the deck editor both make
     * that distinction the same way. A flat list of lines does not, which is
     * what this panel was before.
     *
     * <h2>addFacts, not addInformation</h2>
     * {@code addInformation} is the tooltip's shape: it opens with the name,
     * drawn separately here, and leaves a monster's SPECIES out of its header.
     * {@code addFacts} is the classifications and stats as a printed card lists
     * them. The species then arrives with the effect text, where
     * {@code addMonsterTextHeader} puts it.
     *
     * <h2>Half size</h2>
     * All of it is drawn at {@link #PEEK_TEXT_SCALE}, wrapped to twice the width
     * so the wrap still lands where the panel expects. The scale is snapped to a
     * whole number of DEVICE pixels first -- half of an odd number of them puts
     * a glyph on a half pixel and blurs it, which is the entire reason the deck
     * editor's preview carries the same snap.
     */
    private void drawHeldCardInfo(GuiGraphicsExtractor graphics, CardHolder held)
    {
        if(held == null || held.getCard() == null)
        {
            peekOf = null;
            return;
        }
        // The scroll belongs to the card, not to the screen: moving to another
        // card should start its text at the top rather than partway down
        // wherever the last one was left.
        if(peekOf != held)
        {
            peekOf = held;
            peekScroll = 0;
        }
        de.cas_ual_ty.dueldimension.card.properties.Properties peek = held.getCard();

        float scale = crispScale(PEEK_TEXT_SCALE);
        int panelW = Math.min(width - 40, PEEK_WIDTH);
        int left = (width - panelW) / 2;
        int inner = 6;
        // In the text's OWN units, which at half scale is twice the panel's.
        int wrapW = Math.round((panelW - inner * 2) / scale);
        int lineH = Math.max(1, Math.round(9 * scale));

        java.util.List<net.minecraft.util.FormattedCharSequence> nameLines =
            font.split(Component.literal(peek.getName() == null ? "" : peek.getName()), wrapW);
        java.util.List<Component> facts = new ArrayList<>();
        de.cas_ual_ty.dueldimension.clientutil.CardPresentation.addFacts(peek, facts);
        java.util.List<net.minecraft.util.FormattedCharSequence> factLines = new ArrayList<>();
        for(Component fact : facts)
        {
            if(!fact.getString().isBlank())
            {
                factLines.addAll(font.split(fact, wrapW));
            }
        }
        java.util.List<net.minecraft.util.FormattedCharSequence> textLines =
            de.cas_ual_ty.dueldimension.clientutil.CardPresentation.bodyLines(font, peek, wrapW);

        // Name and facts are not optional, so the effect box gets what is LEFT.
        // A fixed row count either wastes the panel or runs it off the screen,
        // depending on the card -- the editor's preview does this arithmetic for
        // the same reason.
        int head = inner + nameLines.size() * lineH + 3 + factLines.size() * lineH + 4;
        int budget = Math.max(lineH * 2, height / 3 - head - inner);
        int rows = Math.max(1, Math.min(textLines.size(), budget / Math.max(1, lineH)));
        int maxScroll = Math.max(0, textLines.size() - rows);
        peekScroll = Math.max(0, Math.min(peekScroll, maxScroll));

        int boxH = rows * lineH + 6;
        int panelH = head + boxH + inner;
        // Along the BOTTOM. The message window would be in the way and is not
        // drawn while this is up, which is what frees the band.
        int top = height - panelH - PEEK_MARGIN;

        de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice.draw(graphics,
            HubTextures.PANEL,
            left, top, panelW, panelH);
        // The recessed box, in SCREEN units -- it is furniture, not text, so it
        // is placed before the scale goes on.
        int boxY = top + head;
        de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice.draw(graphics,
            HubTextures.PANEL_INSET,
            left + inner - 3, boxY - 3, panelW - inner * 2 + 6, boxH);

        // Everything below is half size. Coordinates are divided by the scale so
        // the text lands where the panel arithmetic put it.
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        float x = (left + inner) / scale;
        float y = (top + inner) / scale;
        float step = 9F;

        for(net.minecraft.util.FormattedCharSequence line : nameLines)
        {
            graphics.text(font, line, (int)x, (int)y, MenuInk.title(), MenuInk.shadow());
            y += step;
        }
        y += 3F / scale;
        for(net.minecraft.util.FormattedCharSequence line : factLines)
        {
            graphics.text(font, line, (int)x, (int)y, MenuInk.body(), MenuInk.shadow());
            y += step;
        }
        y = boxY / scale;
        for(int i = 0; i < rows && i + peekScroll < textLines.size(); i++)
        {
            graphics.text(font, textLines.get(i + peekScroll), (int)x, (int)y, MenuInk.body(), false);
            y += step;
        }
        if(maxScroll > 0)
        {
            // The info screen's own wording, so the two panels say the same
            // thing about the same gesture.
            String more = "scroll  " + Math.min(textLines.size(), peekScroll + rows)
                + " / " + textLines.size();
            graphics.text(font, more,
                (int)((left + panelW - inner) / scale - font.width(more)),
                (int)((boxY + boxH - 3) / scale - step), 0xFF6E7686, true);
        }
        graphics.pose().popMatrix();
    }

    /**
     * A scale that lands on whole device pixels.
     * <p>
     * Half of an odd number of them puts a glyph on a half pixel and blurs it.
     * Lifted from {@code DeckEditorScreen}, which draws its card preview the
     * same way and for the same reason.
     */
    private float crispScale(float wanted)
    {
        return de.cas_ual_ty.dueldimension.clientutil.hub.MenuText.crispScale(wanted);
    }

    private static boolean shiftHeld()
    {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /**
     * The DE balance, top right.
     * <p>
     * The same plate the card shop puts its DP on -- nine-sliced panel, gold
     * label, white figure -- so the two currencies read as the same kind of
     * thing in the same kind of place. It replaces a line of text that spelled
     * the price out in the middle of the screen, which said what a number in the
     * corner says without looking like part of the game.
     */
    private void drawBalance(GuiGraphicsExtractor graphics)
    {
        int w = cs(BADGE_W);
        int h = cs(BADGE_H);
        int x = width - w - cs(CORNER);
        int y = cs(CORNER);
        de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice.draw(graphics,
            HubTextures.PANEL, x, y, w, h);
        // CENTRED ON THE INK, AND ROUNDED THE RIGHT WAY.
        //
        // This was `(h - 8) / 2`, which is wrong twice over. The vanilla font
        // puts its baseline at y+7 and its capitals and digits occupy rows
        // y..y+6 -- SEVEN rows of ink, not eight; the eighth is the descender
        // gap, which "DE 0" has nothing in. Centring an eight-tall block
        // therefore biases everything up by half a pixel.
        //
        // And the integer divide rounded that half-pixel up rather than down:
        // on a 22-tall plate the true centre is y+7.5, `(22-8)/2` gives 7, and
        // the row sat a whole pixel high with the gap visibly larger underneath.
        // Rounding to nearest gives 8, which is the pixel the eye wants.
        int textY = y + Math.round((h - INK_HEIGHT) / 2F);
        graphics.text(font, "DE", x + 6, textY, MenuInk.title(), MenuInk.shadow());
        String value = Integer.toString(
            de.cas_ual_ty.dueldimension.clientutil.hub.EditorState.duelEnergy());
        graphics.text(font, value, x + w - 6 - font.width(value), textY, 0xFFFFFFFF, true);
    }

    /** Where the Done plaque is, in screen pixels. {@code {x, y, w, h}} */
    private int[] doneRect()
    {
        int w = cs(DONE_W);
        int h = cs(DONE_H);
        return new int[] {width - w - cs(CORNER), height - h - cs(CORNER), w, h};
    }

    /**
     * Done, on the disc's own gold plaque.
     * <p>
     * The art is the middle of {@code menu_01_00} -- the double-headed banner
     * the reward screen's constructor loads right after the two pointers -- cut
     * to its flat centre with the bevelled ends and inward chevrons kept. A
     * vanilla button in the corner was the one thing on this screen that came
     * from Minecraft rather than from the disc, and it looked it.
     * <p>
     * Bottom right, on the trim rather than above it: the trim now spans the
     * whole window, so that corner is gold either way and the plaque sits into
     * it instead of on top of it.
     */
    private void drawDone(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        int[] r = doneRect();
        boolean over = hit(r, mouseX, mouseY);
        DdBlitUtil.blit(graphics, DONE, r[0], r[1], r[2], r[3], 0F, 0F, 1F, 1F,
            over ? 0xFFFFFFFF : 0xFFD8D8D8);
        // The game's own typeface, the same band the prompt uses, so the one
        // piece of text on the trim does not arrive in Minecraft's font.
        Component label = Component.literal("Done").setStyle(
            net.minecraft.network.chat.Style.EMPTY.withFont(
                DOD_FONT));
        float scale = canvasScale();
        int textW = font.width(label);
        graphics.pose().pushMatrix();
        // To the plaque's centre first, then into font pixels, so the label is
        // centred on the ART rather than on a rectangle measured in a different
        // unit from the glyphs.
        // The vertical nudge goes in the TRANSLATE, in screen pixels, because
        // it is half a font pixel and text() only takes whole ones.
        graphics.pose().translate(r[0] + r[2] / 2F,
            r[1] + r[3] / 2F + (DONE_INK_CENTRE - MC_BASELINE_IN_LINE) * scale);
        graphics.pose().scale(scale, scale);
        graphics.text(font, label, -textW / 2, 0, 0xFF4A3212, false);
        graphics.pose().popMatrix();
    }

    /**
     * One card's face, drawn the way every other card in the mod is drawn.
     * <p>
     * <b>Sampled out of the letterboxed square</b> through
     * {@code DuelTextures.CARD_U0..CARD_V1}, not blitted whole. The card texture
     * is square with the card sitting inside it, so stretching the full 0..1
     * range across a card-shaped rectangle squashes the art -- which is exactly
     * what this screen did until it borrowed {@code PackOpeningScreen.drawFace}.
     * <p>
     * And at {@code PREVIEW_CARD_SIZE}, which is the 512 px variant.
     * {@code CardRenderUtil.bindMainResourceLocation}, which this used before,
     * hands back the small one meant for a 16 px slot in the binder; blown up to
     * a third of the screen it reads as low resolution because it is.
     */
    private void drawFace(GuiGraphicsExtractor graphics, CardHolder card,
        int x, int y, int w, int h)
    {
        if(card == null || card.getCard() == null)
        {
            return;
        }
        DdBlitUtil.blit(graphics,
            de.cas_ual_ty.dueldimension.clientutil.DuelTextures.cardSmooth(
                card.getCard(), card.getImageIndex(),
                de.cas_ual_ty.dueldimension.clientutil.DuelTextures.PREVIEW_CARD_SIZE),
            x, y, w, h,
            de.cas_ual_ty.dueldimension.clientutil.DuelTextures.CARD_U0,
            de.cas_ual_ty.dueldimension.clientutil.DuelTextures.CARD_V0,
            de.cas_ual_ty.dueldimension.clientutil.DuelTextures.CARD_U1,
            de.cas_ual_ty.dueldimension.clientutil.DuelTextures.CARD_V1,
            DdBlitUtil.NO_TINT);
    }

    /**
     * The right-click menu's rows, in the order they are drawn.
     * <p>
     * One list rather than a row count and a set of draw calls that each know
     * their own index -- the deck editor's note on this is worth repeating,
     * because a menu sized by a constant is what put a row outside its own box
     * there. {@link #menuRowAt} reads its answer off this same list.
     */
    private List<String> menuLabels()
    {
        if(menuCard == null || menuCard.getCard() == null)
        {
            return List.of();
        }
        return List.of(
            de.cas_ual_ty.dueldimension.clientutil.hub.EditorState
                .isFavourite((int)menuCard.getCard().getId()) ? "Unstar" : "Favourite",
            "Card Info");
    }

    private int menuWidth()
    {
        int widest = 0;
        for(String label : menuLabels())
        {
            widest = Math.max(widest, font.width(label));
        }
        return widest + MENU_PAD * 2;
    }

    private int menuHeight()
    {
        return menuLabels().size() * MENU_ROW + MENU_EDGE * 2;
    }

    /**
     * Opens the menu on the card under the cursor.
     *
     * @return true when there was a card there
     */
    private boolean openMenu(double mouseX, double mouseY)
    {
        CardHolder target = hoveredCard((int)mouseX, (int)mouseY);
        if(target == null)
        {
            menuCard = null;
            return false;
        }
        menuCard = target;
        // Placed at the pointer, then pulled back so the whole menu is on
        // screen. Opened near the right or bottom edge it would otherwise hang
        // off it, and the rows that fell outside could be neither read nor
        // clicked -- the same correction the deck editor's menu carries.
        menuX = Math.max(0, Math.min((int)mouseX, width - menuWidth()));
        menuY = Math.max(0, Math.min((int)mouseY, height - menuHeight()));
        return true;
    }

    /** Which row is under the cursor, or -1. */
    private int menuRowAt(double mouseX, double mouseY)
    {
        if(menuCard == null || mouseX < menuX || mouseX > menuX + menuWidth())
        {
            return -1;
        }
        int row = (int)((mouseY - menuY - MENU_EDGE) / MENU_ROW);
        return row >= 0 && row < menuLabels().size() ? row : -1;
    }

    private void drawMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        if(menuCard == null)
        {
            return;
        }
        List<String> labels = menuLabels();
        int menuW = menuWidth();
        de.cas_ual_ty.dueldimension.clientutil.hub.NineSlice.draw(graphics,
            HubTextures.PANEL,
            menuX, menuY, menuW, menuHeight());
        for(int i = 0; i < labels.size(); i++)
        {
            int rowY = menuY + MENU_EDGE + i * MENU_ROW;
            boolean over = mouseX >= menuX && mouseX <= menuX + menuW
                && mouseY >= rowY && mouseY < rowY + MENU_ROW;
            graphics.text(font, labels.get(i), menuX + MENU_PAD,
                (int)(rowY + (MENU_ROW - font.lineHeight) / 2F + 1),
                over ? 0xFFFFE9B0 : MenuInk.label(), MenuInk.shadow());
        }
    }

    /**
     * Acts on a click while the menu is open.
     *
     * @return true when the click was the menu's, whether it hit a row or not
     */
    private boolean handleMenuClick(double mouseX, double mouseY)
    {
        if(menuCard == null)
        {
            return false;
        }
        int row = menuRowAt(mouseX, mouseY);
        CardHolder card = menuCard;
        // Closed first, so an entry that opens another screen does not leave a
        // menu floating over this one to come back to.
        menuCard = null;
        if(row < 0 || card.getCard() == null)
        {
            // A click anywhere else dismisses it, and is still consumed: the
            // click that closes a menu should not also press what is underneath.
            return true;
        }
        if(row == 0)
        {
            de.cas_ual_ty.dueldimension.clientutil.hub.EditorState
                .toggleFavourite((int)card.getCard().getId());
        }
        else if(minecraft != null)
        {
            minecraft.setScreen(
                new de.cas_ual_ty.dueldimension.clientutil.hub.CardInfoScreen(
                    this, card.getCard()));
        }
        return true;
    }

    /**
     * The card size and where the row starts, in canvas pixels.
     * <p>
     * Widths first: a row of n cards with gaps of {@code CARD_GAP_FRACTION} of a
     * card between them is {@code n*w + (n-1)*w*gap} wide, so solving for w
     * against {@link #CARD_ROW_MAX_W} gives the largest card that fits. Capped at
     * {@link #CARD_MAX_W} so a single card does not fill the screen.
     *
     * @return {@code {width, height, left}}
     */
    private float[] cardRow()
    {
        int n = Math.max(1, granted.size());
        float w = Math.min(CARD_MAX_W,
            CARD_ROW_MAX_W / (n + (n - 1) * CARD_GAP_FRACTION));
        float h = w * CARD_ASPECT;
        float total = n * w + (n - 1) * w * CARD_GAP_FRACTION;
        return new float[] {w, h, WINDOW_CENTRE_X - total / 2F};
    }

    /**
     * The game's own message window and the prompt in it.
     * <p>
     * Three pieces: a 43 px header, a 14 px body and a 32 px footer, 548 wide,
     * centred on (320, 409). The body is TILED rather than stretched -- it is a
     * strip carrying the window's left and right border ornaments, and
     * stretching it smears those into streaks, which is what used to make the
     * box look pulled out of shape.
     */
    private void drawMessageWindow(GuiGraphicsExtractor graphics)
    {
        float windowH = WINDOW_TOP_H + WINDOW_BODY_H + WINDOW_BOTTOM_H;
        float left = WINDOW_CENTRE_X - WINDOW_W / 2F;
        float top = WINDOW_CENTRE_Y - windowH / 2F;

        int x = cx(left);
        int w = cs(WINDOW_W);
        int plain = DdBlitUtil.NO_TINT;

        // THE SEAMS ARE SHARED EDGES, NOT TWO ROUNDINGS THAT HAPPEN TO MEET.
        //
        // This is where the tear across the message window came from. Each
        // piece used to be placed at cy(its top) and sized at cs(its height),
        // and those are two independent roundings of two different quantities.
        // Worked through at the size this client actually runs -- 1634x920 at
        // GUI scale 3, so 544x306 in GUI units and a canvas scale of 0.6375 --
        // the window's top is canvas y 370.5, the header lands at cy(370.5) =
        // 236 and is cs(43) = 27 tall, so it ends at 263; the body it is
        // supposed to butt against starts at cy(413.5) = 264. One row of pixels
        // that belongs to neither piece, showing the statues through the box.
        // At GUI scale 1 the same arithmetic opens the same gap at y 792.
        //
        // Nothing about the arithmetic guaranteed otherwise: round(a) +
        // round(b) is not round(a + b), and the error appears or does not
        // depending on the window size, which is exactly why it reads as a
        // glitch rather than as a layout mistake.
        //
        // So the boundaries are rounded ONCE, in canvas space, and each piece
        // is given the gap to the next as its height. Adjacent pieces then share
        // a coordinate by construction and no scale can open a seam. The total
        // height still comes to the window's own, give or take the single pixel
        // the last boundary rounds by.
        int yTop = cy(top);
        int yBody = cy(top + WINDOW_TOP_H);
        int yBottom = cy(top + WINDOW_TOP_H + WINDOW_BODY_H);
        int yEnd = cy(top + WINDOW_TOP_H + WINDOW_BODY_H + WINDOW_BOTTOM_H);

        DdBlitUtil.blit(graphics, DIALOG_TOP, x, yTop, w, yBody - yTop,
            0F, 0F, 1F, 1F, plain);
        DdBlitUtil.blit(graphics, DIALOG_BODY, x, yBody, w, yBottom - yBody,
            0F, 0F, 1F, 1F, plain);
        DdBlitUtil.blit(graphics, DIALOG_BOTTOM, x, yBottom, w, yEnd - yBottom,
            0F, 0F, 1F, 1F, plain);

        // The wording is the game's own, and it is drawn in the GAME'S OWN FONT.
        //
        // Styling a Component rather than fetching a Font: Minecraft resolves the
        // provider per glyph from the style, so the default Font object can be
        // passed straight through and no access widener is needed to reach the
        // font manager.
        //
        // The god's NAME is deliberately absent -- the original does not name it,
        // and the statue in front of you already answers the question.
        // Driven by what the SERVER said, not by having asked. It used to flip
        // the moment the request went out, so a refusal -- not enough DE, most
        // often -- read as "You got new cards!" with no cards anywhere.
        String line = !granted.isEmpty() ? PROMPT_TAKEN
            : !refusal.isEmpty() ? refusal
            : PROMPT;
        Component prompt = Component.literal(line).setStyle(
            net.minecraft.network.chat.Style.EMPTY.withFont(DOD_FONT));

        // The font sheet is at the disc's own pixel size, so it is drawn under
        // the canvas scale like everything else rather than at some fraction
        // fitted by eye. Width comes back in canvas pixels for the same reason.
        float scale = canvasScale();
        float textWidth = font.width(prompt);
        graphics.pose().pushMatrix();
        graphics.pose().translate(
            cx(WINDOW_CENTRE_X - textWidth / 2F),
            cy(PROMPT_BASELINE_Y - MC_BASELINE_IN_LINE));
        graphics.pose().scale(scale, scale);
        graphics.text(font, prompt, 0, 0, 0xFF4A3212, false);
        graphics.pose().popMatrix();
    }

    /**
     * Hands the server's answer to whichever of these screens is open.
     * <p>
     * Does nothing if none is -- a player who closed the screen before the
     * packet landed still has the cards, because the trunk was written first;
     * all that is lost is the picture of them.
     */
    public static void showGranted(List<Integer> codes, List<Integer> arts, List<Boolean> fresh,
        String reason)
    {
        StatueRewardScreen screen = open;
        if(screen == null)
        {
            return;
        }
        if(codes.isEmpty())
        {
            // Refused. The choice goes back on the table rather than leaving the
            // screen stuck claiming a reward it never received.
            screen.chosen = false;
            screen.refusal = reason;
            return;
        }
        screen.refusal = "";
        List<CardHolder> cards = new ArrayList<>(codes.size());
        List<Boolean> marks = new ArrayList<>(codes.size());
        for(int i = 0; i < codes.size(); i++)
        {
            // A code the client's database does not know is skipped rather than
            // drawn as a blank: the trunk on the server has it either way, and a
            // hole in the row is less confusing than a card with no face.
            de.cas_ual_ty.dueldimension.card.properties.Properties card =
                de.cas_ual_ty.dueldimension.DdDatabase.PROPERTIES_LIST.get(
                    (long)(int)codes.get(i));
            if(card == null)
            {
                continue;
            }
            byte art = (byte)(int)(i < arts.size() ? arts.get(i) : 0);
            cards.add(new CardHolder(card, art, ""));
            // Added in the same branch as the card, so an unknown code drops
            // both and the two lists cannot slip against each other.
            marks.add(i < fresh.size() && Boolean.TRUE.equals(fresh.get(i)));
        }
        screen.granted = cards;
        screen.grantedFresh = marks;
        screen.grantedAt = System.currentTimeMillis();
    }

    /**
     * The whole scene, in back-to-front group order.
     * <p>
     * The ordering is now belt-and-braces rather than the thing correctness rests
     * on: {@link StatueRenderer} writes real depth, so interpenetrating parts
     * resolve per pixel. It is kept because alpha-cutout edges still look better
     * laid down far to near, and because it costs nothing.
     */
    private List<StatueRenderer.Placed> build()
    {
        StatueMesh podium = StatueMeshLoader.get("podium");
        StatueMesh crown = StatueMeshLoader.get("crown");

        List<StatueRenderer.Placed> out = new ArrayList<>(9);
        // Far to near, RECOMPUTED rather than fixed: once the carousel turns, which
        // podium is nearest changes, and a hardcoded order would draw the front one
        // behind the back ones half the time.
        StatueScene.Slot[] order = StatueScene.Slot.values().clone();
        java.util.Arrays.sort(order, (a, b) ->
            Float.compare(podiumDepth(b), podiumDepth(a)));
        for(StatueScene.Slot slot : order)
        {
            StatueMesh statue = StatueMeshLoader.get(slot.mesh());
            float yaw = slot.yaw() + sceneYaw;
            float z = -StatueScene.PODIUM_RADIUS;

            out.add(new StatueRenderer.Placed(podium, yaw, 0F, 0F, z, 0F));
            // The plate is polished where the podium under it is not.
            out.add(new StatueRenderer.Placed(crown, yaw, 0F, StatueScene.crownLift, z, 0F,
                new StatueRenderer.Finish(StatueScene.crownShine,
                    StatueScene.crownShininess)));
            if(statue != null && crown != null)
            {
                float[] seat = StatueSeating.seat(crown, statue);
                out.add(new StatueRenderer.Placed(statue, yaw,
                    seat[0], seat[1] + StatueScene.crownLift, z + seat[2],
                    StatueScene.STATUE_YAW_DEGREES, StatueScene.STATUE_SCALE));
            }
        }
        return out;
    }

    /**
     * How far this podium's centre is from the eye, with the carousel where it is.
     * <p>
     * Only the ordering matters, so this is the plain distance rather than a
     * projection along the view axis -- the two agree on which is in front for a
     * ring of three viewed from outside it.
     */
    private float podiumDepth(StatueScene.Slot slot)
    {
        double yaw = Math.toRadians(slot.yaw() + sceneYaw);
        double x = -StatueScene.PODIUM_RADIUS * Math.sin(yaw);
        double z = -StatueScene.PODIUM_RADIUS * Math.cos(yaw);
        double dx = x - StatueScene.CAMERA_EYE[0];
        double dz = z - StatueScene.CAMERA_EYE[2];
        return (float)Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Turns the carousel one podium. Positive goes to the next god clockwise.
     * <p>
     * 120 degrees is the disc's own step, at {@code .rdata 0x0045343C}, which is
     * what three podiums on one circle comes to.
     */
    private void step(int direction)
    {
        // Refused rather than queued once two steps are already outstanding.
        // Holding a key fires OS key-repeat at whatever rate the system is set
        // to, and every repeat used to add another 120 degrees -- so a two
        // second hold banked a dozen steps and the carousel kept spinning long
        // after the key came up. Two ahead is enough to keep the motion
        // continuous while holding and to stop within a step of releasing.
        float pending = sceneYawTarget - sceneYaw;
        if(Math.abs(pending) >= 240F)
        {
            return;
        }
        sceneYawTarget -= direction * 120F;

        // One click per god, not one per commit.
        //
        // Committing is not the same as arriving: the queue holds two steps, so
        // the first moment of a held key commits twice in as many frames and
        // key-repeat tops it up again the instant the carousel moves -- which
        // came out as a burst of three beeps at the start of every hold.
        // Throttled to the length of a step instead, which is the rate the gods
        // actually go past, while still sounding immediately on the first press.
        // Just long enough to swallow a same-instant double commit, and short
        // enough to be inaudible as a delay.
        //
        // This was a whole step (500 ms), which did stop the burst but made
        // deliberate cycling sound like it was ignoring presses -- the click
        // belongs to the PRESS, not to the arrival. The burst it exists for is
        // key-repeat committing twice within a frame or two of each other, and
        // that needs a fraction of a step to catch, not a whole one.
        long now = System.currentTimeMillis();
        if(now - lastRotateSoundAt >= ROTATE_CLICK_GAP_MS)
        {
            lastRotateSoundAt = now;
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    de.cas_ual_ty.dueldimension.DdSounds.STATUE_ROTATE, 1F));
        }
    }

    /** Which god is at the front, 0..2 into {@link StatueScene.Slot#values()}. */
    public int selected()
    {
        int index = Math.round(-sceneYawTarget / 120F) % 3;
        return index < 0 ? index + 3 : index;
    }

    /**
     * @param left the pointer on the left of the screen
     * @return {@code {x, y, w, h}} in SCREEN pixels
     *
     * <p>Both the drawing and the click test come through here, so the target
     * moves with the picture and cannot drift away from it.
     */
    /**
     * How far across its own width the tile floor has travelled, 0 to 1.
     * <p>
     * A plain sawtooth, and here that is right rather than a compromise: the
     * layer WRAPS, so the moment it reaches 1 it is showing exactly what it
     * showed at 0 and there is nothing to see in the reset. That is the whole
     * difference between this and {@code arrowDrift}, which folds because a
     * pointer that teleported back would be the only thing on screen anybody
     * noticed.
     */
    private static float tileScroll()
    {
        return (System.currentTimeMillis() % TILE_WRAP_MS) / (float) TILE_WRAP_MS;
    }

    private int[] arrowRect(boolean left)
    {
        float drift = arrowDrift();
        float x = left ? ARROW_LEFT_X - drift : ARROW_RIGHT_X + drift;
        return new int[] {cx(x), cy(ARROW_Y), cs(ARROW_W), cs(ARROW_H)};
    }

    /**
     * Outward drift in canvas pixels, 0 at the top of the cycle.
     * <p>
     * The disc's own triangle wave, not a cosine. See {@link #ARROW_TRAVEL}.
     */
    private static float arrowDrift()
    {
        float phase = (System.currentTimeMillis() % ARROW_CYCLE_MS)
            / (float)ARROW_CYCLE_MS;
        // Folded about 0.5, exactly as VA 0x00100243 does it.
        float t = phase <= 0.5F ? phase * 2F : (1F - phase) * 2F;
        return t * ARROW_TRAVEL;
    }

    /**
     * The band between the two pointers, above the message window -- which is
     * where the statue at the front stands.
     */
    private boolean betweenPointers(double mouseX, double mouseY)
    {
        int[] left = arrowRect(true);
        int[] right = arrowRect(false);
        return mouseX >= left[0] + left[2] && mouseX < right[0]
            && mouseY >= cy(0F) && mouseY < cy(TRIM_Y);
    }

    private boolean hit(int[] rect, double mouseX, double mouseY)
    {
        return mouseX >= rect[0] && mouseX < rect[0] + rect[2]
            && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
    }

    /**
     * Carries the carousel towards wherever the arrows have pointed it.
     * <p>
     * A FIXED-DURATION tween, because that is what the game does: the rotate
     * call at VA 0x00100159 passes 30 into the tween helper at 0x00021CF0, and
     * 30 frames at the disc's 60 Hz is {@link #ROTATE_MS}. The exponential ease
     * that used to be here never actually arrived -- it only ever approached --
     * and its speed was a number picked to look right.
     * <p>
     * Smoothstep rather than linear: the disc's tween helper is not decoded, but
     * a menu tween that starts and stops abruptly is the one thing it certainly
     * is not, and smoothstep is the cheapest ease that does neither.
     */
    private void advance()
    {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1F, (now - lastFrameAt) / 1000F);
        lastFrameAt = now;
        float remaining = sceneYawTarget - sceneYaw;
        if(remaining == 0F)
        {
            return;
        }

        // Speed, not a tween. The fixed-duration ease that used to be here
        // restarted from the current angle on every step, and since holding a
        // key fires key-repeat several times a second, it restarted several
        // times a second -- each restart beginning at zero speed again, which
        // is exactly the judder.
        //
        // Driving a SPEED instead means a step queued mid-turn simply moves the
        // target further away and the motion never breaks stride.
        float distance = Math.abs(remaining);
        // The disc's own rate: 120 degrees in 30 frames at 60 Hz.
        float top = 120F / (ROTATE_MS / 1000F);
        // Eased only over the last stretch, so a single step still arrives
        // softly instead of stopping dead, while a held key spins at full rate.
        float ease = Math.min(1F, distance / EASE_DEGREES);
        float speed = top * (EASE_FLOOR + (1F - EASE_FLOOR) * ease * ease * (3F - 2F * ease));

        float move = speed * dt;
        if(move >= distance)
        {
            sceneYaw = sceneYawTarget;
            return;
        }
        sceneYaw += Math.signum(remaining) * move;
    }

    /** @return true when a pointer was hit and the carousel turned */
    private boolean clickArrows(double mouseX, double mouseY)
    {
        // Same reason as pressArrows: the pointers are not drawn once the cards
        // are, so their hit boxes must not be live either.
        if(!granted.isEmpty())
        {
            return false;
        }
        if(hit(arrowRect(true), mouseX, mouseY))
        {
            step(-1);
            return true;
        }
        if(hit(arrowRect(false), mouseX, mouseY))
        {
            step(1);
            return true;
        }
        return false;
    }

    /**
     * Keys turn the carousel and take the god at the front.
     * <p>
     * A and D alongside the arrow keys, because a hand already resting on WASD
     * should not have to move to turn a carousel. Space and Enter both choose.
     */
    private boolean pressArrows(int key)
    {
        // Once the cards are up there is nothing to turn: the statues are gone,
        // the pointers are gone, and a keypress that silently rotated a carousel
        // nobody can see would still be paying for it on the next visit.
        if(!granted.isEmpty())
        {
            return false;
        }
        if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT
            || key == org.lwjgl.glfw.GLFW.GLFW_KEY_A)
        {
            step(-1);
            return true;
        }
        if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT
            || key == org.lwjgl.glfw.GLFW.GLFW_KEY_D)
        {
            step(1);
            return true;
        }
        if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
            || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
            || key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE)
        {
            choose();
            return true;
        }
        return false;
    }

    /**
     * Takes the god at the front, once.
     * <p>
     * Only while the carousel is still -- committing mid-turn would pay out for
     * whichever statue happened to be nearest at that instant, which is not the
     * one the player is looking at. And only once: the flag is what stops a
     * second Enter paying a second time before the screen is closed.
     * <p>
     * The client sends nothing but the index. What that is worth is the server's
     * to decide; see {@link de.cas_ual_ty.dueldimension.shop.StatueMessages}.
     */
    private void choose()
    {
        if(chosen || sceneYaw != sceneYawTarget)
        {
            return;
        }
        chosen = true;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new de.cas_ual_ty.dueldimension.shop.StatueMessages.Choose(selected()));
    }

    /**
     * The wheel scrolls the held card's text.
     * <p>
     * Only while Shift is held over a card, so the wheel is free for anything
     * else the rest of the time. The clamp is done where the panel is drawn --
     * see {@link #drawHeldCardInfo} -- because only there is the line count
     * known.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if(shiftHeld() && hoveredCard((int)mouseX, (int)mouseY) != null)
        {
            peekScroll = Math.max(0, peekScroll - (int)Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        // The menu gets the click before anything under it.
        if(handleMenuClick(mouseX, mouseY))
        {
            return true;
        }
        if(button == 0 && hit(doneRect(), mouseX, mouseY))
        {
            onClose();
            return true;
        }
        if(button == 1 && !granted.isEmpty())
        {
            return openMenu(mouseX, mouseY);
        }
        if(button == 0 && clickArrows(mouseX, mouseY))
        {
            return true;
        }
        // Anywhere between the pointers is the statue at the front, which is
        // the thing being offered -- so clicking it takes it.
        if(button == 0 && betweenPointers(mouseX, mouseY))
        {
            choose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers)
    {
        // Numpad '.' toggles the light editor, in development clients only.
        if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_DECIMAL
            && StatueLightEditor.available())
        {
            lightEditor = lightEditor == null ? new StatueLightEditor() : null;
            return true;
        }
        // While it is open it takes the keys first, or its arrows would turn the
        // carousel as well as move its cursor.
        if(lightEditor != null && lightEditor.keyPressed(key))
        {
            return true;
        }
        if(pressArrows(key))
        {
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    /**
     * Deliberately empty, and 1.21.1-only.
     * <p>
     * {@code Screen.render} calls this first, and 1.21.1's implementation runs a
     * BLUR post-process across the whole framebuffer. Because this screen paints
     * before calling {@code super.render}, that blur lands on everything already
     * drawn -- the statues, the window, and the font -- not just on the world
     * behind. It reads as a texture-filtering problem and is nothing of the sort;
     * the giveaway is that the TEXT is blurred too.
     * <p>
     * Every other screen in the mod already does this, for the same reason. The
     * 26.2 copy of this class needs no such override: there the base
     * {@code extractRenderState} does not blur, and screens simply decline to
     * call {@code extractBackground}. This is the one place the two versions of
     * this file genuinely differ in behaviour rather than in spelling.
     */
    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics vanillaGraphics,
        int mouseX, int mouseY, float partialTick)
    {
    }

    /**
     * The pointer flash, on the original's own schedule.
     *
     * <h2>What is measured, and what is not</h2>
     * {@code menu_m_14} ({@code card_get_e00}) animates its effects with UV
     * keyframes, not transforms -- 9 {@code igUvAnimeShader}, 36
     * {@code igUvAnimeKeyList} of 10 keys each, 416 keys in total. These parts
     * are read straight out of the file and are certain:
     * <ul>
     * <li>the cycle is <b>2.000 seconds</b> ({@code dur} 2.0, and 2,000,000,000 ns)
     * <li>it steps at <b>30 fps</b> ({@code frame} 0.03333 s, 33,333,333 ns)
     * <li>each list holds 10 keys at FIXED times -- 0.033, 0.067, 0.100, 0.133,
     *     then 0.500, 0.533, 0.567, 0.600, 0.633, 0.667. Two short bursts, idle
     *     for the rest of the cycle.
     * <li>the key values are small integers, 0 to 3
     * </ul>
     * What is <b>not</b> established is which visual property those integers
     * drive, or which list belongs to which mesh -- the file's own object graph
     * does not resolve its child lists, the same failure the geometry hit. So the
     * TIMING below is the game's, and treating the value as a brightness step is
     * MY reading. If the original turns out to swap sprite frames instead, the
     * schedule stays and only what it drives changes.
     * <p>
     * This drives the FLASH only. The pointers' drift is smooth and separate; see
     * {@link #ARROW_TRAVEL}.
     */
    private static final long EFFECT_CYCLE_MS = 2000L;
    private static final int EFFECT_FPS = 30;
    /** Key times, in frames of the 30 fps cycle: 1-4, then 15-20. */
    private static final int[] BURST_FRAMES = {1, 2, 3, 4, 15, 16, 17, 18, 19, 20};

    /**
     * How lit the pointers are this frame, 0 to 1.
     * <p>
     * Quantised to 30 fps deliberately rather than interpolated. The source is a
     * step animation with keys on frame boundaries; smoothing it would look
     * nicer and be wrong.
     */
    private static float effectPulse()
    {
        long inCycle = System.currentTimeMillis() % EFFECT_CYCLE_MS;
        int frame = (int)(inCycle * EFFECT_FPS / 1000L);
        for(int i = 0; i < BURST_FRAMES.length; i++)
        {
            if(BURST_FRAMES[i] == frame)
            {
                // Within a burst the value climbs, which is what the 0..3 range
                // reads as. Normalised so the caller does not care about the
                // source scale.
                return (i % 5) / 4F;
            }
        }
        return 0F;
    }

    /**
     * The pointer's colour. <b>It does not flash.</b>
     * <p>
     * It used to, on the theory that {@code menu_m_14}'s UV keyframes drove a
     * brightness step. They do not drive the pointers at all: the pointers'
     * whole per-frame update is the function at VA 0x00100243, and every
     * instruction in it computes a POSITION -- it sets x and y and touches
     * nothing else. Whatever those UV keys animate, it is not this.
     * <p>
     * The hover brighten is kept. It is not in the original, which has no mouse,
     * but a clickable thing that does not respond to the cursor is worse than a
     * small liberty.
     */
    private static int arrowTint(boolean hovered)
    {
        return hovered ? 0xFFFFFFFF : 0xFFB9A05E;
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
    /**
     * The hub screen this was opened from, or null. See {@code HubReturn}.
     * <p>
     * Monuments hangs off the hub's Shop tab -- it IS a shop, five DE for five
     * cards -- so closing it belongs back there and not in the world.
     */
    private final net.minecraft.client.gui.screens.Screen dueldimension$parent =
        de.cas_ual_ty.dueldimension.clientutil.hub.HubReturn.parent();

    /** Back to the hub if that is where this came from, otherwise to the world. */
    @Override
    public void onClose()
    {
        if(!de.cas_ual_ty.dueldimension.clientutil.hub.HubReturn.back(dueldimension$parent))
        {
            super.onClose();
        }
    }
}

