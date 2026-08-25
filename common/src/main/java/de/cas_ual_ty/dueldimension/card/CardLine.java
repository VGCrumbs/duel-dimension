package de.cas_ual_ty.dueldimension.card;

import java.util.List;

/**
 * One line of a card's description, before anything decides how to draw it.
 * <p>
 * The card classes describe themselves straight into {@code List<Component>},
 * which makes every one of them version-specific over presentation alone — and
 * they are the mod's most central types, so that single coupling pins roughly a
 * hundred otherwise-shareable files to one Minecraft version. What a card
 * actually knows — its name, its type line, its rules text, its link arrows —
 * has nothing to do with Minecraft.
 * <p>
 * So a card produces lines and a platform turns them into whatever it draws
 * with. The polymorphism survives the move: {@code addFactLines} stays
 * overridden per card kind, it just contributes text instead of components, so a
 * card kind added later still says what it is in one place and every preview
 * picks it up.
 *
 * <h2>Why a line is a list of segments</h2>
 *
 * Because the cards already build them that way, which the first draft of this
 * class got wrong by assuming one style per line. A pendulum header is a blue
 * arrow, a scale, a red arrow and another scale on ONE line; {@link
 * de.cas_ual_ty.dueldimension.card.properties.LinkArrow#buildSymbolsString}
 * appends eight differently-coloured arrows into a single component. A
 * one-style-per-line model cannot express either without splitting lines that
 * are meant to sit together.
 * <p>
 * The four colours are read off the code being replaced rather than invented —
 * {@code RED}, {@code WHITE}, {@code DARK_GRAY}, {@code BLUE} are the complete
 * set the card classes use. Adding a fifth is a decision for whoever needs it.
 *
 * @param segments the pieces of the line, in order; empty for a spacer
 */
public record CardLine(List<Segment> segments)
{
    /** A run of text in one colour. */
    public record Segment(String text, Colour colour)
    {
    }

    /**
     * The colours the card classes actually use.
     * <p>
     * {@code DEFAULT} means "whatever the platform draws unstyled text as",
     * which is not the same as white — most tooltip lines specify nothing at
     * all, and saying {@code WHITE} for them would be inventing a decision the
     * old code did not make.
     */
    public enum Colour
    {
        DEFAULT, RED, WHITE, DARK_GRAY, BLUE
    }

    public static CardLine of(String text)
    {
        return new CardLine(List.of(new Segment(text, Colour.DEFAULT)));
    }

    public static CardLine of(String text, Colour colour)
    {
        return new CardLine(List.of(new Segment(text, colour)));
    }

    /** A line assembled from pieces, for headers that mix colours. */
    public static CardLine of(Segment... segments)
    {
        return new CardLine(List.of(segments));
    }

    /**
     * A spacer.
     * <p>
     * Its own factory rather than {@code of("")} so the intent is legible at the
     * call site, and so a platform can render it as a genuinely empty component
     * rather than as a literal containing nothing.
     */
    public static CardLine blank()
    {
        return new CardLine(List.of());
    }

    public boolean isBlank()
    {
        return segments.isEmpty();
    }

    /** The line as plain text, for a caller that cannot show colour. */
    public String plain()
    {
        StringBuilder out = new StringBuilder();
        for(Segment segment : segments)
        {
            out.append(segment.text());
        }
        return out.toString();
    }
}
