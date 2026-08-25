package de.cas_ual_ty.dueldimension.clientutil.overworld;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Where a point on the board lands on the screen.
 * <p>
 * The exact reverse of the formula the picker uses to turn a cursor into a ray,
 * which is the whole reason it is written out here rather than taken from the
 * game's own matrices: the two have to agree. A label drawn somewhere other
 * than where clicking would find the card it belongs to is worse than no label,
 * and deriving both from one piece of arithmetic is the only way to be sure
 * they cannot drift.
 * <p>
 * Lifted out of {@code BoardPointerScreen}, which used it to anchor a card's
 * menu to the card. The screen still calls it; it no longer owns it.
 */
public final class BoardProjection
{
    private BoardProjection()
    {
    }

    /**
     * A world point in gui coordinates, or null if it is behind the camera.
     *
     * @param screenW the width the caller is drawing in -- gui units, not
     *                pixels, since that is what everything on screen is placed
     *                with
     */
    public static double[] project(Vec3 world, int screenW, int screenH)
    {
        Minecraft client = Minecraft.getInstance();
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 delta = world.subtract(camera.position());
        Vec3 look = viewVector(camera.xRot(), camera.yRot());
        Vec3 right = viewVector(0F, camera.yRot() + 90F);
        Vec3 up = viewVector(camera.xRot() - 90F, camera.yRot());

        double along = delta.dot(look);
        // Behind the eye, or exactly level with it: there is no point on the
        // screen for either, and dividing by it would put one anywhere at all.
        if(along <= 1e-4D)
        {
            return null;
        }
        double half = Math.tan(Math.toRadians(camera.getFov()) / 2D);
        double aspect = (double)screenW / Math.max(1, screenH);
        double ndcX = delta.dot(right) / along / (aspect * half);
        double ndcY = delta.dot(up) / along / half;
        return new double[] {(ndcX + 1D) * screenW / 2D, (1D - ndcY) * screenH / 2D};
    }

    /** A unit vector for a pitch and a yaw, in Minecraft's own convention. */
    public static Vec3 viewVector(float pitch, float yaw)
    {
        float pitchRadians = pitch * ((float)Math.PI / 180F);
        float yawRadians = -yaw * ((float)Math.PI / 180F);
        float cosYaw = net.minecraft.util.Mth.cos(yawRadians);
        float sinYaw = net.minecraft.util.Mth.sin(yawRadians);
        float cosPitch = net.minecraft.util.Mth.cos(pitchRadians);
        float sinPitch = net.minecraft.util.Mth.sin(pitchRadians);
        return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }
}
