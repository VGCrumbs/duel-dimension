package de.cas_ual_ty.dueldimension.duel.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.SkinLayersCompat;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Draws a duelist as a player-shaped character wearing that duelist's skin.
 * Skins are looked up by profile id, so a new opponent needs a texture and a
 * profile, not a new renderer.
 * <p>
 * 1.21.1 renders straight from the entity, and hands the entity to the layers
 * too, so there is no render state and nothing has to be carried across. "Which
 * duelist is this" is answered from the profile id in {@code render} and in
 * {@code getTextureLocation}, and the texture and the arm width are both derived
 * from that one id, so they still cannot disagree.
 */
public class DuelistRenderer
    extends HumanoidMobRenderer<DuelistEntity, PlayerModel<DuelistEntity>>
{
    private static final ResourceLocation FALLBACK =
        ResourceLocation.withDefaultNamespace("textures/entity/steve.png");
    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    /**
     * Duelists built on Alex rather than Steve -- the 3px-armed model. Vanilla
     * settles this per player from the Mojang profile, which an NPC has none
     * of, so it is stated here by profile id instead.
     */
    private static final Set<String> SLIM_PROFILES = Set.of("joey");

    /** One model per skin: injected voxel meshes must never be shared across NPC profiles. */
    private final Map<String, PlayerModel<DuelistEntity>> models = new HashMap<>();

    public DuelistRenderer(EntityRendererProvider.Context context)
    {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public void render(DuelistEntity entity, float entityYaw, float partialTick,
        PoseStack poseStack, MultiBufferSource buffer, int packedLight)
    {
        // Chosen per duelist rather than per renderer, as it was on Forge. The
        // layers reach their model through getModel(), so they follow this
        // without being rebuilt.
        String profile = entity.getProfileId();
        ResourceLocation texture = textureFor(profile);
        boolean slim = SLIM_PROFILES.contains(profile);
        // One model per skin, baked rather than swapped between two shared ones
        // the way the 1.19.2 version did: injected voxel meshes must never be
        // shared across NPC profiles.
        model = models.computeIfAbsent(profile, ignored -> new PlayerModel<>(
            LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64)
                .bakeRoot(), slim));
        showAllSkinLayers(model);
        SkinLayersCompat.apply(model, texture, slim);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * Every second-skin layer on, which is what a player's own renderer does.
     *
     * <h2>The same decision as 26.2, made on a different thing</h2>
     *
     * 26.2 sets six booleans on an {@code AvatarRenderState}, because
     * {@code HumanoidMobRenderer} does not populate a player's cosmetic flags
     * and their default is false — which made every flat or injected
     * second-skin part invisible on NPC duelists.
     * <p>
     * 1.21.1 has no render state; the same information lives on the MODEL, as
     * {@code ModelPart.visible}. Those default to TRUE, so this is not fixing
     * the same bug — a duelist here already shows its layers. It is here so the
     * two trees say the same thing about the same intent, and because it is not
     * free: {@code SkinLayersCompat.apply} runs on the next line and injects
     * geometry into exactly these parts, and anything that later switches one
     * off — a layer hidden for a cosmetic, a model reused between profiles —
     * would hide the injection with it and be very hard to see.
     */
    static void showAllSkinLayers(PlayerModel<DuelistEntity> model)
    {
        model.hat.visible = true;
        model.jacket.visible = true;
        model.leftSleeve.visible = true;
        model.rightSleeve.visible = true;
        model.leftPants.visible = true;
        model.rightPants.visible = true;
    }

    private static ResourceLocation textureFor(String profile)
    {
        return SKINS.computeIfAbsent(profile, id -> ResourceLocation.fromNamespaceAndPath(
            DuelDimension.MOD_ID, "textures/entity/duelist/" + id + ".png"));
    }

    @Override
    public ResourceLocation getTextureLocation(DuelistEntity entity)
    {
        String profile = entity.getProfileId();
        return profile == null ? FALLBACK : textureFor(profile);
    }

    @Override
    protected void scale(DuelistEntity entity, PoseStack poseStack, float partialTick)
    {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F); // same trim the player model uses
    }
}
