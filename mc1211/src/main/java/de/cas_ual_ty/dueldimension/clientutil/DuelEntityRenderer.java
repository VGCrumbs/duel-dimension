package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Draws a duel entity, which is to say draws nothing.
 * <p>
 * A duel entity is a place for a {@code DuelManager} to live, not a thing with
 * a body. It still needs a renderer because every entity type does, so this
 * refuses to be considered for rendering at all and draws nothing if it ever
 * is.
 * <p>
 * The render-state refactor makes this smaller rather than larger: there is no
 * texture to name and no {@code render} to leave empty, only a state nobody
 * fills and a {@code submit} nobody reaches.
 */
public class DuelEntityRenderer extends EntityRenderer<Entity, EntityRenderState>
{
    public DuelEntityRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public boolean shouldRender(Entity entity, Frustum camera, double camX, double camY,
        double camZ)
    {
        return false;
    }

    @Override
    public EntityRenderState createRenderState()
    {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack,
        SubmitNodeCollector collector, CameraRenderState camera)
    {
    }
}
