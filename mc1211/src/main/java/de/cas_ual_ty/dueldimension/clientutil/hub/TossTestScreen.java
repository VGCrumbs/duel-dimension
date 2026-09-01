package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DdSounds;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.BoardPip;
import de.cas_ual_ty.dueldimension.clientutil.model.GlbModel;
import de.cas_ual_ty.dueldimension.clientutil.model.ModelHologram;
import de.cas_ual_ty.dueldimension.clientutil.model.ModelMesh;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import de.cas_ual_ty.dueldimension.compat.SubmitNodeCollector;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Throws the coin and rolls the die, for looking at them.
 *
 * <h2>Development only</h2>
 * Reached with {@code ]}, and it exists because the coin and the die are ported
 * assets with motion of their own — a 2.6 second toss and a 1.8 second roll —
 * and the only way to know they came across correctly is to watch them. The
 * models load from the mod's resources, so what this shows is exactly what a
 * duel would show.
 *
 * <h2>The result is decided before the animation, not after</h2>
 * Both clips end with the prop settled, so the outcome has to be known when the
 * throw starts or the settling sound would announce a face the model is not
 * showing. That is the same order a duel uses: the server decides, and the
 * animation is a way of saying so.
 */
public class TossTestScreen extends Screen
{
    /**
     * Every outcome this screen has produced, per prop, oldest first.
     * <p>
     * <b>On screen because "that does not look random" cannot be answered by
     * reading the generator.</b> A fair coin gives five in a row about once
     * every thirty-two throws, and a person watching remembers the runs and not
     * the throws between them. Arguing that from the source is unconvincing and
     * -- if something really is stuck -- wrong. A tally answers it with counts.
     */
    private final Map<String, java.util.List<Integer>> history = new HashMap<>();

    /** What the exporter names the single animation in each file. */
    private static final String CLIP = "Motion";

    /**
     * A prop: the geometry the GPU draws, and the file it came from.
     * <p>
     * <b>Both, because these models carry no skin.</b> {@code ModelSkeleton}
     * exists to move vertices through joints, and a mesh with no skin gets no
     * skeleton at all -- so {@code ModelHologram}'s animation argument does
     * nothing here, however well named the clip is. What these files animate is
     * the single NODE they sit on, which is a transform around the whole mesh
     * rather than a deformation of it. That is read off the {@link GlbModel} and
     * applied as a matrix below.
     */
    private record Prop(GlbModel model, ModelMesh mesh)
    {
    }

    /** Baked once and kept: baking registers textures, which cannot be undone. */
    private static final Map<String, Prop> MESHES = new HashMap<>();

    /**
     * Where a throw's outcome comes from.
     * <p>
     * {@code ThreadLocalRandom} rather than a {@code new Random()} of its own:
     * one is seeded per screen from the clock, and a screen opened and thrown
     * from immediately is the case most likely to draw a poorly separated seed.
     * This one is seeded from a shared generator instead and costs nothing.
     * <p>
     * A fair coin does repeat -- five in a row is one run in thirty-two, which
     * turns up often enough to notice -- so this is removing a doubt rather
     * than fixing a proven bias.
     */
    private static java.util.Random random()
    {
        return java.util.concurrent.ThreadLocalRandom.current();
    }

    /** Which prop is in the air, or null between throws. */
    private String showing;
    /** When the current throw started, in milliseconds. */
    private long startedAt;
    /** What it is going to land on, decided at the moment of the throw. */
    private int result;
    /** Set once the settling sound has played, so it plays once. */
    private boolean settled;

    public TossTestScreen()
    {
        super(Component.literal("Coin and Dice"));
    }

    /**
     * The model, baked on first use.
     * <p>
     * Returns null rather than throwing if the resource is missing: this is a
     * debug screen, and a missing model should show an empty stage with a
     * message rather than take the client down.
     */
    private static Prop prop(String name)
    {
        if(name == null)
        {
            return null;
        }
        return MESHES.computeIfAbsent(name, key ->
        {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID,
                "models/" + key + ".glb");
            try(InputStream stream = net.minecraft.client.Minecraft.getInstance()
                .getResourceManager().getResourceOrThrow(id).open())
            {
                GlbModel model = GlbModel.load(stream.readAllBytes());
                return new Prop(model, ModelMesh.bake(model, key));
            }
            catch(Exception missing)
            {
                DuelDimension.warn("[toss] could not load " + id + ": " + missing);
                return null;
            }
        });
    }

    /** The one animation each file carries, or null. */
    private static GlbModel.Animation clip(Prop prop)
    {
        return prop == null || prop.model().animations().isEmpty() ? null
            : prop.model().animations().get(0);
    }

    /** The sampler driving one path, or null where the clip does not touch it. */
    private static GlbModel.Sampler channel(GlbModel.Animation clip, GlbModel.Path path)
    {
        for(GlbModel.Channel each : clip.channels())
        {
            if(each.path() == path)
            {
                return clip.samplers().get(each.sampler());
            }
        }
        return null;
    }

    /**
     * One sampler read at a time, interpolated between its keys.
     * <p>
     * Clamped at both ends rather than looped: a throw that has finished should
     * stay finished, showing the face it landed on.
     */
    private static float[] at(GlbModel.Sampler sampler, float seconds)
    {
        int stride = sampler.stride();
        float[] out = new float[stride];
        float[] times = sampler.times();
        if(times.length == 0)
        {
            return out;
        }
        int key = 0;
        while(key < times.length - 1 && times[key + 1] <= seconds)
        {
            key++;
        }
        int next = Math.min(key + 1, times.length - 1);
        float span = times[next] - times[key];
        float mix = span <= 0F ? 0F : Mth.clamp((seconds - times[key]) / span, 0F, 1F);
        for(int i = 0; i < stride; i++)
        {
            float a = sampler.values()[key * stride + i];
            float b = sampler.values()[next * stride + i];
            out[i] = a + (b - a) * mix;
        }
        return out;
    }

    @Override
    protected void init()
    {
        int centreX = width / 2;
        int y = height - 40;
        addRenderableWidget(new HubWidgets.TextureButton(centreX - 104, y, 96, 20,
            Component.literal("Flip coin"), pressed -> throwIt("coin")));
        addRenderableWidget(new HubWidgets.TextureButton(centreX + 8, y, 96, 20,
            Component.literal("Roll dice"), pressed -> throwIt("dice")));
    }

    private void throwIt(String which)
    {
        showing = which;
        startedAt = net.minecraft.Util.getMillis();
        settled = false;
        result = which.equals("coin") ? random().nextInt(2) : 1 + random().nextInt(6);
        history.computeIfAbsent(which, prop -> new java.util.ArrayList<>()).add(result);
        play(which.equals("coin") ? DdSounds.COIN_THROW : DdSounds.DIE_ROLL);
    }

    private void play(SoundEvent sound)
    {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1F, 0.9F));
    }

    /** The run of outcomes so far, or null before the first throw. */
    private String tally()
    {
        java.util.List<Integer> past = showing == null ? null : history.get(showing);
        if(past == null || past.isEmpty())
        {
            return null;
        }
        boolean coin = "coin".equals(showing);
        StringBuilder recent = new StringBuilder();
        for(int i = Math.max(0, past.size() - 24); i < past.size(); i++)
        {
            recent.append(coin ? (past.get(i) == 0 ? "H" : "T") : String.valueOf(past.get(i)));
        }
        int[] counted = new int[7];
        int longest = 0;
        int run = 0;
        for(int i = 0; i < past.size(); i++)
        {
            counted[past.get(i)]++;
            run = i > 0 && past.get(i).equals(past.get(i - 1)) ? run + 1 : 1;
            longest = Math.max(longest, run);
        }
        StringBuilder counts = new StringBuilder();
        if(coin)
        {
            counts.append("heads ").append(counted[0]).append("  tails ").append(counted[1]);
        }
        else
        {
            for(int face = 1; face <= 6; face++)
            {
                counts.append(face).append(':').append(counted[face]).append("  ");
            }
        }
        return recent + "    " + past.size() + " throws    " + counts
            + "   longest run " + longest;
    }

    /** Which model file is on screen. */
    private String file()
    {
        return showing;
    }

    /**
     * No background from vanilla, because this screen draws before
     * {@code super.render} and vanilla draws the background from inside it.
     * <p>
     * In a level that background is the BLUR and nothing else -- the panorama
     * and {@code renderMenuBackground} are both gated on there being no level --
     * so leaving it in place blurs everything this screen has already put down
     * while the widgets drawn afterwards stay sharp. 26.2 refuses it too, in the
     * same words:
     * <blockquote>fillGradient, not extractBackground: that one blurs.</blockquote>
     * The dim, where this screen wants one, is its own and goes down first.
     */
    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics vanillaGraphics,
        int mouseX, int mouseY, float partialTick)
    {
    }

    /** How long the prop's clip runs, in seconds. */
    private float length()
    {
        GlbModel.Animation clip = clip(prop(file()));
        return clip == null ? 0F : clip.duration();
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX,
        int mouseY, float partialTick)
    {
        GuiGraphicsExtractor poseStack = new GuiGraphicsExtractor(vanillaGraphics);
        poseStack.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        String title = "Coin and dice";
        poseStack.text(font, title, width / 2 - font.width(title) / 2, 12, MenuInk.title(), MenuInk.shadow());

        int size = Math.min(width - 40, height - 100);
        int left = (width - size) / 2;
        int top = 30;
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, left - 4, top - 4,
            size + 8, size + 8);

        float seconds = showing == null ? 0F
            : (net.minecraft.Util.getMillis() - startedAt) / 1000F;
        // The settling sound waits for the prop to land, which is the whole of
        // what the two clips per prop are for.
        if(showing != null && !settled && seconds >= length())
        {
            settled = true;
            play(showing.equals("coin")
                ? (result == 0 ? DdSounds.COIN_HEADS : DdSounds.COIN_TAILS)
                : DdSounds.DIE_SETTLE);
        }

        Prop prop = showing == null ? null : prop(file());
        if(prop != null)
        {
            float at = Math.min(seconds, Math.max(0F, length()));
            BoardPip.draw(poseStack, left, top, left + size, top + size,
                (pose, collector) -> paint(pose, collector, prop, at, size));
        }

        String line = showing == null ? "Press a button"
            : !settled ? "..."
            : showing.equals("coin") ? (result == 0 ? "Heads" : "Tails")
            : "Rolled " + result;
        poseStack.text(font, line, width / 2 - font.width(line) / 2, top + size + 8,
            MenuInk.body(), MenuInk.shadow());

        String tally = tally();
        if(tally != null)
        {
            poseStack.text(font, tally, width / 2 - font.width(tally) / 2, top + size + 20,
                0xFF8A93A6, true);
        }

        super.render(vanillaGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * The lowest and highest the prop ever gets, in its own units.
     * <p>
     * The model's own bounds plus however far its clip carries it. Both props
     * travel a long way relative to their size -- the die rises about twelve
     * times its own height -- so a view framed on the model alone throws it
     * straight out of the top, which is exactly what happened while the clip
     * was going unread.
     */
    /**
     * How tall the whole flight should be, as a multiple of the prop itself.
     * <p>
     * The source arcs are staged for a duel field, not a preview: the die
     * climbs about twelve times its own height and the coin about six. Framing
     * the window on that honestly leaves the prop a tenth of the width and
     * unreadable, which is the opposite of what a look-at-it screen is for.
     */
    private static final float ARC = 3.2F;

    /** The raw vertical reach of the clip, in model units. */
    private static float[] rawTravel(Prop prop)
    {
        float low = 0F;
        float high = 0F;
        GlbModel.Animation clip = clip(prop);
        GlbModel.Sampler moves = clip == null ? null
            : channel(clip, GlbModel.Path.TRANSLATION);
        if(moves != null)
        {
            for(int at = 1; at < moves.values().length; at += moves.stride())
            {
                low = Math.min(low, moves.values()[at]);
                high = Math.max(high, moves.values()[at]);
            }
        }
        return new float[] {low, high};
    }

    /**
     * What the clip's translation is multiplied by, so every prop flies the
     * same useful distance whatever its own staging was.
     * <p>
     * The rotation is left alone: the tumble is the thing being checked, and
     * scaling a rotation would be changing the animation rather than framing
     * it.
     */
    private static float compression(Prop prop)
    {
        float[] raw = rawTravel(prop);
        float reach = raw[1] - raw[0];
        float wanted = prop.mesh().modelHeight() * (ARC - 1F);
        return reach <= 0.001F ? 1F : Math.min(1F, wanted / reach);
    }

    /** The lowest and highest the prop gets once compressed, in model units. */
    private static float[] travel(Prop prop)
    {
        float half = prop.mesh().modelHeight() * 0.5F;
        float squeeze = compression(prop);
        float[] raw = rawTravel(prop);
        return new float[] {Math.min(-half, raw[0] * squeeze - half),
                            Math.max(half, raw[1] * squeeze + half)};
    }

    /**
     * The prop, posed at one instant of its clip.
     *
     * <h2>Two coordinate systems, and neither is the world's</h2>
     * The pip pass hands over GUI units with its origin at the top-left of the
     * region, so the world-space call this began as -- a height of 1.4
     * "blocks" -- asked for a model a pixel and a half tall, and drew exactly
     * that. The model is now left at its own size and this matrix does the
     * fitting.
     *
     * <h2>The animation is applied here, not by the hologram</h2>
     * These files have no skin, so there is no skeleton for {@code
     * ModelHologram} to pose -- passing it a clip name does nothing. What the
     * clip drives is the node the mesh hangs on, which is a transform around
     * the whole thing, so it is read and applied as one.
     */
    /**
     * The turn that ends with the right face where the camera can read it.
     *
     * <h2>The die shows its TOP face, not the one facing the viewer</h2>
     * Master Duel looks down at the die from (0, 95, -37) pitched 70 degrees
     * (the {@code ScreenEffect} on {@code DuelDice}, bundle {@code
     * ef/efcde3e7}), so the face a player reads is the one pointing +Y. An
     * earlier version of this mapped the result onto +Z -- towards the viewer
     * -- which is right for a front-on camera and wrong for this one.
     *
     * <h2>The face map is derived, not guessed</h2>
     * Every triangle of {@code dice.glb} was grouped by its normal and its mean
     * UV taken, then placed on the 3x2 grid of {@code DuelDiceTex02}: 1 on +Y
     * (a star, not pips), 2 on +Z, 3 on -X, 4 on +X, 5 on -Z, 6 on -Y. Opposite
     * faces sum to seven on all three axes, which is what says the reading is
     * right rather than merely self-consistent.
     *
     * <h2>The coin</h2>
     * One mesh with two submeshes -- {@code lambert16} for heads, {@code
     * lambert17} for tails -- whose flat triangles face -Z and +Z. Its own
     * camera is at (0, 0, -10) with no pitch at all, so for the coin the face
     * towards the viewer IS the one to show, and tails is already there.
     */
    private org.joml.Quaternionf wantedFacing()
    {
        float quarter = (float)(Math.PI / 2D);
        if("coin".equals(showing))
        {
            return result == 0 ? new org.joml.Quaternionf().rotationY((float)Math.PI)
                : new org.joml.Quaternionf();
        }
        // Each case turns the named face's own normal onto +Y.
        return switch(result)
        {
            case 1 -> new org.joml.Quaternionf();
            case 2 -> new org.joml.Quaternionf().rotationX(-quarter);
            case 3 -> new org.joml.Quaternionf().rotationZ(-quarter);
            case 4 -> new org.joml.Quaternionf().rotationZ(quarter);
            case 5 -> new org.joml.Quaternionf().rotationX(quarter);
            default -> new org.joml.Quaternionf().rotationX((float)Math.PI);
        };
    }

    /**
     * Master Duel's camera for the roll.
     *
     * <h2>Where this came from</h2>
     * The {@code ScreenEffect} component on the {@code DuelDice} prefab
     * (bundle {@code ef/efcde3e7}) carries {@code cameraPosition = (0, 95,
     * -37)} and {@code cameraAngle = (70, 0, 0)}. The rig above the die scales
     * by 0.01, so in the clip's own units the camera stands at (0, 9500,
     * -3700).
     *
     * <h2>Why the flight only makes sense through it</h2>
     * The clip does not bounce the die on the spot. It runs it across the
     * field -- z from 2365 to -2201 -- and the two {@code ShockWave} emitters
     * sit at z = 1138 and z = 739, which is where the clip's first two bounces
     * touch down (z = 1090 and z = 756, the second at t = 0.517 against a
     * shockwave track starting at 0.5167). Seen from a camera pitched 70
     * degrees down, that run reads as the die ARRIVING: coming towards you and
     * down the screen. Seen from a front-on camera it reads as the die lurching
     * sideways, which is what it looked like through three attempts to fix it
     * by adjusting the animation instead of the view.
     */
    private static final float DIE_PITCH = (float)Math.toRadians(70D);

    /**
     * The camera, in die-widths.
     * <p>
     * Master Duel frames the die as one element of a whole duel field, which
     * leaves it about 3% of the frame -- too small to inspect. The fix is a
     * LONGER LENS rather than a nearer camera: narrowing the field of view
     * scales the projected image uniformly about its centre, so the path keeps
     * exactly the shape Master Duel gives it. Moving the camera would not. At
     * 34 degrees the die enters at the top edge and ends centred.
     */
    private static final float[] DIE_CAM = {0F, 48.150F, -18.754F};

    private static final float DIE_TAN = (float)Math.tan(Math.toRadians(26D) / 2D);

    /**
     * The clip's units are the die's own width -- but not the width this model
     * has.
     * <p>
     * {@code dice.glb} is normalised by the mesh's source extent, 219.24. In
     * the duel rig that mesh sits at scale 0.3 under a node at scale 3, making
     * the die 197.3 of the units its own translation is measured in. Without
     * this the arc comes out 11% too large.
     */
    private static final float CLIP_TO_DIE = 219.24F / 197.3F;

    private void paint(PoseStack pose, SubmitNodeCollector collector, Prop prop,
        float seconds, int size)
    {
        if(!"coin".equals(showing))
        {
            paintDie(pose, collector, prop, seconds, size);
            return;
        }

        // The coin's own ScreenEffect puts its camera at (0, 0, -10) with no
        // pitch, so a front-on frame is already the right shape for it and only
        // the fitting below is this screen's own.
        float[] reach = travel(prop);
        float span = Math.max(0.001F, reach[1] - reach[0]);
        float scale = size * 0.82F / span;

        pose.pushPose();
        pose.translate(size / 2F, size * 0.94F + reach[0] * scale, 0F);
        pose.scale(scale, scale, scale);
        pose.mulPose(new org.joml.Quaternionf().rotationX((float)Math.PI));
        animate(pose, prop, seconds, true);
        // DRAWN ABOUT ITS OWN CENTRE, NOT STOOD ON ITS FEET.
        //
        // ModelHologram.submit ends with translate(-footOffset), lifting a model
        // so its lowest point rests where it was placed. That is right for a
        // monster standing on a card and wrong for a prop in mid-air -- and
        // because it happens INSIDE submit, after the rotations below, the lift
        // travels in the prop's own tumbling frame rather than the world's. On a
        // rolled 4 the facing turn maps model +Y onto -X, so the die came to
        // rest half a width to the LEFT of where it was put; every result was
        // displaced a different way. Cancelled here, in the same frame, so the
        // two translations annihilate whatever the prop is doing.
        float[] middle = prop.mesh().footOffset();
        pose.translate(middle[0], middle[1], middle[2]);

        ModelHologram.submit(pose, collector, Vec3.ZERO, Vec3.ZERO,
            prop.mesh().modelHeight(), prop.mesh(), 0F, 0xFFFFFFFF, "", 0F, 0F, 0F, 0F,
            Float.NaN);
        pose.popPose();
    }

    /** The die, projected through the camera Master Duel rolls it under. */
    private void paintDie(PoseStack pose, SubmitNodeCollector collector, Prop prop,
        float seconds, int size)
    {
        GlbModel.Animation clip = clip(prop);
        GlbModel.Sampler moves = clip == null ? null : channel(clip, GlbModel.Path.TRANSLATION);
        float[] p = moves == null ? new float[] {0F, 0F, 0F} : at(moves, seconds);

        // Where the die is, relative to the camera, in die-widths.
        float x = p[0] * CLIP_TO_DIE - DIE_CAM[0];
        float y = p[1] * CLIP_TO_DIE - DIE_CAM[1];
        float z = p[2] * CLIP_TO_DIE - DIE_CAM[2];

        float cos = Mth.cos(DIE_PITCH);
        float sin = Mth.sin(DIE_PITCH);
        float up = y * cos + z * sin;
        float forward = Math.max(0.001F, -y * sin + z * cos);

        // The perspective divide, done here rather than by the pass. The board
        // pip draws flat, and the die is small next to how far away it is, so
        // projecting its origin and scaling it by the same divide is the whole
        // of the perspective that matters -- and it is what makes the die grow
        // as it comes at the camera through the reveal.
        float half = size / 2F;
        float scale = half / (forward * DIE_TAN);

        pose.pushPose();
        pose.translate(half + x * scale, half - up * scale, 0F);
        pose.scale(scale, scale, scale);
        // Half a turn puts model +Y up on a screen that counts down; the pitch
        // then lays the scene back under the camera. Both are about X, so they
        // are one rotation. A negative scale would do the flip too and would
        // mirror the model -- reversed winding, normals pointing inward, a die
        // that comes out dark and turns the wrong way.
        pose.mulPose(new org.joml.Quaternionf().rotationX((float)Math.PI - DIE_PITCH));
        animate(pose, prop, seconds, false);

        // DRAWN ABOUT ITS OWN CENTRE, NOT STOOD ON ITS FEET.
        //
        // ModelHologram.submit ends with translate(-footOffset), lifting a model
        // so its lowest point rests where it was placed. That is right for a
        // monster standing on a card and wrong for a prop in mid-air -- and
        // because it happens INSIDE submit, after the rotations below, the lift
        // travels in the prop's own tumbling frame rather than the world's. On a
        // rolled 4 the facing turn maps model +Y onto -X, so the die came to
        // rest half a width to the LEFT of where it was put; every result was
        // displaced a different way. Cancelled here, in the same frame, so the
        // two translations annihilate whatever the prop is doing.
        float[] middle = prop.mesh().footOffset();
        pose.translate(middle[0], middle[1], middle[2]);

        ModelHologram.submit(pose, collector, Vec3.ZERO, Vec3.ZERO,
            prop.mesh().modelHeight(), prop.mesh(), 0F, 0xFFFFFFFF, "", 0F, 0F, 0F, 0F,
            Float.NaN);
        pose.popPose();
    }

    /**
     * The clip's rotation, and its translation if the caller wants it.
     *
     * @param move true to apply the clip's own vertical travel, false if the
     *             caller has already placed the prop itself
     */
    private void animate(PoseStack pose, Prop prop, float seconds, boolean move)
    {
        GlbModel.Animation clip = clip(prop);
        if(clip == null)
        {
            return;
        }
        GlbModel.Sampler moves = channel(clip, GlbModel.Path.TRANSLATION);
        if(moves != null && move)
        {
            float[] where = at(moves, seconds);
            pose.translate(0F, where[1] * compression(prop), 0F);
        }
        GlbModel.Sampler turns = channel(clip, GlbModel.Path.ROTATION);
        if(turns != null)
        {
            float[] q = at(turns, seconds);
            // glTF orders a quaternion x y z w; JOML's constructor agrees.
            // Normalised because interpolating two unit quaternions linearly
            // does not give a third.
            pose.mulPose(new org.joml.Quaternionf(q[0], q[1], q[2], q[3]).normalize());

            // The clip always ends the same way up, so on its own the prop
            // shows one face however it was thrown -- picture and number
            // disagreeing, which is worse than either being wrong.
            //
            // Corrected AFTER the clip rather than before it. Pre-multiplying
            // turns the whole flight, so the die tumbles about a different axis
            // and stops looking like the original; post-multiplying turns the
            // prop within its own flight, so the path through the air is
            // Master Duel's untouched and only the face that ends up forward
            // changes. At the last key the clip and its inverse cancel and the
            // wanted facing is what is left.
            float[] last = at(turns, clip.duration());
            org.joml.Quaternionf end =
                new org.joml.Quaternionf(last[0], last[1], last[2], last[3]).normalize();
            pose.mulPose(end.conjugate().mul(wantedFacing()));
        }
    }
}
