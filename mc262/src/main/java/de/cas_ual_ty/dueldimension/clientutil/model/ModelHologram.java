package de.cas_ual_ty.dueldimension.clientutil.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * A monster drawn as a real model rather than as a sprite.
 * <p>
 * <b>It faces the opponent, not the viewer.</b> A sprite has to turn to whoever
 * is looking at it, because a flat picture seen edge-on is nothing; a model has
 * a front, and the front of a monster belongs to the duellist it is being played
 * against. That also makes the facing a property of the BOARD rather than of the
 * camera — both duellists see the same dragon from opposite ends, and it must
 * look like the same dragon to both.
 * <p>
 * <b>The basis change is a single rotation.</b> glTF is Y-up and right-handed,
 * and so is Minecraft's world space, so nothing needs negating. That matters
 * more than it sounds: negating an axis to "fix" the facing would mirror the
 * model, which reverses triangle winding and turns every normal inside out —
 * and a double-sided alpha-tested material hides the winding error well enough
 * that only the lighting would look wrong.
 * <p>
 * <b>A glTF asset faces +Z.</b> The specification says so outright — "+Y as up,
 * +Z as forward, -X as right; the front of a glTF asset faces +Z" — and this
 * code was written against -Z, which is the convention a CAMERA looks down in
 * OpenGL and not the one an asset is authored to. The two differ by exactly a
 * half turn, so every model faced its own controller instead of the opponent:
 * correct-looking from one seat, backwards from the other, and on a pedestal
 * simply back-to-front.
 */
public final class ModelHologram
{
    /**
     * Full brightness.
     * <p>
     * A hologram is a thing that emits light, not one that receives it, so it
     * does not dim with the room it is standing in. The same reasoning the
     * sprites use.
     */
    private static final int FULL_BRIGHT = 0xF000F0;

    private ModelHologram()
    {
    }

    /**
     * Draws a model standing on a card, facing the way its controller faces.
     *
     * @param camera    the origin every coordinate is measured against, the same
     *                  one the rest of the board uses
     * @param feet      where the model stands, in {@code camera}'s space
     * @param height    how tall to draw it, in blocks. The model's own units are
     *                  whatever the artist exported — this dragon is forty units
     *                  tall, which at a unit per block would be a dragon the size
     *                  of a village — so the scale is derived rather than
     *                  authored.
     * @param look      the direction the controller of this monster is facing,
     *                  which is the way their monsters look
     * @param tint      ARGB, as the sprites take
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 feet, float height, ModelMesh mesh, Direction look, int tint, String animation,
        float elevation, float turn, float offsetX, float offsetZ, float seconds)
    {
        submit(poseStack, collector, camera, feet, height, mesh, look.toYRot(), tint, animation,
            elevation, turn, offsetX, offsetZ, seconds);
    }

    /** The looping form, for anything with no battle to be part of. */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 feet, float height, ModelMesh mesh, float yaw, int tint, String animation,
        float elevation, float turn, float offsetX, float offsetZ)
    {
        submit(poseStack, collector, camera, feet, height, mesh, yaw, tint, animation,
            elevation, turn, offsetX, offsetZ, Float.NaN);
    }

    /**
     * The same, given a yaw rather than one of the four compass points.
     * <p>
     * <b>A heading, not a bearing on the viewer.</b> This used to be handed the
     * yaw from the model to the camera, so a model on a pedestal turned to
     * follow whoever was looking at it — which is right for a flat sprite, because a picture seen
     * edge-on is nothing, and wrong for a model, because a model has a back and
     * turning it away from you is how you find that out. A thing on a shelf sits
     * the way it was put there.
     *
     * @param yaw       degrees in Minecraft's convention, where 0 is SOUTH
     * @param elevation blocks above {@code feet} to float, before scaling — so it
     *                  means the same distance whatever size the monster is drawn
     * @param turn      degrees added to {@code yaw}, correcting a model that does
     *                  not follow glTF's own +Z-forward convention
     * @param offsetX   sideways and {@code offsetZ} forward, in blocks, in the
     *                  model's own frame — so they turn with it
     * @param seconds   how far into {@code animation} to pose, for a clip played
     *                  ONCE from a known instant; {@link Float#NaN} to loop it
     *                  off the shared clock instead, which is what a monster
     *                  standing about does
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 feet, float height, ModelMesh mesh, float yaw, int tint, String animation,
        float elevation, float turn, float offsetX, float offsetZ, float seconds)
    {
        if(mesh == null || mesh.modelHeight() <= 0F)
        {
            return;
        }
        float scale = height / mesh.modelHeight();
        float[] foot = mesh.footOffset();

        // Posed once for the whole model, not once per part: every part is
        // driven by the same skeleton at the same instant, and posing per part
        // would be both wasteful and — if the clock moved between them — a
        // dragon whose head and body were a frame apart.
        ModelSkeleton skeleton = mesh.skeleton();
        // Named, or idling. An unknown name falls through to the idle too: a
        // definition can outlive the model it was written against -- a re-export
        // with different slots, a card pointed at another file -- and a monster
        // standing in its bind pose is a worse answer than one that breathes.
        int index = skeleton == null ? -1 : skeleton.indexOf(animation);
        // A one-shot only survives if the model HAS the slot being asked for.
        // Not every monster does -- the slots are an eight-bit presence mask, so
        // a monster with no attack simply has no bit 2 -- and pose() clamps
        // rather than wraps, so feeding a one-shot phase to the idle it fell
        // back to would freeze the creature in the idle's last frame for the
        // whole battle. Guarded here, it just keeps breathing instead.
        boolean once = index >= 0 && !Float.isNaN(seconds);
        if(skeleton != null && index < 0)
        {
            index = skeleton.idleIndex();
        }
        // Cross-faded rather than cut, over six frames, because that is what
        // the game does -- see ModelSkeleton.BLEND_SECONDS. Three cases: leaning
        // out of the idle into a swing, the swing itself, and settling back.
        float[] posed = null;
        if(skeleton != null)
        {
            int idle = skeleton.idleIndex();
            float idleAt = timeIn(skeleton, idle, feet);
            int at = index;
            float when = once ? seconds : idleAt;
            int from = -1;
            float fromWhen = 0F;
            float mix = 1F;
            if(once)
            {
                float clip = skeleton.animations().get(index).duration();
                if(seconds > clip)
                {
                    // The tail the battle window now runs on for: the clip is
                    // over and the monster is standing up out of it, so the idle
                    // is what it is becoming and the swing's last pose is what it
                    // is leaving.
                    at = idle;
                    when = idleAt;
                    from = index;
                    fromWhen = clip;
                    mix = Math.clamp((seconds - clip) / ModelSkeleton.BLEND_SECONDS, 0F, 1F);
                }
                else if(seconds < ModelSkeleton.BLEND_SECONDS)
                {
                    // Leaning in. The monster starts the swing from wherever its
                    // breathing had got to rather than from the swing's frame 0,
                    // which is the whole difference between a lunge and a snap.
                    from = idle;
                    fromWhen = idleAt;
                    mix = seconds / ModelSkeleton.BLEND_SECONDS;
                }
            }
            posed = ModelSkeleton.flatten(skeleton.pose(at, when, from, fromWhen, mix));
        }
        // Settled into a final local: the geometry lambdas below capture it, and
        // a variable written inside a branch is not one they may close over.
        final float[] bones = posed;

        poseStack.pushPose();
        // Doubles first, and relative to the camera: the board is drawn in
        // camera space, and folding the offset into the float matrix after
        // scaling would lose precision a long way from spawn.
        // Outside the scale, and in blocks, so that raising a monster by half a
        // block raises it by half a block whether it is drawn as a hatchling or
        // as a dragon. Folded into this translate rather than added to feet.y by
        // every caller, because there are two callers and one of them would
        // eventually not.
        poseStack.translate(feet.x - camera.x, feet.y - camera.y + elevation,
            feet.z - camera.z);

        // -yaw, because a glTF asset faces +Z.
        //
        // Worth the derivation, since it is one sign and one half-turn and both
        // are easy to get wrong. Rotating a model's forward f by t about +Y and
        // requiring it to equal Minecraft's direction for yaw y -- which is
        // (-sin y, 0, cos y), zero being SOUTH -- gives t = -y for f = +Z, and
        // t = 180 - y for f = -Z. This said 180 - y, so it aimed every monster
        // at its own controller.
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw + turn));
        // AFTER the rotation and BEFORE the scale, which places it in the
        // model's own frame and in blocks.
        //
        // Both halves of that matter. The two duellists' monsters face opposite
        // ways, so a nudge applied in world space would push one towards its
        // owner and the other away from theirs -- the same number reading as two
        // different corrections depending on which seat you are in. Applied
        // after the turn, "forward" is the creature's forward on both sides.
        // And before the scale, so half a block is half a block whether the
        // monster is drawn as a hatchling or as a dragon, exactly as the lift is.
        poseStack.translate(offsetX, 0F, offsetZ);
        poseStack.scale(scale, scale, scale);
        // In model units, so it happens inside the scale: this lifts the model
        // so its lowest point sits on the card rather than its origin, which for
        // this dragon is a third of the way up its body.
        poseStack.translate(-foot[0], -foot[1], -foot[2]);

        for(ModelMesh.Part part : mesh.parts())
        {
            if(part.texture() == null)
            {
                continue;
            }
            // COPIED, not referenced. submitCustomGeometry does not draw now --
            // it defers -- and PoseStack recycles the Pose that last() returns,
            // so by the time this lambda runs the live one belongs to something
            // else entirely.
            PoseStack.Pose pose = poseStack.last().copy();
            float[] positions = part.positions();
            float[] normals = part.normals();
            float[] uvs = part.uvs();
            int vertices = part.vertexCount();
            // A part with no bones, or a model with none, is drawn exactly where
            // it was modelled -- which is also the fallback that keeps an
            // unskinned model working now that this method knows about bones.
            int[] joints = bones != null && part.skinned() ? part.joints() : null;
            float[] weights = joints == null ? null : part.weights();

            // Blended only while it is actually part way there. A monster at
            // full solidity keeps the depth-sorted pipeline, which is the one
            // that draws a creature correctly.
            boolean blend = (tint >>> 24) < 0xFF;
            collector.submitCustomGeometry(poseStack,
                ModelMesh.typeFor(part.texture(), blend),
                (unused, buffer) ->
                {
                    // Skinned straight into the vertex, with no posed copy of the
                    // mesh in between. 14,958 vertices is 175KB of positions
                    // alone; allocating that per frame -- per model, per part --
                    // would hand the collector megabytes a second to sweep up.
                    float[] point = new float[3];
                    float[] direction = new float[3];
                    for(int i = 0; i < vertices; i++)
                    {
                        float px = positions[i * 3];
                        float py = positions[i * 3 + 1];
                        float pz = positions[i * 3 + 2];
                        float nx = normals[i * 3];
                        float ny = normals[i * 3 + 1];
                        float nz = normals[i * 3 + 2];
                        if(joints != null)
                        {
                            ModelSkeleton.apply(bones, joints, weights, i, px, py, pz,
                                true, point);
                            px = point[0];
                            py = point[1];
                            pz = point[2];
                            // The normal goes through the same bones, minus the
                            // translation -- a rotated limb whose normals stayed
                            // put would light as though it had never moved.
                            ModelSkeleton.apply(bones, joints, weights, i, nx, ny, nz,
                                false, direction);
                            nx = direction[0];
                            ny = direction[1];
                            nz = direction[2];
                        }
                        float u = uvs[i * 2];
                        float v = uvs[i * 2 + 1];
                        buffer.addVertex(pose, px, py, pz)
                            .setColor(tint)
                            .setUv(u, v)
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(FULL_BRIGHT)
                            .setNormal(pose, nx, ny, nz);
                    }
                });
        }
        poseStack.popPose();
    }

    /**
     * How far into the animation it is, wrapped so it loops.
     * <p>
     * Off the wall clock rather than off game ticks, for the same reason the
     * card preview is: an idle animation is scenery, and scenery running at 20Hz
     * steps visibly however smoothly the keyframes are interpolated. The cost is
     * that it keeps breathing while the game is paused, which is the right way
     * round for a hologram.
     * <p>
     * <b>Every copy of a model runs at the same rate but not in step.</b> The
     * clock is shared; the phase is not. See {@link #offsetAt}.
     */
    private static float timeIn(ModelSkeleton skeleton, int index, Vec3 at)
    {
        if(index < 0 || index >= skeleton.animations().size())
        {
            return 0F;
        }
        float duration = skeleton.animations().get(index).duration();
        if(duration <= 0F)
        {
            // A single-keyframe animation is a pose, and dividing by its zero
            // length would put a NaN into every bone.
            return 0F;
        }
        // Nanoseconds, and the wrap done in whole nanoseconds rather than in
        // seconds.
        //
        // This read MILLISECONDS, and that was the jitter. The keyframes are a
        // uniform 30 per second — measured, 0.0333s apart to six places — so at
        // sixty frames a second each frame should advance the animation by
        // exactly half a keyframe. Off a clock that only counts whole
        // milliseconds it advances 16, 17, 17, 16, 17… instead: a few per cent
        // of velocity flutter, every frame, on every bone at once. Small enough
        // to look like a wobble in the model rather than a wobble in the clock,
        // which is why it is worth naming here.
        //
        // The modulo is integer, so it stays exact however long the machine has
        // been up — reducing first and dividing after keeps the answer inside
        // the range where a float still has resolution to spare.
        long span = (long)(duration * 1.0e9D);
        if(span <= 0L)
        {
            return 0F;
        }
        return (float)(((net.minecraft.util.Util.getNanos() + offsetAt(at, span)) % span)
            / 1.0e9D);
    }

    /**
     * A fixed head start, so that two of the same monster do not breathe in step.
     * <p>
     * They did, and it read as one thing puppeting several bodies rather than as
     * several creatures. Every model was posed from one shared clock, which is
     * right for keeping a single monster consistent between frames and wrong for
     * telling copies of it apart.
     * <p>
     * <b>Derived from where the monster stands rather than from anything about
     * the instance.</b> There is no per-instance identity to hang this on — a
     * monster is "the card in zone N" and nothing more — but a zone is a fixed
     * point in the world, which is exactly the property needed: stable frame to
     * frame, so the offset never jitters, and different per square, so no two
     * neighbours share one. The card display gets it for free, since two
     * pedestals are two different places.
     * <p>
     * Mixed rather than used raw: neighbouring zones are a block or two apart,
     * and the low bits of two nearby coordinates are far too similar to spread a
     * phase across a whole cycle.
     */
    private static long offsetAt(Vec3 at, long span)
    {
        // Sixteenths of a block, so the key changes between adjacent zones but
        // not between frames.
        long key = (long)Math.floor(at.x * 16D) * 73_856_093L
            ^ (long)Math.floor(at.y * 16D) * 19_349_663L
            ^ (long)Math.floor(at.z * 16D) * 83_492_791L;
        key ^= key >>> 33;
        key *= 0xFF51AFD7ED558CCDL;
        key ^= key >>> 33;
        return Math.floorMod(key, span);
    }
}
