package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import net.minecraft.resources.ResourceLocation;

/**
 * Duel-screen textures, and card images at a resolution the screen actually
 * needs.
 * <p>
 * The board art is EDOPro's own (AGPL, see LICENSE.md beside the files). Card
 * art comes from the mod's image pipeline, but requested at an explicit size
 * rather than the {@code cardMainImageSize} config value — that setting exists
 * to keep item icons cheap and defaults to 64px, which is illegibly blurry
 * when a card is drawn large on a duel field or in a preview panel.
 */
public final class DuelTextures
{
    /**
     * The Master Rule 5 playmat. EDOPro keeps one mat per rules era and picks
     * by duel_field; field4 is the modern one, and the *-transparent variant
     * is only swapped in when a face-up Field Spell's own art is drawn
     * underneath it.
     */
    public static final ResourceLocation FIELD =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/field4.png");
    public static final ResourceLocation FIELD_TRANSPARENT =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/field-transparent4.png");
    /** Card backs: EDOPro uses a different one per side (tCover[controler]). */
    public static final ResourceLocation COVER =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/cover.png");
    public static final ResourceLocation COVER_OPPONENT =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/cover2.png");
    /** Fallback art for a card whose image is missing or still downloading. */
    public static final ResourceLocation UNKNOWN =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/unknown.png");
    /** The life-point bar frame; the fill inside it is drawn procedurally. */
    public static final ResourceLocation LP_FRAME =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/lpf.png");
    public static final ResourceLocation BACKDROP =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/bg.png");
    /** 5x4 atlas of 64px digits, used for chain-link and counter badges. */
    public static final ResourceLocation NUMBERS =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/number.png");
    public static final ResourceLocation ACT =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/act.png");
    public static final ResourceLocation ATTACK =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/attack.png");
    public static final ResourceLocation CHAIN =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/chain.png");
    public static final ResourceLocation NEGATED =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/negated.png");
    public static final ResourceLocation TARGET =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/target.png");

    /**
     * Cards drawn on the field. 256 rather than something smaller because the
     * mod's own card info panels already generate that size, so field art
     * appears immediately instead of waiting on a fresh render pass.
     */
    public static final int FIELD_CARD_SIZE = 256;
    /** The preview panel draws a card several hundred pixels tall. */
    public static final int PREVIEW_CARD_SIZE = 256;

    /** EDOPro's cover.png is 480x700; keep that ratio wherever we draw a card. */
    public static final float CARD_ASPECT = 480F / 700F;

    /**
     * The mod stores card images letterboxed inside a square: measured across
     * every cached image, the card occupies u 0.199..0.801 and v 0.0625..0.9375
     * (aspect 0.6875, matching the printed card). Drawing the whole square into
     * a card-shaped quad squeezes the art; sampling this window instead keeps
     * it true. EDOPro's own textures (cover, unknown) are already card-shaped
     * and use the full range.
     */
    public static final float CARD_U0 = 0.19922F;
    public static final float CARD_U1 = 0.80078F;
    public static final float CARD_V0 = 0.0625F;
    public static final float CARD_V1 = 0.9375F;

    private DuelTextures()
    {
    }

    /**
     * A card image at an explicit size. Requesting a size the pipeline has not
     * produced yet kicks off its generation and yields a placeholder in the
     * meantime, exactly as the rest of the mod behaves.
     */
    public static ResourceLocation card(Properties properties, byte imageIndex, int size)
    {
        String image = ImageHandler.getReplacementImage(properties, imageIndex, size);
        // While the pipeline is still fetching (or gave up on) a card, show
        // the reference client's own "unknown card" art rather than the mod's
        // loading placeholder, which reads as a broken card on a duel field.
        if(image.endsWith("card_loading") || image.endsWith("card_failed"))
        {
            return UNKNOWN;
        }
        return new ResourceLocation(DuelDimension.MOD_ID, "textures/item/" + image + ".png");
    }
}
