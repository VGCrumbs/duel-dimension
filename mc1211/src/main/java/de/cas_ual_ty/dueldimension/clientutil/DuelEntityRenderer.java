package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Draws a duel entity, which is to say draws nothing.
 * <p>
 * A duel entity is a place for a {@code DuelManager} to live, not a thing with
 * a body. It still needs a renderer because every entity type does, so this
 * refuses to be considered for rendering at all and draws nothing if it ever
 * is.
 * <p>
 * 1.21.1 renders straight from the entity, so this is back to the 1.19.2 shape:
 * an empty {@code render} and a {@code getTextureLocation} that names nothing.
 * Neither is ever reached, because {@code shouldRender} is false.
 */
public class DuelEntityRenderer extends EntityRenderer<Entity>
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
    public void render(Entity entity, float entityYaw, float partialTick, PoseStack poseStack,
        MultiBufferSource buffer, int packedLight)
    {
    }

    @Override
    public ResourceLocation getTextureLocation(Entity entity)
    {
        return null;
    }
}
