package de.cas_ual_ty.dueldimension.clientutil.statue;

/**
 * A general pinhole camera, projecting disc coordinates to screen pixels.
 * <p>
 * {@link de.cas_ual_ty.dueldimension.clientutil.FieldLayout.Projection} does the
 * same job for the duel field, but it collapses to a closed form because the
 * table is flat and its view matrix reduces to a function of one coordinate.
 * Nothing like that is available here, so this carries an actual basis.
 * <p>
 * <b>Why the projection is done here at all.</b> A GUI pass projects
 * orthographically, and a perspective matrix pushed onto a {@code PoseStack} will
 * not save it: {@code Matrix4f} transforms a position affinely and discards w, so
 * the perspective divide never happens. The divide has to be ours. This is the
 * same reason {@code FieldQuad.drawProjected} exists.
 * <p>
 * Coordinates in are the disc's: <b>Y-up</b>. Coordinates out are GUI pixels
 * relative to the region's top-left, <b>y down</b>.
 */
public final class StatueCamera
{
    private final float eyeX;
    private final float eyeY;
    private final float eyeZ;
    /** Camera basis: right, up, forward. Orthonormal. */
    private final float[] right;
    private final float[] up;
    private final float[] forward;
    /** tan(vfov/2), and the same scaled by aspect for x. */
    private final float tanY;
    private final float tanX;
    private final float halfWidth;
    private final float halfHeight;

    /**
     * @param width  region width in GUI pixels
     * @param height region height in GUI pixels
     */
    public StatueCamera(float[] eye, float[] target, float verticalFovDegrees,
        float width, float height)
    {
        this.eyeX = eye[0];
        this.eyeY = eye[1];
        this.eyeZ = eye[2];

        float[] f = normalise(target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]);
        // right = normalise(forward x worldUp), worldUp = (0,1,0), which reduces
        // to (-fz, 0, fx). Degenerate only if the camera looks straight down, and
        // the reward screen's ~5 degree pitch is nowhere near that.
        //
        // Sanity check on the handedness this produces, since getting it backwards
        // silently mirrors the whole screen: forward is very nearly +Z here, so
        // right comes out as -X. Ra sits at x = -12.99 and therefore lands on the
        // RIGHT, Slifer at x = +12.99 on the LEFT -- which is the reference shot.
        float[] r = normalise(-f[2], 0F, f[0]);
        float[] u = {
            r[1] * f[2] - r[2] * f[1],
            r[2] * f[0] - r[0] * f[2],
            r[0] * f[1] - r[1] * f[0]};

        this.forward = f;
        this.right = r;
        this.up = u;
        this.halfWidth = width / 2F;
        this.halfHeight = height / 2F;
        this.tanY = (float)Math.tan(Math.toRadians(verticalFovDegrees) / 2.0);
        this.tanX = tanY * (height <= 0F ? 1F : width / height);
    }

    /**
     * Projects one point.
     *
     * @param out receives {@code {screenX, screenY, depth}}; depth is the
     *            camera-space distance along the view axis, positive in front
     * @return false when the point is at or behind the eye plane, in which case
     *         {@code out} is untouched and the caller must drop the triangle
     */
    public boolean project(float x, float y, float z, float[] out)
    {
        float vx = x - eyeX;
        float vy = y - eyeY;
        float vz = z - eyeZ;
        float depth = vx * forward[0] + vy * forward[1] + vz * forward[2];
        if(depth <= 0.0001F)
        {
            return false;
        }
        float cx = vx * right[0] + vy * right[1] + vz * right[2];
        float cy = vx * up[0] + vy * up[1] + vz * up[2];
        float ndcX = cx / (depth * tanX);
        float ndcY = cy / (depth * tanY);
        out[0] = halfWidth + ndcX * halfWidth;
        // NDC +1 is the top of the frame; GUI y grows downwards.
        out[1] = halfHeight - ndcY * halfHeight;
        out[2] = depth;
        return true;
    }

    /** Camera-space depth alone, for sorting without a full projection. */
    public float depth(float x, float y, float z)
    {
        return (x - eyeX) * forward[0] + (y - eyeY) * forward[1] + (z - eyeZ) * forward[2];
    }


    /**
     * Chooses a vertical FOV that fits a world-space box, from a fixed eye.
     * <p>
     * The camera solved off the reference screenshot puts the podiums in roughly
     * the right relationship but frames far too tightly -- the statues run off the
     * top. That is unsurprising: the solve had three eyeballed measurements
     * against three unknowns, so it reproduced them exactly and proved nothing.
     * <p>
     * The perspective is the part worth keeping. How near/far podiums compare in
     * size depends ONLY on the eye position, not on the field of view, so moving
     * the eye would change the composition while widening the lens does not.
     * The eye therefore stays where the solve put it and the FOV is derived from
     * what actually has to be on screen.
     *
     * @param margin 1.0 fits exactly; above that leaves a border
     */
    public static float fitVerticalFov(float[] eye, float[] target, float[] box,
        float aspect, float margin)
    {
        float[] f = normalise(target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]);
        float[] r = normalise(-f[2], 0F, f[0]);
        float[] u = {
            r[1] * f[2] - r[2] * f[1],
            r[2] * f[0] - r[0] * f[2],
            r[0] * f[1] - r[1] * f[0]};

        float worst = 0.0001F;
        for(int corner = 0; corner < 8; corner++)
        {
            float x = ((corner & 1) == 0 ? box[0] : box[3]) - eye[0];
            float y = ((corner & 2) == 0 ? box[1] : box[4]) - eye[1];
            float z = ((corner & 4) == 0 ? box[2] : box[5]) - eye[2];
            float depth = x * f[0] + y * f[1] + z * f[2];
            if(depth <= 0.0001F)
            {
                continue;
            }
            float cx = x * r[0] + y * r[1] + z * r[2];
            float cy = x * u[0] + y * u[1] + z * u[2];
            // A corner is inside the frame when |cy|/depth <= tan(v/2) and
            // |cx|/depth <= tan(v/2)*aspect, so the horizontal requirement is
            // divided back by the aspect to be compared in the same units.
            worst = Math.max(worst, Math.abs(cy) / depth);
            worst = Math.max(worst, Math.abs(cx) / depth / Math.max(aspect, 0.0001F));
        }
        double fov = 2.0 * Math.atan(worst * margin);
        return (float)Math.toDegrees(Math.min(Math.max(fov, Math.toRadians(5.0)),
            Math.toRadians(150.0)));
    }

    private static float[] normalise(float x, float y, float z)
    {
        float length = (float)Math.sqrt(x * x + y * y + z * z);
        if(length < 1.0E-6F)
        {
            return new float[] {0F, 0F, 1F};
        }
        return new float[] {x / length, y / length, z / length};
    }
}
