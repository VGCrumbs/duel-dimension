package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSiting;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where to stand, drawn on the ground.
 * <p>
 * Shown only while this player still has somewhere to walk to: the moment the
 * server locks them to the board the markers are done, and the board itself
 * takes over. Only the two duellists are ever sent the field, so only they can
 * draw these.
 * <p>
 * Two marks, told apart by colour rather than by position, because a player
 * arriving from behind the board cannot tell which end is theirs from the
 * geometry alone: yours is bright and pulses, theirs is a dim amber. Yours
 * stops pulsing and goes green the moment you are standing on it, which is the
 * only feedback that the walk is over and the wait is on the other player.
 * <p>
 * Hung on COLLECT_SUBMITS with camera-relative geometry, following
 * {@code OrichalcosRenderer} -- {@code WorldRenderEvents} is gone in 26.2 and
 * this event is what replaced it.
 */
public final class PlacementGuideRenderer
{
    private PlacementGuideRenderer()
    {
    }

    private static final Identifier MARKER =
        Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/overworld/stand_marker.png");
    private static final Identifier OUTLINE =
        Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/overworld/field_outline.png");

    /** Off the floor far enough not to fight it for depth, close enough to read as painted on. */
    private static final float LIFT = 0.02F;

    /** Ticks for one full pulse. */
    private static final float PULSE_TICKS = 30F;

    /** Your mark, and your mark once you are standing on it. */
    private static final int MINE = 0xFFE84A;
    private static final int ARRIVED = 0x7CE38B;
    /** The other duellist's mark: present, but plainly not the one to walk to. */
    private static final int THEIRS = 0xB08A2A;

    public static void render(LevelRenderContext context)
    {
        FieldSiting siting = ClientDuelField.siting();
        if(siting == null || ClientDuelField.locked())
        {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if(client.player == null)
        {
            return;
        }

        int seat = ClientDuelField.seat();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float age = context.levelState().gameTime + partial;
        Vec3 camera = client.gameRenderer.mainCamera().position();
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();

        // Sine over the tick count rather than over wall-clock: a paused
        // singleplayer world should not have a marker pulsing on it.
        float pulse = 0.62F + 0.38F * Mth.sin(age / PULSE_TICKS * Mth.TWO_PI);

        // The area the board will occupy, so the walk has an obvious
        // destination rather than two lonely squares.
        BlockPos anchor = siting.anchor();
        drawQuad(poseStack, collector, OUTLINE, camera,
            anchor.getX() + 0.5D, anchor.getY() + 1, anchor.getZ() + 0.5D,
            siting.spec().areaWidth(), siting.spec().areaDepth(), tint(0x7A6A2E, 0.5F));

        for(int which = 0; which < 2; which++)
        {
            BlockPos stand = siting.stand(which);
            boolean mine = which == seat;
            boolean standingOnIt = mine && onMark(client.player.position(), stand);

            int colour = !mine ? THEIRS : standingOnIt ? ARRIVED : MINE;
            // A mark that is already reached stops asking to be walked to.
            float alpha = !mine ? 0.55F : standingOnIt ? 0.9F : pulse;

            drawQuad(poseStack, collector, MARKER, camera,
                stand.getX() + 0.5D, stand.getY() + 1, stand.getZ() + 0.5D, 1F, 1F,
                tint(colour, alpha));
        }
    }

    /** Standing on the marked square, judged the way the server judges arrival. */
    private static boolean onMark(Vec3 position, BlockPos stand)
    {
        double dx = position.x - (stand.getX() + 0.5D);
        double dz = position.z - (stand.getZ() + 0.5D);
        return dx * dx + dz * dz <= 1D && Math.abs(position.y - (stand.getY() + 1)) <= 1.5D;
    }

    private static int tint(int rgb, float alpha)
    {
        return Math.round(Mth.clamp(alpha, 0F, 1F) * 255F) << 24 | rgb;
    }

    /**
     * One ground-plane quad, centred on a world position and drawn
     * camera-relative.
     * <p>
     * The pose is captured and the corners baked because
     * {@code submitCustomGeometry} defers the draw: a Pose taken from the stack
     * is only good until something pushes over it, and the next marker in the
     * loop would.
     */
    private static void drawQuad(PoseStack poseStack, SubmitNodeCollector collector,
        Identifier texture, Vec3 camera, double x, double y, double z, float width, float depth,
        int tint)
    {
        poseStack.pushPose();
        poseStack.translate(x - camera.x, y - camera.y, z - camera.z);
        PoseStack.Pose pose = poseStack.last();
        float halfW = width / 2F;
        float halfD = depth / 2F;
        collector.submitCustomGeometry(poseStack,
            // The one entity render type that is truly unlit, established by
            // FieldQuad and reused by the Orichalcos seal. A marker that dimmed
            // with the local block light would be invisible at night, which is
            // exactly when someone is squinting for it.
            net.minecraft.client.renderer.rendertype.RenderTypes.breezeWind(texture, 0F, 0F),
            (unused, buffer) ->
            {
                vertex(buffer, pose, -halfW, -halfD, 0F, 0F, tint);
                vertex(buffer, pose, -halfW, halfD, 0F, 1F, tint);
                vertex(buffer, pose, halfW, halfD, 1F, 1F, tint);
                vertex(buffer, pose, halfW, -halfD, 1F, 0F, tint);
            });
        poseStack.popPose();
    }

    /**
     * One corner, with every element the vertex format requires. An unset
     * element is not defaulted -- it reads whatever was in the buffer, which is
     * how a quad ends up black or invisible for no obvious reason.
     */
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float z,
        float u, float v, int tint)
    {
        buffer.addVertex(pose, x, LIFT, z)
            .setColor(tint)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(0xF000F0)
            .setNormal(0F, 1F, 0F);
    }
}
