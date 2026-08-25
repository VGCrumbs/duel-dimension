package de.cas_ual_ty.dueldimension.clientutil.model;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;

/**
 * The bones of a model, and where they are at a given moment.
 * <p>
 * Three steps, in this order, and the order is the whole of it:
 * <ol>
 * <li>sample the animation to get each node's own translation and rotation;
 * <li>walk the hierarchy so every node's transform includes its parents';
 * <li>multiply by the inverse bind matrix, which is what turns "where this bone
 *     is now" into "how far this bone has MOVED since the pose the mesh was
 *     modelled in".
 * </ol>
 * That last step is the one that is easy to leave out and impossible to miss
 * afterwards: without it every vertex is transformed by its bone's full world
 * position rather than by its change, and the model explodes into a scatter of
 * triangles around the origin.
 * <p>
 * <b>Matrices are column-major</b>, which is glTF's layout and also JOML's, so
 * the inverse bind matrices are read straight in. Getting that backwards
 * transposes every bone, which does not throw and does not obviously look like
 * a transpose.
 */
public final class ModelSkeleton
{
    private final List<GlbModel.Node> nodes;
    private final GlbModel.Skin skin;
    private final List<GlbModel.Animation> animations;

    /** The bind matrices, unpacked once rather than per frame. */
    private final Matrix4f[] inverseBind;

    /**
     * Scratch, reused every frame.
     * <p>
     * A pose is computed sixty times a second and thrown away; allocating a
     * matrix per node per frame would hand the collector a few thousand objects
     * a second for no reason. Not thread safe, and does not need to be — posing
     * happens on the render thread.
     */
    private final Matrix4f[] local;
    private final Matrix4f[] global;
    private final boolean[] resolved;
    private final int[] parent;
    private final float[] translation;
    private final float[] rotation;
    /** The same again, for the animation being blended out of. */
    private final float[] wasTranslation;
    private final float[] wasRotation;
    private final Matrix4f[] joints;

    ModelSkeleton(GlbModel model)
    {
        this.nodes = model.nodes();
        this.skin = model.skin();
        this.animations = model.animations();

        int count = nodes.size();
        local = new Matrix4f[count];
        global = new Matrix4f[count];
        resolved = new boolean[count];
        parent = new int[count];
        translation = new float[count * 3];
        rotation = new float[count * 4];
        wasTranslation = new float[count * 3];
        wasRotation = new float[count * 4];
        for(int i = 0; i < count; i++)
        {
            local[i] = new Matrix4f();
            global[i] = new Matrix4f();
        }

        // The hierarchy is stored downward (a node lists its children), but
        // resolving a node needs its PARENT, so the edges are reversed once.
        java.util.Arrays.fill(parent, -1);
        for(int i = 0; i < count; i++)
        {
            for(int child : nodes.get(i).children())
            {
                parent[child] = i;
            }
        }

        int bones = skin == null ? 0 : skin.jointCount();
        inverseBind = new Matrix4f[bones];
        joints = new Matrix4f[bones];
        for(int i = 0; i < bones; i++)
        {
            // Column-major, 16 floats each, which is what JOML's set() reads.
            inverseBind[i] = new Matrix4f().set(
                java.util.Arrays.copyOfRange(skin.inverseBindMatrices(), i * 16, i * 16 + 16));
            joints[i] = new Matrix4f();
        }
    }

    public int jointCount()
    {
        return joints.length;
    }

    public List<GlbModel.Animation> animations()
    {
        return animations;
    }

    /** The index of the animation of that name, or -1 for none. */
    public int indexOf(String name)
    {
        if(name == null || name.isBlank())
        {
            return -1;
        }
        for(int i = 0; i < animations.size(); i++)
        {
            String declared = animations.get(i).name();
            if(name.equals(declared) || name.equals("anim " + i))
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * What Duelists of the Roses uses each animation slot for.
     * <p>
     * <b>A slot number is a fixed position, not a sequence.</b> Each monster's
     * model header carries an eight-bit mask at {@code +0x04}, and bit <i>i</i>
     * says "this monster has slot <i>i</i>". A monster that does not need one
     * simply leaves the bit clear, which is why the slots present are
     * non-contiguous — bits 1 and 3 are set on under 4% of the 683 monsters.
     * Slot 2 therefore means the same thing on every monster that has it.
     * <p>
     * <b>These three meanings come from the game's own code</b>, read out of the
     * PS2 executable and recorded in DotrAssetModder's MOT writer. The battle
     * state machine sets animations through {@code SzModel_SetAnimIntr@0x1f1550},
     * and two of its callers name their slot outright:
     * {@code SzModel_BattleSeq@0x1f0cd0} asks for slot 2 on the attacker, and
     * {@code SzModel_BattleSeq2@0x1f0e50} asks for slot 5 on the defender. Slot 0
     * is the state the machine idles in between them.
     * <p>
     * <b>Slots 1, 3, 4 and 6 are not documented anywhere</b> and are deliberately
     * not named here. Guessing from the data is exactly the trap: slot 4 is the
     * only slot that never returns to its own first pose on any of the 681
     * models that carry it, which reads as an attack — and the attack is slot 2.
     */
    public static final String IDLE = "slot_0";

    /** The attacker's animation. @see #IDLE for where this is documented. */
    public static final String ATTACK = "slot_2";

    /** The defender's, on being hit. @see #IDLE for where this is documented. */
    public static final String HURT = "slot_5";

    /**
     * What the game calls a slot, or null where nothing does.
     * <p>
     * Only the three the executable names. The editor shows this beside the raw
     * slot so that a duellist cycling through them can tell a known animation
     * from one they have to identify by watching it.
     */
    public static String meaning(String slot)
    {
        if(IDLE.equals(slot))
        {
            return "idle";
        }
        if(ATTACK.equals(slot))
        {
            return "attack";
        }
        if(HURT.equals(slot))
        {
            return "hit";
        }
        return null;
    }

    /**
     * What a monster does when it is doing nothing.
     * <p>
     * Every monster gets one. Standing in the bind pose is not a neutral choice
     * — it is the pose the mesh was rigged in, arms out, and it reads as a model
     * that has failed to load rather than as a creature at rest.
     *
     * @return {@link #IDLE}'s index; failing that the first animation the file
     *         has, so that the one monster without a slot_0 still breathes; and
     *         -1 only for a model with no animations at all
     */
    public int idleIndex()
    {
        int idle = indexOf(IDLE);
        return idle >= 0 ? idle : animations.isEmpty() ? -1 : 0;
    }

    /**
     * How long a change of animation takes to cross over, in seconds.
     * <p>
     * Six frames, which is not a guess: {@code SzModel_SetAnimIntr} exists in
     * the game solely as a thunk that supplies 6 as the third argument to the
     * routine that actually changes an animation. Duelists of the Roses does not
     * cut between animations, it blends them — so a monster asked to attack
     * leans into the swing from wherever its idle had got to, rather than
     * snapping to the attack's first frame.
     * <p>
     * Six frames of the 30-per-second the keyframes are authored at, measured
     * across all 683 monsters, which makes it a fifth of a second.
     */
    public static final float BLEND_SECONDS = 6F / 30F;

    /**
     * Poses the skeleton part way between two animations.
     * <p>
     * Blended as TRANSLATION AND ROTATION, before the hierarchy is walked —
     * never as finished matrices. Averaging two bone matrices componentwise does
     * not produce a rotation at all: it produces something that shears and
     * shrinks in the middle of the blend, worst at exactly the halfway point
     * where it is most visible. Rotations slerp; positions lerp; then the
     * skeleton is resolved once from the result.
     *
     * @param mix 1 for entirely {@code animation}, 0 for entirely
     *            {@code previous}
     */
    public Matrix4f[] pose(int animation, float seconds, int previous, float previousSeconds,
        float mix)
    {
        if(previous < 0 || previous >= animations.size() || mix >= 1F)
        {
            return pose(animation, seconds);
        }
        // The outgoing pose first, into its own buffers.
        rest();
        if(previous >= 0)
        {
            sample(animations.get(previous), previousSeconds);
        }
        System.arraycopy(translation, 0, wasTranslation, 0, translation.length);
        System.arraycopy(rotation, 0, wasRotation, 0, rotation.length);

        rest();
        if(animation >= 0 && animation < animations.size())
        {
            sample(animations.get(animation), seconds);
        }

        float towards = Math.clamp(mix, 0F, 1F);
        Quaternionf from = new Quaternionf();
        Quaternionf to = new Quaternionf();
        for(int i = 0; i < nodes.size(); i++)
        {
            for(int c = 0; c < 3; c++)
            {
                float was = wasTranslation[i * 3 + c];
                translation[i * 3 + c] = was + (translation[i * 3 + c] - was) * towards;
            }
            from.set(wasRotation[i * 4], wasRotation[i * 4 + 1], wasRotation[i * 4 + 2],
                wasRotation[i * 4 + 3]);
            to.set(rotation[i * 4], rotation[i * 4 + 1], rotation[i * 4 + 2],
                rotation[i * 4 + 3]);
            from.slerp(to, towards);
            rotation[i * 4] = from.x;
            rotation[i * 4 + 1] = from.y;
            rotation[i * 4 + 2] = from.z;
            rotation[i * 4 + 3] = from.w;
        }
        return resolveInto();
    }

    /**
     * Poses the skeleton and returns one matrix per bone.
     * <p>
     * The returned array is REUSED on the next call. It is consumed immediately
     * by the skinning that follows, so copying it would be a copy nobody reads.
     *
     * @param animation an index into {@link #animations()}, or -1 for the rest
     *                  pose
     * @param seconds   how far into the animation, wrapped by the caller or not
     *                  — this clamps rather than wraps, so a caller that wants a
     *                  loop asks for one
     */
    public Matrix4f[] pose(int animation, float seconds)
    {
        rest();
        if(animation >= 0 && animation < animations.size())
        {
            sample(animations.get(animation), seconds);
        }
        return resolveInto();
    }

    /**
     * Every node back to what the file says it is.
     * <p>
     * So that a node no channel touches keeps its authored place rather than
     * collapsing to the origin.
     */
    private void rest()
    {
        for(int i = 0; i < nodes.size(); i++)
        {
            GlbModel.Node node = nodes.get(i);
            translation[i * 3] = node.translation()[0];
            translation[i * 3 + 1] = node.translation()[1];
            translation[i * 3 + 2] = node.translation()[2];
            rotation[i * 4] = node.rotation()[0];
            rotation[i * 4 + 1] = node.rotation()[1];
            rotation[i * 4 + 2] = node.rotation()[2];
            rotation[i * 4 + 3] = node.rotation()[3];
        }
    }

    /** Walks the hierarchy over whatever is in the TRS buffers, and skins. */
    private Matrix4f[] resolveInto()
    {
        for(int i = 0; i < nodes.size(); i++)
        {
            local[i].identity()
                .translate(translation[i * 3], translation[i * 3 + 1], translation[i * 3 + 2])
                .rotate(new Quaternionf(rotation[i * 4], rotation[i * 4 + 1],
                    rotation[i * 4 + 2], rotation[i * 4 + 3]));
            resolved[i] = false;
        }
        for(int i = 0; i < nodes.size(); i++)
        {
            resolve(i);
        }

        for(int i = 0; i < joints.length; i++)
        {
            // global * inverseBind: where the bone is now, times the undo of
            // where it was when the mesh was built.
            joints[i].set(global[skin.joints()[i]]).mul(inverseBind[i]);
        }
        return joints;
    }

    /**
     * The posed matrices as flat floats, which is what skinning wants.
     * <p>
     * A copy, unlike {@link #pose}: the geometry lambdas run LATER, after the
     * scratch has been posed again for whatever is drawn next, so what they read
     * has to be theirs. Thirty-three bones is 528 floats — small enough that
     * copying it once a frame costs nothing, and the alternative is a model that
     * wears another model's pose.
     */
    public static float[] flatten(Matrix4f[] posed)
    {
        float[] out = new float[posed.length * 16];
        for(int i = 0; i < posed.length; i++)
        {
            posed[i].get(out, i * 16);
        }
        return out;
    }

    /**
     * Moves one vertex by the four bones that own it.
     * <p>
     * Linear blend skinning: each bone says where the vertex would be if it
     * alone controlled it, and the answer is the weighted average of the four.
     * <p>
     * <b>Column-major indexing</b>, matching {@link Matrix4f#get(float[], int)}:
     * the element at column {@code c}, row {@code r} lives at {@code c * 4 + r},
     * so the translation is the LAST four floats and not every fourth one.
     * Transposing this is the classic way to get a model that is recognisably
     * itself and yet completely wrong.
     *
     * @param translate true for a position, false for a normal — a direction
     *                  must not pick up the bone's translation, or every normal
     *                  ends up pointing at wherever that bone happens to be
     * @param out       receives x, y, z
     */
    public static void apply(float[] matrices, int[] joints, float[] weights, int vertex,
        float x, float y, float z, boolean translate, float[] out)
    {
        out[0] = 0F;
        out[1] = 0F;
        out[2] = 0F;
        float total = 0F;
        for(int bone = 0; bone < 4; bone++)
        {
            float weight = weights[vertex * 4 + bone];
            if(weight == 0F)
            {
                continue;
            }
            int joint = joints[vertex * 4 + bone];
            if(joint < 0 || (joint + 1) * 16 > matrices.length)
            {
                continue;
            }
            int m = joint * 16;
            float tx = translate ? matrices[m + 12] : 0F;
            float ty = translate ? matrices[m + 13] : 0F;
            float tz = translate ? matrices[m + 14] : 0F;
            out[0] += weight * (matrices[m] * x + matrices[m + 4] * y + matrices[m + 8] * z + tx);
            out[1] += weight * (matrices[m + 1] * x + matrices[m + 5] * y + matrices[m + 9] * z + ty);
            out[2] += weight * (matrices[m + 2] * x + matrices[m + 6] * y + matrices[m + 10] * z + tz);
            total += weight;
        }
        if(total <= 0F)
        {
            // Unweighted, which happens to stray vertices in a converted model.
            // Left where it was modelled rather than collapsed to the origin,
            // because a stray vertex at the origin drags a triangle across the
            // whole model and is far more visible than one that simply does not
            // animate.
            out[0] = x;
            out[1] = y;
            out[2] = z;
        }
        else if(Math.abs(total - 1F) > 1.0e-4F)
        {
            // The spec says weights sum to one; exporters do not always agree,
            // and a set summing to 0.5 would shrink that vertex halfway to the
            // origin.
            out[0] /= total;
            out[1] /= total;
            out[2] /= total;
        }

        if(!translate)
        {
            // A blended direction is not a unit direction, and the correction
            // above does not make it one: that divides by the weight sum, which
            // is already 1.0 in a well-formed file. Two bones ninety degrees
            // apart at half weight each average to a normal 0.707 long, and
            // nothing downstream fixes it — the vertex format takes the normal
            // as given. Left alone it shades a bending joint as a dark band that
            // slides along the limb as the animation plays, which reads as bad
            // lighting rather than as a bug in the skinning.
            float length = (float)Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
            if(length > 1.0e-6F)
            {
                out[0] /= length;
                out[1] /= length;
                out[2] /= length;
            }
        }
    }

    /**
     * A node's transform including every parent above it.
     * <p>
     * Memoised by {@code resolved}: a skeleton is a spine with limbs hanging off
     * it, so resolving each bone from scratch would walk that spine once per
     * bone. The recursion is bounded by the DEPTH of the rig rather than by its
     * size — a dozen levels for a dragon — which is why it is allowed to be
     * recursion at all.
     */
    private void resolve(int node)
    {
        if(resolved[node])
        {
            return;
        }
        int up = parent[node];
        if(up < 0)
        {
            global[node].set(local[node]);
        }
        else
        {
            resolve(up);
            global[node].set(global[up]).mul(local[node]);
        }
        resolved[node] = true;
    }

    /** Writes one animation's channels over the rest pose. */
    private void sample(GlbModel.Animation animation, float seconds)
    {
        for(GlbModel.Channel channel : animation.channels())
        {
            GlbModel.Sampler sampler = animation.samplers().get(channel.sampler());
            float[] times = sampler.times();
            if(times.length == 0 || channel.node() >= nodes.size())
            {
                continue;
            }
            int stride = sampler.stride();
            float[] values = sampler.values();

            // Which pair of keyframes surrounds this moment, and how far between
            // them we are.
            int next = 0;
            while(next < times.length && times[next] < seconds)
            {
                next++;
            }
            int a;
            int b;
            float mix;
            if(next <= 0)
            {
                a = b = 0;
                mix = 0F;
            }
            else if(next >= times.length)
            {
                a = b = times.length - 1;
                mix = 0F;
            }
            else
            {
                a = next - 1;
                b = next;
                float span = times[b] - times[a];
                // Two keyframes at the same instant are a step, not a ramp, and
                // dividing by their zero span would be a NaN through the whole
                // skeleton.
                mix = span <= 0F ? 0F : (seconds - times[a]) / span;
            }

            if(channel.path() == GlbModel.Path.TRANSLATION && stride >= 3)
            {
                for(int c = 0; c < 3; c++)
                {
                    float from = values[a * stride + c];
                    float to = values[b * stride + c];
                    translation[channel.node() * 3 + c] = from + (to - from) * mix;
                }
            }
            else if(channel.path() == GlbModel.Path.ROTATION && stride >= 4)
            {
                // Spherically, not linearly. A quaternion lerped component by
                // component and renormalised takes a shortcut through the
                // inside of the sphere: the bone still arrives, but it swings
                // at the wrong speed on the way, which reads as a limb that
                // snaps rather than turns.
                Quaternionf from = new Quaternionf(values[a * stride], values[a * stride + 1],
                    values[a * stride + 2], values[a * stride + 3]);
                Quaternionf to = new Quaternionf(values[b * stride], values[b * stride + 1],
                    values[b * stride + 2], values[b * stride + 3]);
                from.slerp(to, mix);
                rotation[channel.node() * 4] = from.x;
                rotation[channel.node() * 4 + 1] = from.y;
                rotation[channel.node() * 4 + 2] = from.z;
                rotation[channel.node() * 4 + 3] = from.w;
            }
        }
    }
}
