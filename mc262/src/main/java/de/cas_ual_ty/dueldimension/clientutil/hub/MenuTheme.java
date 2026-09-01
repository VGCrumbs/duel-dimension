package de.cas_ual_ty.dueldimension.clientutil.hub;

import java.util.List;

/**
 * A menu palette: what colour the furniture is, and what colour the font on it
 * is.
 *
 * <h2>Two colours, not twenty</h2>
 * Every surface the hub draws -- panels, buttons, tabs, slots, scrollbars,
 * chips -- is ONE colour family at a range of shades, plus a single accent used
 * for rules and the lit state of a chip. That is the whole palette. The SHADING
 * lives in the art, as an indexed master under {@code textures/gui/indexed}
 * where each pixel says which of the two roles it belongs to and where on that
 * role's ramp it sits; see {@code tools/gen_hub_ui.py}. A theme supplies the
 * two colours and {@link MenuThemes} repaints the art from them.
 *
 * <h2>The font is chosen, not derived</h2>
 * {@link #text} is one of {@link #WHITE}, {@link #BLACK} or {@link #YELLOW} --
 * picked per theme for legibility on that surface rather than computed, because
 * a computed colour lands wherever the arithmetic says and these three are what
 * actually read at a six-pixel font over a gradient. The REST of the type
 * hierarchy is derived, by {@link MenuInk}, so a theme only ever states one.
 */
public record MenuTheme(String id, String label, int surface, int accent, int text)
{
    /**
     * The three font colours, and the only three.
     * <p>
     * {@link #YELLOW} is the hub's existing gold rather than a pure yellow: it
     * is the colour every title in the mod was already drawn in, so a theme
     * choosing it is choosing continuity rather than a new colour.
     */
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF141418;
    public static final int YELLOW = 0xFFF4D089;
    /**
     * Cobalt, and the reason there are four rather than the three this started
     * with: black on Frost was legible and dead. A light theme is the one place
     * a coloured font has enough contrast behind it to be worth having.
     */
    public static final int BLUE = 0xFF1D4E9C;

    /** The hub as it has always looked: a cool near-black case, gold lettering. */
    public static final MenuTheme GRAPHITE =
        new MenuTheme("graphite", "Graphite", 0x2C303A, 0xD6AC54, YELLOW);
    /** The Duel Bot's, and the only one anything in the mod insists on. */
    public static final MenuTheme COBALT =
        new MenuTheme("cobalt", "Cobalt", 0x204868, 0x96E6FF, YELLOW);
    /** The one light theme, and the reason {@link MenuInk} exists. */
    public static final MenuTheme FROST =
        new MenuTheme("frost", "Frost", 0xD8DCE4, 0x3C5A8C, BLUE);
    public static final MenuTheme RASPBERRY =
        new MenuTheme("raspberry", "Raspberry", 0x6E2040, 0xFFA0BE, WHITE);
    public static final MenuTheme VERDANT =
        new MenuTheme("verdant", "Verdant", 0x224E34, 0xBEF096, YELLOW);

    public static final List<MenuTheme> PRESETS =
        List.of(GRAPHITE, COBALT, FROST, RASPBERRY, VERDANT);

    /** The id a player's own colours are stored under. */
    public static final String CUSTOM = "custom";

    public static MenuTheme preset(String id)
    {
        for(MenuTheme theme : PRESETS)
        {
            if(theme.id().equals(id))
            {
                return theme;
            }
        }
        return GRAPHITE;
    }

    /**
     * A theme from colours the player picked.
     * <p>
     * Only the surface and the font are asked for. The accent is derived,
     * because it is a hairline rule and the lit state of a chip rather than
     * anything a player would think of as "the menu colour" -- and because an
     * accent chosen independently of the surface is the shortest route to a
     * menu that cannot be read.
     */
    public static MenuTheme custom(int surface, int text)
    {
        return new MenuTheme(CUSTOM, "Custom", surface, accentFor(surface), text);
    }

    /** An accent well clear of the surface, away from whichever end it is nearer. */
    private static int accentFor(int surface)
    {
        return luminance(surface) < 128 ? mix(surface, 0xFFFFFF, 0.62F)
            : mix(surface, 0x000000, 0.55F);
    }

    /** Rec. 601 luminance, which is what "is this surface light" means here. */
    public static int luminance(int rgb)
    {
        return (299 * ((rgb >> 16) & 0xFF) + 587 * ((rgb >> 8) & 0xFF)
            + 114 * (rgb & 0xFF)) / 1000;
    }

    /** {@code amount} of the way from {@code from} to {@code to}, per channel. */
    public static int mix(int from, int to, float amount)
    {
        int out = 0;
        for(int shift = 16; shift >= 0; shift -= 8)
        {
            int a = (from >> shift) & 0xFF;
            int b = (to >> shift) & 0xFF;
            out |= Math.round(a + (b - a) * amount) << shift;
        }
        return out;
    }

    /**
     * The same theme in a different font.
     * <p>
     * The font is a SETTING, not part of the preset's identity: a player who
     * likes Cobalt but wants white on it should get that, and should still be
     * on Cobalt. So the id survives and only the text moves -- which is also
     * why {@code MenuThemeSettings} writes the font out separately rather than
     * trusting the preset to supply it on the way back in.
     */
    public MenuTheme withText(int colour)
    {
        return new MenuTheme(id, label, surface, accent, colour);
    }

    /** The next font colour, for a control that cycles rather than lists. */
    public static int nextFont(int colour)
    {
        if(colour == WHITE)
        {
            return BLACK;
        }
        if(colour == BLACK)
        {
            return YELLOW;
        }
        return colour == YELLOW ? BLUE : WHITE;
    }

    /** What built textures are cached under; two themes never collide. */
    public String key()
    {
        return id + '_' + Integer.toHexString(surface) + '_' + Integer.toHexString(accent);
    }

    /** "White", "Black" or "Yellow", for a settings row to say. */
    public static String fontName(int colour)
    {
        if(colour == WHITE)
        {
            return "White";
        }
        if(colour == BLACK)
        {
            return "Black";
        }
        return colour == BLUE ? "Blue" : "Yellow";
    }
}
