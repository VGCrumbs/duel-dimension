package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;

/**
 * Greying an unowned card at draw time, on one image.
 * <p>
 * A card the player does not own is shown desaturated so the page says WHICH
 * card is missing while reading instantly as not yet found. That used to be a
 * second, desaturated <em>copy</em> of the PNG, built pixel by pixel inside the
 * {@code IoSupplier} the resource pack handed back — measured at 29ms for a
 * 512px preview against 3.6ms for the same card owned, paid again after every
 * restart because nothing was written down. A second copy of 13,864 card images
 * is not something to cache; it is something not to make.
 * <p>
 * So the arithmetic moves into the fragment shader and the file is drawn once.
 * {@code GUI_TEXTURED}'s tint is an ARGB <b>multiply</b> and multiplication
 * cannot desaturate — it darkens or colourises — so no blend state and no tint
 * substitutes for this. What desaturates is a luminance of the three channels,
 * which is a cross-channel operation and therefore a shader.
 * <p>
 * <b>Two pipelines, because there are two roads.</b> The deck editor and the
 * binder blit through {@code GuiGraphicsExtractor}, which takes a
 * {@link RenderPipeline}; the card preview submits custom geometry through
 * {@link FieldQuad}, which takes a {@code RenderType}. Each is its stock
 * counterpart with the fragment shader swapped and nothing else changed.
 * <p>
 * Neither is registered, and neither can be: {@code RenderPipelines.register}
 * is private. It does not matter — {@code GlDevice} caches compiled pipelines
 * keyed on the pipeline instance, so an unregistered one compiles lazily at
 * first use. {@link FoilPipelines} has shipped on exactly this footing.
 */
public final class UnownedPipelines
{
    /**
     * Rec.709 luma. The same weights the CPU pass used, so the look does not
     * change with the mechanism.
     */
    public static final float RED_WEIGHT = 0.2126F;
    public static final float GREEN_WEIGHT = 0.7152F;
    public static final float BLUE_WEIGHT = 0.0722F;

    /**
     * How much of the original chroma survives.
     * <p>
     * Not zero: a little hue keeps monster, Spell and Trap frames recognizable
     * while ownership stays unmistakable at thumbnail size.
     */
    public static final float KEPT_CHROMA = 0.15F;

    /**
     * The GUI blit's greyed twin: {@code GUI_TEXTURED} with the fragment shader
     * replaced and the blend, vertex format, bind groups and defines copied.
     */
    public static final RenderPipeline GUI = PipelineCopy.of(RenderPipelines.GUI_TEXTURED,
        "gui_card_desaturate",
        Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "core/card_desaturate"), null);

    /**
     * The custom-geometry twin: {@code BREEZE_WIND} the same way.
     * <p>
     * {@code BREEZE_WIND} is the one entity pipeline that is truly unlit, which
     * is why {@link FieldQuad} draws the board through it; see that class for
     * the three that were tried first.
     */
    public static final RenderPipeline MESH = PipelineCopy.of(RenderPipelines.BREEZE_WIND,
        "mesh_card_desaturate",
        Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "core/card_desaturate_entity"),
        null);

    /**
     * The fallback tint, and it is a DIM rather than a desaturation.
     * <p>
     * If the shader will not compile — bad GLSL, a hostile driver, a resource
     * pack shadowing it — unowned cards draw through the stock pipeline with
     * this multiplied into their tint instead. Dimmer and cooler than the cards
     * beside it, unmistakably "not yours" at thumbnail size, and no second
     * image. It must not be called desaturation anywhere: a multiply leaves HSV
     * saturation exactly where it found it.
     */
    public static final float DIM_RED = 0.62F;
    public static final float DIM_GREEN = 0.62F;
    public static final float DIM_BLUE = 0.68F;

    /**
     * Whether both pipelines compiled, or null before anything has asked.
     * <p>
     * A mod pipeline is not in {@code getStaticPipelines()}, so
     * {@code ShaderManager}'s reload-time validate sweep skips it: a GLSL typo
     * does not fail at startup, it becomes {@code GlCommandEncoder} throwing
     * "Pipeline contains invalid shader program" at the first unowned card.
     * Asking the device to precompile turns that crash into a log line plus the
     * fallback above.
     */
    private static Boolean valid;

    private UnownedPipelines()
    {
    }

    /**
     * Asks the device to compile both pipelines and latches the answer.
     * <p>
     * Called from the {@code init()} of every screen that draws an unowned
     * card. That is cheap (a cache lookup once compiled), it is on the render
     * thread, and it is self-healing: {@code ShaderManager.apply} clears the
     * pipeline cache on every resource reload and screens re-init after one, so
     * a pack that breaks or fixes the shader is noticed at the next screen
     * rather than never.
     */
    public static void refresh()
    {
        GpuDevice device = RenderSystem.tryGetDevice();
        if(device == null)
        {
            // Before the window exists there is nothing to ask. Left unset
            // rather than answered, so the next caller asks again.
            return;
        }
        boolean gui = device.precompilePipeline(GUI).isValid();
        boolean mesh = device.precompilePipeline(MESH).isValid();
        if(!gui || !mesh)
        {
            DuelDimension.warn("Card desaturation shader did not compile (gui=" + gui
                + ", mesh=" + mesh + "); unowned cards will be drawn dimmed instead."
                + " Look for \"Couldn't compile program for pipeline\" above.");
        }
        valid = gui && mesh;
    }

    /**
     * Whether unowned cards can be drawn greyed rather than dimmed.
     * <p>
     * Both pipelines are gated together rather than separately. They share a
     * look, and a screen where the icon grid is grey and the preview of the
     * same card is merely dim would read as a bug rather than as a degradation.
     */
    public static boolean available()
    {
        if(valid == null)
        {
            refresh();
        }
        return valid != null && valid;
    }

    /**
     * The desaturating twin of {@code RenderTypes.breezeWind(texture, 0, 0)}.
     * <p>
     * Every step is that method's, read off its bytecode rather than guessed —
     * the {@code Sampler0} binding, the zero texture offset {@code BREEZE_WIND}
     * needs because it defines {@code APPLY_TEXTURE_MATRIX}, the lightmap and
     * the upload sort. Only the pipeline differs.
     * <p>
     * {@code RenderType.create} is package-private, which is the one widening
     * this change needed; see {@code dueldimension.accesswidener}.
     */
    public static RenderType mesh(Identifier texture)
    {
        return RenderType.create("dd_card_desaturate", RenderSetup.builder(MESH)
            .withTexture("Sampler0", texture)
            .setTextureTransform(new TextureTransform.OffsetTextureTransform(0F, 0F))
            .useLightmap()
            .sortOnUpload()
            .createRenderSetup());
    }

    /** {@link #DIM_RED} and friends multiplied into an ARGB tint, alpha kept. */
    public static int dimmed(int argb)
    {
        int alpha = argb >>> 24;
        int red = Math.round((argb >>> 16 & 0xFF) * DIM_RED);
        int green = Math.round((argb >>> 8 & 0xFF) * DIM_GREEN);
        int blue = Math.round((argb & 0xFF) * DIM_BLUE);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    /**
     * What the shader does, in Java, for one pixel.
     * <p>
     * <b>Nothing draws through this.</b> It is the reference the two {@code
     * .fsh} files are the transliteration of, and it is here so the constants
     * above have something that exercises them: a shader is invisible to javac
     * and to every unit test, so without this the look could drift with nothing
     * failing. {@code UnownedPipelinesTest} pins both halves — this arithmetic,
     * and that the shader sources still spell the same numbers.
     */
    public static int unownedColour(int argb)
    {
        int alpha = argb >>> 24;
        int red = argb >>> 16 & 0xFF;
        int green = argb >>> 8 & 0xFF;
        int blue = argb & 0xFF;
        int grey = Math.round(red * RED_WEIGHT + green * GREEN_WEIGHT + blue * BLUE_WEIGHT);
        red = Math.round(grey + (red - grey) * KEPT_CHROMA);
        green = Math.round(grey + (green - grey) * KEPT_CHROMA);
        blue = Math.round(grey + (blue - grey) * KEPT_CHROMA);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
