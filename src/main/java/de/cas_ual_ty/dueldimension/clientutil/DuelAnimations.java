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
    private long duration(DuelEvent event)
    {
        long base = switch(event.kind())
        {
            case MOVE -> MOVE_MS;
            case SET -> SET_MS;
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
            case MOVE, SUMMON, SPECIAL_SUMMON, SET, ACTIVATE, DRAW ->
                playing.add(new Playing(event, now, duration));
            case DAMAGE, RECOVER -> flashes.add(new Playing(event, now, duration));
            case ATTACK -> attacks.add(new Playing(event, now, duration));
            case CHAINING, BECOME_TARGET -> overlays.add(new Playing(event, now, duration));
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
            case FLIP -> DdSounds.FLIP.get();
            case CHAINING -> DdSounds.ACTIVATE.get();
            case BECOME_TARGET -> DdSounds.EQUIP.get();
            case SHUFFLE -> DdSounds.SHUFFLE.get();
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
            float ax = from.x() + from.w() / 2F;
            float ay = from.y() + from.h() / 2F;

            float dx;
            float dy;
            FieldLayout.Rect target = zoneRect(event.toZone());
            if(target != null)
            {
                dx = target.x() + target.w() / 2F;
                dy = target.y() + target.h() / 2F;
            }
            else
            {
                // Direct attack: aim at the defending player's side of the table.
                dx = DIRECT_ATTACK_X;
                dy = event.player() == 0 ? -DIRECT_ATTACK_Y : DIRECT_ATTACK_Y;
            }

            float t = animation.progress(now);
            // The line reaches the target first, then the sword launches along
            // it, so the direction reads before the strike lands.
            float reach = Math.min(1F, t * 2.2F);
            float alpha = t < 0.8F ? 1F : 1F - (t - 0.8F) / 0.2F;
            drawArrow(poseStack, projection, ax, ay, ax + (dx - ax) * reach, ay + (dy - ay) * reach,
                alpha);

            // The sword sets off once the line is drawn and flies to the target.
            if(t > 0.35F)
            {
                float travel = Math.min(1F, (t - 0.35F) / 0.5F);
                float swordX = ax + (dx - ax) * travel;
                float swordY = ay + (dy - ay) * travel;
                FieldQuad.draw(poseStack, DuelTextures.ATTACK,
                    projection.cardQuad(swordX, swordY, SWORD_SIZE, SWORD_SIZE),
                    1F, 1F, 1F, alpha);
            }
        }
    }

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

    /** One arrow from (ax, ay) to (dx, dy) in field units. */
    private static void drawArrow(PoseStack poseStack, FieldLayout.Projection projection,
        float ax, float ay, float dx, float dy, float alphaScale)
    {
        float vx = dx - ax;
        float vy = dy - ay;
        float length = (float)Math.sqrt(vx * vx + vy * vy);
        if(length < 0.05F)
        {
            return;
        }
        float ux = vx / length;
        float uy = vy / length;
        // Perpendicular, for the shaft's and head's width.
        float px = -uy;
        float py = ux;

        float headLength = Math.min(ARROW_HEAD_LENGTH, length * 0.6F);
        float neckX = dx - ux * headLength;
        float neckY = dy - uy * headLength;

        int colour = scaleAlpha(ATTACK_ARROW, alphaScale);

        FieldQuad.fill(poseStack, corners(projection,
            ax + px * ARROW_HALF_WIDTH, ay + py * ARROW_HALF_WIDTH,
            neckX + px * ARROW_HALF_WIDTH, neckY + py * ARROW_HALF_WIDTH,
            neckX - px * ARROW_HALF_WIDTH, neckY - py * ARROW_HALF_WIDTH,
            ax - px * ARROW_HALF_WIDTH, ay - py * ARROW_HALF_WIDTH), colour);

        // The head, as a quad with its two tip corners coincident.
        FieldQuad.fill(poseStack, corners(projection,
            neckX + px * ARROW_HEAD_HALF_WIDTH, neckY + py * ARROW_HEAD_HALF_WIDTH,
            dx, dy, dx, dy,
            neckX - px * ARROW_HEAD_HALF_WIDTH, neckY - py * ARROW_HEAD_HALF_WIDTH), colour);
    }

    /** Projects four field-unit points into screen corners. */
    private static FieldQuad.Corners corners(FieldLayout.Projection projection,
        float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3)
    {
        return new FieldQuad.Corners(
            projection.x(x0, y0), projection.y(y0),
            projection.x(x1, y1), projection.y(y1),
            projection.x(x2, y2), projection.y(y2),
            projection.x(x3, y3), projection.y(y3));
    }

    private static int scaleAlpha(int colour, float scale)
    {
        int alpha = Math.round((colour >>> 24) * Math.max(0F, Math.min(1F, scale)));
        return (alpha << 24) | (colour & 0xFFFFFF);
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
            || !shatters.isEmpty() || !queue.isEmpty();
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
