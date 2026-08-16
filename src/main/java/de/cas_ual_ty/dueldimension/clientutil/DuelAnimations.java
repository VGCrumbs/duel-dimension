package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.DdSounds;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.ocg.prompt.DuelEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Plays what the duel did: a card flying to the zone it moved into, a flash
 * where damage landed, a lunge when an attack is declared, and the reference
 * client's own sound for each.
 * <p>
 * Animations are cosmetic and never gate play — the board snapshot is already
 * authoritative, so an animation that is still running simply draws over the
 * settled state and expires.
 * <p>
 * <b>Every {@code render*} method here takes a {@code PoseStack} and a
 * {@code SubmitNodeCollector} rather than the screen's extractor.</b> All of them
 * draw arbitrary quads -- a sword lying at an angle, a shard tumbling off a
 * card, a card narrowing as it turns over -- and a GUI can only blit
 * axis-aligned rectangles. That pair is what exists inside the picture-in-
 * picture pass ({@link BoardPip}) where {@link FieldQuad} is allowed to work, so
 * it is what the whole draw chain carries.
 */
public class DuelAnimations
{
    /**
     * EDOPro's own pacing, in frames at 60fps, taken from the
     * {@code WaitFrameSignal(n, lock)} call in each message handler in
     * {@code gframe/duelclient.cpp}.
     * <p>
     * That call is the reference client's timing primitive: it blocks the
     * message loop for n frames, so the duel state can never run ahead of what
     * is on screen. These are its numbers rather than invented ones:
     * <pre>
     * MSG_MOVE            10        MSG_SUMMONING     11 + 30
     * MSG_DRAW             5        MSG_SPSUMMONING   11 + 30
     * MSG_CHAINING        30        MSG_FLIPSUMMONING 11 + 30
     * MSG_BECOME_TARGET   30        MSG_DAMAGE        11 + 30
     * MSG_ATTACK          40        MSG_RECOVER       11 + 30
     * MSG_NEW_PHASE       40        MSG_POS_CHANGE    11
     * MSG_NEW_TURN        40        MSG_SHUFFLE_DECK  10
     * MSG_WIN            120        MSG_CHAINED       20
     * </pre>
     */
    private static long frames(int count)
    {
        return Math.round(count * 1000D / 60D);
    }

    private static final long MOVE_MS = frames(10);
    /** MSG_SET has no wait in the reference: sound, then the slide arrives. */
    private static final long SET_MS = frames(5);
    private static final long SUMMON_MS = frames(11 + 30);
    private static final long DRAW_MS = frames(5);
    /**
     * NUM_RED is a 496 ms LP-counting clip. Damage owns this rounded half
     * second so its visual finishes with the sound instead of the board
     * snapping to the settled value partway through it.
     */
    private static final long DAMAGE_MS = 500;
    /** NUM_GREEN is a 784 ms LP-counting clip, rounded to the nearest ms. */
    private static final long RECOVER_MS = 784;
    /** First 120 ms: hold the old LP and turn only the changing segment white. */
    private static final long LP_FLASH_MS = 120;
    private static final long ATTACK_MS = frames(40);
    private static final long PHASE_MS = frames(40);
    private static final long TURN_MS = frames(40);
    private static final long CHAIN_MS = frames(30);
    private static final long TARGET_MS = frames(30);
    private static final long SHUFFLE_MS = frames(10);
    private static final long TOSS_MS = frames(40);
    private static final long POSITION_MS = frames(11);
    private static final long WIN_MS = frames(120);
    /**
     * A destroyed card breaks apart. EDOPro has no such effect -- destruction
     * is just a MSG_MOVE to the graveyard there -- so this is the one timing
     * here that is ours, stretched enough for the shards to read.
     */
    private static final long SHATTER_MS = frames(30);
    /**
     * How many pieces a breaking card comes apart into, across and down.
     * <p>
     * Uneven on purpose — the cuts are not evenly spaced, so a five-by-four
     * grid gives twenty shards of twenty different sizes. A square grid of
     * equal squares reads as a card sliding apart into tiles, which is what
     * this used to look like.
     */
    public static final int SHARD_COLUMNS = 5;
    public static final int SHARD_ROWS = 4;
    /** However far behind we are, nothing flashes past faster than this. */
    private static final long FLOOR_MS = frames(5);

    /**
     * The attack line. EDOPro's own arrow is green
     * ({@code DECLR(DUELFIELD_ATTACK_ARROW, 0x8000ff00)}); this project asked
     * for a red line with a sword launching along it, so the shaft is red and
     * {@code attack.png} -- the same texture EDOPro bobs over a card that may
     * attack -- rides it from attacker to target.
     */
    private static final int ATTACK_ARROW = 0xC0FF2020;
    /** How big the travelling sword is, in field units. */
    private static final float SWORD_SIZE = 1.0F;

    // GenArrow builds a 0.2-wide shaft ending in a wider head; these are the
    // same proportions expressed in field units.
    private static final float ARROW_HALF_WIDTH = 0.1F;
    private static final float ARROW_HEAD_LENGTH = 0.55F;
    private static final float ARROW_HEAD_HALF_WIDTH = 0.3F;

    /**
     * Where a direct attack points, from duelclient.cpp MSG_ATTACK:
     * {@code xd = 3.95f; yd = (info1.controler == 0) ? -3.5f : 3.5f}.
     */
    private static final float DIRECT_ATTACK_X = 3.95F;
    private static final float DIRECT_ATTACK_Y = 3.5F;

    /** One playing animation. */
    private record Playing(DuelEvent event, long start, long duration)
    {
        float progress(long now)
        {
            return Math.min(1F, (now - start) / (float)duration);
        }

        boolean done(long now)
        {
            return now - start >= duration;
        }
    }

    private final List<Playing> playing = new ArrayList<>();
    /** Damage flashes, kept separate so they can tint the life bars. */
    private final List<Playing> flashes = new ArrayList<>();
    /** Attack arrows, which are drawn as arrows rather than moving cards. */
    private final List<Playing> attacks = new ArrayList<>();
    /**
     * Chain and target markers. EDOPro ships tChain and tChainTarget and lays
     * them over the card that is activating and the cards it picked; we shipped
     * the same textures but never drew them, which is why targeting an effect
     * or an equip card gave no feedback at all.
     */
    private final List<Playing> overlays = new ArrayList<>();
    /** Cards turning over in place. */
    private final List<Playing> flips = new ArrayList<>();
    /** Coin and dice results, announced over the middle of the table. */
    private final List<Playing> tosses = new ArrayList<>();
    /** Cards breaking apart where they were destroyed. */
    private final List<Playing> shatters = new ArrayList<>();

    /**
     * One entry in the playback queue: either an event to animate, or a commit
     * to run once everything before it has finished.
     */
    private record Step(DuelEvent event, Runnable commit)
    {
    }

    /** Events waiting their turn to be played. */
    private final java.util.ArrayDeque<Step> queue = new java.util.ArrayDeque<>();
    /** When the next queued event may start. */
    private long nextStart;

    /**
     * Accepts what the server reported.
     * <p>
     * Events are queued rather than started, because a whole turn of them
     * arrives in one update: starting them all at the same instant played an
     * entire turn's summons, attacks and damage on top of each other inside a
     * third of a second, which is unreadable. {@link #tick} releases them one
     * at a time so a sequence can be followed.
     */
    public void accept(List<DuelEvent> events, long now)
    {
        events.forEach(event -> queue.add(new Step(event, null)));
    }

    /**
     * Queues an update's events and the board they produced, so the board is
     * only shown once those events have played.
     * <p>
     * This is what {@code WaitFrameSignal} buys the reference client. There the
     * card's state and its animation are the same object and the message loop
     * blocks, so the field can never show a card that has not finished moving.
     * Here the server sends a settled snapshot, and applying it on arrival put
     * every card in place before its own animation had run -- which is also
     * what forced the arrival-suppression hack that made cards vanish. Holding
     * the snapshot behind its events removes both problems at the source.
     */
    public void accept(List<DuelEvent> events, Runnable applyBoard)
    {
        events.forEach(event -> queue.add(new Step(event, null)));
        queue.add(new Step(null, applyBoard));
    }

    /**
     * How long one event holds the queue. This is both its animation's length
     * and the wait before the next event starts, which is exactly what
     * WaitFrameSignal does in the reference.
     */
    /** How long a revealed card stays on screen. */
    private static final long REVEAL_MS = 2200;

    /** Cards an effect is currently showing this player. */
    private final List<Playing> reveals = new ArrayList<>();

    /**
     * Cards an effect is showing, laid across the middle of the screen.
     * <p>
     * Drawn as a row rather than at the zone each card came from: a reveal
     * often names cards in a deck or a face-down hand, which have no place on
     * the board to point at, and a player being shown something wants to read
     * it rather than hunt for it.
     * <p>
     * PORT-NOTE: this is the one animation measured against the <em>window</em>
     * rather than the field, and inside the pass the coordinates are local to
     * the picture-in-picture region, not the screen. So {@code screenWidth} and
     * {@code screenHeight} now have to be the region's size for the row to
     * centre, and anything the row overhangs is clipped by the region. If the
     * duel screen ends up opening the board region over the field only, the
     * banner will be centred on the field and cut off at its edges; the fix is
     * either a full-window region for the whole board draw, or a second region
     * for this one call. Unresolved here because the choice belongs to the
     * screen, which owns the regions.
     */
    public void renderReveals(PoseStack poseStack, SubmitNodeCollector collector,
        net.minecraft.client.gui.Font font, int screenWidth, int screenHeight, long now)
    {
        reveals.removeIf(playing -> playing.done(now));
        if(reveals.isEmpty())
        {
            return;
        }
        int cardW = 68;
        int cardH = Math.round(cardW / DuelTextures.CARD_ASPECT);
        int gap = 6;
        int total = reveals.size() * cardW + (reveals.size() - 1) * gap;
        int x = (screenWidth - total) / 2;
        int y = (screenHeight - cardH) / 2 - 20;

        String caption = reveals.size() == 1 ? "Revealed" : "Revealed " + reveals.size() + " cards";
        // The extractor's fill is not reachable from in here, and a flat colour
        // still has to go through the textured path: FieldQuad.fill is white.png
        // tinted, which is the same rectangle by another route.
        FieldQuad.fill(poseStack, collector, new FieldQuad.Corners(
            0, y - 18, screenWidth, y - 18, screenWidth, y + cardH + 8, 0, y + cardH + 8),
            0x88000000);
        text(poseStack, collector, caption, screenWidth / 2F - font.width(caption) / 2F,
            y - 14, 0xFFF4D089, true);

        for(Playing playing : reveals)
        {
            Properties card = DdDatabase.PROPERTIES_LIST.get((long)playing.event().code());
            // Two blits became two quads. The texture used to be bound first
            // (bindSmooth) and the rectangle drawn after; now the texture is an
            // argument to the draw, and the smoothing that call also set up is
            // the open item recorded in PORTING.md.
            FieldQuad.Corners quad = new FieldQuad.Corners(
                x, y, x + cardW, y, x + cardW, y + cardH, x, y + cardH);
            if(card == null)
            {
                FieldQuad.draw(poseStack, collector, DuelTextures.COVER, quad);
            }
            else
            {
                // Card art is letterboxed inside a square file, so only the
                // card's own window of it is sampled.
                FieldQuad.draw(poseStack, collector,
                    DuelTextures.cardSmooth(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE), quad,
                    DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                    DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);
            }
            x += cardW + gap;
        }
    }

    /**
     * Text inside the board's pass.
     * <p>
     * {@code Font} draws through a screen's extractor, which does not exist in
     * here; the collector takes the same job as a submission. Full-bright
     * because the board is a picture rather than a thing in the world and must
     * not dim in a cave, and no background or outline because neither of the
     * two callers had one.
     * <p>
     * The colour must carry its own alpha byte -- one with none draws nothing --
     * and is deliberately not forced opaque here: both callers animate their
     * alpha, and clamping it up would make a fading label pop back.
     */
    private static void text(PoseStack poseStack, SubmitNodeCollector collector, String label,
        float x, float y, int argb, boolean dropShadow)
    {
        // Order 1, not the default 0, and this is the whole reason text on the
        // board is visible at all. Within one order group
        // FeatureRenderDispatcher.executeTranslucent runs
        //     ... -> texts -> translucentCustomGeometry
        // and FieldQuad's draws are custom geometry, so text submitted at the
        // same order is painted over by the board that was described before it.
        // submitsPerOrder is a sorted map drained ascending, so a higher order
        // runs strictly after -- which is what "on top" means here.
        collector.order(TEXT_ORDER).submitText(poseStack, x, y, Component.literal(label).getVisualOrderText(),
            dropShadow, net.minecraft.client.gui.Font.DisplayMode.NORMAL, FULL_BRIGHT,
            argb, 0, 0);
    }

    /** Daylight, so the board does not dim with the player's surroundings. */
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Drawn after the board's geometry rather than under it. */
    private static final int TEXT_ORDER = 1_000_000;

    private long duration(DuelEvent event)
    {
        long base = switch(event.kind())
        {
            case MOVE -> MOVE_MS;
            case SET -> SET_MS;
            // MSG_POS_CHANGE holds 11 frames in duelclient.cpp.
            case POSITION -> POSITION_MS;
            case SUMMON, SPECIAL_SUMMON, FLIP -> SUMMON_MS;
            case DESTROY -> SHATTER_MS;
            case DRAW -> DRAW_MS;
            case ACTIVATE, CHAINING -> CHAIN_MS;
            case BECOME_TARGET -> TARGET_MS;
            case ATTACK -> ATTACK_MS;
            case DAMAGE -> DAMAGE_MS;
            case RECOVER -> RECOVER_MS;
            case PHASE -> PHASE_MS;
            case NEW_TURN -> TURN_MS;
            case SHUFFLE -> SHUFFLE_MS;
            // MSG_TOSS_COIN and MSG_TOSS_DICE both hold 40 frames in
            // duelclient.cpp, the same beat as a phase change.
            case COIN, DICE -> TOSS_MS;
            // A reveal is read, not watched: it holds long enough to take in a
            // card name rather than for the length of an animation.
            case REVEAL -> REVEAL_MS;
            case WIN -> WIN_MS;
        };
        return Math.max(FLOOR_MS, Math.round(base * backlogScale()));
    }

    /**
     * Playback never compresses during ordinary play — that is the reference's
     * behaviour, and getting it wrong was the whole "no delay between actions"
     * bug. EDOPro has exactly one speed-up, {@code isCatchingUp}, used for
     * replays and reconnects; every live message plays at full pace no matter
     * how many are queued (each handler checks only that flag).
     * <p>
     * Two mistakes here previously compressed every busy turn. The threshold
     * counted {@code queue.size()}, which includes the zero-length board-commit
     * steps — about one per event (197 checkpoints per 1031 messages,
     * measured) — so a 15-event turn looked like 30. And the threshold itself
     * (20) was below a single turn's event count. Now only real events are
     * counted, and the thresholds mean "multiple full turns behind", our
     * equivalent of catching up.
     */
    private float backlogScale()
    {
        int waiting = 0;
        for(Step step : queue)
        {
            if(step.event() != null)
            {
                waiting++;
            }
        }
        if(waiting > 100)
        {
            return 0.4F;
        }
        if(waiting > 60)
        {
            return 0.7F;
        }
        return 1F;
    }

    /** Starts one event: its sound, then its visual. */
    private void start(DuelEvent event, long now)
    {
        playSound(event);
        long duration = duration(event);
        switch(event.kind())
        {
            case DESTROY -> shatters.add(new Playing(event, now, duration));
            // A set is announced, not slid: MSG_SET has no wait in the
            // reference and the MSG_MOVE that follows carries the card into
            // its zone. Giving SET a slide of its own drew the same card
            // twice, one arriving just behind the other -- the stagger.
            case MOVE, SUMMON, SPECIAL_SUMMON, ACTIVATE, DRAW ->
                playing.add(new Playing(event, now, duration));
            // A flip turns in place. Routed through renderMoves it was treated
            // as a move with no origin, which starts a card at the owner's
            // hand edge: an attacked face-down monster vanished and slid back
            // in from the hand instead of turning over.
            case FLIP, POSITION -> flips.add(new Playing(event, now, duration));
            case SET ->
            {
            }
            case DAMAGE, RECOVER -> flashes.add(new Playing(event, now, duration));
            case ATTACK -> attacks.add(new Playing(event, now, duration));
            case CHAINING, BECOME_TARGET -> overlays.add(new Playing(event, now, duration));
            case REVEAL -> reveals.add(new Playing(event, now, duration));
            case COIN, DICE -> tosses.add(new Playing(event, now, duration));
            default ->
            {
            }
        }
        // LP changes hold their board commit until their displayed value has
        // reached it; otherwise the authoritative snapshot snaps mid-count.
        nextStart = now + duration;
    }

    /** Overridable so tests can run the queue without Minecraft's registries. */
    protected void playSound(DuelEvent event)
    {
        SoundEvent sound = switch(event.kind())
        {
            case SUMMON -> DdSounds.SUMMON;
            case SPECIAL_SUMMON -> DdSounds.SPECIAL_SUMMON;
            case SET -> DdSounds.SET;
            case ACTIVATE -> DdSounds.ACTIVATE;
            case ATTACK -> DdSounds.ATTACK;
            case DAMAGE -> DdSounds.DAMAGE;
            case RECOVER -> DdSounds.GAIN_LP;
            case DESTROY -> DdSounds.DESTROYED;
            case DRAW -> DdSounds.DRAW;
            case FLIP, POSITION -> DdSounds.FLIP;
            case CHAINING -> DdSounds.ACTIVATE;
            case BECOME_TARGET -> DdSounds.EQUIP;
            case SHUFFLE -> DdSounds.SHUFFLE;
            case COIN -> DdSounds.COIN_FLIP;
            case DICE -> DdSounds.DICE_ROLL;
            case PHASE -> DdSounds.PHASE;
            case NEW_TURN -> DdSounds.NEXT_TURN;
            default -> null;
        };
        if(sound != null)
        {
            // Master volume applies; these are UI sounds with no position.
            Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(sound, 1F, volumeFor(event.kind())));
        }
    }

    /** The general level for a duel effect sound. */
    private static final float EFFECT_VOLUME = 0.6F;

    /**
     * How loud one kind of event is.
     * <p>
     * Two are louder than the rest because they are the beats a player is
     * actually waiting on: the engine announcing that the phase has turned,
     * and a card being drawn. Note this is the AUTOMATIC phase change --
     * MSG_NEW_PHASE, the duel moving on by itself. Pressing a phase on the
     * phase bar is a different sound entirely
     * ({@code EngineDuelScreen.SlimPhaseButton.playDownSound}) at its own
     * level, which is what makes asking for a phase and the phase arriving
     * sound like two different things.
     */
    private static float volumeFor(DuelEvent.Kind kind)
    {
        return switch(kind)
        {
            case PHASE, DRAW -> 1F;
            default -> EFFECT_VOLUME;
        };
    }

    /** Drops finished animations and releases the next queued event. */
    public void tick(long now)
    {
        playing.removeIf(animation -> animation.done(now));
        flashes.removeIf(animation -> animation.done(now));
        attacks.removeIf(animation -> animation.done(now));
        overlays.removeIf(animation -> animation.done(now));
        shatters.removeIf(animation -> animation.done(now));
        tosses.removeIf(animation -> animation.done(now));
        flips.removeIf(animation -> animation.done(now));

        // Release as many zero-length steps as are ready, so a commit that
        // follows a finished event lands on the same frame rather than a frame
        // later, but never more than one event.
        while(!queue.isEmpty() && now >= nextStart)
        {
            Step step = queue.poll();
            if(step.event() != null)
            {
                start(step.event(), now);
                break;
            }
            step.commit().run();
        }
    }

    /**
     * The attack arrow, ported from drawing.cpp's {@code is_attacking} block.
     * <p>
     * EDOPro does not lunge the attacking card: it builds an arrow with
     * {@code Materials::GenArrow}, lays it along the line between attacker and
     * target, and draws it while the attack resolves. This does the same in the
     * projected table's own coordinates, so the arrow lies on the field and
     * foreshortens with it. It extends towards the target rather than pulsing
     * along a strip, because our events are discrete and the arrow has one
     * attack's worth of time to read rather than however long a battle takes.
     */
    public void renderAttacks(PoseStack poseStack, SubmitNodeCollector collector,
        FieldLayout.Projection projection, long now)
    {
        if(projection == null)
        {
            return;
        }
        for(Playing animation : attacks)
        {
            DuelEvent event = animation.event();
            FieldLayout.Rect from = zoneRect(event.fromZone());
            if(from == null)
            {
                continue;
            }
            float ax = projection.x(from.x() + from.w() / 2F, from.y() + from.h() / 2F);
            float ay = projection.y(from.y() + from.h() / 2F);

            FieldLayout.Rect target = zoneRect(event.toZone());
            float tx;
            float ty;
            if(target != null)
            {
                tx = projection.x(target.x() + target.w() / 2F, target.y() + target.h() / 2F);
                ty = projection.y(target.y() + target.h() / 2F);
            }
            else
            {
                // Direct attack: aim at the defending player's side of the
                // table, duelclient.cpp's own point (3.95, -+3.5).
                float fieldY = event.player() == 0 ? -DIRECT_ATTACK_Y : DIRECT_ATTACK_Y;
                tx = projection.x(DIRECT_ATTACK_X, fieldY);
                ty = projection.y(fieldY);
            }

            float t = animation.progress(now);
            // The line reaches the target first, then the sword launches along
            // it, so the direction reads before the strike lands.
            float reach = Math.min(1F, t * 2.2F);
            float alpha = t < 0.8F ? 1F : 1F - (t - 0.8F) / 0.2F;
            drawLine(poseStack, collector, ax, ay, ax + (tx - ax) * reach,
                ay + (ty - ay) * reach, alpha);

            if(t > 0.35F)
            {
                float travel = Math.min(1F, (t - 0.35F) / 0.5F);
                float size = zoneWidth(projection, from) * 0.9F;
                drawSword(poseStack, collector, ax + (tx - ax) * travel, ay + (ty - ay) * travel,
                    tx - ax, ty - ay, size, alpha);
            }
        }
    }

    /**
     * The aiming pointer: the attack line and sword drawn from the attacker to
     * wherever the player is pointing, while the core waits for the target.
     * <p>
     * PORT-NOTE: {@code targetX}/{@code targetY} are the mouse, and the mouse is
     * the one input here that does not come through the projection -- the caller
     * hands it straight from the screen. Inside the pass everything else is in
     * the region's coordinates, so if the board's region does not start at the
     * window origin the sword will point at an offset from the cursor by exactly
     * the region's top-left. Unresolved here because only the screen knows where
     * it opened the region; it must subtract that origin before calling, or pass
     * the mouse through the same transform the projection got.
     */
    public void renderAim(PoseStack poseStack, SubmitNodeCollector collector,
        FieldLayout.Projection projection, int fromZone, float targetX, float targetY)
    {
        FieldLayout.Rect from = projection == null ? null : zoneRect(fromZone);
        if(from == null)
        {
            return;
        }
        float ax = projection.x(from.x() + from.w() / 2F, from.y() + from.h() / 2F);
        float ay = projection.y(from.y() + from.h() / 2F);
        drawLine(poseStack, collector, ax, ay, targetX, targetY, 0.9F);
        // The sword stays planted on the attacker while aiming; only its
        // rotation follows the cursor. It flies when the attack itself plays.
        float size = zoneWidth(projection, from) * 0.9F;
        drawSword(poseStack, collector, ax, ay, targetX - ax, targetY - ay, size, 1F);
    }

    private static float zoneWidth(FieldLayout.Projection projection, FieldLayout.Rect rect)
    {
        FieldQuad.Corners corners = projection.quad(rect);
        return corners.maxX() - corners.minX();
    }

    /** The red attack line, as a rotated quad cut from white.png. */
    private static void drawLine(PoseStack poseStack, SubmitNodeCollector collector,
        float x1, float y1, float x2, float y2, float alpha)
    {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if(length < 1F)
        {
            return;
        }
        float px = -dy / length * LINE_HALF_WIDTH;
        float py = dx / length * LINE_HALF_WIDTH;
        FieldQuad.drawCorners(poseStack, collector, DuelTextures.WHITE, new FieldQuad.Corners(
                x1 + px, y1 + py, x2 + px, y2 + py, x2 - px, y2 - py, x1 - px, y1 - py),
            0F, 0F, 1F, 1F, 1F, 0.15F, 0.15F, alpha * 0.8F);
    }

    /**
     * The sword, rotated to fly point-first at its target. The art points
     * straight up, so its base heading is -PI/2 and the rotation needed is
     * atan2(dy, dx) + PI/2.
     * <p>
     * The angle was already radians on Forge and stays radians: it never went
     * through a {@code Quaternion} or a matrix rotate, it turns the four corners
     * by hand with cos and sin. Nothing to convert.
     */
    private static void drawSword(PoseStack poseStack, SubmitNodeCollector collector,
        float cx, float cy, float dirX, float dirY, float size, float alpha)
    {
        float angle = (float)(Math.atan2(dirY, dirX) + Math.PI / 2);
        float cos = (float)Math.cos(angle);
        float sin = (float)Math.sin(angle);
        float half = size / 2F;
        // Corners TL, TR, BR, BL of a square rotated about its centre.
        float[] xs = {-half, half, half, -half};
        float[] ys = {-half, -half, half, half};
        float[] corner = new float[8];
        for(int i = 0; i < 4; i++)
        {
            corner[i * 2] = cx + xs[i] * cos - ys[i] * sin;
            corner[i * 2 + 1] = cy + xs[i] * sin + ys[i] * cos;
        }
        FieldQuad.drawCorners(poseStack, collector, DuelTextures.ATTACK, new FieldQuad.Corners(
                corner[0], corner[1], corner[2], corner[3], corner[4], corner[5], corner[6], corner[7]),
            0F, 0F, 1F, 1F, 1F, 1F, 1F, alpha);
    }

    /** Half thickness of the attack line, in gui pixels. */
    private static final float LINE_HALF_WIDTH = 1.6F;

    /**
     * A destroyed card breaking like glass.
     * <p>
     * Four things separate breaking glass from a picture coming apart, and the
     * old effect — an even three-by-three grid drifting outward at a constant
     * rate — had none of them:
     * <ul>
     * <li>the pieces are all different sizes, because a fracture does not
     *     measure;</li>
     * <li>they leave fast and slow down, rather than travelling at one speed
     *     for the whole animation;</li>
     * <li>they fall, because they weigh something;</li>
     * <li>and they go out at once, from a point, with a flash of light at it.
     * </ul>
     * <p>
     * Every piece of that comes out of {@link #shardNoise}, a hash of the card
     * and the shard, rather than a random number generator: this runs once per
     * frame and has to produce the same break each time, so the card that broke
     * last frame is still breaking the same way this one.
     */
    public void renderShatters(PoseStack poseStack, SubmitNodeCollector collector,
        FieldLayout.Projection projection, long now)
    {
        if(projection == null)
        {
            return;
        }
        for(Playing animation : shatters)
        {
            DuelEvent event = animation.event();
            // Destroyed cards move to the graveyard, so the card's last place
            // on the board is where it came FROM.
            FieldLayout.Rect rect = zoneRect(event.fromZone());
            if(rect == null)
            {
                continue;
            }
            float t = animation.progress(now);
            int code = event.code();
            Identifier texture = artFor(event.code());
            boolean edoproArt = isEdoproArt(texture);
            float windowU0 = edoproArt ? 0F : DuelTextures.CARD_U0;
            float windowU1 = edoproArt ? 1F : DuelTextures.CARD_U1;
            float windowV0 = edoproArt ? 0F : DuelTextures.CARD_V0;
            float windowV1 = edoproArt ? 1F : DuelTextures.CARD_V1;

            float centreX = rect.x() + rect.w() / 2F;
            float centreY = rect.y() + rect.h() / 2F;
            float left = centreX - CARD_W / 2F;
            float top = centreY - CARD_H / 2F;

            // Where it broke: off centre, so the break is not symmetrical, but
            // well inside the card so no shard starts on top of the impact.
            float hitU = 0.3F + 0.4F * shardNoise(code, 0, 11);
            float hitV = 0.3F + 0.4F * shardNoise(code, 0, 12);
            float hitX = left + CARD_W * hitU;
            float hitY = top + CARD_H * hitV;

            // Out fast, then slowing: a fracture spends its energy at once.
            float burst = 1F - (1F - t) * (1F - t);
            // And down, because glass weighs something. Squared, since that is
            // what falling does.
            float fall = t * t * CARD_H * 0.55F;

            float[] columns = shardCuts(code, SHARD_COLUMNS, 21);
            float[] rows = shardCuts(code, SHARD_ROWS, 22);

            for(int row = 0; row < SHARD_ROWS; row++)
            {
                for(int column = 0; column < SHARD_COLUMNS; column++)
                {
                    int index = row * SHARD_COLUMNS + column;

                    // The shard's own patch of the card, in 0..1 of the card.
                    float fu0 = columns[column];
                    float fu1 = columns[column + 1];
                    float fv0 = rows[row];
                    float fv1 = rows[row + 1];

                    float shardW = CARD_W * (fu1 - fu0);
                    float shardH = CARD_H * (fv1 - fv0);
                    float x = left + CARD_W * fu0;
                    float y = top + CARD_H * fv0;

                    // Away from the impact, faster the closer it started to it.
                    float awayX = (x + shardW / 2F) - hitX;
                    float awayY = (y + shardH / 2F) - hitY;
                    float reach = Math.max(0.001F, (float)Math.sqrt(awayX * awayX + awayY * awayY));
                    float speed = (0.9F + 1.4F * shardNoise(code, index, 31))
                        * (1F + CARD_W / (reach * 6F));

                    // Shrinking as it goes: a shard tumbling edge-on catches
                    // almost no light, and shrinking is the nearest thing to
                    // that available to a quad that cannot turn.
                    float shrink = 1F - 0.5F * t;
                    float drawW = shardW * shrink;
                    float drawH = shardH * shrink;

                    // Each on its own schedule, so the group thins out instead
                    // of every piece vanishing on the same frame.
                    float fadeFrom = 0.3F + 0.35F * shardNoise(code, index, 41);
                    float alpha = t <= fadeFrom ? 1F
                        : Math.max(0F, 1F - (t - fadeFrom) / (1F - fadeFrom));
                    if(alpha <= 0F)
                    {
                        continue;
                    }

                    // The shard's slice is taken from whatever window this
                    // texture's card actually occupies, so a face-down card
                    // shatters into pieces of its back rather than pieces of
                    // the middle of its back.
                    float u0 = windowU0 + (windowU1 - windowU0) * fu0;
                    float u1 = windowU0 + (windowU1 - windowU0) * fu1;
                    float v0 = windowV0 + (windowV1 - windowV0) * fv0;
                    float v1 = windowV0 + (windowV1 - windowV0) * fv1;

                    // The shard's fade was a shader colour set before the draw;
                    // it is now the draw's own tint. Squared, as it was.
                    FieldQuad.drawProjected(poseStack, collector, texture, projection,
                        new FieldLayout.Rect(
                            x + awayX * burst * speed + (shardW - drawW) / 2F,
                            y + awayY * burst * speed + fall + (shardH - drawH) / 2F,
                            drawW, drawH),
                        1, 0, u0, v0, u1, v1, 1F, 1F, 1F, alpha * alpha);
                }
            }

            // The break itself: a flash over the card's last position, gone
            // almost before it registers. It is what tells the eye the card was
            // struck rather than that it decided to come apart.
            if(t < FLASH_FRACTION)
            {
                float flash = 1F - t / FLASH_FRACTION;
                FieldQuad.drawProjected(poseStack, collector, DuelTextures.WHITE, projection,
                    new FieldLayout.Rect(left, top, CARD_W, CARD_H), 1, 0, 0F, 0F, 1F, 1F,
                    1F, 1F, 1F, flash * flash * 0.8F);
            }
            // The white reset that closed this loop had a job only while the
            // shader colour was global state. Each draw above carries its own
            // tint now, so there is nothing left over to put back.
        }
    }

    /** How much of the animation the impact flash lasts. */
    private static final float FLASH_FRACTION = 0.22F;

    /**
     * Where the cuts fall across one axis of a breaking card.
     * <p>
     * Widths between 0.6 and 1.6 of an even share, normalised so they still
     * cover the card exactly. Returned as {@code pieces + 1} edges from 0 to 1,
     * which is the form both the geometry and the texture window want.
     */
    public static float[] shardCuts(int code, int pieces, int salt)
    {
        float[] weight = new float[pieces];
        float total = 0F;
        for(int i = 0; i < pieces; i++)
        {
            weight[i] = 0.6F + shardNoise(code, i, salt);
            total += weight[i];
        }
        float[] edge = new float[pieces + 1];
        float at = 0F;
        for(int i = 0; i < pieces; i++)
        {
            edge[i] = at;
            at += weight[i] / total;
        }
        edge[pieces] = 1F;
        return edge;
    }

    /**
     * A number in 0..1 that depends only on the card, the shard and what is
     * being asked.
     * <p>
     * Not a random number generator. This is called from a render pass that
     * runs every frame of the animation, so drawing the same shard twice has to
     * give the same answer twice — otherwise the card does not break, it
     * seethes. A hash gives that for free and needs nothing remembered.
     */
    public static float shardNoise(int code, int index, int salt)
    {
        int hash = code * 0x9E3779B9 + index * 0x85EBCA6B + salt * 0xC2B2AE35;
        hash ^= hash >>> 15;
        hash *= 0x2545F491;
        hash ^= hash >>> 13;
        return (hash >>> 8) / (float)(1 << 24);
    }

    /**
     * A card turning over where it lies.
     * <p>
     * The quad narrows to nothing at the halfway point and opens again, which
     * is what a card rotating about its long axis looks like from above, and
     * the face swaps at that midpoint: back then front for a card being turned
     * up, front then back for one being turned down.
     */
    public void renderFlips(PoseStack poseStack, SubmitNodeCollector collector,
        FieldLayout.Projection projection, long now)
    {
        if(projection == null)
        {
            return;
        }
        for(Playing animation : flips)
        {
            DuelEvent event = animation.event();
            FieldLayout.Rect zone = zoneRect(event.toZone());
            if(zone == null)
            {
                continue;
            }
            float t = animation.progress(now);
            boolean endsFaceUp = event.amount() != 0;
            boolean showFace = (t < 0.5F) != endsFaceUp;
            Identifier texture = showFace ? artFor(event.code(), event.toZone())
                : (event.player() == 0 ? DuelTextures.COVER : DuelTextures.COVER_OPPONENT);

            // |cos| gives one full narrow-and-open across the animation.
            float squash = Math.abs((float)Math.cos(Math.PI * t));
            float width = FLIP_CARD_W * Math.max(0.04F, squash);
            FieldLayout.Rect card = new FieldLayout.Rect(
                zone.x() + (zone.w() - width) / 2F,
                zone.y() + (zone.h() - FLIP_CARD_H) / 2F, width, FLIP_CARD_H);

            boolean edopro = !showFace || event.code() == 0;
            FieldQuad.drawProjected(poseStack, collector, texture, projection, card, FLIP_STEPS,
                event.player() == 1 ? 2 : 0,
                edopro ? 0F : DuelTextures.CARD_U0, edopro ? 0F : DuelTextures.CARD_V0,
                edopro ? 1F : DuelTextures.CARD_U1, edopro ? 1F : DuelTextures.CARD_V1);
        }
    }

    /** The card quad and subdivision a flip uses, matching the board's. */
    private static final float FLIP_CARD_W = 0.7F;
    private static final float FLIP_CARD_H = 1.0F;
    private static final int FLIP_STEPS = 4;

    /**
     * A coin or dice result, announced over the middle of the table.
     * <p>
     * The reference has no art for either -- it prints the outcome through
     * stACMessage and holds 40 frames -- so the timing and the sound are its
     * own while the face drawing is ours: a coin is a disc reading H or T, a
     * die a rounded square with pips.
     */
    public void renderTosses(PoseStack poseStack, SubmitNodeCollector collector,
        net.minecraft.client.gui.Font font, FieldLayout.Projection projection, long now)
    {
        if(projection == null)
        {
            return;
        }
        for(Playing animation : tosses)
        {
            DuelEvent event = animation.event();
            int count = Math.max(1, event.code());
            boolean coin = event.kind() == DuelEvent.Kind.COIN;
            float t = animation.progress(now);
            // Rises into place, holds, then fades out.
            float rise = Math.min(1F, t * 4F);
            float alpha = t < 0.75F ? 1F : 1F - (t - 0.75F) / 0.25F;

            float centreX = projection.x(FIELD_CENTRE_X, 0F);
            float centreY = projection.y(0F) - 26F * rise;
            int size = 22;
            int spacing = size + 6;
            float startX = centreX - (count - 1) * spacing / 2F - size / 2F;

            for(int i = 0; i < count; i++)
            {
                int value = coin ? (event.amount() >> i) & 1 : (event.amount() >> (i * 6)) & 0x3F;
                if(coin)
                {
                    drawCoin(poseStack, collector, startX + i * spacing + size / 2F,
                        centreY, size, t, value == 1, alpha, i);
                }
                else
                {
                    drawTossFace(poseStack, collector, font, Math.round(startX + i * spacing),
                        Math.round(centreY - size / 2F), size, false, value, alpha);
                }
            }
        }
    }

    /** Turns a coin makes on the way up and down. */
    private static final int COIN_SPINS = 5;

    /** How high it is thrown, in board pixels. */
    private static final float COIN_ARC = 34F;

    /**
     * One coin, thrown and caught.
     * <p>
     * The flight is a parabola over the first three quarters of the
     * announcement, so the coin is at rest for the last quarter while the
     * result is read — a coin still turning when the player looks at it is a
     * coin they cannot read. The spin uses the same eased clock and lands on a
     * whole number of turns, which is what puts the winning face towards them
     * rather than leaving it wherever the timing happened to stop.
     *
     * @param index staggers the throw, so two coins are not one shape
     */
    private static void drawCoin(PoseStack poseStack, SubmitNodeCollector collector,
        float centreX, float restY, int size, float t, boolean heads, float alpha, int index)
    {
        // The throw, then the hold. Squeezed slightly per coin so a pair lands
        // one after the other rather than together.
        float flightEnd = 0.72F + index * 0.05F;
        float flight = Math.min(1F, t / Math.max(0.01F, flightEnd));
        // Eased so it leaves fast and settles slowly, as a thrown thing does.
        float eased = 1F - (1F - flight) * (1F - flight);
        // A parabola: up and back down to where it started.
        float height = COIN_ARC * 4F * flight * (1F - flight);
        float spin = (float)(eased * COIN_SPINS * 2 * Math.PI);

        CoinModel.draw(poseStack, collector, centreX, restY - height,
            size / 2F, Math.max(1F, size / 12F), spin, heads, alpha);
    }

    /** One die, drawn from white.png and the font. */
    private static void drawTossFace(PoseStack poseStack, SubmitNodeCollector collector,
        net.minecraft.client.gui.Font font,
        int x, int y, int size, boolean coin, int value, float alpha)
    {
        int a = Math.round(Math.max(0F, Math.min(1F, alpha)) * 255);
        FieldQuad.Corners box = new FieldQuad.Corners(x, y, x + size, y, x + size, y + size, x, y + size);
        // Body, then a lighter inner face, so it reads as a struck object.
        FieldQuad.drawCorners(poseStack, collector, DuelTextures.WHITE, box, 0F, 0F, 1F, 1F,
            0.10F, 0.11F, 0.13F, a / 255F);
        FieldQuad.Corners face = new FieldQuad.Corners(x + 2, y + 2, x + size - 2, y + 2,
            x + size - 2, y + size - 2, x + 2, y + size - 2);
        FieldQuad.drawCorners(poseStack, collector, DuelTextures.WHITE, face, 0F, 0F, 1F, 1F,
            0.90F, 0.90F, 0.92F, a / 255F);
        String label = Integer.toString(Math.max(1, Math.min(6, value)));
        text(poseStack, collector, label, x + (size - font.width(label)) / 2F,
            y + (size - 8) / 2F, (a << 24) | 0x14161A, false);
    }

    /** The table's middle, where an announcement belongs. */
    private static final float FIELD_CENTRE_X =
        (FieldLayout.FIELD_MIN_X + FieldLayout.FIELD_MAX_X) / 2F;

    /**
     * Chain and target markers over the cards they concern, the way
     * drawing.cpp lays tChain over a chaining card and tChainTarget over a
     * targeted one.
     */
    public void renderOverlays(PoseStack poseStack, SubmitNodeCollector collector,
        FieldLayout.Projection projection, long now)
    {
        if(projection == null)
        {
            return;
        }
        for(Playing animation : overlays)
        {
            DuelEvent event = animation.event();
            FieldLayout.Rect rect = zoneRect(event.toZone());
            if(rect == null)
            {
                continue; // activated from a hand or a pile: nothing to mark
            }
            Identifier texture = event.kind() == DuelEvent.Kind.CHAINING
                ? DuelTextures.CHAIN : DuelTextures.TARGET;
            FieldQuad.drawProjected(poseStack, collector, texture, projection, rect, 2);
        }
    }


    /**
     * A card sliding into the zone it just moved to. Drawn after the board so
     * it reads as the card arriving on top of the settled field.
     */
    public void renderMoves(PoseStack poseStack, SubmitNodeCollector collector,
        BoardRenderer board, FieldLayout.Projection projection, long now)
    {
        if(projection == null)
        {
            return;
        }
        for(Playing animation : playing)
        {
            DuelEvent event = animation.event();
            FieldLayout.Rect to = zoneRect(event.toZone());
            if(to == null)
            {
                continue;
            }
            FieldLayout.Rect from = zoneRect(event.fromZone());
            float t = ease(animation.progress(now));

            // Off-board origins (hand, deck) come in from the owner's edge.
            float startX = from != null ? from.x() : to.x();
            float startY = from != null ? from.y()
                : event.player() == 0 ? FieldLayout.FIELD_MAX_Y : FieldLayout.FIELD_MIN_Y;

            float x = startX + (to.x() - startX) * t;
            float y = startY + (to.y() - startY) * t;
            // A slight lift at the midpoint reads as the card being carried.
            float lift = (float)Math.sin(Math.PI * t) * 0.12F;

            Identifier texture = artFor(event.code(), event.toZone());
            boolean edoproArt = isEdoproArt(texture);
            FieldQuad.drawProjected(poseStack, collector, texture, projection,
                new FieldLayout.Rect(x, y - lift, to.w(), to.h()), 4, false,
                edoproArt ? 0F : DuelTextures.CARD_U0, edoproArt ? 0F : DuelTextures.CARD_V0,
                edoproArt ? 1F : DuelTextures.CARD_U1, edoproArt ? 1F : DuelTextures.CARD_V1);
        }
    }

    /**
     * The values needed to draw one LP-change animation. During stage one the
     * displayed LP stays at {@code currentLifePoints} and the entire interval
     * between it and {@code targetLifePoints} flashes white. During stage two,
     * {@code displayedLifePoints} counts toward the target and that white
     * interval shrinks with the moving edge. This makes recovery the exact
     * spatial and numeric inverse of damage.
     */
    public record LifePointState(int displayedLifePoints, int targetLifePoints, float whiteAlpha)
    {
    }

    public LifePointState lifePointState(int player, int currentLifePoints, long now)
    {
        for(int i = flashes.size() - 1; i >= 0; i--)
        {
            Playing animation = flashes.get(i);
            DuelEvent event = animation.event();
            if((event.kind() != DuelEvent.Kind.DAMAGE && event.kind() != DuelEvent.Kind.RECOVER)
                || event.player() != player)
            {
                continue;
            }

            int amount = Math.max(0, event.amount());
            int target = event.kind() == DuelEvent.Kind.DAMAGE
                ? Math.max(0, currentLifePoints - amount)
                : (int)Math.min(Integer.MAX_VALUE, (long)currentLifePoints + amount);
            float progress = animation.progress(now);
            float flashFraction = Math.min(1F, LP_FLASH_MS / (float)animation.duration());
            if(progress < flashFraction)
            {
                float flashIn = ease(progress / flashFraction);
                return new LifePointState(currentLifePoints, target, flashIn);
            }

            float change = ease((progress - flashFraction) / (1F - flashFraction));
            int displayed = (int)Math.round(currentLifePoints + (target - (double)currentLifePoints) * change);
            return new LifePointState(displayed, target, 1F);
        }
        return new LifePointState(currentLifePoints, currentLifePoints, 0F);
    }

    /** Drops everything, for when a duel ends or is left. */
    /**
     * An attack being played, for a renderer that is not the 2D board.
     * <p>
     * The zones are packed the way {@link DuelEvent#zoneOf} packs them, which
     * is the same packing {@code EnginePrompt.zoneRef} uses; a caller unpacks
     * them with the bits that put them there. Exposed rather than a second
     * render method, because the world board's geometry has nothing in common
     * with this class's projected quads and only the TIMING is shared.
     */
    public record AttackView(int fromZone, int toZone, float progress)
    {
    }

    /**
     * A card coming apart, for a board that draws its own geometry.
     * <p>
     * The same arrangement as {@link AttackView}, and for the same reason: the
     * world board has nothing in common with this class's projected quads, and
     * only the TIMING and the fracture are shared. The texture and its window
     * ride along because working them out means knowing which art a code wears
     * and whether that art is EDOPro's, and neither of those is the world
     * board's business.
     */
    public record ShatterView(int code, int fromZone, float progress, Identifier texture,
        float u0, float v0, float u1, float v1)
    {
    }

    /**
     * A card on its way from one zone to another, for a board that draws its
     * own geometry.
     * <p>
     * The origin is a packed zone like the destination, except when there is
     * none: a card coming out of a hand or a deck has nowhere on the mat to
     * start from, so {@code fromZone} is negative and the owner is given
     * instead, for a caller that wants to bring it in over that duellist's
     * edge.
     */
    public record MoveView(int code, int fromZone, int toZone, int player, float progress,
        Identifier texture, float u0, float v0, float u1, float v1)
    {
    }

    /** Every card currently moving, with how far through it is. */
    public java.util.List<MoveView> movesInFlight(long now)
    {
        java.util.List<MoveView> views = new ArrayList<>(playing.size());
        for(Playing animation : playing)
        {
            DuelEvent event = animation.event();
            Identifier texture = artFor(event.code(), event.toZone());
            boolean edoproArt = isEdoproArt(texture);
            views.add(new MoveView(event.code(), event.fromZone(), event.toZone(), event.player(),
                animation.progress(now), texture,
                edoproArt ? 0F : DuelTextures.CARD_U0, edoproArt ? 0F : DuelTextures.CARD_V0,
                edoproArt ? 1F : DuelTextures.CARD_U1, edoproArt ? 1F : DuelTextures.CARD_V1));
        }
        return views;
    }

    /**
     * A card turning over where it lies.
     * <p>
     * Which face is showing is decided here rather than by the caller, because
     * it depends on the halfway point and on which way the card is being
     * turned -- back then front for one being turned up, front then back for
     * one being turned down. A caller that worked it out again would be a
     * second copy of a rule with two places to get it backwards.
     */
    public record FlipView(int code, int zone, int player, boolean showingFace, float progress,
        Identifier texture, float u0, float v0, float u1, float v1)
    {
    }

    /** Every card currently turning over. */
    public java.util.List<FlipView> flipsInFlight(long now)
    {
        java.util.List<FlipView> views = new ArrayList<>(flips.size());
        for(Playing animation : flips)
        {
            DuelEvent event = animation.event();
            float t = animation.progress(now);
            boolean endsFaceUp = event.amount() != 0;
            boolean showFace = (t < 0.5F) != endsFaceUp;
            Identifier texture = showFace ? artFor(event.code(), event.toZone())
                : (event.player() == 0 ? DuelTextures.COVER : DuelTextures.COVER_OPPONENT);
            boolean edoproArt = !showFace || event.code() == 0;
            views.add(new FlipView(event.code(), event.toZone(), event.player(), showFace, t,
                texture,
                edoproArt ? 0F : DuelTextures.CARD_U0, edoproArt ? 0F : DuelTextures.CARD_V0,
                edoproArt ? 1F : DuelTextures.CARD_U1, edoproArt ? 1F : DuelTextures.CARD_V1));
        }
        return views;
    }

    /**
     * A chain or become-target marker, over the card it concerns.
     * <p>
     * drawing.cpp lays tChain over a chaining card and tChainTarget over a
     * targeted one, which is what {@code chaining} chooses between.
     */
    public record OverlayView(int zone, boolean chaining, float progress)
    {
    }

    /** Every marker currently standing. */
    public java.util.List<OverlayView> overlaysInFlight(long now)
    {
        java.util.List<OverlayView> views = new ArrayList<>(overlays.size());
        for(Playing animation : overlays)
        {
            views.add(new OverlayView(animation.event().toZone(),
                animation.event().kind() == DuelEvent.Kind.CHAINING, animation.progress(now)));
        }
        return views;
    }

    /**
     * A coin or dice result.
     * <p>
     * The values arrive packed the way the core packs them -- one bit each for
     * coins, six bits each for dice -- and are unpacked by whoever draws them,
     * since that is the same unpacking either presentation does.
     */
    public record TossView(boolean coin, int count, int values, float progress)
    {
    }

    /** Every coin or dice result currently being announced. */
    public java.util.List<TossView> tossesInFlight(long now)
    {
        java.util.List<TossView> views = new ArrayList<>(tosses.size());
        for(Playing animation : tosses)
        {
            DuelEvent event = animation.event();
            views.add(new TossView(event.kind() == DuelEvent.Kind.COIN,
                Math.max(1, event.code()), event.amount(), animation.progress(now)));
        }
        return views;
    }

    /** One card being shown to everybody, with the art it wears. */
    public record RevealView(int code, float progress, Identifier texture,
        float u0, float v0, float u1, float v1)
    {
    }

    /**
     * Every card currently revealed.
     * <p>
     * Pruned here as well as in the render, because the reveal queue is the one
     * that expires by being drawn rather than by being ticked -- and a queue
     * only emptied by the presentation that reads it is a queue that grows
     * forever in the presentation that does not.
     */
    public java.util.List<RevealView> revealsInFlight(long now)
    {
        reveals.removeIf(playing -> playing.done(now));
        java.util.List<RevealView> views = new ArrayList<>(reveals.size());
        for(Playing animation : reveals)
        {
            Identifier texture = artFor(animation.event().code());
            boolean edoproArt = isEdoproArt(texture);
            views.add(new RevealView(animation.event().code(), animation.progress(now), texture,
                edoproArt ? 0F : DuelTextures.CARD_U0, edoproArt ? 0F : DuelTextures.CARD_V0,
                edoproArt ? 1F : DuelTextures.CARD_U1, edoproArt ? 1F : DuelTextures.CARD_V1));
        }
        return views;
    }

    /** Every card currently breaking, with how far through it is. */
    public java.util.List<ShatterView> shattersInFlight(long now)
    {
        java.util.List<ShatterView> views = new ArrayList<>(shatters.size());
        for(Playing animation : shatters)
        {
            DuelEvent event = animation.event();
            Identifier texture = artFor(event.code());
            boolean edoproArt = isEdoproArt(texture);
            views.add(new ShatterView(event.code(), event.fromZone(), animation.progress(now),
                texture,
                edoproArt ? 0F : DuelTextures.CARD_U0, edoproArt ? 0F : DuelTextures.CARD_V0,
                edoproArt ? 1F : DuelTextures.CARD_U1, edoproArt ? 1F : DuelTextures.CARD_V1));
        }
        return views;
    }

    /** Every attack currently in flight, with how far through it is. */
    public java.util.List<AttackView> attacksInFlight(long now)
    {
        java.util.List<AttackView> views = new ArrayList<>(attacks.size());
        for(Playing animation : attacks)
        {
            views.add(new AttackView(animation.event().fromZone(), animation.event().toZone(),
                animation.progress(now)));
        }
        return views;
    }

    public void clear()
    {
        queue.clear();
        playing.clear();
        tosses.clear();
        flips.clear();
        flashes.clear();
        attacks.clear();
        overlays.clear();
        shatters.clear();
        nextStart = 0;
    }

    /** True while anything is still playing, for callers that want to wait. */
    public boolean isBusy()
    {
        return !playing.isEmpty() || !attacks.isEmpty() || !overlays.isEmpty()
            || !shatters.isEmpty() || !tosses.isEmpty() || !flips.isEmpty() || !queue.isEmpty();
    }

    /**
     * Whether a texture is already card-shaped rather than letterboxed.
     * <p>
     * A downloaded card sits in a window of a square file, which is what
     * {@code CARD_U0..CARD_V1} exists to sample. EDOPro's own art — the card
     * backs and the unknown-card placeholder — is the card and nothing else, so
     * sampling it through that window crops it to its middle and stretches what
     * survives. {@link #artFor} hands back a back for every face-down card, so
     * every face-down card in a move or a shatter was drawn that way.
     * <p>
     * {@code BoardRenderer} has made the same test since the board was ported;
     * this is that test where the animations can reach it. It went unnoticed
     * because the old back was a dark vortex whose middle looks much like its
     * edges — a flat back shows the crop immediately.
     */
    private static boolean isEdoproArt(Identifier texture)
    {
        return texture.equals(DuelTextures.COVER)
            || texture.equals(DuelTextures.COVER_OPPONENT)
            || texture.equals(DuelTextures.UNKNOWN);
    }

    private static Identifier artFor(int code)
    {
        return artFor(code, -1);
    }

    /**
     * @param zoneRef the field zone this card is standing in by the time the
     *                animation runs, or -1 when there is none. The board is
     *                applied before its events play, so for a card arriving
     *                somewhere that zone already holds it — which is how a
     *                copy wearing chosen artwork is drawn wearing it while it
     *                travels, instead of changing picture on landing.
     */
    private static Identifier artFor(int code, int zoneRef)
    {
        if(code == 0)
        {
            return DuelTextures.COVER;
        }
        Properties card = DdDatabase.PROPERTIES_LIST.get((long)code);
        return card == null ? DuelTextures.COVER
            : DuelTextures.card(card, DuelTextures.artIndex(card, artAtZone(code, zoneRef)),
                DuelTextures.FIELD_CARD_SIZE);
    }

    /**
     * The artwork worn by the copy in a field zone, and 0 for anything less
     * certain than that.
     * <p>
     * The code must match as well as the zone. A zone identifies one physical
     * card, but only of the board it was read from, and an update may move
     * several cards through one zone before this frame is drawn; requiring the
     * code to agree makes the answer either exactly this copy's artwork or the
     * printed one. There is no third case where a wrong artwork is shown
     * confidently, which for a card whose whole point is which picture it
     * wears would be worse than the printed one.
     */
    private static int artAtZone(int code, int zoneRef)
    {
        if(zoneRef < 0)
        {
            return 0;
        }
        boolean opponent = (zoneRef & 16) != 0;
        boolean monsterZone = (zoneRef & 8) != 0;
        int sequence = zoneRef & 7;
        de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot snapshot = DuelClientState.board;
        de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot.Side side =
            opponent ? snapshot.opponent() : snapshot.self();
        java.util.List<de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot.Slot> zones =
            monsterZone ? side.monsters() : side.spells();
        if(sequence < 0 || sequence >= zones.size())
        {
            return 0;
        }
        de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot.Slot slot = zones.get(sequence);
        return slot.present() && slot.code() == code ? slot.art() : 0;
    }

    /** Unpacks a zone reference back into its rectangle on the table. */
    private static FieldLayout.Rect zoneRect(int zoneRef)
    {
        if(zoneRef < 0)
        {
            return null;
        }
        boolean opponent = (zoneRef & 16) != 0;
        boolean monsterZone = (zoneRef & 8) != 0;
        int sequence = zoneRef & 7;
        return FieldLayout.zone(opponent ? 1 : 0,
            monsterZone ? de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_MZONE
                : de.cas_ual_ty.dueldimension.ocg.OcgConstants.LOCATION_SZONE,
            sequence);
    }

    /** The card quad from materials.cpp, matching BoardRenderer's. */
    private static final float CARD_W = 0.7F;
    private static final float CARD_H = 1.0F;

    /** Ease-out, so a card decelerates into its zone. */
    /**
     * How a card crosses the board: quickly at first and settling in.
     * <p>
     * Shared rather than restated, because the world board draws the same move
     * from the same timing and a second easing would be the same card arriving
     * at two different moments depending on where it was being watched from.
     */
    public static float ease(float t)
    {
        return 1F - (1F - t) * (1F - t);
    }
}
