package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Seal of Orichalcos closing on someone, drawn in the world.
 * <p>
 * Two pieces, in sequence. The seal itself grows on the ground from a single
 * pixel to eight blocks across over six seconds, staying centred under its
 * target as they move — it is closing in on a person, not marking a spot, so it
 * follows. When it reaches full size the soul goes up: a thick green beam
 * through the target into the sky, which fades over the following eight
 * seconds.
 * <p>
 * <b>Driven by one packet, not by the server's clock.</b> The server says
 * "begin" once and the client counts from there. So the animation is smooth
 * regardless of what the connection is doing, and it costs one packet per
 * watcher instead of one per tick.
 */
public final class OrichalcosRenderer
{
    /** The seal, as supplied. */
    private static final ResourceLocation SEAL_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/misc/orichalcos_seal.png");

    /** The green of the beam, as asked for: #5dc488. */
    private static final int BEAM_COLOUR = 0xFF5DC488;

    /** One pixel, in blocks. Where the seal starts. */
    private static final float START_DIAMETER = 1F / 16F;

    /** Eight blocks across, which is where it stops. */
    private static final float FULL_DIAMETER = 8F;

    /**
     * How much thicker than a beacon's beam this is.
     * <p>
     * A beacon is 0.2 blocks of core and 0.25 of glow, which at any distance
     * reads as a thread. Tripled it reads as the pillar the effect is meant to
     * be, which is what "large beacon" asks for.
     */
    private static final float BEAM_THICKNESS = 3F;

    /** Far enough up to leave the sky, without paying for the whole build limit. */
    private static final int BEAM_HEIGHT = 512;

    /** Clear of the ground, so the decal does not fight the block face it sits on. */
    private static final float GROUND_CLEARANCE = 0.02F;

    /**
     * How far below a jumping target to look for the ground, in blocks.
     * <p>
     * Bounded rather than unlimited: this runs every tick, and a target out
     * over a hole should hold its last surface rather than pay to scan down to
     * bedrock and find nothing.
     */
    private static final int GROUND_SEARCH = 32;

    /**
     * How far the seal turns while it closes in, in degrees.
     * <p>
     * Half a revolution over the six seconds -- a slow drift into place rather
     * than a spin. The turn is measured BACKWARDS from this to zero, so
     * whatever the figure, the seal always comes to rest square with the world
     * rather than stopping at an arbitrary angle.
     */
    private static final float SPIN_DEGREES = 180F;

    /** The beam's core, as a fraction of its thickness. Vanilla's beacon ratio. */
    private static final float CORE_RATIO = 0.2F;

    /** The beam's outer glow. Also vanilla's. */
    private static final float GLOW_RATIO = 0.25F;

    /** How see-through the glow shell is at full strength, out of 255. */
    private static final int GLOW_ALPHA = 48;

    /** What the seal is closing on, by network id. */
    private static final Map<Integer, Seal> ACTIVE = new ConcurrentHashMap<>();

    private OrichalcosRenderer()
    {
    }

    /** One running seal. */
    private static final class Seal
    {
        private final int growTicks;
        private final int holdTicks;
        private final int fadeTicks;
        private int age;
        /**
         * Where it was last seen.
         * <p>
         * Kept rather than read from the entity every frame because the target
         * DIES when the seal completes — the beam has eight seconds still to
         * run at a place whose entity has gone.
         */
        private double x;
        private double y;
        private double z;
        private boolean located;

        private Seal(int growTicks, int holdTicks, int fadeTicks)
        {
            this.growTicks = Math.max(1, growTicks);
            this.holdTicks = Math.max(0, holdTicks);
            this.fadeTicks = Math.max(1, fadeTicks);
        }

        /**
         * The tick the beam fires, counted from the start.
         * <p>
         * Not the tick the target dies -- the server holds that back a further
         * quarter second. The visuals key off the beam, which is what is
         * actually on screen.
         */
        private int beamAt()
        {
            return growTicks + holdTicks;
        }

        private int total()
        {
            return beamAt() + fadeTicks;
        }

        /**
         * How much of this is left, 1 down to 0.
         * <p>
         * Full while the seal is still closing in, then falling away over the
         * fade. Shared by the seal and the beam so the two go out together
         * rather than one outliving the other.
         */
        private float remaining(float age)
        {
            if(age <= beamAt())
            {
                return 1F;
            }
            return 1F - Math.min(1F, (age - beamAt()) / fadeTicks);
        }
    }

    /** The server says the seal has begun on this entity. */
    public static void begin(int entityId, int growTicks, int holdTicks, int fadeTicks)
    {
        ACTIVE.put(entityId, new Seal(growTicks, holdTicks, fadeTicks));
    }

    /** Nothing survives leaving the world. */
    public static void clear()
    {
        ACTIVE.clear();
    }

    /**
     * Advances every running seal, from the client tick.
     * <p>
     * Position is refreshed here rather than at draw time so that the frame
     * after the target dies still has somewhere to put the beam.
     */
    public static void tick()
    {
        if(ACTIVE.isEmpty())
        {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        for(Iterator<Map.Entry<Integer, Seal>> it = ACTIVE.entrySet().iterator(); it.hasNext();)
        {
            Map.Entry<Integer, Seal> entry = it.next();
            Seal seal = entry.getValue();
            if(++seal.age > seal.total())
            {
                it.remove();
                continue;
            }
            Entity target = client.level == null ? null : client.level.getEntity(entry.getKey());
            if(target != null)
            {
                seal.x = target.getX();
                seal.z = target.getZ();
                seal.y = groundUnder(target, seal.located ? seal.y : target.getY());
                seal.located = true;
            }
        }
    }

    /**
     * The surface under an entity, which is where the seal lies.
     * <p>
     * Deliberately not the entity's own Y. The seal is burned into the ground:
     * following a jumping player upward left it hovering under their feet like
     * a platform. Standing on the ground the two are the same figure, so the
     * cheap test is tried first; in the air it looks straight down instead.
     *
     * @param fallback where to stay if there is nothing below — over a void or
     *                 a long drop, holding the last surface beats dropping the
     *                 seal out of the world
     */
    private static double groundUnder(Entity target, double fallback)
    {
        if(target.onGround())
        {
            return target.getY();
        }
        Vec3 from = target.position();
        net.minecraft.world.phys.BlockHitResult hit = target.level().clip(
            new net.minecraft.world.level.ClipContext(from,
                from.subtract(0D, GROUND_SEARCH, 0D),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                // Fluids are not ground. A seal over water belongs on the bed
                // below it, not on the surface.
                net.minecraft.world.level.ClipContext.Fluid.NONE, target));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
            ? fallback : hit.getLocation().y;
    }

    /**
     * Draws every running seal.
     * <p>
     * Hung on COLLECT_SUBMITS, which is where geometry is handed to the
     * renderer for this frame. {@code WorldRenderEvents} is gone in this
     * version; this event and its {@link LevelRenderContext} are what replaced
     * it.
     */
    public static void render(LevelRenderContext context)
    {
        if(ACTIVE.isEmpty())
        {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = client.gameRenderer.mainCamera().position();
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();
        long gameTime = context.levelState().gameTime;

        for(Seal seal : ACTIVE.values())
        {
            if(!seal.located)
            {
                continue;
            }
            float age = seal.age + partial;

            poseStack.pushPose();
            poseStack.translate(seal.x - camera.x, seal.y - camera.y, seal.z - camera.z);

            drawSeal(poseStack, collector, seal, age);
            // The beam waits out the hold: the seal lands, sits there a
            // second, and only then does the soul go up.
            if(age >= seal.beamAt())
            {
                // Camera offset in the beam's own space, for the face cull.
                drawBeam(poseStack, collector, seal, age, gameTime, partial,
                    (float)(camera.x - seal.x), (float)(camera.z - seal.z));
            }

            poseStack.popPose();
        }
    }

    /**
     * The ring on the ground, growing.
     * <p>
     * Drawn flat at the target's feet and centred on them, so it tracks
     * whatever it is closing on rather than staying where it started.
     */
    private static void drawSeal(PoseStack poseStack, SubmitNodeCollector collector,
        Seal seal, float age)
    {
        float progress = Math.min(1F, age / seal.growTicks);
        float radius = Mth.lerp(progress, START_DIAMETER, FULL_DIAMETER) / 2F;

        // Fades out with the beam rather than being cut off when the entry
        // expires. It was popping out of existence at full opacity, which
        // undid the whole slow close-in that came before it.
        int alpha = Math.round(255F * seal.remaining(age));
        if(alpha <= 0)
        {
            return;
        }
        int tint = alpha << 24 | 0xFFFFFF;

        // Turning as it closes, coming to rest exactly as it reaches full size.
        // Baked into the corner positions rather than done with mulPose:
        // submitCustomGeometry defers the draw, and a Pose captured from the
        // stack is only safe until something pushes over it again -- which the
        // next seal in the loop would.
        float spin = (float)Math.toRadians(SPIN_DEGREES * (1F - progress));
        float cos = Mth.cos(spin);
        float sin = Mth.sin(spin);

        // Copied: the stack recycles its Pose objects and the draw is
        // deferred, so a reference held here can be overwritten before it is
        // read. See WorldQuad.
        PoseStack.Pose pose = poseStack.last().copy();
        collector.submitCustomGeometry(poseStack,
            // The one entity render type that is truly unlit -- see FieldQuad
            // for how that was established. A seal that dims with the local
            // block light would be a seal that vanishes at night, which is
            // exactly when it is most likely to be seen.
            net.minecraft.client.renderer.rendertype.RenderTypes.breezeWind(SEAL_TEXTURE, 0F, 0F),
            (unused, buffer) ->
            {
                corner(buffer, pose, -radius, -radius, cos, sin, 0F, 0F, tint);
                corner(buffer, pose, -radius, radius, cos, sin, 0F, 1F, tint);
                corner(buffer, pose, radius, radius, cos, sin, 1F, 1F, tint);
                corner(buffer, pose, radius, -radius, cos, sin, 1F, 0F, tint);
            });
    }

    /**
     * The soul leaving, as a beam of light.
     * <p>
     * Drawn here rather than through {@code BeaconRenderer.submitBeaconBeam},
     * which cannot fade. That helper draws its core with
     * {@code beaconBeam(texture, false)}, and the {@code false} selects
     * {@code BEACON_BEAM_OPAQUE} -- a pipeline with no blending, so the alpha
     * byte of the colour it is handed is ignored and the core stays solid green
     * until its radius reaches zero. Both shells are drawn here on the
     * translucent variant instead, which is the same render type vanilla uses
     * for the glow, so alpha reaches the core too.
     * <p>
     * The beam therefore both narrows and fades over the eight seconds.
     */
    private static void drawBeam(PoseStack poseStack, SubmitNodeCollector collector,
        Seal seal, float age, long gameTime, float partial, float toCameraX, float toCameraZ)
    {
        float faded = seal.remaining(age);
        if(faded <= 0F)
        {
            return;
        }
        float thickness = BEAM_THICKNESS * faded;
        float core = CORE_RATIO * thickness;
        float glow = GLOW_RATIO * thickness;
        if(core <= 0F)
        {
            return;
        }

        // Vanilla's scroll, so the beam crawls the way a beacon's does rather
        // than standing there as a static stripe.
        float time = Math.floorMod(gameTime, 40) + partial;
        float v0 = -1F + Mth.frac(-time * 0.2F - Mth.floor(-time * 0.1F));
        // Tied to the beam's FULL thickness, not its current one, so the
        // texture does not stretch as the beam narrows.
        float v1 = v0 + BEAM_HEIGHT * 0.5F / (CORE_RATIO * BEAM_THICKNESS);

        net.minecraft.client.renderer.rendertype.RenderType type =
            net.minecraft.client.renderer.rendertype.RenderTypes
                .beaconBeam(BeaconRenderer.BEAM_LOCATION, true);
        // Copied: the stack recycles its Pose objects and the draw is
        // deferred, so a reference held here can be overwritten before it is
        // read. See WorldQuad.
        PoseStack.Pose pose = poseStack.last().copy();
        int rgb = BEAM_COLOUR & 0xFFFFFF;

        column(collector, poseStack, pose, type, core, v0, v1,
            Math.round(255F * faded) << 24 | rgb, toCameraX, toCameraZ);
        column(collector, poseStack, pose, type, glow, v0, v1,
            Math.round(GLOW_ALPHA * faded) << 24 | rgb, toCameraX, toCameraZ);
    }

    /**
     * One four-sided shell of the beam, from the ground to {@link #BEAM_HEIGHT}.
     * <p>
     * <b>Back faces are not submitted at all.</b> The beacon render type does
     * not cull -- that is why a vanilla beacon reads as a hollow tube you can
     * see the inside of -- so the two sides pointing away from the camera are
     * dropped here instead. Doing it at submission rather than asking the
     * pipeline to cull is correct whatever that pipeline's cull state is, and
     * it is half the geometry rather than half of it transformed and thrown
     * away.
     */
    private static void column(SubmitNodeCollector collector, PoseStack poseStack,
        PoseStack.Pose pose, net.minecraft.client.renderer.rendertype.RenderType type,
        float radius, float v0, float v1, int tint, float toCameraX, float toCameraZ)
    {
        collector.submitCustomGeometry(poseStack, type, (unused, buffer) ->
        {
            // Four sides, each corner to the next so the shell closes, with the
            // outward normal of each in the last two columns.
            float[][] sides = {
                {-radius, -radius, radius, -radius, 0F, -1F},
                {radius, -radius, radius, radius, 1F, 0F},
                {radius, radius, -radius, radius, 0F, 1F},
                {-radius, radius, -radius, -radius, -1F, 0F}};
            for(float[] side : sides)
            {
                if(side[4] * toCameraX + side[5] * toCameraZ <= 0F)
                {
                    continue;   // facing away; the near side is in front of it
                }
                // Wound so the OUTWARD face is the front face. Vanilla's beam
                // winds the other way, which is what made it look like a tube
                // you could see the inside of -- and why culling the away-facing
                // sides left nothing at all: the pipeline was already discarding
                // the near ones.
                vertex(buffer, pose, side[2], BEAM_HEIGHT, side[3], 1F, v1, tint);
                vertex(buffer, pose, side[2], 0F, side[3], 1F, v0, tint);
                vertex(buffer, pose, side[0], 0F, side[1], 0F, v0, tint);
                vertex(buffer, pose, side[0], BEAM_HEIGHT, side[1], 0F, v1, tint);
            }
        });
    }

    /**
     * One corner, with every element the format requires.
     * <p>
     * An unset element is not defaulted; it reads whatever was in the buffer,
     * which is how a quad ends up black or invisible for no obvious reason.
     */
    /** A ground-plane corner at (x, z), turned about the centre by cos/sin. */
    private static void corner(VertexConsumer buffer, PoseStack.Pose pose,
        float x, float z, float cos, float sin, float u, float v, int tint)
    {
        vertex(buffer, pose, x * cos - z * sin, GROUND_CLEARANCE, x * sin + z * cos, u, v, tint);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose,
        float x, float y, float z, float u, float v, int tint)
    {
        buffer.addVertex(pose, x, y, z)
            .setColor(tint)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(0xF000F0)
            .setNormal(0F, 1F, 0F);
    }
}
