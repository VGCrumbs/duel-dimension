package de.cas_ual_ty.dueldimension.duel.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a duelist as a player-shaped character wearing that duelist's skin.
 * Skins are looked up by profile id, so a new opponent needs a texture and a
 * profile, not a new renderer.
 */
public class DuelistRenderer extends HumanoidMobRenderer<DuelistEntity, PlayerModel<DuelistEntity>>
{
    private static final ResourceLocation FALLBACK = new ResourceLocation("textures/entity/steve.png");
    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    public DuelistRenderer(EntityRendererProvider.Context context)
    {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(DuelistEntity entity)
    {
        String profile = entity.getProfileId();
        return SKINS.computeIfAbsent(profile, id -> new ResourceLocation(DuelDimension.MOD_ID,
            "textures/entity/duelist/" + id + ".png"));
    }

    @Override
    protected void scale(DuelistEntity entity, PoseStack poseStack, float partialTick)
    {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F); // same trim the player model uses
    }
}
