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
    /** How long each kind of animation lasts, in milliseconds. */
    private static final long MOVE_MS = 320;
    private static final long FLASH_MS = 420;
    private static final long LUNGE_MS = 260;

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

    /**
     * Accepts what the server reported. Sounds fire immediately; visuals are
     * queued and drawn until they expire.
     */
    public void accept(List<DuelEvent> events, long now)
    {
        for(DuelEvent event : events)
        {
            playSound(event);
            switch(event.kind())
            {
                case MOVE, SUMMON, SPECIAL_SUMMON, SET, ACTIVATE, DESTROY, DRAW ->
                    playing.add(new Playing(event, now, MOVE_MS));
                case DAMAGE, RECOVER -> flashes.add(new Playing(event, now, FLASH_MS));
                case ATTACK -> playing.add(new Playing(event, now, LUNGE_MS));
                default ->
                {
                }
            }
        }
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

    /** Drops finished animations. Call once per frame. */
    public void tick(long now)
    {
        playing.removeIf(animation -> animation.done(now));
        flashes.removeIf(animation -> animation.done(now));
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

    /** True while anything is still playing, for callers that want to wait. */
    public boolean isBusy()
    {
        return !playing.isEmpty();
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

    /** Ease-out, so a card decelerates into its zone. */
    private static float ease(float t)
    {
        return 1F - (1F - t) * (1F - t);
    }
}
