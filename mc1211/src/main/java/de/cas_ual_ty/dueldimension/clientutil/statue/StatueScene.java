package de.cas_ual_ty.dueldimension.clientutil.statue;

/**
 * Where everything on the Dawn of Destiny reward screen goes.
 * <p>
 * All of this is in the disc's own coordinates -- <b>Y-up</b>, the same space the
 * {@code .igb} files are authored in -- so the numbers below can be checked
 * against the game's data rather than against a Blender scene.
 *
 * <h2>The podiums</h2>
 * {@code menu_m_03.igb} carries eight {@code igTransform}s with real 4x4
 * matrices, stored <b>row-vector</b> (the translation is in the last ROW, so a
 * point is {@code v * M}). Three of them place the podium:
 * <pre>
 * cyl2                     translate (0,0,-15)          -> (      0, 0,  -15)
 * cyl2_2 under null1_1_1   rotY 120 degrees             -> (-12.99, 0,  7.5)
 * cyl2_3 under null1_1_2   rotY 240 degrees             -> ( 12.99, 0,  7.5)
 * </pre>
 * Three podiums on a 15-unit circle, 120 degrees apart. The near one is at -Z,
 * which is why the middle podium is the largest on screen.
 * <p>
 * The other three transforms -- {@code torus4}, {@code torus4_1},
 * {@code torus4_2} -- are a centre ring at Y -7.9 scaled 2x. That is NOT part of
 * a podium and is not drawn here.
 * <p>
 * A caution for anyone re-deriving this: walking the file's scene graph returns
 * every node as a root, so parent links never resolve and an accumulated matrix
 * always comes out identity. The geometry objects carry the same names as their
 * transforms, so the pairing is by NAME.
 */
public final class StatueScene
{
    /** Radius of the circle the three podiums stand on. */
    public static final float PODIUM_RADIUS = 15.0F;

    /**
     * How far the crown sinks into the podium.
     * <p>
     * <b>This number is in no file.</b> {@code menu_m_10} has no
     * {@code igTransform} at all, and neither model records its height relative
     * to the other -- the game applies that offset at runtime. At the authored
     * coordinates (a lift of 0) the crown's cap floats visibly above the podium
     * rim, so this is fitted against a reference screenshot and is the one value
     * here that is a judgement rather than a readout.
     */
    public static float crownLift = 0.0F;

    /**
     * The statues face OUTWARD from the circle, and their authored facing is the
     * inward one, so each needs half a turn about its own vertical axis after the
     * podium's rotation is applied.
     * <p>
     * <b>A flat 180 for all three, deliberately.</b> The scene builder gives each
     * god its own angle -- {@code push 2.96706 / 3.31613 / 2.61799} radians at VA
     * 0x0010121B/0x0010122F/0x00101243, i.e. 170, 190 and 150 degrees, into the
     * set-rotation-Y at 0x001E4020 -- and those numbers are certain. Substituting
     * them made the statues sit visibly wrong, which means the frame they are
     * expressed in is not the frame {@code extraYawDegrees} applies them in. Until
     * that mapping is worked out, the fitted value that renders correctly wins
     * over the readout that does not. See the note on {@link #CAMERA_EYE}.
     */
    public static final float STATUE_YAW_DEGREES = 180.0F;

    /**
     * The rig's own half-turn, read at VA 0x00101A5C ({@code push 3.14159} into
     * the same set-rotation-Y). <b>Not applied here</b>: the camera below sits on
     * the opposite side of the scene from the game's, and a mirrored camera
     * absorbs this turn exactly, so applying both would double it. Recorded
     * because it is the other half of why the camera constants differ.
     */
    public static final float RIG_YAW_DEGREES = 180.0F;

    /** Which god stands on which podium, matched to the reference screenshot. */
    public enum Slot
    {
        /** (0, 0, -15) -- nearest the camera, renders centre. */
        OBELISK(0.0F, "obelisk"),
        /** (-12.99, 0, 7.5) -- renders right. */
        RA(120.0F, "ra"),
        /** (12.99, 0, 7.5) -- renders left. */
        SLIFER(240.0F, "slifer");

        private final float yaw;
        private final String mesh;

        Slot(float yaw, String mesh)
        {
            this.yaw = yaw;
            this.mesh = mesh;
        }

        /** Degrees about Y that carry the base placement onto this podium. */
        public float yaw()
        {
            return yaw;
        }

        public String mesh()
        {
            return mesh;
        }

        /** Centre of this podium in disc coordinates. */
        public float[] centre()
        {
            double r = Math.toRadians(yaw);
            double sin = Math.sin(r);
            double cos = Math.cos(r);
            // rotY applied to (0, 0, -PODIUM_RADIUS)
            return new float[] {(float)(-PODIUM_RADIUS * sin), 0F, (float)(-PODIUM_RADIUS * cos)};
        }
    }

    // ---- the camera ----
    //
    // The values below are SOLVED from a reference screenshot. The game's own
    // are known and are NOT these:
    //
    //   eye (0, 9, 59), at (0, 4, 0), up (0, 1, 0), 45 degrees HORIZONTAL,
    //   aspect 4:3, near 1, far 10000  ->  34.52 degrees vertical
    //
    // built on the stack at VA 0x00025881-0x000258C1 and passed to 0x00049EC0.
    // Those are readouts and they are not in doubt as NUMBERS. Rendering with
    // them produced a visibly wrong picture -- statues mis-oriented and the
    // arrangement rotated a step -- where the solved values produce a correct
    // one, so something between the two frames is still unaccounted for. The
    // candidates, none of them checked yet:
    //
    //   - the argument order of 0x00049EC0 was read as (eye, at, up) from the
    //     order the pointers are pushed, which is an inference, not a signature;
    //   - the scene builder writes 4.0 into three objects at 0x001011B2/DA/202
    //     before calling 0x001E3BA0, once per statue. If that is a SCALE, the
    //     game's world is four times the size of the authored .igb coordinates
    //     these constants are in, and no camera position read from it can be
    //     used here unscaled;
    //   - the rig's rotY = pi (see RIG_YAW_DEGREES) and this camera being on the
    //     far side are two half-turns that have to be accounted for exactly once.
    //
    // Until one of those is settled, the fitted camera stays, because it is the
    // one that demonstrably renders the reference. A readout that does not
    // reproduce is a lead, not an answer.

    /** Eye position in disc coordinates. */
    public static final float[] CAMERA_EYE = {0.0F, 9.40F, -75.25F};
    /**
     * What it looks at. Raised from the podium-top centroid the solve used --
     * the statues are the subject, not the cone below -- then lowered again to 6
     * once the framing was checked against a capture of the real screen, which
     * sits the podiums lower and lets the message window overlap them.
     */
    public static final float[] CAMERA_TARGET = {0.0F, 6.0F, 0.0F};
    /**
     * Vertical field of view, degrees -- the value the photogrammetry solve
     * produced. It is NOT what gets used: at this distance 20 degrees crops
     * the statues off the top, which is the first thing rendering it showed.
     * {@link StatueCamera#fitVerticalFov} derives the real one from
     * {@link #FRAME_BOX} so it also adapts to the window's aspect. Kept
     * because it is what the solve said, and that is worth not losing.
     */
    public static float cameraVerticalFov = 20.01F;
    /**
     * The part of the scene that is meant to be on screen.
     * <p>
     * NOT the whole model. The podium's tapering cone runs 20 units below its
     * skirt and is cut off by the floor in the original -- fitting the camera to
     * the full content pushes the field of view from 29 degrees to 58 and leaves
     * the statues tiny in the middle of the frame.
     * <p>
     * Measured content is x[-22.6, 22.3] y[-20.0, 18.3] z[-24.3, 18.0]; the only
     * change here is clipping the bottom.
     *
     * {@code {minX, minY, minZ, maxX, maxY, maxZ}}
     */
    public static final float[] FRAME_BOX = {-22.6F, -4.0F, -24.3F, 22.3F, 18.3F, 18.0F};


    // ---- lighting, READ OUT OF THE SCENE BUILDER ----
    //
    // This was a fitted rig for a long time, on the belief that the lights were
    // built in code and therefore unreachable. They are built in code -- and the
    // code is readable. The scene builder creates FOUR of them, at
    // VA 0x00101521 / 0x00101620 / 0x0010171F / 0x0010181C, and each one is set
    // up the same way immediately before: four floats written to [esp+0x54..0x60]
    // and passed to the light's vtable slots
    //
    //     +0x68 ambient    +0x6c diffuse    +0x70 specular    +0x78 direction
    //
    // Reading these needs care, because the stack slots PERSIST: a block that
    // writes only three of them inherits the fourth from the block before, and a
    // slot set to 0.0 looks like an absent write unless zeros are printed too.
    // Both of those caught this out once.
    //
    // What the four are, verbatim:
    //
    //   1  @0x0010144D  diffuse (1.4, 1.4, 1.0)  ambient (0.5, 0.5, 0.0)
    //                   specular (0.8, 1.0, 0.3)  direction (3.4, -1.3, -1.5)
    //   2  @0x0010154C  diffuse (0.0, 0.1, 0.1)  ambient (0, 0, 0)
    //                   specular (0.0, 0.2, 0.0)  direction (-1, -1, -1)
    //   3  @0x0010164B  diffuse (0.8, 0.8, 0.8)  ambient (0, 0, 0)
    //                   specular (0, 0, 0)        direction (1, -1, -1)
    //   4  @0x00101766  diffuse (0, 0, 0)         ambient (0, 0, 0)
    //                   specular (0, 0, 0)        direction (-1, -1, -1)
    //
    // SLOT 0x68 IS DIFFUSE, NOT AMBIENT. It was read the other way round first,
    // on the assumption that the setters run in GL's ambient-diffuse-specular
    // order, and the result was a scene lit only by a 0.5 yellow -- far too
    // dark, which is how the mistake showed. Swapped, the rig makes immediate
    // sense: a bright warm key at (1.4, 1.4, 1.0), a grey fill at 0.8 from the
    // opposite side, a yellow AMBIENT of (0.5, 0.5, 0.0), and the yellow-green
    // specular that puts the rim on the edges. Three lights carrying zero
    // diffuse and a nonzero ambient would have been a strange thing to author;
    // three carrying real diffuse and one carrying the ambient is ordinary.
    //
    // THE YELLOW IS LIGHT 1. Its diffuse (0.5, 0.5, 0.0) is pure yellow and its
    // specular (0.8, 1.0, 0.3) is a yellow-green -- that is the warm rim along
    // the models' edges, and it was never something to be fitted by eye.
    // Lights 3 and 4 carry no diffuse or specular at all; light 3 is a flat grey
    // ambient and light 4 contributes nothing.
    //
    // DIRECTIONS ARE TURNED HALF A CIRCLE. The whole scene here is the game's
    // rotated 180 degrees about Y -- our podiums are at the authored coordinates
    // where the game's have the rig's rotY = pi applied, and our camera sits at
    // -Z where the game's sits at +Z. So a direction read from the game becomes
    // (x, y, z) -> (-x, y, -z) here. They are also NEGATED, because the disc
    // stores the direction the light TRAVELS and the shading below wants the
    // direction TOWARD the light: every one of the four points downward in y,
    // which only makes sense as travel.
    //
    // Ambients are summed and multiplied by the material's own ambient, which
    // for these models is (0.5, 0.5, 0.5). The four sum to (2.2, 2.3, 1.9), so
    // that product is over 1 before anything else is added -- which cannot be
    // what the hardware did or the models would be flat white. Something about
    // the +0x68 slot is therefore still not understood, and AMBIENT_SCALE below
    // is the one number here that is a judgement rather than a readout.

    /** One of the disc's four lights. Colours verbatim; direction transformed. */
    public record Light(float[] direction, float[] ambient, float[] diffuse, float[] specular)
    {
    }

    /**
     * The rig, in this file's coordinates and pointing toward the light.
     * <p>
     * Light 4 is omitted: it is all zeroes and contributes nothing.
     */
    public static final Light[] LIGHTS = {
        // 1 -- edited in the in-game light editor.
        new Light(new float[] {-2.900F, 0.800F, 1.500F},
            new float[] {0.400F, 0.380F, 0.000F},
            new float[] {-0.020F, 0.000F, 0.000F},
            new float[] {0.800F, 1.000F, 0.300F}),
        // 2 -- edited in the in-game light editor.
        new Light(new float[] {1.000F, 1.000F, -1.000F},
            new float[] {1.460F, 0.780F, 1.100F},
            new float[] {0.560F, 2.460F, 0.500F},
            new float[] {0.020F, 0.000F, 0.000F}),
        // 3 -- edited in the in-game light editor.
        new Light(new float[] {0.000F, 0.050F, -0.150F},
            new float[] {0.100F, -1.600F, 0.180F},
            new float[] {0.100F, 1.000F, 0.000F},
            new float[] {0.000F, 0.080F, 0.000F}),
    };

    /**
     * What the summed light ambient is multiplied by before it reaches the
     * material.
     * <p>
     * 1.0 -- no fudge. With slot 0x68 read as diffuse, the only light carrying
     * an ambient is the first, at (0.5, 0.5, 0.0), and against a material
     * ambient of (0.5, 0.5, 0.5) that comes to a yellow floor of (0.25, 0.25, 0)
     * -- which is a sensible base rather than something that needed scaling
     * down. The 0.42 that used to be here existed only to rescue the wrong
     * reading of the slots.
     */
    public static float ambientScale = 1.440F;

    /**
     * What every light's specular is multiplied by.
     * <p>
     * Not a readout -- the disc's specular colours go in verbatim and this sits
     * on top of them. The rim they produce is the right COLOUR at 1.0 and too
     * faint to read as the original's, which has a hard bright edge along the
     * top and outside of every model.
     * <p>
     * Why it needs a boost at all is worth saying rather than hiding: the
     * specular here is computed against a FIXED view direction rather than a
     * per-vertex one (see {@code StatueRenderer.shade}), which spreads the
     * highlight out and flattens its peak. A wider, flatter highlight at the
     * same energy reads as dimmer. This is the cheap correction for that, and
     * the honest fix is a per-vertex view vector.
     */
    public static float specularBoost = 3.200F;

    /**
     * How glossy the plates the gods stand on are.
     * <p>
     * {@code menu_m_10}, the disc on top of each podium -- NOT the podium below
     * it, which stays matte gold. The disc's own material makes the two look
     * alike; in the original the plate reads as polished metal against the
     * stone, and the difference is a stronger, tighter highlight rather than a
     * different colour.
     * <p>
     * Neither number is a readout. {@code igMaterialAttr} gives one material per
     * part and this raises it, so these are a judgement -- which is why they are
     * mutable and appear in the in-game light editor alongside the rig.
     */
    public static float crownShine = 0.800F;
    /** The exponent: higher is a tighter, more mirror-like glint. */
    public static float crownShininess = 90.000F;

    /**
     * Below 1.0 on purpose: this CROPS INTO the frame box rather than leaving a
     * border, which is what the original does -- the podiums run off the bottom
     * of the screen and behind the message window rather than sitting whole in
     * view.
     * <p>
     * 0.70 lands the lens at 20.0 degrees at 16:9, matched against a capture of
     * the real screen. Fitting the box with air around it (1.04, which was the
     * first attempt) gives 29 degrees and leaves everything looking distant.
     */
    public static final float FRAME_MARGIN = 0.70F;

    /**
     * The disc's own proportions. Left at 1.0 deliberately.
     * <p>
     * This was 1.15 for a while, to stop the gods looking dwarfed -- but they
     * only looked dwarfed because the CAMERA was wrong, sitting too far back and
     * too wide. Enlarging the models was treating the symptom, and against a
     * capture of the real screen it over-corrects: there the podiums are broad
     * and the gods stand on them at their authored size.
     * <p>
     * The scale still applies about the model's own origin and BEFORE the
     * platform offset, so changing it cannot sink a statue through its platform.
     */
    public static final float STATUE_SCALE = 1.0F;


    private StatueScene()
    {
    }
}
