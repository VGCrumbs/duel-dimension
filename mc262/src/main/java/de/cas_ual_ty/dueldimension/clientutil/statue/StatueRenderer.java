package de.cas_ual_ty.dueldimension.clientutil.statue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.cas_ual_ty.dueldimension.clientutil.ScreenUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the reward screen's models into a picture-in-picture region.
 *
 * <h2>How this gets to draw at all</h2>
 * A GUI can only blit axis-aligned rectangles.
 * {@link de.cas_ual_ty.dueldimension.clientutil.BoardPip} is the way out: it
 * opens a real render pass over a rectangle and hands back a {@code PoseStack}
 * and a {@code SubmitNodeCollector}, which is what
 * {@code submitCustomGeometry} needs. That pass projects orthographically, so
 * the perspective divide is done here in {@link StatueCamera} and what reaches
 * the buffer is already flat screen pixels.
 *
 * <h2>Depth</h2>
 * This used to emit {@code z = 0} for every vertex and rely entirely on a
 * painter's algorithm: triangles sorted back to front within a part, parts
 * submitted far to near. That is not enough, and the way it failed -- a far
 * crown drawn over a near podium -- was blamed on Minecraft merging draws by
 * {@code RenderType}. <b>That was wrong.</b> The merge is real
 * ({@code RenderTypeFeatureRenderer$Group.getOrAddDraw}) but it is scoped to
 * one group, and {@code SubmitNodeStorage.order(int)} puts every
 * {@code order()} value in its own collection, so the ladder here already
 * kept the parts apart. The real fault was that no per-part sort of any
 * granularity can order INTERPENETRATING geometry -- and a crown sunk into a
 * podium interpenetrates by construction.
 * <p>
 * The pass has had a working depth buffer the whole time.
 * {@code PictureInPictureRenderer.prepareTexturesAndProjection} allocates and
 * binds a {@code depthTexture} and sets up
 * {@code Projection.setupOrtho(-1000, 1000, w, h, invertY)}. So real depth is
 * emitted now and the sort is only a tie-breaker.
 * <p>
 * <b>The sign is not a guess.</b> {@code setupOrtho} ends in
 * {@code Matrix4f.setOrtho(0, w, h, 0, -1000, 1000, ...)}, which is
 * right-handed -- a LARGER pose-space z gives a SMALLER depth value. The
 * picture-in-picture base then applies {@code poseStack.scale(s, s, -s)},
 * negating z on the way through. And the test is GREATER_THAN_OR_EQUAL
 * against a buffer cleared to 0.0, so the larger depth value wins. The two
 * negations cancel: <b>nearer geometry must be emitted with a LARGER z</b>,
 * which is what {@link #DEPTH_SPAN} arranges.
 * <p>
 * This currently projects and sorts EVERY FRAME -- roughly 20k triangles for the
 * full screen. The camera does not move, so the whole thing is cacheable on the
 * view, and that is the obvious optimisation if it ever shows up in a profile.
 * It is not done yet, and saying so is cheaper than pretending otherwise.
 *
 * <h2>Quads, not triangles</h2>
 * Entity render types use a QUADS vertex format. Each triangle is therefore
 * emitted as four vertices with the last repeated -- the standard degenerate
 * quad. Emitting three would silently consume the next triangle's first vertex
 * and shear the whole mesh.
 */
public final class StatueRenderer
{
    private static final org.apache.logging.log4j.Logger LOGGER =
        org.apache.logging.log4j.LogManager.getLogger();

    /**
     * Logs one line per part on the next draw, then stops.
     * <p>
     * A screen that renders nothing gives no information at all -- it looks the
     * same whether the meshes failed to load, the projection put everything off
     * screen, the cull ate every face, or the pipeline dropped the draw. This
     * says which.
     */
    public static boolean diagnose;

    private static final int FULL_BRIGHT = 0xF000F0;

    /**
     * The depth band the scene is mapped into, in pose-space units before the
     * picture-in-picture's own scale.
     *
     * <h2>Which end is near, and how that was settled</h2>
     * <b>Near is {@link #DEPTH_NEAR}, the SMALL end.</b> That is the opposite of
     * what reading the class files says, and the class files were wrong twice.
     * <p>
     * The derivation went: {@code Projection.setupOrtho} ends in
     * {@code Matrix4f.setOrtho(0, w, h, 0, -1000, 1000, ...)}, which is
     * right-handed, so a larger pose-space z gives a smaller depth; the
     * picture-in-picture applies {@code poseStack.scale(s, s, -s)}, negating z on
     * the way through; and the test is GREATER_THAN_OR_EQUAL against a buffer
     * cleared to 0.0, so the larger depth wins. Two negations cancel, therefore
     * nearer needs the larger z. It was checked twice and came out the same both
     * times, and in the client it renders inside out -- the far podium drawn over
     * the near one, statues behind their own platforms.
     * <p>
     * So one of those three links is false. The likeliest is the last: that
     * {@code breezeWind}'s pipeline does not use {@code DepthStencilState.DEFAULT}
     * and its test is not GEQUAL at all. That has NOT been checked, and until it
     * is, this constant is set from what the client actually draws rather than
     * from what the bytecode appears to say. A rendering fact the screen
     * disagrees with is not a fact.
     * <p>
     * Both ends are positive and small on purpose. The picture-in-picture's scale
     * is {@code guiScale * state.scale()} and it multiplies z, so a large value
     * at a large gui scale falls out of the ortho's [-1000, 1000] and the
     * geometry vanishes rather than mis-sorting -- and it would only do it at
     * some gui scales, which is the worst way for a bug to behave.
     */
    private static final float DEPTH_NEAR = 1F;
    private static final float DEPTH_SPAN = 50F;

    /**
     * Whether to emit real depth. <b>On.</b>
     * <p>
     * This was off for a while after a first attempt appeared to make the
     * layering worse. That reading does not survive scrutiny: depth was turned
     * on in the same build that swapped the camera for a set of values that
     * turned out to be in an unresolved coordinate frame, so the picture it was
     * judged on had the statues mis-oriented and the whole arrangement rotated a
     * step. Depth never got a fair test, and the sign was re-derived twice from
     * the class files and came out the same both times.
     * <p>
     * It is needed. Back-face culling makes a hollow model opaque, but it cannot
     * ORDER the faces that survive, and a painter's algorithm sorted by triangle
     * centroid gets that wrong wherever one triangle is much larger than its
     * neighbours -- which is exactly the podium, whose broad top plate has a
     * centroid far behind the small spikes standing on it. No sort of any
     * granularity fixes that; only per-pixel depth does.
     */
    public static boolean useDepth = true;

    /** Scratch, reused per vertex so a frame does not allocate per triangle. */
    private final float[] projected = new float[3];
    private final float[] world = new float[3];

    /**
     * How glossy one placed mesh is, over and above its own material.
     *
     * @param specular  multiplies the specular term. 1 leaves the disc's own
     *                  value alone.
     * @param shininess overrides the exponent, which is what decides whether a
     *                  highlight is a broad sheen or a tight glint. Zero or less
     *                  keeps the material's own.
     */
    public record Finish(float specular, float shininess)
    {
        public static final Finish PLAIN = new Finish(1F, 0F);
    }

    /** One mesh placed in the scene, ready to draw. */
    public record Placed(StatueMesh mesh, float yawDegrees, float offsetX, float offsetY,
        float offsetZ, float extraYawDegrees, float scale, Finish finish)
    {
        /**
         * @param yawDegrees      the podium's own rotation about Y
         * @param extraYawDegrees turned about the model's OWN centre afterwards,
         *                        which is how the statues face outward
         * @param scale           about the model's own origin, applied BEFORE the
         *                        offset so a scaled statue still stands on its
         *                        platform instead of sinking through it
         */
        public Placed
        {
            scale = scale <= 0F ? 1F : scale;
            finish = finish == null ? Finish.PLAIN : finish;
        }

        /** Unscaled and unpolished, which is most of them. */
        public Placed(StatueMesh mesh, float yawDegrees, float offsetX, float offsetY,
            float offsetZ, float extraYawDegrees)
        {
            this(mesh, yawDegrees, offsetX, offsetY, offsetZ, extraYawDegrees, 1F,
                Finish.PLAIN);
        }

        /** Scaled but unpolished. */
        public Placed(StatueMesh mesh, float yawDegrees, float offsetX, float offsetY,
            float offsetZ, float extraYawDegrees, float scale)
        {
            this(mesh, yawDegrees, offsetX, offsetY, offsetZ, extraYawDegrees, scale,
                Finish.PLAIN);
        }

        /** Unscaled, with a finish of its own -- the plates the gods stand on. */
        public Placed(StatueMesh mesh, float yawDegrees, float offsetX, float offsetY,
            float offsetZ, float extraYawDegrees, Finish finish)
        {
            this(mesh, yawDegrees, offsetX, offsetY, offsetZ, extraYawDegrees, 1F, finish);
        }
    }

    /**
     * Draws a list of placed meshes, nearest last.
     * <p>
     * Each part becomes one submission, ordered so that farther groups are laid
     * down first. Callers pass the list already in back-to-front group order;
     * within a group the natural order is podium, crown, statue.
     */
    public void draw(PoseStack poseStack, SubmitNodeCollector collector, StatueCamera camera,
        List<Placed> placed, int order)
    {
        // Pass one: project everything, and find the depth range while doing it.
        // The range has to be known before ANY vertex is emitted, because the
        // mapping into the pass's depth buffer is relative to it -- which is why
        // this cannot stay a single loop that submits as it goes.
        List<Batch> batches = new ArrayList<>();
        float near = Float.MAX_VALUE;
        float far = -Float.MAX_VALUE;
        for(Placed item : placed)
        {
            if(item.mesh() == null)
            {
                continue;
            }
            for(StatueMesh.Part part : item.mesh().parts())
            {
                List<Face> faces = buildFaces(camera, item, part);
                if(faces.isEmpty())
                {
                    continue;
                }
                for(Face face : faces)
                {
                    for(int c = 0; c < 3; c++)
                    {
                        near = Math.min(near, face.z()[c]);
                        far = Math.max(far, face.z()[c]);
                    }
                }
                batches.add(new Batch(part.texture(), faces));
            }
        }

        // Pass two: turn camera depth into pass depth in place, then submit.
        float span = far - near;
        for(Batch batch : batches)
        {
            for(Face face : batch.faces())
            {
                float[] z = face.z();
                for(int c = 0; c < 3; c++)
                {
                    // NEAR gets the SMALL value. See DEPTH_NEAR.
                    z[c] = span > 1.0E-4F
                        ? DEPTH_NEAR + (DEPTH_SPAN - DEPTH_NEAR) * ((z[c] - near) / span)
                        : DEPTH_NEAR;
                }
            }
        }

        int layer = order;
        for(Batch batch : batches)
        {
            submit(poseStack, collector, batch, layer++);
        }
        // One frame's worth, then quiet. Left on would be a line per part per
        // frame, which is a log nobody can read and a hitch of its own.
        diagnose = false;
    }

    /** One part's worth of projected geometry, waiting for the depth range. */
    private record Batch(Identifier texture, List<Face> faces)
    {
    }

    private void submit(PoseStack poseStack, SubmitNodeCollector collector, Batch batch, int layer)
    {
        List<Face> faces = batch.faces();
        Identifier texture = batch.texture();
        // breezeWind, for the reasons FieldQuad already documents: it is
        // NO_CARDINAL_LIGHTING, so vertex colour passes straight through, and its
        // cull is off.
        //
        // Both matter here. An entity type applies directional lighting off the
        // vertex normal, and these models come out muddy and dark under it -- the
        // disc lit them with fixed-function Blinn-Phong, not Minecraft's block
        // lighting, so any of Minecraft's shading is wrong by construction.
        // And a GPU cull that disagrees with our winding removes exactly the faces
        // the software sort was going to keep, which renders pure black and looks
        // like nothing ran at all. That was the first bug on this screen.
        collector.order(layer).submitCustomGeometry(poseStack,
            net.minecraft.client.renderer.rendertype.RenderTypes.breezeWind(texture, 0F, 0F),
            (pose, buffer) ->
            {
                for(Face face : faces)
                {
                    emit(buffer, pose, face);
                }
            });
    }

    /**
     * Projects, culls and sorts one part.
     * <p>
     * Back-face culling is done on the SIGN OF THE PROJECTED AREA rather than on
     * the vertex normals. The two disagree on these models -- the exporter welds
     * on the exact vertex tuple, so a shared vertex carries one normal for
     * several faces -- and the screen-space winding is what actually decides
     * whether a face is turned away.
     */
    private List<Face> buildFaces(StatueCamera camera, Placed item, StatueMesh.Part part)
    {
        int triangles = part.triangleCount();
        List<Face> faces = new ArrayList<>(triangles);
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float[] positions = part.positions();
        float[] uvs = part.uvs();
        int[] indices = part.indices();

        double yaw = Math.toRadians(item.yawDegrees());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double extra = Math.toRadians(item.extraYawDegrees());
        double sinE = Math.sin(extra);
        double cosE = Math.cos(extra);

        float[] sx = new float[3];
        float[] sy = new float[3];
        float[] depth = new float[3];
        float[] u = new float[3];
        float[] v = new float[3];
        int[] lit = new int[3];
        float[] normals = part.normals();
        StatueMesh.Material material = part.material();

        for(int t = 0; t < triangles; t++)
        {
            boolean visible = true;
            for(int c = 0; c < 3; c++)
            {
                int index = indices[t * 3 + c];
                place(positions, index, item, sinE, cosE, sin, cos);
                if(!camera.project(world[0], world[1], world[2], projected))
                {
                    visible = false;
                    break;
                }
                sx[c] = projected[0];
                sy[c] = projected[1];
                depth[c] = projected[2];
                u[c] = uvs[index * 2];
                v[c] = uvs[index * 2 + 1];
                lit[c] = shade(normals, index, material, item.finish(),
                    sinE, cosE, sin, cos);
            }
            if(!visible)
            {
                continue;
            }
            // Signed area in SCREEN space, which is what decides whether a face
            // is turned away. Not culled here -- the sign that means "facing us"
            // is not knowable in advance (see below), so both are kept and one
            // set is dropped once the part is complete.
            float area = (sx[1] - sx[0]) * (sy[2] - sy[0])
                - (sx[2] - sx[0]) * (sy[1] - sy[0]);
            for(int c = 0; c < 3; c++)
            {
                minX = Math.min(minX, sx[c]);
                maxX = Math.max(maxX, sx[c]);
                minY = Math.min(minY, sy[c]);
                maxY = Math.max(maxY, sy[c]);
            }
            faces.add(new Face(sx.clone(), sy.clone(), depth.clone(), u.clone(), v.clone(),
                lit.clone(), (depth[0] + depth[1] + depth[2]) / 3F, area));
        }
        faces = cullBackFaces(faces);
        // Back to front: the farthest is laid down first and everything nearer
        // paints over it.
        faces.sort((a, b) -> Float.compare(b.depth(), a.depth()));
        if(diagnose)
        {
            LOGGER.info(
                "statue part={} tris={} kept={} yaw={} off=({},{},{}) screen x[{}..{}] y[{}..{}]",
                part.name(), triangles, faces.size(), Math.round(item.yawDegrees()),
                Math.round(item.offsetX()), Math.round(item.offsetY()),
                Math.round(item.offsetZ()),
                Math.round(minX), Math.round(maxX), Math.round(minY), Math.round(maxY));
        }
        return faces;
    }

    /**
     * Model space to disc space, into {@link #world}.
     * <p>
     * Order matters and is not commutative: the model turns about its OWN centre
     * first (the outward-facing flip), then it is offset onto its podium, then
     * the whole thing is carried round the circle by the podium's yaw. Applying
     * the podium yaw before the offset would swing the model off its podium.
     */
    private void place(float[] positions, int index, Placed item,
        double sinE, double cosE, double sin, double cos)
    {
        float scale = item.scale();
        float mx = positions[index * 3] * scale;
        float my = positions[index * 3 + 1] * scale;
        float mz = positions[index * 3 + 2] * scale;

        double rx = mx * cosE + mz * sinE;
        double rz = -mx * sinE + mz * cosE;

        double ox = rx + item.offsetX();
        double oy = my + item.offsetY();
        double oz = rz + item.offsetZ();

        world[0] = (float)(ox * cos + oz * sin);
        world[1] = (float)oy;
        world[2] = (float)(-ox * sin + oz * cos);
    }


    /**
     * Fixed-function Blinn-Phong for one vertex, packed as ARGB.
     *
     * <p>This is the disc's own lighting model, not Minecraft's. The material
     * values come straight out of {@code igMaterialAttr} -- shininess from slot
     * 4, diffuse 5, ambient 6, specular 8 -- and the lights are the fitted rig in
     * {@link StatueScene}.
     *
     * <p>The normal is rotated by the SAME turns as the position, and in the same
     * order, or the lighting slides across the model as the carousel turns.
     * Translation is deliberately not applied: a normal is a direction.
     *
     * <p>The half-vector uses a fixed view direction rather than a per-vertex one.
     * The camera is 75 units from a scene 45 across, so the view barely diverges
     * across it, and a constant is both cheaper and steadier than recomputing it
     * for 20,000 vertices a frame.
     */
    private static int shade(float[] normals, int index, StatueMesh.Material material,
        Finish finish, double sinE, double cosE, double sin, double cos)
    {
        float nx = normals[index * 3];
        float ny = normals[index * 3 + 1];
        float nz = normals[index * 3 + 2];

        double rx = nx * cosE + nz * sinE;
        double rz = -nx * sinE + nz * cosE;
        double wx = rx * cos + rz * sin;
        double wy = ny;
        double wz = -rx * sin + rz * cos;
        double length = Math.sqrt(wx * wx + wy * wy + wz * wz);
        if(length > 1.0E-6)
        {
            wx /= length;
            wy /= length;
            wz /= length;
        }

        float[] out = new float[3];
        float[] ka = material.ambient();
        float[] kd = material.diffuse();
        float[] ks = material.specular();

        // Fixed-function sums every light's ambient and multiplies the total by
        // the material's own, so this is one product rather than one per light.
        for(StatueScene.Light light : StatueScene.LIGHTS)
        {
            for(int c = 0; c < 3; c++)
            {
                out[c] += ka[c] * light.ambient()[c] * StatueScene.ambientScale;
            }
        }
        for(StatueScene.Light light : StatueScene.LIGHTS)
        {
            // The finish rides on top of the disc's own material rather than
            // replacing it: a plain mesh passes 1 and 0 and comes out exactly as
            // it did before this existed.
            addLight(out, wx, wy, wz, light.direction(), light.diffuse(),
                light.specular(), kd, ks,
                finish.shininess() > 0F ? finish.shininess() : material.shininess(),
                finish.specular());
        }

        return ScreenUtil.colour(Math.min(1F, out[0]), Math.min(1F, out[1]),
            Math.min(1F, out[2]), 1F);
    }

    /**
     * One directional light's diffuse and specular contribution, accumulated.
     * <p>
     * The light carries a SEPARATE diffuse and specular colour, as the disc's do
     * -- three of its four lights have a diffuse of pure black and exist only for
     * their ambient or specular, which a single colour per light cannot express.
     */
    private static void addLight(float[] out, double nx, double ny, double nz,
        float[] dir, float[] lightDiffuse, float[] lightSpecular, float[] kd,
        float[] ks, float shininess, float gloss)
    {
        double ll = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        if(ll < 1.0E-6)
        {
            return;
        }
        double lx = dir[0] / ll;
        double ly = dir[1] / ll;
        double lz = dir[2] / ll;
        double ndotl = nx * lx + ny * ly + nz * lz;
        if(ndotl <= 0.0)
        {
            return;
        }
        for(int c = 0; c < 3; c++)
        {
            out[c] += (float)(kd[c] * lightDiffuse[c] * ndotl);
        }
        if(shininess > 0F)
        {
            // Half-vector against a fixed view axis; see the note on shade().
            double hx = lx;
            double hy = ly;
            double hz = lz - 1.0;
            double hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
            if(hl > 1.0E-6)
            {
                double ndoth = (nx * hx / hl) + (ny * hy / hl) + (nz * hz / hl);
                if(ndoth > 0.0)
                {
                    double spec = Math.pow(ndoth, shininess);
                    for(int c = 0; c < 3; c++)
                    {
                        out[c] += (float)(ks[c] * lightSpecular[c] * spec
                            * StatueScene.specularBoost * gloss);
                    }
                }
            }
        }
    }

    /**
     * One triangle, already flat and already lit, emitted as a degenerate quad.
     * <p>
     * {@code z} holds CAMERA depth when the face is built and PASS depth after
     * {@link #draw} has rescaled it. It is mutated in place rather than copied
     * into a second array: there are 20,000 of these a frame and the two
     * meanings never overlap in time.
     */
    private record Face(float[] x, float[] y, float[] z, float[] u, float[] v, int[] colour,
        float depth, float area)
    {
    }

    /**
     * Drops the faces pointing away from the camera.
     *
     * <h2>Why the sign is measured and not chosen</h2>
     * Screen y runs DOWNWARD, which flips the sign of a projected-area test
     * against the y-up intuition, and the exporter's winding is not guaranteed
     * either. Picking a sign by reasoning has been wrong here before: the first
     * attempt culled the visible half and rendered the screen black.
     * <p>
     * So it is worked out from the geometry instead. For a closed shell seen
     * from outside, the faces pointing at the camera are on average NEARER than
     * the ones pointing away -- the front of a cylinder is in front of its back.
     * Comparing the mean depth of the positive-area faces against the mean depth
     * of the negative-area ones therefore says which set is the front, whatever
     * the winding convention is, and it says it per part so a part exported with
     * the opposite winding still comes out right.
     * <p>
     * If the two means are too close to separate -- a flat sheet seen edge on,
     * where there is no front and back -- nothing is culled, because on a flat
     * part there is nothing to gain and a wrong guess would delete half of it.
     * <p>
     * This is what stops the far wall of a podium showing through the near one.
     * It is not an optimisation; without a depth buffer it is the only thing
     * that makes a hollow model opaque.
     */
    private static List<Face> cullBackFaces(List<Face> faces)
    {
        double posDepth = 0.0;
        double negDepth = 0.0;
        int pos = 0;
        int neg = 0;
        for(Face face : faces)
        {
            if(face.area() > 0F)
            {
                posDepth += face.depth();
                pos++;
            }
            else if(face.area() < 0F)
            {
                negDepth += face.depth();
                neg++;
            }
        }
        if(pos == 0 || neg == 0)
        {
            // Single-sided: every face winds the same way, so there is no back
            // half to identify and nothing to drop.
            return faces;
        }
        double posMean = posDepth / pos;
        double negMean = negDepth / neg;
        // No exemption for parts whose two halves sit at nearly the same depth.
        // There was one, on the reasoning that a flat sheet has no back to
        // remove -- but that is exactly backwards. The podium's top plate IS
        // such a part: a disc whose top and underside are all but coincident, so
        // the exemption fired and BOTH were kept, and the underside showed
        // through the top with the two z-fighting along the way. That was the
        // artefact circled in the reference.
        //
        // A part that genuinely has no back at all -- single-sided geometry --
        // never reaches here: all its faces wind the same way, so either pos or
        // neg is zero and the early return above has already kept everything.
        boolean keepPositive = posMean < negMean;
        List<Face> kept = new ArrayList<>(Math.max(pos, neg));
        for(Face face : faces)
        {
            if(keepPositive ? face.area() > 0F : face.area() < 0F)
            {
                kept.add(face);
            }
        }
        return kept;
    }

    private static void emit(VertexConsumer buffer, PoseStack.Pose pose, Face face)
    {
        vertex(buffer, pose, face, 0);
        vertex(buffer, pose, face, 1);
        vertex(buffer, pose, face, 2);
        // The fourth is the third again. An entity format is QUADS; three
        // vertices would eat the next triangle's first corner.
        vertex(buffer, pose, face, 2);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Face face, int i)
    {
        buffer.addVertex(pose, face.x()[i], face.y()[i], useDepth ? face.z()[i] : 0F)
            // The lighting is HERE, in the vertex colour, because breezeWind is
            // NO_CARDINAL_LIGHTING and passes colour through untouched. That is
            // what lets the disc's own fixed-function model be reproduced exactly
            // instead of approximated with Minecraft's block lighting.
            .setColor(face.colour()[i])
            .setUv(face.u()[i], face.v()[i])
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT)
            // Unused by this pipeline, but the format requires a value and an
            // unset element reads whatever was left in the buffer.
            .setNormal(0F, 0F, 1F);
    }
}
