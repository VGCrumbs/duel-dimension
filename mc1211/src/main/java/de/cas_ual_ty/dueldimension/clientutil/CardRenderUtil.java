package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.card.CardSleevesType;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.duel.playfield.CardPosition;
import de.cas_ual_ty.dueldimension.duel.playfield.DuelCard;
import de.cas_ual_ty.dueldimension.rarity.RarityEntry;
import de.cas_ual_ty.dueldimension.rarity.RarityLayer;
import net.minecraft.client.gui.Font;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedList;
import java.util.List;

/**
 * Where a card's picture comes from, and how a card is drawn small.
 * <p>
 * Two jobs that used to be one. "Bind" is gone as an idea — a texture is an
 * argument to a draw now, not a piece of global state set before one — so the
 * binder calls here no longer bind anything: they ask {@link CardImageManager}
 * for the card's art and hand back the id the caller should draw with, which is
 * the real art once a worker has decoded it and the placeholder until then. The
 * name is kept because every call site is a port of a call site.
 * <p>
 * The duel field's half is here too, now that the two things it waited on are
 * settled: a card lying on its side turns the quad about its centre
 * ({@link DdBlitUtil#fullBlitTurned}), and a foil glints through two blend
 * passes ({@link FoilPipelines}) which were tested to survive batching.
 */
public class CardRenderUtil
{
    public static final ResourceLocation MASK_RL = ResourceLocation.fromNamespaceAndPath(
        DuelDimension.MOD_ID, "textures/gui/rarity_mask.png");

    // The two LimitedTextureBinders that used to live here are gone. They
    // capped how many card images were resident, by count, on the one path
    // (item icons and the duel field) that fed them -- no hub screen ever did,
    // which is why a browse could grow without bound. CardImageManager's own
    // per-size LRU now covers every path, in bytes rather than in a count, so a
    // second cap over a subset of the same textures would only fight it.

    public static void renderCardInfo(GuiGraphicsExtractor ms, CardHolder card)
    {
        CardRenderUtil.renderCardInfo(ms, card, 100);
    }

    public static void renderCardInfo(GuiGraphicsExtractor ms, CardHolder card, int width)
    {
        CardRenderUtil.renderCardInfo(ms, card, false, width);
    }

    /**
     * A card's picture with its text under it, at half scale.
     * <p>
     * The half scale used to be a {@code PoseStack} scale around the text and a
     * doubling of every measurement to compensate. The GUI matrix is still
     * there, so the trick still works and is kept: the alternative is a second
     * font size, and the text has to line up with a Forge screen's.
     */
    public static void renderCardInfo(GuiGraphicsExtractor ms, CardHolder card, boolean token,
        int width)
    {
        if(card == null || card.getCard() == null)
        {
            return;
        }

        final float f = 0.5f;
        final int imageSize = 64;
        int margin = 2;

        int maxWidth = width - margin * 2;

        ms.pose().pushMatrix();

        int x = margin;

        if(maxWidth < imageSize)
        {
            // draw it centered if the space we got is limited
            // to make sure the image is NOT rendered more to the right of the center
            x = (maxWidth - imageSize) / 2 + margin;
        }

        // card texture

        DdBlitUtil.fullBlit(ms, CardRenderUtil.bindInfoResourceLocation(card),
            x, margin, imageSize, imageSize);

        if(token)
        {
            DdBlitUtil.fullBlit(ms, CardRenderUtil.getInfoTokenOverlay(),
                x, margin, imageSize, imageSize);
        }

        // need to multiply x2 because we are scaling the text to x0.5
        maxWidth *= 2;
        margin *= 2;
        ms.pose().scale(f, f);

        // card description text

        Font fontRenderer = ClientProxy.getMinecraft().font;

        List<Component> list = new LinkedList<>();
        CardPresentation.addInformation(card.getCard(), list);

        ScreenUtil.drawSplitString(ms, fontRenderer, list, margin, imageSize * 2 + margin * 2,
            maxWidth, 0xFFFFFF);

        ms.pose().popMatrix();
    }

    /**
     * Asks for a card's inspect-size image and says what to draw now.
     * <p>
     * Returning the id is the whole change: the caller draws with it rather
     * than relying on it having been made current. It comes back through
     * {@link CardImageManager}, which is the placeholder on a first sighting and
     * the art from the next frame or two -- this and {@code DuelTextures.card}
     * are the two accessors every piece of card art in the client reaches the
     * screen through, and an ResourceLocation that skips them is decoded and uploaded
     * inline on the render thread.
     */
    public static ResourceLocation bindInfoResourceLocation(CardHolder c)
    {
        return CardRenderUtil.bindInfoResourceLocation(CardPresentation.infoImage(c));
    }

    public static ResourceLocation bindMainResourceLocation(CardHolder c)
    {
        return CardRenderUtil.bindMainResourceLocation(CardPresentation.mainImage(c));
    }

    public static ResourceLocation bindInfoResourceLocation(Properties p, byte imageIndex)
    {
        return CardRenderUtil.bindInfoResourceLocation(CardPresentation.infoImage(p, imageIndex));
    }

    public static ResourceLocation bindMainResourceLocation(Properties p, byte imageIndex)
    {
        return CardRenderUtil.bindMainResourceLocation(CardPresentation.mainImage(p, imageIndex));
    }

    public static ResourceLocation bindInfoResourceLocation(ResourceLocation r)
    {
        return CardImageManager.getTextureCard(r, ClientProxy.activeCardInfoImageSize);
    }

    public static ResourceLocation bindMainResourceLocation(ResourceLocation r)
    {
        return CardImageManager.getTextureCard(r, ClientProxy.activeCardMainImageSize);
    }

    public static ResourceLocation bindSleeves(CardSleevesType s)
    {
        return s.getMainRL(ClientProxy.activeCardMainImageSize);
    }

    public static ResourceLocation getInfoCardBack()
    {
        return ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "textures/item/" + ClientProxy.activeCardInfoImageSize + "/"
                + itemPath(DdItems.CARD_BACK) + ".png");
    }

    public static ResourceLocation getMainCardBack()
    {
        return ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "textures/item/" + ClientProxy.activeCardMainImageSize + "/"
                + itemPath(DdItems.CARD_BACK) + ".png");
    }

    public static ResourceLocation getInfoTokenOverlay()
    {
        return ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "textures/item/" + ClientProxy.activeCardInfoImageSize + "/token_overlay.png");
    }

    public static ResourceLocation getMainTokenOverlay()
    {
        return ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "textures/item/" + ClientProxy.activeCardMainImageSize + "/token_overlay.png");
    }

    public static ResourceLocation getRarityOverlay()
    {
        return ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "textures/item/" + ClientProxy.activeCardInfoImageSize + "/token_overlay.png");
    }

    /**
     * A card on the duel field, with its rarity foil over it.
     * <p>
     * The foil is two draws: a soft mask at the cursor that writes only alpha,
     * then the foil itself shown in proportion to that alpha. That is the Forge
     * effect exactly -- the glint follows the mouse -- and the reason it can be
     * written at all is that a retained-mode GUI turned out to preserve the
     * order of the two passes.
     */
    public static void renderDuelCardAdvanced(GuiGraphicsExtractor ms, CardSleevesType back,
        int mouseX, int mouseY, int x, int y, int width, int height, DuelCard card,
        CardPosition position, DdBlitUtil.FullBlitMethod blitMethod)
    {
        renderDuelCardAdvanced(ms, back, mouseX, mouseY, x, y, width, height, card, position,
            blitMethod, DdBlitUtil.NO_TINT);
    }

    /**
     * As above, faded.
     * <p>
     * A zone widget fades in, and on Forge that was a shader colour set once
     * before everything it drew -- including its cards. There is no such colour
     * now, so the tint has to travel: the widget works it out, and it reaches
     * the card face, the token overlay and the foil alike.
     */
    public static void renderDuelCardAdvanced(GuiGraphicsExtractor ms, CardSleevesType back,
        int mouseX, int mouseY, int x, int y, int width, int height, DuelCard card,
        CardPosition position, DdBlitUtil.FullBlitMethod blitMethod, int tint)
    {
        ResourceLocation face = position.isFaceUp
            ? bindMainResourceLocation(card.getCardHolder())
            : back.getMainRL(ClientProxy.activeCardMainImageSize);
        blitMethod.fullBlit(ms, face, x, y, width, height, tint);

        if(card.getIsToken())
        {
            blitMethod.fullBlit(ms, getMainTokenOverlay(), x, y, width, height,
                tint);
        }

        if(!position.isFaceUp || card.getIsToken())
        {
            return;
        }

        RarityEntry rarity = DdDatabase.getRarity(card.getCardHolder().getRarity());
        if(rarity == null)
        {
            return;
        }
        for(RarityLayer layer : rarity.layers)
        {
            // The mask is drawn at the CURSOR, not at the card: that offset is
            // the whole effect. It writes alpha only, so nothing of it shows.
            ms.blit(FoilPipelines.MASK, MASK_RL, mouseX - width / 2, mouseY - height / 2,
                0F, 0F, width, height, width, height, width, height, tint);
            ms.blit(layer.type.invertedRendering ? FoilPipelines.FOIL_INVERTED
                    : FoilPipelines.FOIL,
                layer.getMainImageResourceLocation(), x, y,
                0F, 0F, width, height, width, height, width, height, tint);
        }
    }

    public static void renderDuelCardAdvanced(GuiGraphicsExtractor ms, CardSleevesType back,
        int mouseX, int mouseY, int x, int y, int width, int height, DuelCard card,
        DdBlitUtil.FullBlitMethod blitMethod, boolean forceFaceUp)
    {
        CardPosition position = card.getCardPosition();

        // bind the texture depending on faceup or facedown
        if(!card.getCardPosition().isFaceUp && forceFaceUp)
        {
            position = position.flip();
        }

        renderDuelCardAdvanced(ms, back, mouseX, mouseY, x, y, width, height, card, position,
            blitMethod);
    }

    /** Upright, or on its side if the card is in defence. */
    public static void renderDuelCard(GuiGraphicsExtractor ms, CardSleevesType back,
        int mouseX, int mouseY, int x, int y, int width, int height, DuelCard card,
        boolean forceFaceUp)
    {
        renderDuelCardAdvanced(ms, back, mouseX, mouseY, x, y, width, height, card,
            card.getCardPosition().isStraight
                ? DdBlitUtil::fullBlit
                : DdBlitUtil::fullBlit90Degree, forceFaceUp);
    }

    /** The same, seen from the other side of the table. */
    public static void renderDuelCardReversed(GuiGraphicsExtractor ms, CardSleevesType back,
        int mouseX, int mouseY, int x, int y, int width, int height, DuelCard card,
        boolean forceFaceUp)
    {
        renderDuelCardAdvanced(ms, back, mouseX, mouseY, x, y, width, height, card,
            card.getCardPosition().isStraight
                ? DdBlitUtil::fullBlit180Degree
                : DdBlitUtil::fullBlit270Degree, forceFaceUp);
    }

    public static void renderDuelCardCentered(GuiGraphicsExtractor ms, CardSleevesType back,
        int mouseX, int mouseY, int x, int y, int width, int height, DuelCard card,
        boolean forceFaceUp)
    {
        // if width and height are more of a rectangle, this centers the texture
        // horizontally -- and it is also what makes a quarter turn legal: a
        // turned quad only covers the same pixels when the region is square.
        x -= (height - width) / 2;
        width = height;

        renderDuelCard(ms, back, mouseX, mouseY, x, y, width, height, card, forceFaceUp);
    }

    public static void renderDuelCardReversedCentered(GuiGraphicsExtractor ms,
        CardSleevesType back, int mouseX, int mouseY, int x, int y, int width, int height,
        DuelCard card, boolean forceFaceUp)
    {
        x -= (height - width) / 2;
        width = height;

        renderDuelCardReversed(ms, back, mouseX, mouseY, x, y, width, height, card, forceFaceUp);
    }

    /**
     * An item's registry name, which used to be reachable through Forge's
     * registry object and is now asked of the registry directly.
     */
    private static String itemPath(net.minecraft.world.item.Item item)
    {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
    }
}
