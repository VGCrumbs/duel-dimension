package de.cas_ual_ty.dueldimension.duel.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.PlayerSkins;
import de.cas_ual_ty.dueldimension.clientutil.SkinLayersCompat;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Draws a duelist as a player-shaped character wearing that duelist's skin.
 * Skins are looked up by profile id, so a new opponent needs a texture and a
 * profile, not a new renderer.
 * <p>
 * The render state is {@code AvatarRenderState} -- the player's own -- and that
 * is the whole trick. A renderer's layers are handed a state, never the entity,
 * so "which duelist is this" has to be answered during extraction and carried
 * across. {@code AvatarRenderState} already carries a {@code PlayerSkin}, which
 * is exactly a texture plus a body type, so there is nothing to invent: the
 * profile is resolved to a skin once per frame and both the texture and the arm
 * width come off it -- which also means they can no longer disagree.
 */
public class DuelistRenderer
    extends HumanoidMobRenderer<DuelistEntity, AvatarRenderState, PlayerModel>
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
    private final Map<String, PlayerModel> models = new HashMap<>();

    public DuelistRenderer(EntityRendererProvider.Context context)
    {
        super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public AvatarRenderState createRenderState()
    {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(DuelistEntity entity, AvatarRenderState state,
        float partialTick)
    {
        super.render(entity.vanilla(), state, partialTick);
        // The one moment the entity is in hand. Everything below reads the skin.
        String profile = entity.getProfileId();
        state.skin = PlayerSkins.skinFor(
            SKINS.computeIfAbsent(profile, id -> ResourceLocation.fromNamespaceAndPath(
                DuelDimension.MOD_ID, "textures/entity/duelist/" + id + ".png")),
            SLIM_PROFILES.contains(profile));
        // HumanoidMobRenderer does not populate AvatarRenderState's player
        // cosmetic flags. Their defaults are false, which made every flat or
        // injected second-skin part invisible on NPC duelists.
        showAllSkinLayers(state);
    }

    static void showAllSkinLayers(AvatarRenderState state)
    {
        state.showHat = true;
        state.showJacket = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;
        state.showLeftPants = true;
        state.showRightPants = true;
    }

    @Override
    public void submit(AvatarRenderState state, PoseStack poseStack,
        SubmitNodeCollector collector, CameraRenderState camera)
    {
        // Chosen per duelist rather than per renderer, as it was on Forge. The
        // layers reach their model through getModel(), so they follow this
        // without being rebuilt.
        String profile = profileFor(state.skin.body().texturePath());
        boolean slim = state.skin.model() == PlayerModelType.SLIM;
        model = models.computeIfAbsent(profile, ignored -> new PlayerModel(LayerDefinition.create(
            PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim));
        SkinLayersCompat.apply(model, state.skin.body().texturePath(), slim);
        super.submit(state, poseStack, collector, camera);
    }

    private static String profileFor(ResourceLocation texture)
    {
        String path = texture.getPath();
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return path.substring(slash + 1, dot > slash ? dot : path.length());
    }

    @Override
    public ResourceLocation getTextureLocation(AvatarRenderState state)
    {
        return state.skin == null ? FALLBACK : state.skin.body().texturePath();
    }

    @Override
    protected void scale(AvatarRenderState state, PoseStack poseStack)
    {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F); // same trim the player model uses
    }
}
