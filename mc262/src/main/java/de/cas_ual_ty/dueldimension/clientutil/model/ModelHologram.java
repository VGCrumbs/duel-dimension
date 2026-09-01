package de.cas_ual_ty.dueldimension.clientutil.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
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
        submit(poseStack, collector, camera, feet, height, mesh, look, tint, animation,
            elevation, turn, offsetX, offsetZ, seconds, null);
    }

    /** The same, lit by the world around it. */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 feet, float height, ModelMesh mesh, Direction look, int tint, String animation,
        float elevation, float turn, float offsetX, float offsetZ, float seconds,
        ModelLight light)
    {
        submit(poseStack, collector, camera, feet, height, mesh, look.toYRot(), tint,
            animation, elevation, turn, offsetX, offsetZ, seconds, light);
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
        submit(poseStack, collector, camera, feet, height, mesh, yaw, tint, animation,
            elevation, turn, offsetX, offsetZ, seconds, null);
    }

    /**
     * @param light the world's light around this monster, or null to draw it
     *              at full brightness as a hologram used to be. Built during
     *              extraction, because that is where a renderer may look at
     *              the world -- see ModelLight.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 feet, float height, ModelMesh mesh, float yaw, int tint, String animation,
        float elevation, float turn, float offsetX, float offsetZ, float seconds,
        ModelLight light)
    {
        submit(poseStack, collector, camera, feet, height, mesh, yaw, tint, animation,
            elevation, turn, offsetX, offsetZ, seconds, light, null, null);
    }

    /**
     * @param skin which parts to draw and what to draw them with: given a part's
     *             index it returns the texture to use, or null to skip that part
     *             entirely. Null for the whole thing painted as it was exported,
     *             which is what a monster wants.
     *             <p>
     *             This exists for the player characters, where one model file
     *             holds every body, hairstyle, face and duel disk on a shared
     *             skeleton and a character is four of them wearing colours
     *             chosen at runtime. Selecting and re-skinning here rather than
     *             in a renderer of its own keeps ONE skinning loop: the
     *             alternative was a second copy of the vertex path, which is the
     *             part of this file that is least safe to have two of.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 feet, float height, ModelMesh mesh, float yaw, int tint, String animation,
        float elevation, float turn, float offsetX, float offsetZ, float seconds,
        ModelLight light, java.util.function.IntFunction<Identifier> skin)
    {
        submit(poseStack, collector, camera, feet, height, mesh, yaw, tint, animation,
            elevation, turn, offsetX, offsetZ, seconds, light, skin, null, null);
    }

    /**
     * @param head one joint turned on top of the animation, or null. For a
     *             player character's neck, which follows where its player is
     *             looking while the clip underneath carries on walking.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 feet, float height, ModelMesh mesh, float yaw, int tint, String animation,
        float elevation, float turn, float offsetX, float offsetZ, float seconds,
        ModelLight light, java.util.function.IntFunction<Identifier> skin,
        ModelSkeleton.Turn head)
    {
        submit(poseStack, collector, camera, feet, height, mesh, yaw, tint, animation,
            elevation, turn, offsetX, offsetZ, seconds, light, skin, null,
            head == null ? null : new ModelSkeleton.Turn[] {head}, null);
    }

    /**
     * @param under the clip a ONE-SHOT leans out of and settles back into, or
     *              null for the model's idle.
     *              <p>
     *              A monster has one thing it does when it is not swinging, so
     *              the idle is the right answer and the parameter is not needed.
     *              A player character has three -- standing, walking, running --
     *              and blending a punch out of the idle while its legs are in
     *              the middle of a stride is a visible jolt in both directions.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 feet, float height, ModelMesh mesh, float yaw, int tint, String animation,
        float elevation, float turn, float offsetX, float offsetZ, float seconds,
        ModelLight light, java.util.function.IntFunction<Identifier> skin,
        ModelSkeleton.Turn head, String under)
    {
        submit(poseStack, collector, camera, feet, height, mesh, yaw, tint, animation,
            elevation, turn, offsetX, offsetZ, seconds, light, skin, under,
            head == null ? null : new ModelSkeleton.Turn[] {head}, null, false);
    }

    /**
     * @param turns joints turned on top of the pose, in order; null for none
     * @param limb  which branch a ONE-SHOT is confined to, by the name of the
     *              node it hangs from -- null for the whole body.
     *              <p>
     *              A monster's attack is its whole self, so a monster passes
     *              null and nothing here applies. A duellist throwing a punch is
     *              doing one thing with an arm and another with their legs, and
     *              a full-body cross-fade cannot express that: the punch's own
     *              legs are standing still, so blending it in stops the walk
     *              dead for a third of a second and then starts it again.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 feet, float height, ModelMesh mesh, float yaw, int tint, String animation,
        float elevation, float turn, float offsetX, float offsetZ, float seconds,
        ModelLight light, java.util.function.IntFunction<Identifier> skin, String under,
        ModelSkeleton.Turn[] turns, String limb)
    {
        submit(poseStack, collector, camera, feet, height, mesh, yaw, tint, animation,
            elevation, turn, offsetX, offsetZ, seconds, light, skin, under, turns, limb,
            false, null);
    }

    /**
     * @param backwards run the LOOPING clip from its end towards its start.
     *                  <p>
     *                  A walk cycle played forwards while its owner is going the
     *                  other way is the moonwalk, and it reads as one. The DS has
     *                  no backwards walk and never needed one -- its duellists
     *                  are driven by a d-pad and turn to face wherever they are
     *                  going. Minecraft's do not: strafing and walking backwards
     *                  keep the body facing forwards, so the same cycle has to
     *                  serve both directions, and the honest way to make it do
     *                  that is to run it the other way round.
     *                  <p>
     *                  Only the looping path. A one-shot has a beginning and an
     *                  end that mean something.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 feet, float height, ModelMesh mesh, float yaw, int tint, String animation,
        float elevation, float turn, float offsetX, float offsetZ, float seconds,
        ModelLight light, java.util.function.IntFunction<Identifier> skin, String under,
        ModelSkeleton.Turn[] turns, String limb, boolean backwards)
    {
        submit(poseStack, collector, camera, feet, height, mesh, yaw, tint, animation,
            elevation, turn, offsetX, offsetZ, seconds, light, skin, under, turns, limb,
            backwards, null, null);
    }

    /**
     * Something hung off a bone: a sword in a hand, and nothing else so far.
     * <p>
     * Called from INSIDE, with the pose stack already carrying everything that
     * puts the model where it is -- the position, the facing, the scale and the
     * lift onto its own feet. A caller cannot rebuild that frame from outside
     * without repeating all four, and repeating them is how the two drift.
     */
    public interface Attached
    {
        void draw(PoseStack pose, ModelSkeleton skeleton, float[] bones);
    }

    /** @param attached drawn in the model's own frame once it is posed */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 feet, float height, ModelMesh mesh, float yaw, int tint, String animation,
        float elevation, float turn, float offsetX, float offsetZ, float seconds,
        ModelLight light, java.util.function.IntFunction<Identifier> skin, String under,
        ModelSkeleton.Turn[] turns, String limb, boolean backwards, Attached attached)
    {
        submit(poseStack, collector, camera, feet, height, mesh, yaw, tint, animation,
            elevation, turn, offsetX, offsetZ, seconds, light, skin, under, turns, limb,
            backwards, attached, null, 1F);
    }

    /**
     * Leaving one looping clip for another.
     *
     * @param clip the clip being left, still running on its own clock
     * @param mix  0 at the moment of the change, 1 once the new one has it
     */
    public record Fade(String clip, float mix)
    {
    }

    /**
     * @param fade a cross-fade out of the clip that was playing, or null to cut.
     *             <p>
     *             A monster changes what it is doing when something happens to
     *             it, and the one-shot path below already eases those. A player
     *             changes constantly -- every step started and every step
     *             stopped -- and cutting between a run and a stand is a visible
     *             snap several times a minute.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 feet, float height, ModelMesh mesh, float yaw, int tint, String animation,
        float elevation, float turn, float offsetX, float offsetZ, float seconds,
        ModelLight light, java.util.function.IntFunction<Identifier> skin, String under,
        ModelSkeleton.Turn[] turns, String limb, boolean backwards, Attached attached,
        Fade fade)
    {
        submit(poseStack, collector, camera, feet, height, mesh, yaw, tint, animation,
            elevation, turn, offsetX, offsetZ, seconds, light, skin, under, turns, limb,
            backwards, attached, fade, 1F);
    }

    /**
     * @param reach how far into the limb clip the overlay is allowed to go, 1
     *              being all the way. Only meaningful with {@code limb} -- it
     *              scales the mask's blend, so a lighter swing is the same
     *              motion arrested rather than a different one.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 feet, float height, ModelMesh mesh, float yaw, int tint, String animation,
        float elevation, float turn, float offsetX, float offsetZ, float seconds,
        ModelLight light, java.util.function.IntFunction<Identifier> skin, String under,
        ModelSkeleton.Turn[] turns, String limb, boolean backwards, Attached attached,
        Fade fade, float reach)
    {
        submit(poseStack, collector, camera, feet, height, mesh, yaw, tint, animation,
            elevation, turn, offsetX, offsetZ, seconds, light, skin, under, turns, limb,
            backwards, attached, fade, reach, 1F);
    }

    /**
     * @param rate how fast a LOOPING clip runs, 1 being its authored speed.
     *             <p>
     *             For a cycle that has to keep company with something outside
     *             itself. A monster breathes at whatever rate it was drawn at
     *             and wants 1; a duellist's walk has to land its steps near
     *             where Minecraft's own limb swing lands them, and the DS
     *             authored that cycle for a character who moves at the DS's
     *             speed rather than at 4.317 blocks a second.
     *             <p>
     *             Ignored on the one-shot path, which is driven by an explicit
     *             {@code seconds} and has a beginning to be measured from.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
        Vec3 feet, float height, ModelMesh mesh, float yaw, int tint, String animation,
        float elevation, float turn, float offsetX, float offsetZ, float seconds,
        ModelLight light, java.util.function.IntFunction<Identifier> skin, String under,
        ModelSkeleton.Turn[] turns, String limb, boolean backwards, Attached attached,
        Fade fade, float reach, float rate)
    {
        if(mesh == null || mesh.modelHeight() <= 0F)
        {
            return;
        }
        // A NEGATIVE HEIGHT MEANS "AS AUTHORED", and it is the player
        // characters that need it.
        //
        // Everything else here is a monster: a rip in whatever units its artist
        // used, so it is measured and scaled to fit. That measurement is the
        // WHOLE mesh, which for a monster is the monster. A character file is
        // sixty parts -- fifteen bodies, fifteen hairstyles, fifteen faces,
        // fifteen duel disks, all in one file on one skeleton -- so its union
        // spans y = -1.01 to 2.00 and measuring it scales the duellist to two
        // thirds of their size and lifts them two thirds of a block off the
        // ground. Which is exactly what it did.
        //
        // The exporter already put them in metres, feet at the origin, two
        // blocks tall (see export_glb.py, PLAYER_HEIGHT). So the fixed frame
        // this needs is the one it was exported in, and it also settles the
        // rule that a hat must not resize the wearer: the frame does not depend
        // on which four of the sixty are being drawn.
        boolean authored = height < 0F;
        float scale = authored ? 1F : height / mesh.modelHeight();
        float[] foot = authored ? new float[] {0F, 0F, 0F} : mesh.footOffset();

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
            int idle = under == null ? skeleton.idleIndex() : skeleton.indexOf(under);
            if(idle < 0)
            {
                idle = skeleton.idleIndex();
            }
            // feet MINUS camera, not feet: the phase key must not change
            // between frames, and this is the position the caller measured in
            // the frame they measured it in. A board zone is fixed relative to
            // the board, so monsters keep their stagger; a player character
            // passes the camera for both and so gets zero, which is the only
            // stable answer for something that moves.
            Vec3 phase = feet.subtract(camera);
            float idleAt = timeIn(skeleton, idle, phase);
            int at = index;
            // A LOOPING CLIP RUNS ON ITS OWN LENGTH, not on the idle's.
            //
            // This was `idleAt`, and for a monster it was invisibly right: the
            // looping path only ever reaches here with `index` already fallen
            // back to the idle, so the two were the same number. A player
            // character loops a walk and a run as well, and those have their own
            // durations -- and it has no `slot_0` at all, so its "idle" is
            // whichever clip happens to be first in the file. So a two-second
            // idle was being sampled on a two-thirds-of-a-second clock: a third
            // of the animation, on repeat, with a jump where the rest should
            // have been.
            //
            // `idleAt` is still what the one-shot blends lean out of and back
            // into below, which is the case it was written for.
            float when = once ? seconds : timeIn(skeleton, index, phase, rate);
            if(backwards && !once && index >= 0 && index < skeleton.animations().size())
            {
                // Mirrored about the clip's length rather than negated, so it
                // stays inside the samplers' range and the loop still closes on
                // the duplicate final key the exporter writes.
                float span = skeleton.animations().get(index).duration();
                if(span > 0F)
                {
                    when = span - when;
                }
            }
            int from = -1;
            float fromWhen = 0F;
            float mix = 1F;
            int[] only = null;
            if(once && limb != null)
            {
                // Laid OVER the base clip rather than cross-faded with it: `at`
                // is the punch, `from` is whatever the legs are already doing,
                // and the mask keeps the punch off them. The ramp is only there
                // so the arm arrives and leaves rather than snapping.
                float clip = skeleton.animations().get(index).duration();
                only = skeleton.branch(limb);
                at = index;
                when = seconds;
                from = idle;
                fromWhen = idleAt;
                mix = seconds < ModelSkeleton.BLEND_SECONDS
                    ? seconds / ModelSkeleton.BLEND_SECONDS
                    : Math.clamp(1F - (seconds - clip) / ModelSkeleton.BLEND_SECONDS, 0F, 1F);
                mix *= Math.clamp(reach, 0F, 1F);
            }
            else if(once)
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
            if(!once && fade != null && fade.mix() < 1F)
            {
                // Leaving one loop for another. Both keep their own clocks --
                // the walk carries on stepping while it fades out, which is
                // what stops the legs from freezing mid-stride and then
                // sliding to a stand.
                int leaving = skeleton.indexOf(fade.clip());
                if(leaving >= 0 && leaving != at)
                {
                    from = leaving;
                    fromWhen = timeIn(skeleton, leaving, phase);
                    mix = Math.clamp(fade.mix(), 0F, 1F);
                }
            }
            posed = ModelSkeleton.flatten(
                skeleton.pose(at, when, from, fromWhen, mix, only, turns));
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

        for(int slot = 0; slot < mesh.parts().size(); slot++)
        {
            ModelMesh.Part part = mesh.parts().get(slot);
            Identifier painted = skin == null ? part.texture() : skin.apply(slot);
            if(painted == null)
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
                ModelMesh.typeFor(painted, blend),
                (unused, buffer) ->
                {
                    // Skinned straight into the vertex, with no posed copy of the
                    // mesh in between. 14,958 vertices is 175KB of positions
                    // alone; allocating that per frame -- per model, per part --
                    // would hand the collector megabytes a second to sweep up.
                    float[] point = new float[3];
                    float[] direction = new float[3];
                    for(int triangle = 0; triangle + 2 < vertices; triangle += 3)
                    {
                        for(int corner = 0; corner < 3; corner++)
                        {
                            emit(buffer, pose, camera, triangle + corner, positions,
                                normals, uvs, joints, weights, bones, tint, point,
                                direction, light, false);
                        }
                        // AND THE SAME TRIANGLE BACKWARDS, normal turned round.
                        // These models are rips: a cape is one sheet with nothing
                        // behind it, so culling leaves a hole and not culling shows
                        // the sheet's back lit by a normal facing away -- dark from
                        // one side. Drawing the reverse face explicitly gives every
                        // sheet a correctly-lit front from either side.
                        for(int corner = 2; corner >= 0; corner--)
                        {
                            emit(buffer, pose, camera, triangle + corner, positions,
                                normals, uvs, joints, weights, bones, tint, point,
                                direction, light, true);
                        }
                    }
                });
        }
        if(attached != null && skeleton != null && bones != null)
        {
            attached.draw(poseStack, skeleton, bones);
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
        return timeIn(skeleton, index, at, 1F);
    }

    /**
     * How far into a looping clip a PLAYER CHARACTER is, right now.
     * <p>
     * The same number the renderer poses with, exposed because something other
     * than the renderer needs it: a footstep has to be played when the foot
     * lands, and the sound is not drawn. Sharing the function rather than
     * reproducing the arithmetic is the whole point -- two clocks that agree
     * today would be one refactor away from the sound drifting off the picture,
     * and a few milliseconds of that is audible where the same error in a
     * monster's breathing would not be.
     * <p>
     * A character's phase offset is zero by construction: {@code offsetAt} is
     * fed {@code feet - camera}, and a player passes the same vector for both.
     * See the note there.
     */
    public static float loopPhase(ModelSkeleton skeleton, int index, float rate)
    {
        return skeleton == null ? 0F : timeIn(skeleton, index, Vec3.ZERO, rate);
    }

    /**
     * The same, run faster or slower than the clip was authored at.
     * <p>
     * <b>The SPAN is scaled, not the clock.</b> Multiplying the nanosecond
     * count by the rate is the obvious way and it throws precision away: the
     * clock is already around 1e18 and a double carries 53 bits, so the product
     * is quantised to hundreds of nanoseconds and the wrap stops being exact.
     * Shortening the period instead keeps the modulo in whole nanoseconds --
     * which is the thing the note below went to some trouble to get right --
     * and the result is scaled back into the clip's real range afterwards.
     */
    private static float timeIn(ModelSkeleton skeleton, int index, Vec3 at, float rate)
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
        float speed = rate > 0F ? rate : 1F;
        long span = (long)(duration / speed * 1.0e9D);
        if(span <= 0L)
        {
            return 0F;
        }
        // Back into the clip's own range: the phase was measured against a
        // period shortened by `speed`, so multiplying by it undoes exactly that
        // and lands inside [0, duration) whatever the rate is.
        return (float)(((net.minecraft.util.Util.getNanos() + offsetAt(at, span)) % span)
            / 1.0e9D * speed);
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

    /**
     * One vertex, skinned and written.
     *
     * @param flip the back copy of this triangle: the caller reverses the
     *             winding, so the normal has to turn with it. Without that a
     *             sheet seen from behind is lit by a normal pointing away and
     *             reads as dark from one side only.
     */
    private static void emit(com.mojang.blaze3d.vertex.VertexConsumer buffer,
        PoseStack.Pose pose, Vec3 camera, int i, float[] positions, float[] normals,
        float[] uvs, int[] joints, float[] weights, float[] bones, int tint,
        float[] point, float[] direction, ModelLight light, boolean flip)
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
                    // A DEGENERATE NORMAL IS NOT RARE HERE, and it does not
                    // fail quietly -- it fails as NaN.
                    //
                    // Across the 686 models installed, 87,090 normals are
                    // exactly zero, in 676 of them. Handed on, the pose's
                    // transformNormal normalises by 1/sqrt(0), so every
                    // component becomes NaN; Java then converts NaN to 0
                    // packing the byte normal, and a zero normal takes no
                    // diffuse light at all. The vertex shades black and the
                    // rasteriser spreads that across every triangle using
                    // it, which reads as blotches on a surface that should
                    // have been smooth.
                    //
                    // Up, because it has to be SOMETHING finite and a zero
                    // normal carries no direction to recover. The blend can
                    // also cancel to nothing when a vertex is shared by
                    // bones facing opposite ways, so this is checked after
                    // skinning rather than on the source.
                    if(nx * nx + ny * ny + nz * nz < 1.0e-12F)
                    {
                        nx = 0F;
                        ny = 1F;
                        nz = 0F;
                    }
                    if(flip)
                    {
                        nx = -nx;
                        ny = -ny;
                        nz = -nz;
                    }
                    float u = uvs[i * 2];
                    float v = uvs[i * 2 + 1];
                    buffer.addVertex(pose, px, py, pz)
                        .setColor(tint)
                        .setUv(u, v)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        // Per vertex, from the world's own light around
                        // this monster. The vertex is in the model's posed
                        // local space here, so it goes back through the
                        // pose to find where in the WORLD it actually is --
                        // which is the whole point: a dragon's near side and
                        // far side are metres apart and can be lit
                        // differently.
                        .setLight(light == null ? FULL_BRIGHT
                            : light.at(
                                pose.pose().m00() * px + pose.pose().m10() * py
                                    + pose.pose().m20() * pz + pose.pose().m30()
                                    + camera.x,
                                pose.pose().m01() * px + pose.pose().m11() * py
                                    + pose.pose().m21() * pz + pose.pose().m31()
                                    + camera.y,
                                pose.pose().m02() * px + pose.pose().m12() * py
                                    + pose.pose().m22() * pz + pose.pose().m32()
                                    + camera.z))
                        .setNormal(pose, nx, ny, nz);
    }

}
