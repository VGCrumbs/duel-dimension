package de.cas_ual_ty.dueldimension.clientutil.character;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.character.CharacterLook;
import de.cas_ual_ty.dueldimension.clientutil.BoardPip;
import de.cas_ual_ty.dueldimension.clientutil.model.ModelHologram;
import de.cas_ual_ty.dueldimension.clientutil.model.ModelMesh;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * The character in the editor, turned by dragging.
 * <p>
 * <b>The same model the world draws, not a picture of it.</b> Same mesh, same
 * palette rewrite, same idle animation — so there is no second rendering path
 * that could agree with the first today and disagree after a change. What is in
 * this pane is what everyone else will see.
 * <p>
 * Turned rather than spun: a preview that rotates on its own is showing you the
 * back of the head at the moment you change the fringe. It starts three-quarters
 * on, which shows a face and a silhouette at once, and stays where it is put.
 */
public final class CharacterPreview
{
    /** Three-quarters on, in degrees. */
    private static final float START = 25F;

    /**
     * How far forward the model sits, in GUI pixels.
     * <p>
     * <b>A character has depth and a screen does not.</b> The panel behind this
     * pane is drawn at z = 0 and writes depth like everything else, so half the
     * model -- everything behind its own middle -- failed the depth test against
     * it and simply was not there. It read as a duellist sliced down the
     * front.
     * <p>
     * The model is about 0.3 blocks deep either side of centre, which at this
     * pane's scale is some 36 pixels, so a hundred clears the panel with room
     * to spare. It cannot cover anything else: the pip scissors this to the
     * pane, and nothing else is drawn inside it.
     */
    private static final float FORWARD = 100F;

    private float yaw = START;
    private boolean turning;

    private int x;
    private int y;
    private int width;
    private int height;

    /**
     * Air above the head and under the feet, in pane pixels.
     * <p>
     * Small, and the same at both ends: the model is being FITTED now rather
     * than composed into the pane, so this is clearance against the frame's own
     * bevel rather than a share of the height. Zero would put a boot sole on the
     * inset's border line.
     */
    private static final float PANE_MARGIN = 4F;

    public void render(GuiGraphicsExtractor graphics, CharacterLook look,
        int x, int y, int width, int height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        ModelMesh mesh = CharacterModels.mesh(look.gender());
        if(mesh == null)
        {
            return;
        }
        Identifier[] painted = new Identifier[CharacterRenderer.SLOTS.length];
        int[] slots = new int[CharacterRenderer.SLOTS.length];
        for(int i = 0; i < slots.length; i++)
        {
            slots[i] = CharacterModels.primitive(look, CharacterRenderer.SLOTS[i]);
            painted[i] = CharacterModels.texture(look, CharacterRenderer.SLOTS[i]);
        }
        float turn = yaw;
        BoardPip.draw(graphics, x, y, x + width, y + height,
            (pose, collector) -> paint(pose, collector, mesh, look, slots, painted, turn));
    }

    private void paint(PoseStack pose, SubmitNodeCollector collector, ModelMesh mesh,
        CharacterLook look, int[] slots, Identifier[] painted, float turn)
    {
        pose.pushPose();
        // The pane's own frame: the origin is its top left and a unit is a
        // pixel, so the model is put at the bottom middle and scaled to fill
        // the height. Two blocks tall becomes `height` pixels tall.
        // Forward BEFORE the scale, in the pip's own pixels: the pip has
        // already turned z round so that it points away, which is why this is
        // negative to come towards the viewer.
        // FITTED TO THE MODEL, not to a pair of numbers chosen to suit it.
        //
        // This was `height * 0.94F` and `height * 0.49F`: the model placed
        // near the bottom and scaled as though it were 1.8 units tall. Both
        // halves were assumptions. The crown is at 1.800 by construction --
        // that is what the exporter's scale rule guarantees -- but the FEET are
        // not at zero, and hair is deliberately allowed above the line, so the
        // real extent is neither 1.8 nor the same for two characters. The
        // result was a figure floating clear of the bottom of its frame with
        // its own headroom decided by whichever hair was on.
        //
        // So the mesh is asked. `modelHeight` is max - min in the model's own
        // units and `footOffset`'s y is that minimum, which is the sole thing
        // needed to put the feet ON the floor rather than near it.
        float span = Math.max(0.001F, mesh.modelHeight());
        float scale = (height - PANE_MARGIN * 2F) / span;
        // The floor of the pane, less the bottom margin. The model's own
        // lowest point lands here, whatever that point is -- a boot sole on one
        // character and a longer skirt on another.
        pose.translate(width / 2F, height - PANE_MARGIN, -FORWARD);
        // (x, -y, -z), NOT (x, -y, z).
        //
        // The pane's y runs down and the model's runs up, so y flips; that much
        // was right. But the pip flips z as well, and leaving that flip in place
        // is an ODD number of flips overall -- a reflection, not a rotation. The
        // character came out mirrored, wearing the duel disk on the wrong arm,
        // and it was only obvious BECAUSE the disk is not symmetric. Flipping z
        // back here makes the pair of flips a half-turn about x instead, which
        // is what turning a y-up model into a y-down pane actually is.
        pose.scale(scale, -scale, -scale);

        // And the model's own base to the origin, in MODEL units, after the
        // scale -- so this is the same subtraction whatever the pane is sized
        // to. Without it the model hangs by whatever its author left the origin
        // at, which is the trap `footOffset` was written for in the first place.
        pose.translate(0F, -mesh.footOffset()[1], 0F);

        // -1F: as authored, the same frame the world draws them in. See
        // CharacterRenderer.HEIGHT -- a preview that fitted the model to the
        // pane would be a different size from the character everyone else sees.
        ModelHologram.submit(pose, collector, Vec3.ZERO, Vec3.ZERO, -1F, mesh,
            turn, 0xFFFFFFFF, CharacterRenderer.idle(look), 0F, 0F, 0F, 0F,
            Float.NaN, null, part ->
            {
                for(int i = 0; i < slots.length; i++)
                {
                    if(slots[i] == part)
                    {
                        return painted[i];
                    }
                }
                return null;
            });
        pose.popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY)
    {
        turning = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        return turning;
    }

    public boolean mouseDragged(double dragX)
    {
        if(!turning)
        {
            return false;
        }
        yaw = (yaw + (float) dragX * 1.2F) % 360F;
        return true;
    }

    public void mouseReleased()
    {
        turning = false;
    }

    /** Back to the angle it opened at. */
    public void reset()
    {
        yaw = START;
    }
}
