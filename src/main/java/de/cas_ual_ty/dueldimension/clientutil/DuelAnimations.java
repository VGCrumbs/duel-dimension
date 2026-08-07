package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.DdSounds;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.ocg.prompt.DuelEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
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
    private static final long FLASH_MS = frames(11 + 30);
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
    /** Fragments per axis: the card breaks into SHARDS x SHARDS pieces. */
    private static final int SHARDS = 3;
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
     */
    public void renderReveals(PoseStack poseStack, net.minecraft.client.gui.Font font,
        int screenWidth, int screenHeight, long now)
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
        net.minecraft.client.gui.GuiComponent.fill(poseStack, 0, y - 18, screenWidth,
            y + cardH + 8, 0x88000000);
        font.drawShadow(poseStack, caption, screenWidth / 2F - font.width(caption) / 2F,
            y - 14, 0xFFF4D089);

        for(Playing playing : reveals)
        {
            Properties card = DdDatabase.PROPERTIES_LIST.get((long)playing.event().code());
            ScreenUtil.white();
            if(card == null)
            {
                DuelTextures.bindSmooth(DuelTextures.COVER);
                DdBlitUtil.fullBlit(poseStack, x, y, cardW, cardH);
            }
            else
            {
                DuelTextures.bindSmooth(
                    DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE));
                DdBlitUtil.blit(poseStack, x, y, cardW, cardH,
                    DuelTextures.CARD_U0, DuelTextures.CARD_V0,
                    DuelTextures.CARD_U1 - DuelTextures.CARD_U0,
                    DuelTextures.CARD_V1 - DuelTextures.CARD_V0, 1, 1);
            }
            x += cardW + gap;
        }
    }

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
            case DAMAGE, RECOVER -> FLASH_MS;
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
        // A damage flash sits on the life bar and need not hold up the board.
        long hold = switch(event.kind())
        {
            case DAMAGE, RECOVER -> duration / 2;
            default -> duration;
        };
        nextStart = now + hold;
    }

    /** Overridable so tests can run the queue without Minecraft's registries. */
    protected void playSound(DuelEvent event)
    {
        SoundEvent sound = switch(event.kind())
        {
            case SUMMON -> DdSounds.SUMMON.get();
            case SPECIAL_SUMMON -> DdSounds.SPECIAL_SUMMON.get();
            case SET -> DdSounds.SET.get();
            case ACTIVATE -> DdSounds.ACTIVATE.get();
            case ATTACK -> DdSounds.ATTACK.get();
            case DAMAGE -> DdSounds.DAMAGE.get();
            case RECOVER -> DdSounds.GAIN_LP.get();
            case DESTROY -> DdSounds.DESTROYED.get();
            case DRAW -> DdSounds.DRAW.get();
            case FLIP, POSITION -> DdSounds.FLIP.get();
            case CHAINING -> DdSounds.ACTIVATE.get();
            case BECOME_TARGET -> DdSounds.EQUIP.get();
            case SHUFFLE -> DdSounds.SHUFFLE.get();
            case COIN -> DdSounds.COIN_FLIP.get();
            case DICE -> DdSounds.DICE_ROLL.get();
            case PHASE -> DdSounds.PHASE.get();
            case NEW_TURN -> DdSounds.NEXT_TURN.get();
            default -> null;
        };
        if(sound != null)
        {
            // Master volume applies; these are UI sounds with no position.
            Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(sound, 1F, 0.6F));
        }
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
    public void renderAttacks(PoseStack poseStack, FieldLayout.Projection projection, long now)
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
            drawLine(poseStack, ax, ay, ax + (tx - ax) * reach, ay + (ty - ay) * reach, alpha);

            if(t > 0.35F)
            {
                float travel = Math.min(1F, (t - 0.35F) / 0.5F);
                float size = zoneWidth(projection, from) * 0.9F;
                drawSword(poseStack, ax + (tx - ax) * travel, ay + (ty - ay) * travel,
                    tx - ax, ty - ay, size, alpha);
            }
        }
    }

    /**
     * The aiming pointer: the attack line and sword drawn from the attacker to
     * wherever the player is pointing, while the core waits for the target.
     */
    public void renderAim(PoseStack poseStack, FieldLayout.Projection projection,
        int fromZone, float targetX, float targetY)
    {
        FieldLayout.Rect from = projection == null ? null : zoneRect(fromZone);
        if(from == null)
        {
            return;
        }
        float ax = projection.x(from.x() + from.w() / 2F, from.y() + from.h() / 2F);
        float ay = projection.y(from.y() + from.h() / 2F);
        drawLine(poseStack, ax, ay, targetX, targetY, 0.9F);
        // The sword stays planted on the attacker while aiming; only its
        // rotation follows the cursor. It flies when the attack itself plays.
        float size = zoneWidth(projection, from) * 0.9F;
        drawSword(poseStack, ax, ay, targetX - ax, targetY - ay, size, 1F);
    }

    private static float zoneWidth(FieldLayout.Projection projection, FieldLayout.Rect rect)
    {
        FieldQuad.Corners corners = projection.quad(rect);
        return corners.maxX() - corners.minX();
    }

    /** The red attack line, as a rotated quad cut from white.png. */
    private static void drawLine(PoseStack poseStack, float x1, float y1, float x2, float y2,
        float alpha)
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
        FieldQuad.drawCorners(poseStack, DuelTextures.WHITE, new FieldQuad.Corners(
                x1 + px, y1 + py, x2 + px, y2 + py, x2 - px, y2 - py, x1 - px, y1 - py),
            0F, 0F, 1F, 1F, 1F, 0.15F, 0.15F, alpha * 0.8F);
    }

    /**
     * The sword, rotated to fly point-first at its target. The art points
     * straight up, so its base heading is -PI/2 and the rotation needed is
     * atan2(dy, dx) + PI/2.
     */
    private static void drawSword(PoseStack poseStack, float cx, float cy, float dirX, float dirY,
        float size, float alpha)
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
        FieldQuad.drawCorners(poseStack, DuelTextures.ATTACK, new FieldQuad.Corners(
                corner[0], corner[1], corner[2], corner[3], corner[4], corner[5], corner[6], corner[7]),
            0F, 0F, 1F, 1F, 1F, 1F, 1F, alpha);
    }

    /** Half thickness of the attack line, in gui pixels. */
    private static final float LINE_HALF_WIDTH = 1.6F;

    /**
     * A destroyed card breaking apart.
     * <p>
     * The card is cut into a grid of shards, each drawn from its own corner of
     * the art (drawProjected already samples a UV window, which is what makes
     * this possible), then thrown outwards from the centre and faded. Without
     * it a destroyed card simply vanished into the graveyard count.
     */
    public void renderShatters(PoseStack poseStack, FieldLayout.Projection projection, long now)
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
            float alpha = 1F - t * t;
            ResourceLocation texture = artFor(event.code());

            float shardW = CARD_W / SHARDS;
            float shardH = CARD_H / SHARDS;
            float centreX = rect.x() + rect.w() / 2F;
            float centreY = rect.y() + rect.h() / 2F;

            for(int row = 0; row < SHARDS; row++)
            {
                for(int column = 0; column < SHARDS; column++)
                {
                    // Where this shard starts, as a card-sized grid cell.
                    float x = centreX - CARD_W / 2F + column * shardW;
                    float y = centreY - CARD_H / 2F + row * shardH;
                    // Thrown out from the middle, further the longer it runs.
                    float awayX = (x + shardW / 2F) - centreX;
                    float awayY = (y + shardH / 2F) - centreY;
                    float drift = t * 1.9F;

                    // The matching window of the card's art.
                    float u0 = DuelTextures.CARD_U0
                        + (DuelTextures.CARD_U1 - DuelTextures.CARD_U0) * column / SHARDS;
                    float u1 = DuelTextures.CARD_U0
                        + (DuelTextures.CARD_U1 - DuelTextures.CARD_U0) * (column + 1) / SHARDS;
                    float v0 = DuelTextures.CARD_V0
                        + (DuelTextures.CARD_V1 - DuelTextures.CARD_V0) * row / SHARDS;
                    float v1 = DuelTextures.CARD_V0
                        + (DuelTextures.CARD_V1 - DuelTextures.CARD_V0) * (row + 1) / SHARDS;

                    com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1F, 1F, 1F, alpha);
                    FieldQuad.drawProjected(poseStack, texture, projection,
                        new FieldLayout.Rect(x + awayX * drift, y + awayY * drift, shardW, shardH),
                        1, false, u0, v0, u1, v1);
                }
            }
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }
    }

    /**
     * A card turning over where it lies.
     * <p>
     * The quad narrows to nothing at the halfway point and opens again, which
     * is what a card rotating about its long axis looks like from above, and
     * the face swaps at that midpoint: back then front for a card being turned
     * up, front then back for one being turned down.
     */
    public void renderFlips(PoseStack poseStack, FieldLayout.Projection projection, long now)
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
            ResourceLocation texture = showFace ? artFor(event.code())
                : (event.player() == 0 ? DuelTextures.COVER : DuelTextures.COVER_OPPONENT);

            // |cos| gives one full narrow-and-open across the animation.
            float squash = Math.abs((float)Math.cos(Math.PI * t));
            float width = FLIP_CARD_W * Math.max(0.04F, squash);
            FieldLayout.Rect card = new FieldLayout.Rect(
                zone.x() + (zone.w() - width) / 2F,
                zone.y() + (zone.h() - FLIP_CARD_H) / 2F, width, FLIP_CARD_H);

            boolean edopro = !showFace || event.code() == 0;
            FieldQuad.drawProjected(poseStack, texture, projection, card, FLIP_STEPS,
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
    public void renderTosses(PoseStack poseStack, net.minecraft.client.gui.Font font,
        FieldLayout.Projection projection, long now)
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
                drawTossFace(poseStack, font, Math.round(startX + i * spacing),
                    Math.round(centreY - size / 2F), size, coin, value, alpha);
            }
        }
    }

    /** One coin or die, drawn from white.png and the font. */
    private static void drawTossFace(PoseStack poseStack, net.minecraft.client.gui.Font font,
        int x, int y, int size, boolean coin, int value, float alpha)
    {
        int a = Math.round(Math.max(0F, Math.min(1F, alpha)) * 255);
        FieldQuad.Corners box = new FieldQuad.Corners(x, y, x + size, y, x + size, y + size, x, y + size);
        // Body, then a lighter inner face, so it reads as a struck object.
        FieldQuad.drawCorners(poseStack, DuelTextures.WHITE, box, 0F, 0F, 1F, 1F,
            0.10F, 0.11F, 0.13F, a / 255F);
        FieldQuad.Corners face = new FieldQuad.Corners(x + 2, y + 2, x + size - 2, y + 2,
            x + size - 2, y + size - 2, x + 2, y + size - 2);
        if(coin)
        {
            // coin.png is two frames side by side: tails left, heads right.
            FieldQuad.drawCorners(poseStack, DuelTextures.COIN, box,
                value == 1 ? 0.5F : 0F, 0F, value == 1 ? 1F : 0.5F, 1F, 1F, a / 255F);
        }
        else
        {
            FieldQuad.drawCorners(poseStack, DuelTextures.WHITE, face, 0F, 0F, 1F, 1F,
                0.90F, 0.90F, 0.92F, a / 255F);
            String label = Integer.toString(Math.max(1, Math.min(6, value)));
            font.draw(poseStack, label, x + (size - font.width(label)) / 2F, y + (size - 8) / 2F,
                (a << 24) | 0x14161A);
        }
    }

    /** The table's middle, where an announcement belongs. */
    private static final float FIELD_CENTRE_X =
        (FieldLayout.FIELD_MIN_X + FieldLayout.FIELD_MAX_X) / 2F;

    /**
     * Chain and target markers over the cards they concern, the way
     * drawing.cpp lays tChain over a chaining card and tChainTarget over a
     * targeted one.
     */
    public void renderOverlays(PoseStack poseStack, FieldLayout.Projection projection, long now)
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
            ResourceLocation texture = event.kind() == DuelEvent.Kind.CHAINING
                ? DuelTextures.CHAIN : DuelTextures.TARGET;
            FieldQuad.drawProjected(poseStack, texture, projection, rect, 2);
        }
    }


    /**
     * A card sliding into the zone it just moved to. Drawn after the board so
     * it reads as the card arriving on top of the settled field.
     */
    public void renderMoves(PoseStack poseStack, BoardRenderer board, FieldLayout.Projection projection,
        long now)
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

            ResourceLocation texture = artFor(event.code());
            FieldQuad.drawProjected(poseStack, texture, projection,
                new FieldLayout.Rect(x, y - lift, to.w(), to.h()), 4, false,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0, DuelTextures.CARD_U1, DuelTextures.CARD_V1);
        }
    }

    /** How strongly to tint a player's life bar right now, 0 to 1. */
    public float damageFlash(int player, long now)
    {
        float strongest = 0;
        for(Playing animation : flashes)
        {
            if(animation.event().player() == player)
            {
                strongest = Math.max(strongest, 1F - animation.progress(now));
            }
        }
        return strongest;
    }

    /** Drops everything, for when a duel ends or is left. */
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

    private static ResourceLocation artFor(int code)
    {
        if(code == 0)
        {
            return DuelTextures.COVER;
        }
        Properties card = DdDatabase.PROPERTIES_LIST.get((long)code);
        return card == null ? DuelTextures.COVER
            : DuelTextures.card(card, (byte)0, DuelTextures.FIELD_CARD_SIZE);
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
    private static float ease(float t)
    {
        return 1F - (1F - t) * (1F - t);
    }
}
