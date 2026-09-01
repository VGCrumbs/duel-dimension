package de.cas_ual_ty.dueldimension.clientutil.character;

import de.cas_ual_ty.dueldimension.clientutil.model.ModelMesh;
import de.cas_ual_ty.dueldimension.clientutil.model.ModelSkeleton;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * When each foot lands, read out of the animation itself.
 *
 * <h2>Why the sound had to move</h2>
 * Minecraft does not time a footstep against a footstep. {@code Entity.move}
 * accumulates distance travelled and fires a sound every time that total passes
 * {@code nextStep} — so the interval is right on average and every individual
 * step is wherever the counter happened to land. With a cube for a body nobody
 * can tell. With a character whose feet are visible, a sound that arrives while
 * the foot is still in the air is the sort of thing that reads as "off" long
 * before anyone works out why.
 *
 * <h2>The contacts are measured, not authored</h2>
 * Nothing in the DS data marks a footfall, so this finds them: the skeleton is
 * posed across the whole clip, each foot's height is tracked, and the frames
 * where a foot is at the bottom of its travel are the frames it is standing on.
 * That works for any clip, including ones nobody has looked at, and it cannot
 * drift out of step with an animation the way a hand-written table would.
 *
 * @see #contacts the detection, and why it is a threshold rather than a minimum
 */
public final class FootSteps
{
    /** The two joints that touch the ground. Both rigs have both. */
    private static final String[] FEET = {"footL", "footR"};

    /**
     * How many times the clip is sampled to find the contacts.
     * <p>
     * The clips are keyed at 30 a second and the longest is 120 frames, so this
     * is a little over one sample per frame on the longest and several on the
     * short ones. Fine enough that a contact lands within a frame of the truth,
     * and it is paid once per clip for the life of the process.
     */
    private static final int SAMPLES = 180;

    /**
     * How far above its lowest point a foot still counts as down, as a fraction
     * of that foot's total travel in the clip.
     *
     * <h2>A band, not a minimum</h2>
     * A planted foot does not touch its lowest point for one instant and leave;
     * it sits at the bottom for a third of the cycle while the body travels over
     * it. So the height curve has a flat-bottomed trough, not a spike, and
     * hunting for a local minimum inside that plateau finds whichever sample
     * noise happened to dip lowest — a different frame each time the sampling
     * changes, and sometimes several per step.
     * <p>
     * Taking the FIRST sample of each run below a band is stable, and it is also
     * the moment that matters: the strike, not the middle of the stance.
     */
    private static final float CONTACT_BAND = 0.18F;

    /** One entry per (model, clip); the answer never changes for a given pair. */
    private static final Map<String, float[]> CACHE = new HashMap<>();

    private FootSteps()
    {
    }

    /**
     * The times within {@code clip}, in seconds, at which a foot lands.
     * <p>
     * Both feet together and sorted, because the caller does not care which foot
     * it was — a step is a step, and the sound is the same one.
     *
     * @return the contact times, or an empty array if the clip is unknown or has
     *         no feet to speak of
     */
    public static float[] contacts(ModelMesh mesh, String clip)
    {
        if(mesh == null || clip == null)
        {
            return new float[0];
        }
        ModelSkeleton skeleton = mesh.skeleton();
        if(skeleton == null)
        {
            return new float[0];
        }
        int index = skeleton.indexOf(clip);
        if(index < 0 || index >= skeleton.animations().size())
        {
            return new float[0];
        }
        String key = System.identityHashCode(mesh) + "/" + clip;
        float[] cached = CACHE.get(key);
        if(cached != null)
        {
            return cached;
        }
        float[] found = measure(skeleton, index, clip);
        CACHE.put(key, found);
        return found;
    }

    /** Poses the clip across its whole length and reads the feet off it. */
    private static float[] measure(ModelSkeleton skeleton, int index, String clip)
    {
        float duration = skeleton.animations().get(index).duration();
        if(duration <= 0F)
        {
            return new float[0];
        }
        List<Float> times = new ArrayList<>();
        for(String foot : FEET)
        {
            int joint = skeleton.nodeIndex(foot);
            if(joint < 0)
            {
                continue;
            }
            float[] height = new float[SAMPLES];
            for(int i = 0; i < SAMPLES; i++)
            {
                Matrix4f[] posed = skeleton.pose(index, duration * i / SAMPLES);
                // m31 is the translation's y: where this joint has ended up.
                height[i] = posed != null && joint < posed.length ? posed[joint].m31() : 0F;
            }
            collect(height, duration, times);
        }
        float[] out = new float[times.size()];
        for(int i = 0; i < out.length; i++)
        {
            out[i] = times.get(i);
        }
        java.util.Arrays.sort(out);
        return out;
    }

    /**
     * The first sample of each run spent below the contact band.
     * <p>
     * <b>Wrapped, because a cycle has no beginning.</b> A walk that happens to
     * be exported with a foot already planted at frame 0 has its first run
     * straddling the loop point, and treating the array as a line rather than a
     * ring would report a contact at time zero that is really the middle of a
     * step that started before it.
     */
    private static void collect(float[] height, float duration, List<Float> into)
    {
        float low = Float.POSITIVE_INFINITY;
        float high = Float.NEGATIVE_INFINITY;
        for(float value : height)
        {
            low = Math.min(low, value);
            high = Math.max(high, value);
        }
        float travel = high - low;
        if(travel <= 1.0e-4F)
        {
            // A foot that never leaves the ground is not stepping: this is the
            // idle, or a clip where the legs do not move. No contacts rather
            // than one every sample.
            return;
        }
        float limit = low + travel * CONTACT_BAND;
        int count = height.length;
        for(int i = 0; i < count; i++)
        {
            boolean down = height[i] <= limit;
            boolean wasDown = height[(i - 1 + count) % count] <= limit;
            if(down && !wasDown)
            {
                into.add(duration * i / count);
            }
        }
    }
}
