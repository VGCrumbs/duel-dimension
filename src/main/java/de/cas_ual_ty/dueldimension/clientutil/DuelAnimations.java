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
     * How long each kind of animation lasts, in milliseconds. These are paced
     * for following a duel rather than for speed: events play one after another
     * (see {@link #tick}), so each of these is also the wait before the next.
     */
    private static final long MOVE_MS = 750;
    private static final long FLASH_MS = 700;
    /** An attack arrow holds long enough to read before the damage lands. */
    private static final long ATTACK_MS = 1300;
    /** Events with no visual still get a beat, so their sounds stay distinct. */
    private static final long BEAT_MS = 320;
    /** A chain or target marker has to be readable before it goes. */
    private static final long OVERLAY_MS = 1200;
    /** A destroyed card breaks apart over this long. */
    private static final long SHATTER_MS = 850;
    /** Fragments per axis: the card breaks into SHARDS x SHARDS pieces. */
    private static final int SHARDS = 3;
    /** However far behind we are, nothing is allowed to flash past faster. */
    private static final long FLOOR_MS = 260;

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

    /** Events waiting their turn to be played. */
    private final java.util.ArrayDeque<DuelEvent> queue = new java.util.ArrayDeque<>();
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
        queue.addAll(events);
    }

    /**
     * How long one event's visual runs. A long backlog compresses these, so a
     * turn full of effects catches up instead of falling further behind — the
     * same idea as the reference client's own catching-up mode.
     */
    private long duration(DuelEvent event)
    {
        long base = switch(event.kind())
        {
            case DESTROY -> SHATTER_MS;
            case MOVE, SUMMON, SPECIAL_SUMMON, SET, ACTIVATE, DRAW -> MOVE_MS;
            case ATTACK -> ATTACK_MS;
            case DAMAGE, RECOVER -> FLASH_MS;
            case CHAINING, BECOME_TARGET -> OVERLAY_MS;
            // No visual of their own: just enough of a beat to hear the sound.
            default -> BEAT_MS;
        };
        return Math.max(FLOOR_MS, Math.round(base * backlogScale()));
    }

    /**
     * Only a genuinely long backlog compresses playback, and never below the
     * floor. The first attempt dropped to 0.3x past a dozen queued events,
     * which a single busy turn reaches easily -- so the pacing it was supposed
     * to fix came straight back.
     */
    private float backlogScale()
    {
        int waiting = queue.size();
        if(waiting > 40)
        {
            return 0.4F;
        }
        if(waiting > 20)
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

    private void playSound(DuelEvent event)
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

        if(!queue.isEmpty() && now >= nextStart)
        {
            start(queue.poll(), now);
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

    /**
     * True while a card is still travelling into this zone, so the board can
     * hold back the settled copy until the animation lands.
     */
    public boolean isArriving(int zoneRef, long now)
    {
        if(zoneRef < 0)
        {
            return false;
        }
        for(Playing animation : playing)
        {
            if(animation.event().toZone() == zoneRef && !animation.done(now))
            {
                return true;
            }
        }
        // Also while it is still queued, or it would pop in and then fly again.
        for(DuelEvent waiting : queue)
        {
            if(waiting.toZone() == zoneRef && isMove(waiting))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isMove(DuelEvent event)
    {
        return switch(event.kind())
        {
            case MOVE, SUMMON, SPECIAL_SUMMON, SET, ACTIVATE, DESTROY, DRAW -> true;
            default -> false;
        };
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
