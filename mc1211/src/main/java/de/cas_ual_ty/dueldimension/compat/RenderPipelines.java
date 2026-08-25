package de.cas_ual_ty.dueldimension.compat;

/**
 * Stand-ins for 26.2's GUI render pipelines, which 1.21.1 does not have.
 * <p>
 * 26.2 makes every GUI draw name a {@code RenderPipeline}; 1.21.1 has one path
 * for all of them and no such type. The mod only ever passes the standard GUI
 * ones, so {@link GuiGraphicsExtractor} takes the argument and discards it --
 * and these constants exist so the call sites naming them still compile.
 * <p>
 * Deliberately {@code Object}-typed and valueless. Giving them a richer type
 * would invite code to branch on which one it has, and there is nothing behind
 * them to branch on: on 1.21.1 they are all the same pipeline.
 */
public final class RenderPipelines
{
    public static final Object GUI = new Object();
    public static final Object GUI_TEXTURED = new Object();
    public static final Object GUI_TEXTURED_PREMULTIPLIED_ALPHA = new Object();
    public static final Object GUI_OPAQUE_TEXTURED_BACKGROUND = new Object();
    public static final Object GUI_NAUSEA_OVERLAY = new Object();
    public static final Object GUI_TEXT_HIGHLIGHT = new Object();
    public static final Object GUI_GHOST_RECIPE_OVERLAY = new Object();

    private RenderPipelines()
    {
    }
}
