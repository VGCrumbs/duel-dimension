package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.CardHolder;
import de.cas_ual_ty.dueldimension.card.CardLine;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a card looks like on THIS Minecraft, kept out of the card itself.
 * <p>
 * {@link Properties} used to build its own {@code Component}s and its own
 * {@code Identifier}s. Both are presentation, both are version-specific, and
 * between them they pinned the mod's most central type to one Minecraft — and
 * through it, roughly a hundred files that name no Minecraft class of their own.
 * <p>
 * The card says what it is; this says how it looks. Splitting them costs one
 * indirection at about forty call sites and buys a card model that both
 * platforms share rather than each maintaining a copy.
 *
 * @see CardLine the shape a card describes itself in
 */
public final class CardPresentation
{
    private CardPresentation()
    {
    }

    // ---- text ----

    /** One line, with its segments' colours applied. */
    public static Component component(CardLine line)
    {
        if(line.isBlank())
        {
            // A genuinely empty component rather than a literal containing
            // nothing: that is what the code this replaces produced, and an
            // empty literal is not always laid out identically.
            return Component.empty();
        }
        MutableComponent out = Component.literal("");
        for(CardLine.Segment segment : line.segments())
        {
            MutableComponent piece = Component.literal(segment.text());
            ChatFormatting colour = formatting(segment.colour());
            if(colour != null)
            {
                piece.setStyle(Style.EMPTY.applyFormat(colour));
            }
            out.append(piece);
        }
        return out;
    }

    public static List<Component> components(List<CardLine> lines)
    {
        List<Component> out = new ArrayList<>(lines.size());
        for(CardLine line : lines)
        {
            out.add(component(line));
        }
        return out;
    }

    /**
     * {@code null} for {@link CardLine.Colour#DEFAULT}, which is the point of
     * that value existing: most tooltip lines set no style at all, and applying
     * WHITE to them would be a decision the old code never made.
     */
    private static ChatFormatting formatting(CardLine.Colour colour)
    {
        return switch(colour)
        {
            case DEFAULT -> null;
            case RED -> ChatFormatting.RED;
            case WHITE -> ChatFormatting.WHITE;
            case DARK_GRAY -> ChatFormatting.DARK_GRAY;
            case BLUE -> ChatFormatting.BLUE;
        };
    }

    /** A card's full description, straight into a tooltip list. */
    public static void addInformation(Properties card, List<Component> tooltip)
    {
        List<CardLine> lines = new ArrayList<>();
        card.addInformation(lines);
        tooltip.addAll(components(lines));
    }

    /** A card's classifications and stats; see {@link Properties#addFacts}. */
    public static void addFacts(Properties card, List<Component> tooltip)
    {
        List<CardLine> lines = new ArrayList<>();
        card.addFacts(lines);
        tooltip.addAll(components(lines));
    }

    /** One specific copy of a card -- its name, code, rarity and artwork. */
    public static void addInformation(CardHolder holder, List<Component> tooltip)
    {
        List<CardLine> lines = new ArrayList<>();
        holder.addInformation(lines);
        tooltip.addAll(components(lines));
    }

    // ---- textures ----
    //
    // These ask DuelDimension.proxy, so they were never shareable regardless of
    // the Identifier: the proxy is where a resource pack's replacement art is
    // resolved, and that is a client concern.

    public static String infoImageName(Properties card, byte imageIndex)
    {
        return DuelDimension.proxy.addCardInfoTag(card.getImageName(imageIndex));
    }

    public static String itemImageName(Properties card, byte imageIndex)
    {
        return DuelDimension.proxy.addCardItemTag(card.getImageName(imageIndex));
    }

    public static String mainImageName(Properties card, byte imageIndex)
    {
        return DuelDimension.proxy.addCardMainTag(card.getImageName(imageIndex));
    }

    public static Identifier infoImage(Properties card, byte imageIndex)
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/item/"
            + DuelDimension.proxy.getCardInfoReplacementImage(card,
                card.adjustImageIndex(imageIndex)) + ".png");
    }

    public static Identifier itemImage(Properties card, byte imageIndex)
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID,
            "item/" + itemImageName(card, imageIndex));
    }

    public static Identifier mainImage(Properties card, byte imageIndex)
    {
        return Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/item/"
            + DuelDimension.proxy.getCardMainReplacementImage(card,
                card.adjustImageIndex(imageIndex)) + ".png");
    }

    // ---- textures, for one held copy ----
    //
    // These were CardHolder's, delegating to Properties'. Both ends moved here
    // together rather than leaving a delegate behind that would have to move
    // again later.

    public static String infoImageName(CardHolder holder)
    {
        return infoImageName(holder.getCard(), holder.getImageIndex());
    }

    public static String itemImageName(CardHolder holder)
    {
        return itemImageName(holder.getCard(), holder.getImageIndex());
    }

    public static String mainImageName(CardHolder holder)
    {
        return mainImageName(holder.getCard(), holder.getImageIndex());
    }

    public static Identifier infoImage(CardHolder holder)
    {
        return infoImage(holder.getCard(), holder.getImageIndex());
    }

    public static Identifier itemImage(CardHolder holder)
    {
        return itemImage(holder.getCard(), holder.getImageIndex());
    }

    public static Identifier mainImage(CardHolder holder)
    {
        return mainImage(holder.getCard(), holder.getImageIndex());
    }
}
