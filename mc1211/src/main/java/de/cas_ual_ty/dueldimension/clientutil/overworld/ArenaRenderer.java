package de.cas_ual_ty.dueldimension.clientutil.overworld;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.clientutil.PlayMats;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSiting;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldTransform;
import de.cas_ual_ty.dueldimension.duel.overworld.FieldSpec;
import de.cas_ual_ty.dueldimension.duel.overworld.arena.Arena;
import de.cas_ual_ty.dueldimension.duel.overworld.arena.ArenaScan;
import de.cas_ual_ty.dueldimension.DdBlocks;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The arena markers, and the board they describe.
 * <p>
 * These blocks are {@code RenderShape.INVISIBLE}, so nothing else in the game
 * draws them at all -- this is the only thing that ever does, and it refuses
 * outright unless the player is in creative mode. That is what makes "visible
 * to a builder only" a fact about the code rather than a hope: there is no
 * state a survival player can reach in which a marker appears, because the one
 * piece of code that can draw one has already gone home.
 * <p>
 * Held TAB brings up the board itself, drawn with the real playmat geometry at
 * the real derived size -- not a sketch of it. A builder moving a corner wants
 * to know what the board will actually be, and an outline would answer a
 * different question: the mat is fitted to the marked rectangle, so a rectangle
 * of the wrong proportions leaves the board smaller than the space, and that is
 * exactly the thing worth seeing before a duel starts on it.
 * <p>
 * Every rule about validity is asked of {@link Arena}, the same pure code the
 * server starts duels with, so a point that flashes red here is a point the
 * server would refuse, and a board drawn here is the board that would appear.
 */
public final class ArenaRenderer
{
    private ArenaRenderer()
    {
    }

    private static final ResourceLocation CORNER_MARK = ResourceLocation.fromNamespaceAndPath(
        DuelDimension.MOD_ID, "textures/duel/overworld/field_outline.png");
    private static final ResourceLocation POINT_MARK = ResourceLocation.fromNamespaceAndPath(
        DuelDimension.MOD_ID, "textures/duel/overworld/stand_marker.png");

    /** Clear of the block face it sits on, close enough to read as painted on it. */
    private static final float LIFT = 0.02F;
    /** The board's own clearance, matching {@code OverworldBoardRenderer}. */
    private static final double SURFACE_LIFT = 0.02D;

    private static final float PULSE_TICKS = 16F;

    /** A corner in a set that makes a rectangle, and one in a set that does not. */
    private static final int CORNER_GOOD = 0x63C8FF;
    private static final int CORNER_BAD = 0xFF4A3A;
    /** A point somebody could stand on, and one they could not. */
    private static final int POINT_GOOD = 0x7CE38B;
    private static final int POINT_BAD = 0xFF4A3A;

    /** How often the world is swept for markers, in ticks. */
    private static final int REFRESH_TICKS = 10;

    private static List<BlockPos> corners = List.of();
    private static List<BlockPos> points = List.of();
    private static long sweptAt = Long.MIN_VALUE;

    public static void render(LevelRenderContext context)
    {
        Minecraft client = Minecraft.getInstance();
        if(client.player == null || client.level == null)
        {
            corners = List.of();
            points = List.of();
            return;
        }
        // The whole feature, gated in one place. Creative sees the arena it is
        // building; anyone else sees the kind of marker they are holding and
        // nothing else. Since nothing else can draw these blocks, a player who
        // qualifies for neither sees nothing at all.
        boolean creative = client.player.isCreative();
        boolean showCorners = creative || DdBlocks.ARENA_CORNER.holding(client.player);
        boolean showPoints = creative || DdBlocks.ARENA_POINT.holding(client.player);
        if(!showCorners && !showPoints)
        {
            corners = List.of();
            points = List.of();
            return;
        }
        sweep(client, context.levelState().gameTime);
        // Filtered where they are DRAWN rather than where they are found, so
        // that swapping what is in hand does not have to wait for the next
        // sweep -- and so the sweep's cache stays a picture of the world rather
        // than of the world plus whoever was holding what.
        //
        // Only the drawing is filtered. Whether a point sits inside the arena
        // is a fact about the arena, so it is still asked of every corner that
        // is there: a builder holding player points would otherwise be told all
        // of them are invalid, because the corners proving them valid happened
        // not to be in their hand.
        List<BlockPos> drawnCorners = showCorners ? corners : List.<BlockPos>of();
        List<BlockPos> drawnPoints = showPoints ? points : List.<BlockPos>of();
        if(drawnCorners.isEmpty() && drawnPoints.isEmpty())
        {
            return;
        }

        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();
        float partial = client.getTimer().getGameTimeDeltaPartialTick(false);
        float age = context.levelState().gameTime + partial;
        float pulse = 0.55F + 0.45F * Mth.sin(age / PULSE_TICKS * Mth.TWO_PI);
        boolean asking = tabHeld(client);

        Arena.Rect rect = Arena.rectangle(corners);

        // A corner sits at the height of the board's SURFACE, so its mark is
        // drawn on the top face of the block beneath it -- which is where the
        // mat will be, and therefore what the builder is really placing.
        for(BlockPos corner : drawnCorners)
        {
            drawQuad(poseStack, collector, CORNER_MARK, camera, corner,
                tint(rect != null ? CORNER_GOOD : CORNER_BAD, rect != null ? 0.7F : pulse));
        }

        for(BlockPos point : drawnPoints)
        {
            boolean fits = Arena.pointValid(rect, point);
            // Red and flashing while the board is up, which is when somebody is
            // asking. A marker that flashed all day would be one nobody read.
            drawQuad(poseStack, collector, POINT_MARK, camera, point,
                tint(fits ? POINT_GOOD : POINT_BAD, fits ? 0.8F : asking ? pulse : 0.55F));
        }

        if(asking && rect != null)
        {
            drawBoard(poseStack, collector, camera, client, rect);
        }
    }

    /**
     * The board these markers would produce, at the size they would produce it.
     * <p>
     * Built through the very same call the server makes, so what a builder sees
     * while holding the key is not a drawing OF the board -- it is the board,
     * asked for early.
     */
    private static void drawBoard(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Minecraft client, Arena.Rect rect)
    {
        Arena.Built built = Arena.of(rect, points);
        if(built == null)
        {
            return;
        }
        FieldSiting siting = built.siting(FieldSpec.current(),
            built.nearest(client.player.blockPosition()));
        FieldTransform transform = new FieldTransform(siting);
        // This player's own mat on the end they would stand at, which is how it
        // will look when the duel starts. The opponent's is whatever this client
        // last saw; before any duel that is the classic mat, which is also what
        // an opponent who has never changed theirs will bring.
        PlayMats[] mats = new PlayMats[2];
        mats[FieldTransform.controllerFor(0, true)] = DuelClientState.selfMat;
        mats[FieldTransform.controllerFor(0, false)] = DuelClientState.opponentMat;
        for(BoardMesh.Piece piece : BoardMesh.pieces(mats))
        {
            WorldQuad.submit(poseStack, collector, WorldQuad.Kind.SOLID, piece.texture(), camera,
                transform.corners(piece.rect(), SURFACE_LIFT + piece.lift()), 0xFFFFFFFF);
        }
    }

    /**
     * Swept rather than remembered.
     * <p>
     * A record of every marker ever placed would be faster and would also be
     * wrong the first time somebody pasted an arena in with a structure block,
     * a schematic or an edit tool -- none of which run a placement hook. Twice
     * a second is quick enough that a corner appears as soon as it is put down,
     * and it only ever runs for a player in creative mode with markers about.
     */
    private static void sweep(Minecraft client, long gameTime)
    {
        if(gameTime - sweptAt < REFRESH_TICKS && sweptAt != Long.MIN_VALUE)
        {
            return;
        }
        sweptAt = gameTime;
        ArenaScan.Markers found = ArenaScan.markers(client.level, client.player.blockPosition(),
            ArenaScan.RADIUS, ArenaScan.HEIGHT);
        corners = found.corners();
        points = found.points();
    }

    /**
     * Read from the window rather than through a key mapping.
     * <p>
     * Tab is the player list, which the game consumes for itself, so there is
     * no binding to ask -- and this wants the raw state of the key anyway,
     * which is what "while it is held" means.
     */
    private static boolean tabHeld(Minecraft client)
    {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(client.getWindow(),
            org.lwjgl.glfw.GLFW.GLFW_KEY_TAB);
    }

    private static int tint(int rgb, float alpha)
    {
        return Math.round(Mth.clamp(alpha, 0F, 1F) * 255F) << 24 | rgb;
    }

    /**
     * One marker, lying on the top face of the block below it and drawn
     * camera-relative.
     * <p>
     * The pose is captured and the corners baked because the draw is deferred:
     * a Pose taken off the stack is only good until something pushes over it,
     * and the next marker in the loop would.
     */
    private static void drawQuad(PoseStack poseStack, SubmitNodeCollector collector,
        ResourceLocation texture, Vec3 camera, BlockPos at, int tint)
    {
        poseStack.pushPose();
        poseStack.translate(at.getX() + 0.5D - camera.x, at.getY() - camera.y,
            at.getZ() + 0.5D - camera.z);
        // Copied: the stack recycles its Pose objects, and this one is
        // popped below while the draw is still deferred. See WorldQuad.
        PoseStack.Pose pose = poseStack.last().copy();
        collector.submitCustomGeometry(poseStack,
            // The one entity render type that is truly unlit, as the placement
            // guide uses: a marker that dimmed with the block light would be
            // invisible in exactly the dark corner someone is building in.
            net.minecraft.client.renderer.RenderType.breezeWind(texture, 0F, 0F),
            (unused, buffer) ->
            {
                vertex(buffer, pose, -0.5F, -0.5F, 0F, 0F, tint);
                vertex(buffer, pose, -0.5F, 0.5F, 0F, 1F, tint);
                vertex(buffer, pose, 0.5F, 0.5F, 1F, 1F, tint);
                vertex(buffer, pose, 0.5F, -0.5F, 1F, 0F, tint);
            });
        poseStack.popPose();
    }

    /** One corner, with every element the vertex format declares. */
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
