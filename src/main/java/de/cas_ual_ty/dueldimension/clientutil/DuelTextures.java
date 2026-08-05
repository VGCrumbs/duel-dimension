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
    public static final ResourceLocation FIELD =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/field-transparent.png");
    public static final ResourceLocation COVER =
        new ResourceLocation(DuelDimension.MOD_ID, "textures/duel/cover.png");
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
        return new ResourceLocation(DuelDimension.MOD_ID,
            "textures/item/" + ImageHandler.getReplacementImage(properties, imageIndex, size) + ".png");
    }
}
