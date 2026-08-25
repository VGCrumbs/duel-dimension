package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.CardImageManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts finished card decodes on the GPU, once a frame, on the render thread.
 * <p>
 * EDOPro pumps its equivalent from {@code drawing.cpp}:774, the first act of
 * {@code Game::DrawGUI}. The structural twin in 26.2 is
 * {@code GameRenderer.extract}: {@code Minecraft.renderFrame} calls it
 * unconditionally at bytecode offset 441, once per frame, immediately before
 * {@code GameRenderer.render} at 520. Extract-HEAD sits between two positions
 * vanilla already creates textures from — its own reload applies them from
 * {@code Minecraft.execute}, drained at offset 137 of {@code runTick} and so
 * earlier in the frame, and today's synchronous {@code getTexture} creates them
 * during the GUI extract pass and so later.
 * <p>
 * <b>Not {@code Minecraft.execute}.</b> {@code BlockableEventLoop.runAllTasks()}
 * is {@code while(pollTask());}, so a task that re-submits itself is picked up
 * again in the same drain and the frame never ends. A pump has to re-arm every
 * frame, so it cannot live there.
 * <p>
 * One divergence, in our favour. EDOPro's upload lands after the scene is drawn,
 * so a texture uploaded in frame N is first drawn in frame N+1. Ours lands
 * before the extract pass, so it is drawn the same frame. That is a real
 * difference between the two GUIs rather than a liberty: MC's is retained-mode
 * and has a named, separable "describe the frame" pass; Irrlicht's immediate
 * loop has no such point, so EDOPro had to pick a place inside the draw.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin
{
    @Inject(method = "extract(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void dueldimension$uploadCardTextures(DeltaTracker deltaTracker,
        boolean renderLevel, CallbackInfo callback)
    {
        CardImageManager.refreshCachedTextures();
    }
}
