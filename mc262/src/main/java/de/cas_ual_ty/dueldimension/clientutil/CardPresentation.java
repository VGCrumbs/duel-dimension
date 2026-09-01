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

    /**
     * A card's own words, wrapped, for a panel that is already showing its
     * facts -- see {@link Properties#addBodyText}.
     * <p>
     * Wrapping is done here rather than by the caller because the body is now
     * SEVERAL lines rather than one string, and each has to be wrapped on its
     * own: a Pendulum monster's scales, its Pendulum Effect, a blank, and then
     * its lower box. Joining them and wrapping once would run the last line of
     * one paragraph into the first of the next.
     * <p>
     * Every panel that shows card text calls this. The one thing that must not
     * is SEARCH, which matches against {@code getText()} deliberately: a search
     * index is not a rendering.
     */
    public static List<net.minecraft.util.FormattedCharSequence> bodyLines(
        net.minecraft.client.gui.Font font, Properties card, int wrapWidth)
    {
        List<net.minecraft.util.FormattedCharSequence> out = new ArrayList<>();
        if(card == null)
        {
            return out;
        }
        List<CardLine> lines = new ArrayList<>();
        card.addBodyText(lines);
        for(Component line : components(lines))
        {
            if(line.getString().isEmpty())
            {
                // A deliberate blank -- the border between a Pendulum card's
                // two boxes. font.split drops an empty component entirely, so
                // the separator has to be put back by hand or the two effects
                // run together as one paragraph.
                out.add(net.minecraft.util.FormattedCharSequence.EMPTY);
                continue;
            }
            out.addAll(font.split(line, wrapWidth));
        }
        return out;
    }

    /**
     * How many of {@link #bodyLines}' LEADING lines are the Pendulum box.
     * <p>
     * Zero for every card that is not a Pendulum monster, which is nearly all
     * of them. Wrapped at the same width by the same call, so the answer counts
     * the same lines the panel is about to draw -- a box measured at one width
     * and drawn at another is a box that fits the text on some cards and not
     * others.
     * <p>
     * Leading is guaranteed by {@code Properties.addBodyText}, which puts the
     * box first and everything else after it. A panel can therefore frame rows
     * {@code 0} to this and needs to know nothing else about what a Pendulum
     * card is.
     */
    public static int pendulumLineCount(net.minecraft.client.gui.Font font, Properties card,
        int wrapWidth)
    {
        if(card == null)
        {
            return 0;
        }
        List<CardLine> lines = new ArrayList<>();
        card.addPendulumBox(lines);
        int count = 0;
        for(Component line : components(lines))
        {
            count += line.getString().isEmpty() ? 1 : font.split(line, wrapWidth).size();
        }
        return count;
    }

    /**
     * A card's body laid out for a panel that draws the Pendulum scales as a
     * plate of their own.
     *
     * @param lines         every line, in scroll order
     * @param pendulumLines how many leading lines belong to the Pendulum box
     * @param scale         the scales, or null when the card has none
     */
    public record PeekBody(List<net.minecraft.util.FormattedCharSequence> lines,
        int pendulumLines, String scale)
    {
    }

    /**
     * The body, with the Pendulum Effect starting ON the scales' line.
     *
     * <h2>The hanging indent, and why it is done by hand</h2>
     * {@code font.split} wraps a whole string to one width; it has no notion of
     * a first line that is shorter than the rest. So the first line is split at
     * the narrow width, its text taken back off the front, and the remainder
     * wrapped at the full width. Splitting the whole thing narrow instead would
     * indent every line to clear a plate that is only beside the first.
     *
     * @param scaleWidth how much room the scales' plate takes on the first
     *                   line, in the same units as {@code wrap}
     */
    public static PeekBody peekBody(net.minecraft.client.gui.Font font, Properties card,
        int wrap, int scaleWidth)
    {
        List<net.minecraft.util.FormattedCharSequence> out = new ArrayList<>();
        if(card == null)
        {
            return new PeekBody(out, 0, null);
        }
        String scale = card.pendulumScaleText();
        String pendulum = card.pendulumEffectText();
        int pendulumLines = 0;
        if(scale != null)
        {
            String text = pendulum == null ? "" : pendulum;
            int firstWrap = Math.max(1, wrap - scaleWidth);
            List<net.minecraft.network.chat.FormattedText> firstPass =
                font.getSplitter().splitLines(net.minecraft.network.chat.FormattedText.of(text),
                    firstWrap, net.minecraft.network.chat.Style.EMPTY);
            String first = firstPass.isEmpty() ? "" : firstPass.get(0).getString();
            String rest = text.length() <= first.length() ? ""
                : text.substring(first.length()).stripLeading();
            // BUILT FROM STRINGS, AND BLANKS DROPPED.
            //
            // This is the phantom extra row at the foot of the Pendulum box,
            // and it was never a spacing constant. Two separate things put an
            // empty line in here: the splitter and font.split measure the same
            // text a hair differently, so re-wrapping a line the splitter had
            // already decided could hand back two; and a wrap landing on a
            // trailing space produces a final line with nothing in it. Either
            // way the panel counted a line, reserved a line's height for it,
            // and drew nothing -- so the tinted box ran a row past its text.
            //
            // Splitting to TEXT rather than to a character sequence is what
            // makes the blanks visible enough to drop: a FormattedCharSequence
            // will not tell you whether it is empty.
            List<net.minecraft.network.chat.FormattedText> pieces =
                new ArrayList<>();
            pieces.add(net.minecraft.network.chat.FormattedText.of(first));
            if(!rest.isEmpty())
            {
                pieces.addAll(font.getSplitter().splitLines(
                    net.minecraft.network.chat.FormattedText.of(rest), wrap,
                    net.minecraft.network.chat.Style.EMPTY));
            }
            for(net.minecraft.network.chat.FormattedText piece : pieces)
            {
                if(!piece.getString().isBlank())
                {
                    out.add(net.minecraft.util.FormattedCharSequence.forward(
                        piece.getString(), net.minecraft.network.chat.Style.EMPTY));
                }
            }
            pendulumLines = Math.max(1, out.size());
            // No blank line between the boxes. There was one to stand in for
            // the border a printed card has -- but the panel now DRAWS that
            // border, as the tinted plate behind these lines, so a blank as
            // well is the separator counted twice and the lower box floating
            // away from the upper one.
        }
        String lower = card.getText();
        if(lower != null && !lower.isEmpty())
        {
            out.addAll(font.split(Component.literal(lower), wrap));
        }
        return new PeekBody(out, pendulumLines, scale);
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
