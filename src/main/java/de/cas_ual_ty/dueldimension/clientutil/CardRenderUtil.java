package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DdItems;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.card.CardSleevesType;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.LinkedList;
import java.util.List;

/**
 * Where a card's picture comes from, and how a card is drawn small.
 * <p>
 * Two jobs that used to be one. "Bind" is gone as an idea — a texture is an
 * argument to a draw now, not a piece of global state set before one — so the
 * binder calls here no longer bind anything: they tell the {@link
 * LimitedTextureBinder} that this image is in use, which is what stops it being
 * released while it is on screen, and they hand back the id for the caller to
 * draw with. The name is kept because every call site is a port of a call site.
 * <p>
 * <b>Not here yet:</b> the duel field's card rendering — the rotated blits, and
 * the rarity foils, which were drawn by masking one texture against another
 * with a colour mask and a second pass. Both belong to the duel screen, which
 * is not ported, and neither can be written honestly without it: a rotation is
 * a transform on a quad this API does not expose, and the mask needs a decision
 * about how a foil is composited that should be made while looking at the
 * field. See PORTING.md.
 */
public class CardRenderUtil
{
    public static final Identifier MASK_RL = Identifier.fromNamespaceAndPath(
        DuelDimension.MOD_ID, "textures/gui/rarity_mask.png");

    static LimitedTextureBinder infoTextureBinder;
    static LimitedTextureBinder mainTextureBinder;

    // called from ClientProxy
    public static void init(int maxInfoImages, int maxMainImages)
    {
        CardRenderUtil.infoTextureBinder =
            new LimitedTextureBinder(ClientProxy.getMinecraft(), maxInfoImages);
        CardRenderUtil.mainTextureBinder =
            new LimitedTextureBinder(ClientProxy.getMinecraft(), maxMainImages);
    }

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
        card.getCard().addInformation(list);

        ScreenUtil.drawSplitString(ms, fontRenderer, list, margin, imageSize * 2 + margin * 2,
            maxWidth, 0xFFFFFF);

        ms.pose().popMatrix();
    }

    /**
     * Marks a card's inspect-size image as in use and says where it is.
     * <p>
     * Returning the id is the whole change: the caller draws with it rather
     * than relying on it having been made current.
     */
    public static Identifier bindInfoResourceLocation(CardHolder c)
    {
        return CardRenderUtil.bindInfoResourceLocation(c.getInfoImageResourceLocation());
    }

    public static Identifier bindMainResourceLocation(CardHolder c)
    {
        return CardRenderUtil.bindMainResourceLocation(c.getMainImageResourceLocation());
    }

    public static Identifier bindInfoResourceLocation(Properties p, byte imageIndex)
    {
        return CardRenderUtil.bindInfoResourceLocation(p.getInfoImageResourceLocation(imageIndex));
    }

    public static Identifier bindMainResourceLocation(Properties p, byte imageIndex)
    {
        return CardRenderUtil.bindMainResourceLocation(p.getMainImageResourceLocation(imageIndex));
    }

    public static Identifier bindInfoResourceLocation(Identifier r)
    {
        CardRenderUtil.infoTextureBinder.bind(r);
        return r;
    }

    public static Identifier bindMainResourceLocation(Identifier r)
    {
        CardRenderUtil.mainTextureBinder.bind(r);
        return r;
    }

    public static Identifier bindSleeves(CardSleevesType s)
    {
        return s.getMainRL(ClientProxy.activeCardMainImageSize);
    }

    public static Identifier getInfoCardBack()
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "textures/item/" + ClientProxy.activeCardInfoImageSize + "/"
                + itemPath(DdItems.CARD_BACK) + ".png");
    }

    public static Identifier getMainCardBack()
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "textures/item/" + ClientProxy.activeCardMainImageSize + "/"
                + itemPath(DdItems.CARD_BACK) + ".png");
    }

    public static Identifier getInfoTokenOverlay()
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "textures/item/" + ClientProxy.activeCardInfoImageSize + "/token_overlay.png");
    }

    public static Identifier getMainTokenOverlay()
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "textures/item/" + ClientProxy.activeCardMainImageSize + "/token_overlay.png");
    }

    public static Identifier getRarityOverlay()
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "textures/item/" + ClientProxy.activeCardInfoImageSize + "/token_overlay.png");
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
