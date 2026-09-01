package de.cas_ual_ty.dueldimension.clientutil.hub;

/**
 * The hub's type hierarchy, in whatever theme is active.
 *
 * <h2>Why the body text is not part of the theme</h2>
 * A theme states one font colour -- see {@link MenuTheme} -- and that is the
 * heading. Everything else is derived from the SURFACE, because the thing that
 * decides whether small text can be read is the contrast against what it is
 * drawn on, not what the heading happens to be. On Frost, a light theme, every
 * one of these flips to dark ink without Frost having to say so.
 *
 * <h2>These replaced five hard-coded literals</h2>
 * {@code 0xFFF4D089} appeared 75 times across the client, {@code 0xFFC2C9D6}
 * 54, and three more besides. They were the hub's palette written out by hand
 * at every call site, which is exactly the thing that cannot be re-themed.
 * Colours that MEAN something -- a green confirmation, a red warning -- are
 * deliberately not here and stay written where they are used.
 */
public final class MenuInk
{
    private MenuInk()
    {
    }

    /** Headings and the emphasised word: the theme's own chosen font colour. */
    public static int title()
    {
        return MenuThemes.active().text();
    }

    /** Body text: as far from the surface as it can get. */
    public static int label()
    {
        return contrast();
    }

    /** Secondary text -- a hint, a count, a subtitle. */
    public static int body()
    {
        return fade(0.28F);
    }

    /** Disabled, or a note that should not compete with anything. */
    public static int dim()
    {
        return fade(0.55F);
    }

    /** Above this the surface is a light one, and everything below flips. */
    private static final int LIGHT = 140;

    private static int contrast()
    {
        return MenuTheme.luminance(MenuThemes.active().surface()) > LIGHT
            ? 0xFF16181C : 0xFFF2F5FA;
    }

    /**
     * Softened towards the surface -- but less far on a light theme.
     * <p>
     * The same step costs a light theme more contrast than a dark one: the ink
     * starts near black either way, so on a near-white surface a 55% mix lands
     * in the middle greys and disappears, where on a near-black one it is still
     * well clear of the background.
     */
    private static int fade(float amount)
    {
        MenuTheme theme = MenuThemes.active();
        float toward = MenuTheme.luminance(theme.surface()) > LIGHT
            ? amount * 0.7F : amount;
        return 0xFF000000
            | MenuTheme.mix(contrast() & 0xFFFFFF, theme.surface(), toward);
    }

    /**
     * Whether text should carry Minecraft's drop shadow on this theme.
     *
     * <h2>Off for light themes, and this is not a taste call</h2>
     * The shadow is not a colour a caller chooses -- Minecraft draws it as the
     * text's own colour darkened, one pixel down and right. On a dark surface
     * that is a black edge under light type and it is what makes the hub's text
     * legible over a gradient.
     * <p>
     * On a LIGHT surface the type is already near-black, so the shadow is a
     * second near-black copy of every glyph offset by a pixel. At the six-pixel
     * font that does not read as depth, it reads as a smear: every stroke
     * doubles and the counters inside a, e and o fill in. Frost was illegible
     * for exactly this reason, and no choice of text colour fixes it, because
     * the shadow follows whatever that colour is.
     */
    public static boolean shadow()
    {
        return MenuTheme.luminance(MenuThemes.active().surface()) <= LIGHT;
    }
}
