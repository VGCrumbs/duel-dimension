package de.cas_ual_ty.dueldimension.clientutil.character;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.character.CharacterLook;
import de.cas_ual_ty.dueldimension.clientutil.model.ModelHologram;
import de.cas_ual_ty.dueldimension.clientutil.model.ModelMesh;
import de.cas_ual_ty.dueldimension.clientutil.model.ModelSkeleton;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A duellist drawn as their created character.
 * <p>
 * <b>A player's own height, 1.8 blocks.</b> Not the two blocks of headroom a
 * player needs to stand up in — that is the space around them, and building a
 * character to fill it makes them a head taller than everyone else. The model
 * is exported at that size rather than scaled here (see {@code export_glb.py}),
 * so a character stands in a doorway, fits under a ceiling and is hit by an
 * arrow where they look like they are. Their own proportions are the DS
 * game's; only the overall height is Minecraft's.
 * <p>
 * <b>Which animation is chosen from what the player is doing</b>, not from what
 * they last asked for: the render state already carries the walk speed the
 * vanilla model would have used to swing its arms, so the same number picks
 * between standing, walking and running. Nothing new has to be synced for a
 * character to walk when its player walks.
 */
public final class CharacterRenderer
{
    /**
     * Drawn in the frame it was exported in, rather than measured and fitted.
     * <p>
     * The exporter already put the character in metres, feet at the origin and
     * a player's own 1.8 blocks tall, so there is nothing here to derive -- and deriving it
     * would be wrong twice over: the model file holds sixty parts, so its
     * bounds are the union of every body and hairstyle at once, and a scale
     * taken from the parts on show would mean a hat made its wearer shorter.
     * {@code ModelHologram} reads a negative height as "as authored".
     */
    private static final float HEIGHT = -1F;

    /**
     * The clips, and what they are.
     * <p>
     * The DS names them `fpa01` and leaves it at that — its code indexes them
     * numerically and only one script in the whole game names one — so what each
     * contains was established by watching them. These four are the ones a
     * player standing in a world needs.
     * <p>
     * {@code 05a} is the seated one, and the DS uses it for the Duel Runner and
     * for a horse alike — which is why it serves every vehicle here without
     * asking what is underneath. {@code 05b} is a second seated clip, not yet
     * identified, and nothing plays it.
     * <p>
     * NONE of these animate the root joint: the translation tracks are absent in
     * every clip in both files, so a pose is joints only and the model stands
     * wherever it is put. That is what makes the seated clip work with no offset
     * of its own — Minecraft has already moved the passenger to the saddle, and
     * 05a bends the legs around it.
     */
    private static final String IDLE = "01";
    private static final String WALK = "03";
    private static final String RUN = "04";

    /**
     * How much faster than authored the walk and run cycles play.
     *
     * <h2>Why they need to be faster at all</h2>
     * The DS clips are the DS's, and they were drawn for a character who covers
     * ground at the DS's rate. Minecraft's is different -- a walk is 4.317
     * blocks a second -- so a cycle played at its own speed lands its footfalls
     * slightly behind the distance actually travelled, and the character reads
     * as gliding: the feet finish a step after the body has already gone past
     * where that step should have ended.
     *
     * <h2>Why a constant and not the player's speed</h2>
     * Because a step's LENGTH is drawn into the clip and cannot be changed by
     * playing it faster. Driving the phase from distance travelled -- which is
     * what vanilla does with {@code walkAnimation.position} -- would keep the
     * feet planted at every speed, and it would also stretch and squash the
     * cycle every time a duellist walked up a slope or into a cobweb, because
     * the eased speed it comes from is not steady. A fixed nudge keeps the
     * animation the DS's own and just stops it lagging.
     * <p>
     * <b>1.12 was still too slow, and the 1.2 ceiling this used to claim was a
     * guess.</b> That number was written from reasoning about when a swing
     * "starts to read as a different walk" rather than from watching one, and
     * the answer on screen is that it does not: at 1.25 the cycle still reads as
     * the same walk and the feet stop sliding. If it is raised again, raise it
     * by looking, because that is the only instrument that has been right about
     * this so far.
     */
    private static final float STRIDE_RATE = 1.25F;

    /** The authored rate, for everything that is not locomotion. */
    private static final float OWN_RATE = 1F;

    /**
     * The playback rate for whichever clip is running.
     *
     * <h2>The WALK only</h2>
     * Not the run, and not a crouch. Both were swept up when this was written
     * against "the two locomotion cycles", and neither was asked for.
     * <p>
     * The reason it is the walk alone is the reason the rate exists at all: the
     * DS cycle lags the ground Minecraft actually covers. A walk is 4.317 blocks
     * a second and the authored cycle falls behind it. A sprint covers more
     * ground but {@code mpa04} is a faster cycle drawn for it, and a crouch
     * covers about a third of a walk -- so in both of those the authored timing
     * is already at or ahead of the distance travelled, and hurrying it puts the
     * feet in front of the ground instead of behind it.
     * <p>
     * A crouch has no clip of its own: it is the WALK folded over by
     * {@link #crouch}, which is exactly why speeding up the walk sped up the
     * crouch-walk with it.
     */
    private static float rateFor(String clip, boolean crouching)
    {
        return !crouching && clip != null && clip.endsWith(WALK)
            ? STRIDE_RATE : OWN_RATE;
    }
    private static final String RIDE = "05a";


    /**
     * The joint the head hangs from, and the one the look is written onto.
     *
     * <h2>Not `neck`, and that is the whole bug</h2>
     * `neck` is the base of the neck and follows the torso, which is right. The
     * head is on `neck_spine` below it -- and EVERY clip in the file drives
     * `neck_spine`, the idle and the walk and the run alike. So a look added on
     * top of the clip was added to a bone the clip was already turning, and the
     * walk's own head motion swamped it: the pitch worked while standing, where
     * the idle barely moves it, and stopped working the moment anyone took a
     * step.
     * <p>
     * Written ABSOLUTELY for that reason -- see {@link ModelSkeleton.Turn#looking}.
     * Vanilla's head is not animated at all; it is assigned the pitch and yaw
     * the player is holding, and nothing else gets a say. This is that.
     */
    private static final String NECK = "neck_spine";

    /**
     * The joint below it, which takes half the bend.
     *
     * <h2>A neck is two joints and we were using one</h2>
     * `neck` is the base and `neck_spine` carries the head — the rig has both,
     * and putting the WHOLE look on the top one folds the head over a stump that
     * has not moved. At small angles nothing shows; at the clamps, sixty degrees
     * of it arrives at a single joint and the neck kinks, which is the "messed
     * up at certain look directions" and not the clamps themselves.
     * <p>
     * Half at each is what the joints are for. It works out for free because the
     * turns are ABSOLUTE and absolute means SET: `neck` is set to half the angle
     * and then `neck_spine` is set to the whole of it, and because the second
     * one sets a GLOBAL orientation it does not matter that the first already
     * moved it. The head still arrives exactly where it was told; the neck under
     * it now arrives half way, which is what a neck does.
     */
    private static final String NECK_BASE = "neck";

    /** How much of the look the lower joint takes. Half; see {@link #NECK_BASE}. */
    private static final float NECK_SHARE = 0.5F;


    /**
     * How far a neck actually goes, in degrees.
     * <p>
     * Standard anthropometric range of motion, and every one of them is well
     * short of what Minecraft allows: a vanilla player's head yaws freely and
     * pitches a full ninety degrees either way, which on a model with a face is
     * the difference between looking up and looking through the back of one's
     * own skull.
     * <p>
     * Dorsal and ventral flexion are not the same number, which is the detail a
     * single symmetric clamp would lose -- a neck bends further back than it
     * folds forward.
     */
    private static final float YAW_LIMIT = 80F;
    private static final float LOOK_UP = 60F;
    private static final float LOOK_DOWN = 50F;

    /**
     * The strike, played over whatever the legs are doing.
     * <p>
     * `11b` reflected -- see {@code export_glb.mirror_map}. The DS animates the
     * swing on the LEFT arm, which is the arm the duel disk is bolted to, and a
     * duellist who punches with their duel disk reads as a bug rather than as a
     * punch.
     */
    private static final String HIT = "11bR";

    /**
     * How long the strike takes, in seconds.
     * <p>
     * The clip is 20 frames at 60, which is a third of a second; a vanilla swing
     * is six ticks, which is three tenths. They were authored by different
     * people for different games and they agree to within one frame, so the
     * clip is played at its own rate and no scaling is invented to reconcile
     * them.
     */
    private static final float HIT_SECONDS = 20F / 60F;

    /**
     * How much of the strike a tool gets over everything else.
     * <p>
     * A duellist swinging a pickaxe is doing something with weight behind it; a
     * duellist swinging a bucket is waving. The clip is the same either way --
     * there is one -- so the difference is in how far through it the arm gets,
     * which is the mask's blend rather than a second animation.
     * <p>
     * Applied to the OVERLAY strength, so a tool reaches the clip's own full
     * pose and anything else stops short of it. Nothing is exaggerated past what
     * was authored: the tool is the honest reading and the rest is pulled back.
     */
    private static final float SWING_TOOL = 1F;
    private static final float SWING_OTHER = 0.6F;

    /**
     * The arm the mirrored strike swings, and the only part of the body it is
     * allowed to touch.
     * <p>
     * Everything below {@code armR1} -- forearm, hand, the socket on it. The
     * legs stay on whatever they were doing, which is what lets a duellist
     * punch while walking instead of stopping to punch.
     */
    private static final String HIT_ARM = "armR1";

    /** The joint the waist folds at, above the legs and below the arms. */
    private static final String WAIST = "spine";
    /**
     * Crouching: the hips sink, the knees take it, the feet stay put.
     *
     * <h2>Built rather than found</h2>
     * The DS has no crouch -- its duellists never had a reason to -- so there is
     * no clip to label, and picking one of the unnamed clips would be guessing.
     * Minecraft's own crouch is not a clip either: {@code HumanoidModel.setupAnim}
     * pitches the body by {@code 0.5F} radians and lowers it by {@code 3.2} of
     * its sixteenths. Those two numbers are the whole specification.
     *
     * <h2>Why the legs have to be solved and not just posed</h2>
     * Vanilla's legs are single cuboids, so lowering the body is the entire
     * animation. This model has a hip, a knee and an ankle, and folding only at
     * the waist bends the skirt while the legs stay straight -- which reads as a
     * bow, not a crouch.
     * <p>
     * So the drop is given to the LEGS and the geometry is solved for it. Drop
     * the hip by {@code d} and the ankles would go through the floor with it;
     * fold the knees and the ankles rise by exactly as much; do both and the
     * feet have not moved. That is a two-link chain with the ankle held under
     * the hip, which is the law of cosines and not an eyeballed angle:
     * <pre>
     *     cos a = (L1^2 + H^2 - L2^2) / (2 * L1 * H)     thigh, from vertical
     *     cos b = (L2^2 + H^2 - L1^2) / (2 * L2 * H)     shin,  from vertical
     * </pre>
     * where {@code H} is what the hip-to-ankle distance becomes. The thigh
     * turns forward by {@code a}, the knee back by {@code a + b}, and the ankle
     * forward by {@code b} -- that last one is what keeps the sole flat, because
     * the foot's accumulated turn is then exactly zero.
     * <p>
     * The lengths are MEASURED off the skeleton rather than written down, so the
     * two genders -- whose legs differ, 0.427/0.425 against 0.360/0.402 -- each
     * get their own answer without either being a special case.
     */
    private static final String HIPS = "hip";
    private static final String[] THIGH = {"legL1", "legR1"};
    private static final String[] SHIN = {"legL2", "legR2"};
    private static final String[] ANKLE = {"footL", "footR"};

    /**
     * A skirt is a two-link chain hung over a two-link leg, so it takes the
     * leg's angles.
     *
     * <h2>Hanging was not enough</h2>
     * These are children of {@link #WAIST}, so the crouch's fold reached them
     * for free -- which is wrong, because cloth is not bolted to the torso's
     * angle. Taking the fold back off leaves the skirt hanging straight down
     * under gravity, which is right for the cloth and still wrong for the
     * duellist inside it: the thighs swing forward through a panel that is not
     * moving, and a knee comes out of the front of the skirt.
     *
     * <h2>What the panels actually are</h2>
     * Front-left, front-right and centre-back, each in TWO links. That is the
     * same shape as the leg underneath -- thigh then shin -- and it is what
     * makes this solvable rather than a guess:
     * <ul>
     * <li>a front panel's upper link rides the thigh it covers, so it turns by
     *     the same {@code a}
     * <li>its lower link rides the shin, so it turns by the same {@code a + b}
     *     the knee does -- and ends at the shin's own angle, exactly as the
     *     shin does
     * <li>the back panel hangs from the waist, because the seat does not swing
     *     forward when someone squats; only its lower link follows, and only by
     *     {@code b}, to keep the calves from coming through the back of it
     * </ul>
     * This is also why long coats were affected and not just skirts. Anything
     * that reaches past the hip is skinned to these joints, so a duellist in a
     * floor-length coat had the same knee coming through the same seam.
     */
    private static final String[][] SKIRT_FRONT =
        {{"skirt_Lf_01", "skirt_Lf_02"}, {"skirt_Rf_01", "skirt_Rf_02"}};
    private static final String[] SKIRT_BACK = {"skirt_Cb_01", "skirt_Cb_02"};

    /**
     * The fold, which is vanilla's own 0.5 radians, and the sink, which is not.
     * <p>
     * The drop started at vanilla's `3.2` sixteenths -- the number
     * {@code HumanoidModel} lowers a crouching body by -- and that is too far
     * HERE for a reason worth writing down: vanilla's legs are single cuboids
     * that do not bend, so its drop never has to look like anything. Given to a
     * leg with a knee in it, the same distance solves to about 85 degrees of
     * flexion, which is a squat rather than a sneak.
     * <p>
     * So this one is chosen by eye and kept in sixteenths, because that is the
     * unit the rest of the crouch is quoted in.
     */
    private static final float CROUCH_PITCH = (float) Math.toDegrees(0.5F);
    private static final float CROUCH_DROP = 2.0F / 16F;

    /**
     * The crouch, worked out once per skeleton.
     * <p>
     * It cannot change: it is a function of bone lengths and two constants. So
     * it is solved on the first crouch and kept, rather than run through three
     * arc-cosines for every player in view every frame.
     */
    private static final java.util.Map<ModelSkeleton, ModelSkeleton.Turn[]> CROUCHES =
        new java.util.WeakHashMap<>();

    private static ModelSkeleton.Turn[] crouch(ModelMesh mesh)
    {
        ModelSkeleton skeleton = mesh.skeleton();
        if(skeleton == null)
        {
            return new ModelSkeleton.Turn[0];
        }
        return CROUCHES.computeIfAbsent(skeleton, CharacterRenderer::solveCrouch);
    }

    private static ModelSkeleton.Turn[] solveCrouch(ModelSkeleton skeleton)
    {
        float[] hip = skeleton.restPosition(THIGH[0]);
        float[] knee = skeleton.restPosition(SHIN[0]);
        float[] ankle = skeleton.restPosition(ANKLE[0]);
        if(hip == null || knee == null || ankle == null)
        {
            // No legs to solve. The waist fold alone is still better than
            // standing bolt upright while sneaking.
            return new ModelSkeleton.Turn[]
                {new ModelSkeleton.Turn(WAIST, 0F, CROUCH_PITCH, -CROUCH_DROP)};
        }
        float thigh = length(hip, knee);
        float shin = length(knee, ankle);
        float straight = hip[1] - ankle[1];
        // Never past what the leg can reach, in either direction: a fold deeper
        // than the links allow has no triangle, and acos of a number outside
        // [-1, 1] is NaN -- which would put a NaN through every bone below.
        float folded = Math.clamp(straight - CROUCH_DROP,
            Math.abs(thigh - shin) + 0.01F, thigh + shin - 0.01F);
        float a = (float) Math.toDegrees(Math.acos(Math.clamp(
            (thigh * thigh + folded * folded - shin * shin) / (2F * thigh * folded),
            -1F, 1F)));
        float b = (float) Math.toDegrees(Math.acos(Math.clamp(
            (shin * shin + folded * folded - thigh * thigh) / (2F * shin * folded),
            -1F, 1F)));

        java.util.List<ModelSkeleton.Turn> turns = new java.util.ArrayList<>();
        // The hips first, and everything below comes with them -- the legs AND
        // the torso, which is why the waist fold below needs no drop of its own.
        turns.add(new ModelSkeleton.Turn(HIPS, 0F, 0F, -(straight - folded)));
        for(int side = 0; side < 2; side++)
        {
            turns.add(new ModelSkeleton.Turn(THIGH[side], 0F, -a));
            turns.add(new ModelSkeleton.Turn(SHIN[side], 0F, a + b));
            turns.add(new ModelSkeleton.Turn(ANKLE[side], 0F, -b));
        }
        turns.add(new ModelSkeleton.Turn(WAIST, 0F, CROUCH_PITCH));
        // AFTER the waist, because these are answering what the waist just did
        // to them. Applied before it, the fold would be reapplied on top.
        for(String[] panel : SKIRT_FRONT)
        {
            turns.add(new ModelSkeleton.Turn(panel[0], 0F, -CROUCH_PITCH - a));
            turns.add(new ModelSkeleton.Turn(panel[1], 0F, a + b));
        }
        turns.add(new ModelSkeleton.Turn(SKIRT_BACK[0], 0F, -CROUCH_PITCH));
        turns.add(new ModelSkeleton.Turn(SKIRT_BACK[1], 0F, b));
        return turns.toArray(new ModelSkeleton.Turn[0]);
    }

    private static float length(float[] from, float[] to)
    {
        float dx = from[0] - to[0];
        float dy = from[1] - to[1];
        float dz = from[2] - to[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The four slots a character is made of, in the order they are drawn. */
    public static final String[] SLOTS = {"wear", "hair", "face", "disc"};

    private CharacterRenderer()
    {
    }

    /**
     * The two hands, which are each other's reflection.
     * <p>
     * The hand BONES, not {@code sys_hand_R} and {@code sys_disc_L}: those two
     * are mounts for particular things -- the disk sits on the left forearm --
     * and they are not a symmetric pair, so one anchor could not serve both.
     * {@code handL} and {@code handR} are, to within half a thousandth in the
     * bind pose.
     */
    private static final String HAND_RIGHT = "handR";
    private static final String HAND_LEFT = "handL";

    /**
     * Puts a held item in a hand.
     * <p>
     * The anchor is one grip mirrored, not two -- see {@link ItemAnchor}. The
     * mirror is a conjugation and not a reflection, because a reflection turns
     * the item's normals inside out; the comment on the arithmetic is below.
     */
    private static void grip(PoseStack pose, ModelSkeleton skeleton, float[] bones,
        String hand, boolean left, ItemAnchor.Kind kind)
    {
        org.joml.Matrix4f moved = skeleton.movedAt(hand, bones);
        float[] rest = skeleton.restPosition(hand);
        if(moved == null || rest == null)
        {
            return;
        }
        ItemAnchor.Grip grip = ItemAnchor.grip(kind);
        float side = left ? -1F : 1F;
        // AUTHORED GLOBALLY, CARRIED RIGIDLY -- and it takes both to be right.
        //
        // Two obvious ways to attach something to a bone, and each is wrong on
        // its own. Use the bone's FRAME and the grip inherits the bone's axes,
        // which on a DS rig are whatever the artist left them as: every number
        // in the editor then means something different depending on where the
        // arm is, and the two hands disagree whenever the animation poses them
        // differently -- which the idle does, because one arm carries a duel
        // disk. Use only the bone's POSITION and the numbers become intelligible
        // but the item stops turning with the hand, so it slides about inside it
        // as the arm swings.
        //
        // So: the grip is stated at the bone's REST position in the model's own
        // axes -- global, and the same for both hands -- and then the bone's
        // CHANGE since the bind pose is applied over it. At rest the two agree
        // exactly; in motion the item is carried as rigidly as a vertex is,
        // because it is being carried by the same matrix a vertex is.
        pose.mulPose(moved);
        pose.translate(rest[0], rest[1], rest[2]);
        // Conjugated for the left, which is vanilla's own answer in
        // ItemTransform.apply: negate the x offset and the yaw and roll, touch
        // nothing else. That is X G X, a proper rotation, so nothing is turned
        // inside out -- a reflection would place it just as well and invert
        // every normal on it.
        pose.translate(grip.x() * side, grip.y(), grip.z());
        pose.mulPose(new org.joml.Quaternionf().rotationYXZ(
            (float) Math.toRadians(grip.yaw() * side),
            (float) Math.toRadians(grip.pitch()),
            (float) Math.toRadians(grip.roll() * side)));
        // And the second turn, in the frame the first one left behind. Applied
        // after, which is the whole of what makes it local: the axes it turns
        // about are the item's, not the model's.
        //
        // Conjugated as well, and it has to be. The frame this lands in has
        // already been mirrored for a left hand, so a local turn taken raw would
        // come out the other way round and the two hands would drift apart at
        // exactly the point somebody was fine-tuning them.
        pose.mulPose(new org.joml.Quaternionf().rotationYXZ(
            (float) Math.toRadians(grip.localYaw() * side),
            (float) Math.toRadians(grip.localPitch()),
            (float) Math.toRadians(grip.localRoll() * side)));
        pose.scale(grip.scale(), grip.scale(), grip.scale());
    }

    /**
     * 26.2 resolved the item during extraction, so there is nothing to render
     * here -- only somewhere to put what has already been worked out.
     */
    private static void hold(PoseStack pose, ModelSkeleton skeleton, float[] bones,
        net.minecraft.client.renderer.item.ItemStackRenderState item, boolean right,
        SubmitNodeCollector collector, int light, ItemAnchor.Kind kind)
    {
        if(item == null || item.isEmpty())
        {
            return;
        }
        pose.pushPose();
        grip(pose, skeleton, bones, right ? HAND_RIGHT : HAND_LEFT, !right, kind);
        item.submit(pose, collector, light,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    /**
     * How long a change of locomotion takes to cross over, in seconds.
     * <p>
     * Short. This is not the sixth-of-a-second lean the one-shots use, which is
     * a monster deciding to swing; this is a pair of legs stopping, and legs
     * stop quickly. Long enough that it is a settle rather than a cut, short
     * enough that nobody would call it a glide.
     */
    private static final float FADE_SECONDS = 0.12F;

    /**
     * What each duellist was doing, and when they stopped doing it.
     * <p>
     * Keyed on the object the renderer is handed -- the player here, the render
     * state on 26.2 -- because both are kept one per entity and reused, which
     * makes them exactly the identity this needs and one the renderer is not
     * otherwise given. Weak, so a duellist who logs out takes their entry with
     * them.
     */
    private record Blend(String clip, String from, long since)
    {
    }

    private static final java.util.Map<Object, Blend> BLENDS =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * The clip to play, and how far through leaving the last one.
     * <p>
     * Called once per frame per duellist. A clip that has not changed returns a
     * finished fade, which the renderer reads as "no fade" and skips.
     */
    private static ModelHologram.Fade fade(Object who, String clip)
    {
        // 26.2 moved `Util` under `net.minecraft.util`; 1.21.1 has it at the root.
        long now = net.minecraft.util.Util.getNanos();
        Blend was = BLENDS.get(who);
        if(was == null || !was.clip().equals(clip))
        {
            // Whatever was on screen a moment ago is what this fades out of --
            // including a fade that had not finished, so changing your mind
            // mid-stride leaves from the pose you were actually in rather than
            // from the one you were heading for.
            BLENDS.put(who, new Blend(clip, was == null ? null : was.clip(), now));
            return was == null ? null : new ModelHologram.Fade(was.clip(), 0F);
        }
        if(was.from() == null)
        {
            return null;
        }
        float through = (now - was.since()) / 1.0E9F / FADE_SECONDS;
        return through >= 1F ? null : new ModelHologram.Fade(was.from(), through);
    }

    /**
     * How fast this duellist's speed decays when nobody is driving it.
     * <p>
     * Minecraft multiplies horizontal velocity by {@code friction * 0.91} every
     * tick, and takes the friction from the block returned by
     * {@code getBlockPosBelowThatAffectsMyMovement} -- so this is the engine's
     * own number by the engine's own lookup, and a modded slippery block is
     * covered without being named. Ordinary ground is 0.6, ice 0.98, slime 0.8.
     */
    private static float decay(net.minecraft.client.player.AbstractClientPlayer player)
    {
        return player.level().getBlockState(player.getBlockPosBelowThatAffectsMyMovement())
            .getBlock().getFriction() * 0.91F;
    }

    /**
     * How much of last tick's speed a duellist has to keep to be walking.
     *
     * <h2>Why the physics and not the keys</h2>
     * Coasting and walking look identical in the position, and the INPUT that
     * tells them apart exists only for the player holding the keys -- Minecraft
     * networks a sprint flag and a position, and nothing about what anybody is
     * pressing. So the question is asked of the physics instead, where the two
     * are not alike at all: a duellist who lets go decays by {@link #decay}
     * every tick, and one still walking sits at the fixed point where the
     * acceleration they add balances exactly that loss, so their speed holds.
     *
     * <h2>Why it is not one number</h2>
     * It was 0.96, chosen for ice, and applied only on ice -- which left
     * ordinary ground with no test at all and the walk running on for nearly
     * half a second after the keys came up. The decay is 0.546 on ordinary
     * ground against 0.892 on ice, so ONE constant cannot serve both: 0.96 is
     * far too twitchy for ground, where stepping up a stair dips the speed by
     * more than four per cent, and anything loose enough for ground would never
     * see a release on ice at all.
     * <p>
     * So the threshold is the midpoint of the gap in whatever regime the
     * duellist is standing in -- 0.773 on ground, 0.946 on ice, 0.864 on slime.
     * Each one is as far from "coasting" as it is from "holding speed", which is
     * the most room a single tick's evidence can be given on both sides. One
     * tick either way, everywhere, which is what makes the ground behave like
     * the ice did.
     */
    private static float holding(net.minecraft.client.player.AbstractClientPlayer player)
    {
        return (decay(player) + 1F) / 2F;
    }

    /**
     * What a duellist's speed was last tick, and what was decided from it.
     * <p>
     * Keyed weakly on the player, like {@link #BLENDS}, and stamped with the
     * tick it belongs to. Two things need that stamp: this is called once per
     * FRAME and must answer the same thing every frame within a tick, and a
     * duellist who was off screen for a while has a previous speed that is not
     * the previous tick's and cannot be compared with.
     */
    /**
     * @param holding the threshold that speed was judged against, which is a
     *                property of the SURFACE. Kept so the next tick can tell
     *                whether it is comparing like with like -- see
     *                {@link #striding}.
     */
    private record Glide(int tick, double speed, boolean striding, float holding)
    {
    }

    private static final java.util.Map<Object, Glide> GLIDES =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * Is this duellist walking, rather than being carried by what they are
     * standing on?
     * <p>
     * Asked everywhere, on every surface. It used to be asked only on slippery
     * ground, on the reasoning that friction stops a duellist quickly enough
     * elsewhere -- but "quickly" was nine ticks: a walk decaying at 0.546 takes
     * that long to fall under the "did they move at all" threshold, and those
     * nine ticks were the walk cycle visibly running on after the keys came up.
     * The gap this reads is WIDER on ordinary ground than on ice, so the surface
     * that had no test was the one where the test works best.
     */
    private static boolean striding(net.minecraft.client.player.AbstractClientPlayer player)
    {
        double dx = player.getX() - player.xOld;
        double dz = player.getZ() - player.zOld;
        double speed = Math.sqrt(dx * dx + dz * dz);
        Glide was = GLIDES.get(player);
        if(was != null && was.tick() == player.tickCount)
        {
            // Already decided this tick. Deciding again would compare this
            // tick's speed with itself and always answer "walking".
            return was.striding();
        }
        // Only the tick straight before this one can be compared with. Anything
        // older is a different journey, and the benign answer is the one that
        // was right before any of this: they are moving, so they are walking.
        float hold = holding(player);
        boolean striding;
        if(was == null || was.tick() + 1 != player.tickCount)
        {
            striding = true;
        }
        else if(Math.abs(was.holding() - hold) > 1.0E-4F)
        {
            // THE SURFACE CHANGED UNDER THEM BETWEEN THESE TWO TICKS.
            //
            // The comparison below is last tick's speed against a fraction that
            // describes how fast speed decays on the block being stood on -- and
            // on the tick a duellist steps from ground onto ice those are two
            // different regimes. `was.speed()` was produced at ground's 0.546
            // and `hold` has already become ice's 0.946, so the test demands the
            // speed hold within five per cent of a number that was never
            // governed by that rule. It fails, `striding` flips for one tick,
            // and the walk snaps to the idle and back: the stutter at the edge
            // of a frozen lake.
            //
            // So the answer is carried rather than recomputed. This is the same
            // rule the stale-glide branch above states -- only the tick straight
            // before this one can be compared with, and anything else is a
            // different journey -- applied to a change of ground rather than a
            // change of time. It costs one tick of staleness at each surface
            // change, which is the tolerance `holding` is already built around.
            striding = was.striding();
        }
        else
        {
            striding = speed >= was.speed() * hold;
        }
        GLIDES.put(player, new Glide(player.tickCount, speed, striding, hold));
        return striding;
    }

    /**
     * Is the ground moving this duellist rather than being walked on?
     * <p>
     * One question now. It was two -- slippery AND coasting -- and the first
     * half was doing nothing but exempting ordinary ground from the second.
     * <p>
     * The objection to asking it everywhere was that a duellist walking into a
     * wall would stop moving their legs while still pushing against it. That is
     * true, and it is also what a vanilla player does: {@code walkAnimation} is
     * fed the distance actually moved, so vanilla's legs stop against a wall
     * too. Matching it is the answer rather than the problem.
     * <p>
     * Public here and private on 1.21.1, and called from the EXTRACTION rather
     * than from the renderer, because by the time 26.2 draws a character the
     * player it belongs to is gone. The body is otherwise identical.
     */
    public static boolean carried(net.minecraft.client.player.AbstractClientPlayer player)
    {
        return !striding(player);
    }

    /** The idle clip for this character's skeleton, which the editor stands in. */
    public static String idle(CharacterLook look)
    {
        return (look.female() ? "fpa" : "mpa") + IDLE;
    }

    /**
     * Draws the character in place of the player.
     *
     * @param state the render state the vanilla body would have been drawn from,
     *              which is where the facing and the walk speed come from
     */
    public static void submit(AvatarRenderState state, PoseStack poseStack,
        SubmitNodeCollector collector, CharacterLook look)
    {
        ModelMesh mesh = CharacterModels.mesh(look.gender());
        if(mesh == null)
        {
            return;
        }
        // THE STACK IS NOT TURNED YET. The origin is at the player's feet --
        // the dispatcher did that -- but the body rotation is applied INSIDE
        // the submit this replaces, at the point the vanilla model is about to
        // be posed. Cancelling at its head therefore cancels the turn too, so
        // the yaw has to be handed on here or every character in the world
        // faces south while walking north.
        Identifier[] painted = new Identifier[SLOTS.length];
        int[] slots = new int[SLOTS.length];
        for(int i = 0; i < SLOTS.length; i++)
        {
            slots[i] = CharacterModels.primitive(look, SLOTS[i]);
            painted[i] = CharacterModels.texture(look, SLOTS[i]);
        }

        String moving = clipFor(state, look);
        boolean hitting = state instanceof CharacterCarrier carrier
            && carrier.dueldimension$swing() >= 0F;
        String clip = hitting ? (look.female() ? "fpa" : "mpa") + HIT : moving;
        float seconds = hitting
            ? ((CharacterCarrier) state).dueldimension$swing() : Float.NaN;

        // bodyRot, not the head's: a player's head turns to look about while
        // their body keeps walking, and it is the body the model stands on.
        //
        // The camera goes in for BOTH the origin and the reference point, which
        // reads oddly and is exactly right: the pose is already camera-relative
        // -- the dispatcher put it there -- so the translate below cancels to
        // nothing, while the light lookup gets the camera added back and
        // therefore asks the world about a world position.
        CharacterCarrier worn = (CharacterCarrier) state;
        RideAnchor.Seat seat = seat(worn.dueldimension$riding());
        ModelHologram.submit(poseStack, collector, worn.dueldimension$eye(),
            worn.dueldimension$eye(), HEIGHT, mesh,
            state.bodyRot, 0xFFFFFFFF, clip, seat.y(), seat.yaw(), seat.x(), seat.z(),
        // ONE LIGHT FOR THE WHOLE CHARACTER, and it is the light vanilla
        // would have used.
        //
        // ModelLight.standing samples a BOX and interpolates between its eight
        // corners, which is right for a dragon whose near and far side are
        // metres apart and lit differently. A duellist is 1.8 blocks of person,
        // and the box costs more than it buys: stand near a wall, or half a
        // pixel inside a doorframe, and a corner lands inside solid stone and
        // reads as pitch dark. The interpolation then drags that darkness
        // across the model, so a character standing beside a block goes grey
        // down one side.
        //
        // A vanilla player has no such gradient -- it is lit by a single packed
        // value at its own position, which the renderer is handed. Taking that
        // same value is both the fix and the parity: a character is lit exactly
        // as the player it replaces would have been, wall or no wall.
            seconds, de.cas_ual_ty.dueldimension.clientutil.model.ModelLight.flat(
                state.lightCoords),
            part ->
            {
                // Four of sixty. Anything else in the file is another
                // hairstyle or somebody else's jacket.
                for(int i = 0; i < slots.length; i++)
                {
                    if(slots[i] == part)
                    {
                        return painted[i];
                    }
                }
                return null;
            },
            moving, turns(state, mesh, seat), HIT_ARM, worn.dueldimension$backwards(),
            // Both hands, as Minecraft draws them. The vanilla layer that would
            // have done this went with the rest of the vanilla body when the
            // renderer was cancelled -- which is what takes the floating duel
            // disk away and also, until now, everything else a duellist was
            // carrying.
            (pose, skeleton, bones) ->
            {
                // The STACK decides the grip, not the resolved render state --
                // 26.2 keeps both, and only the stack knows what the item is.
                boolean tuning = net.minecraft.client.Minecraft.getInstance().gui.screen()
                    instanceof de.cas_ual_ty.dueldimension.clientutil.hub.ItemAnchorScreen;
                // While the editor is open, whatever is in the hand stands in
                // for the category being tuned. Otherwise switching the tab to
                // "Block" while holding a sword moves the gizmo and leaves the
                // sword where it was, and the numbers are being set blind --
                // which defeats a screen whose whole argument is that these are
                // values nobody can reason about and everybody can see.
                ItemAnchor.Kind shown = tuning
                    ? de.cas_ual_ty.dueldimension.clientutil.hub.ItemAnchorScreen.editing()
                    : null;
                hold(pose, skeleton, bones, state.rightHandItemState, true,
                    collector, state.lightCoords, shown != null ? shown
                        : ItemAnchor.kindOf(state.rightHandItemStack));
                hold(pose, skeleton, bones, state.leftHandItemState, false,
                    collector, state.lightCoords, shown != null ? shown
                        : ItemAnchor.kindOf(state.leftHandItemStack));
                // The handles, only while their editor is open. On EVERY
                // character rather than only the one editing them, because the
                // render state carries no identity to tell them apart -- which
                // is a difference from 1.21.1 and not worth a field, since a
                // duellist with the tuning screen open is alone with it.
                if(tuning)
                {
                    for(boolean right : new boolean[] {true, false})
                    {
                        pose.pushPose();
                        grip(pose, skeleton, bones, right ? HAND_RIGHT : HAND_LEFT, !right,
                            de.cas_ual_ty.dueldimension.clientutil.hub.ItemAnchorScreen
                                .editing());
                        AnchorGizmo.draw(pose, collector, mesh);
                        pose.popPose();
                    }
                }
            },
            fade(state, moving),
            // A tool swings all the way; a bucket does not. The stack in the
            // arm that is actually swinging decides it -- the main hand, since
            // that is the one HIT is masked onto.
            ItemAnchor.kindOf(state.rightHandItemStack) == ItemAnchor.Kind.TOOL
                ? SWING_TOOL : SWING_OTHER,
            // `clip`, not `moving`: while a strike is playing, `clip` is the
            // strike and the walk underneath it is `moving`. The rate belongs
            // to whichever cycle the looping clock is actually running, and on
            // the one-shot path it is ignored anyway.
            rateFor(clip, crouching(state)));
    }

    /**
     * Everything bent on top of the animation: the crouch, then the neck.
     * <p>
     * In that order because the second is a child of the first -- a neck turned
     * before the waist folded would pivot about where the neck used to be.
     */
    /**
     * Whether this duellist is crouching, asked in one place.
     * <p>
     * Both the fold and the walk's playback rate depend on it, and two reads of
     * the same question is one refactor away from them disagreeing about it.
     * <p>
     * A rider does not crouch: the seated clip already has the legs, and folding
     * them under a horse would take them through it.
     */
    private static boolean crouching(AvatarRenderState state)
    {
        return state.isCrouching
            && !(state instanceof CharacterCarrier carrier && carrier.dueldimension$riding());
    }

    private static ModelSkeleton.Turn[] turns(AvatarRenderState state, ModelMesh mesh,
        RideAnchor.Seat seat)
    {
        // A rider does not crouch: the seated clip already has the legs, and
        // folding them under a horse would take them through it.
        boolean crouching = crouching(state);
        ModelSkeleton.Turn[] head = look(state);
        // The lean folds at the WAIST, which is the joint the crouch already
        // bends and the joint a rider actually folds at. Tilting the whole
        // character instead would take the legs with it and lift them off the
        // saddle, which is the opposite of settling into one.
        if(seat.lean() != 0F)
        {
            ModelSkeleton.Turn over = new ModelSkeleton.Turn(WAIST, 0F, seat.lean());
            ModelSkeleton.Turn[] leaning = append(
                crouching ? crouch(mesh) : new ModelSkeleton.Turn[0], over);
            return append(leaning, head);
        }
        if(!crouching)
        {
            return head;
        }
        // The crouch first and the neck last, because they compose: a head
        // turned before the hips sank would pivot about where the neck used to
        // be, and end up looking somewhere nobody asked for.
        return append(crouch(mesh), head);
    }

    /** One array from a list of turns and some more, in that order. */
    private static ModelSkeleton.Turn[] append(ModelSkeleton.Turn[] first,
        ModelSkeleton.Turn... rest)
    {
        ModelSkeleton.Turn[] all = java.util.Arrays.copyOf(first, first.length + rest.length);
        System.arraycopy(rest, 0, all, first.length, rest.length);
        return all;
    }


    /**
     * The seat adjustment, but only while there is something to sit on.
     * <p>
     * Zero otherwise, so a duellist on foot is placed exactly where Minecraft
     * put them and none of these numbers can wander into the walk cycle. See
     * {@link RideAnchor} for why each of the five already had a mechanism
     * waiting for it.
     */
    private static RideAnchor.Seat seat(boolean riding)
    {
        return riding ? RideAnchor.seat() : new RideAnchor.Seat(0F, 0F, 0F, 0F, 0F);
    }

    /**
     * The neck, following where the player is looking.
     * <p>
     * <b>Relative to the body, not to the world.</b> A player's head yaw and
     * body yaw are two different numbers -- the body lags, and turns to catch
     * up only once the head has gone far enough -- so the difference is what the
     * neck is actually doing, and it is the difference that has to be clamped.
     * Handing the absolute yaw over would turn the head with the whole model and
     * then again on top of it.
     * <p>
     * Clamped rather than let run: see {@link #YAW_LIMIT}. Minecraft's own head
     * has no such limit because a cube has no chin.
     *
     */
    private static ModelSkeleton.Turn[] look(AvatarRenderState state)
    {
        float yaw = Math.clamp(Mth.wrapDegrees(state.yRot - state.bodyRot),
            -YAW_LIMIT, YAW_LIMIT);
        // Minecraft's pitch is positive DOWNWARD, so the ventral limit is
        // the positive end and the dorsal one the negative.
        float pitch = Math.clamp(state.xRot, -LOOK_UP, LOOK_DOWN);
        // The base first and the head second: both SET a global orientation, so
        // the second is not disturbed by the first having already moved it, and
        // the bend arrives spread over the two joints the rig provides.
        // Off leaves the neck exactly as the clip left it, which is the
        // control case: anything still crooked with this off is not the look's
        // doing. See HeadTrackingSettings.
        if(!de.cas_ual_ty.dueldimension.clientutil.HeadTrackingSettings.enabled())
        {
            return new ModelSkeleton.Turn[0];
        }
        return new ModelSkeleton.Turn[] {
            ModelSkeleton.Turn.looking(NECK_BASE, yaw * NECK_SHARE, pitch * NECK_SHARE),
            ModelSkeleton.Turn.looking(NECK, yaw, pitch),
        };
    }


    /**
     * Which clip suits what this player is doing.
     * <p>
     * The gender prefix is part of the name because the two skeletons have
     * different bone counts and each set of clips was authored for one of them —
     * see {@code nsbca.fits} for how they are told apart.
     */
    /**
     * How far a duellist must have moved in a tick to count as walking.
     * <p>
     * 1.21.1's own constant, needed here because {@link #clipOf} asks the
     * player directly where {@link #clipFor} asks the carrier -- and the two
     * have to draw the line in the same place or the sound and the picture
     * disagree about whether a step is happening at all.
     */
    private static final double MOVED = 1.0E-6D;

    /**
     * The clip this duellist is playing, asked of the PLAYER rather than of a
     * render state.
     *
     * <h2>Why this is not just clipFor</h2>
     * {@link FootSteps} runs on the client tick, and 26.2's render state does
     * not exist outside a frame -- it is extracted per frame and thrown away.
     * So the same question is asked of the player directly, which is what
     * 1.21.1's {@code clipFor} already does. The two must agree, and the rule
     * below is deliberately written in the same order as the one in
     * {@code clipFor} so that a change to either is obvious next to the other.
     */
    public static String clipOf(net.minecraft.client.player.AbstractClientPlayer player,
        CharacterLook look)
    {
        String prefix = look.female() ? "fpa" : "mpa";
        if(player.isPassenger())
        {
            return prefix + RIDE;
        }
        double dx = player.getX() - player.xOld;
        double dz = player.getZ() - player.zOld;
        if(dx * dx + dz * dz < MOVED)
        {
            return prefix + IDLE;
        }
        return prefix + (player.isSprinting() ? RUN : WALK);
    }

    /** The rate that clip is running at. See {@link #rateFor}. */
    public static float rateOf(net.minecraft.client.player.AbstractClientPlayer player,
        String clip)
    {
        return rateFor(clip, player.isCrouching() && !player.isPassenger());
    }

    /** Whether {@code clip} is one a duellist takes steps during. */
    public static boolean stepsDuring(String clip)
    {
        return clip != null && (clip.endsWith(WALK) || clip.endsWith(RUN));
    }

    private static String clipFor(AvatarRenderState state, CharacterLook look)
    {
        String prefix = look.female() ? "fpa" : "mpa";
        CharacterCarrier carrier = state instanceof CharacterCarrier got ? got : null;
        // Riding beats everything. A passenger's position changes because the
        // horse's does, so the movement test below would otherwise have them
        // walking in the saddle -- and there is a seated clip for exactly this.
        if(carrier != null && carrier.dueldimension$riding())
        {
            return prefix + RIDE;
        }
        // WHETHER THEY MOVED, not how fast the arm-swing thinks they are going;
        // see the carrier's own note. 1.21.1 reads the player's position here
        // directly, which is the same question asked where the answer is kept.
        if(carrier == null || !carrier.dueldimension$moving()
            || carrier.dueldimension$coasting())
        {
            return prefix + IDLE;
        }
        return prefix + (carrier.dueldimension$sprinting() ? RUN : WALK);
    }
}
