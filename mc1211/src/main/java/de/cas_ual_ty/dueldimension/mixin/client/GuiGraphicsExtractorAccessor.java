package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the GUI's render state so the mod can submit a picture-in-picture
 * region of its own.
 * <p>
 * The extractor has a method for each kind of region vanilla needs — an entity,
 * a skin, a book, a banner — and each one puts a state on {@code guiRenderState}.
 * That field is private and there is no general "submit this state" method, so a
 * mod with its own region has nowhere to put it. Fabric API registers custom
 * <em>renderers</em> ({@code PictureInPictureRendererRegistry}) but does not
 * expose the submission side, which leaves an accessor as the way through.
 * <p>
 * An accessor rather than an injection on purpose: it adds nothing and changes
 * nothing, so it cannot conflict with another mod doing the same, and it breaks
 * loudly at load time if the field is ever renamed rather than quietly drawing
 * nothing.
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor
{
    @Accessor("guiRenderState")
    GuiRenderState dueldimension$guiRenderState();
}
