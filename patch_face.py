import sys

SHARED = [
    # ---- the state carries the block's heading -----------------------------
    ("""        public boolean faceDown;""",
     """        public boolean faceDown;
        /**
         * Which way the pedestal is turned.
         * <p>
         * FACING points from the block towards whoever placed it, exactly as a
         * furnace's does, so it is also the way the card should be read from
         * and the way a monster standing on it should look.
         */
        public net.minecraft.core.Direction facing;"""),

    ("""        state.defence = display.defence();""",
     """        state.defence = display.defence();
        // Off the block state rather than the block entity: the facing is the
        // BLOCK's, so it lives where the block's own data lives and needs no
        // saving of its own.
        net.minecraft.world.level.block.state.BlockState block = display.getBlockState();
        state.facing = block.hasProperty(
            de.cas_ual_ty.dueldimension.duel.overworld.display.CardDisplayBlock.FACING)
            ? block.getValue(
                de.cas_ual_ty.dueldimension.duel.overworld.display.CardDisplayBlock.FACING)
            : net.minecraft.core.Direction.NORTH;"""),

    # ---- the card turns with the block --------------------------------------
    ("""        DisplayCard.submit(poseStack, collector, (int)state.code, state.art, state.defence,
            state.faceDown, 0xFFFFFFFF);""",
     """        // TURNED TO FACE WHOEVER PLACED IT.
        //
        // The card is drawn in block-local coordinates around the block's own
        // centre, so turning it is a rotation of the pose about that centre and
        // nothing else -- which is why this wraps only the card. drawMonster
        // below works in WORLD space, and a rotated pose would move it off the
        // pedestal rather than turn it; it is given the heading as a number
        // instead.
        //
        // -toYRot, and the sign is derived rather than guessed. DisplayCard
        // winds its top face so that the texture's top edge lies along the
        // NORTH edge and its left along the WEST -- which is a card the right
        // way up to somebody standing to the SOUTH looking north. So the
        // undrawn-rotation card already faces south, and what is wanted is the
        // turn that takes south to FACING: 0 for south, 90 for east, 180 for
        // north, 270 for west. That is -toYRot for all four, since toYRot is
        // south 0, west 90, north 180, east 270.
        float heading = state.facing == null ? 0F : -state.facing.toYRot();
        poseStack.pushPose();
        poseStack.translate(0.5F, 0F, 0.5F);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(heading));
        poseStack.translate(-0.5F, 0F, -0.5F);
        DisplayCard.submit(poseStack, collector, (int)state.code, state.art, state.defence,
            state.faceDown, 0xFFFFFFFF);
        poseStack.popPose();"""),

    # ---- and so does whatever is standing on it -----------------------------
    ("""            // A fixed heading, not one taken off the camera. This followed the
            // viewer, the way the sprites do -- but a sprite has to turn,
            // because a flat picture seen edge-on is nothing, while a model has
            // a back and a front and a shape that reads differently from every
            // side. A dragon that pivots to keep facing you as you walk round it
            // is a dragon that never seems to be standing anywhere.
            //
            // The block carries no facing of its own, so "fixed" means fixed at
            // the definition's own Turn -- which is a number somebody sets once
            // while looking at it, rather than one this has to guess.
            de.cas_ual_ty.dueldimension.clientutil.model.ModelHologram.submit(poseStack,
                collector, block, feet, height, mesh, 0F, 0xFFFFFFFF,""",
     """            // A fixed heading, not one taken off the camera. This followed the
            // viewer, the way the sprites do -- but a sprite has to turn,
            // because a flat picture seen edge-on is nothing, while a model has
            // a back and a front and a shape that reads differently from every
            // side. A dragon that pivots to keep facing you as you walk round it
            // is a dragon that never seems to be standing anywhere.
            //
            // "Fixed" now means fixed at the BLOCK's heading, with the
            // definition's own Turn on top of it. It used to mean fixed at the
            // Turn alone, because the block had no facing to add -- and every
            // display in a row therefore faced the same way however it had been
            // placed. Now that the pedestal is turned when it is put down, the
            // thing standing on it turns with it, and Turn goes back to being
            // what it was written to be: a per-monster correction, not a
            // heading.
            de.cas_ual_ty.dueldimension.clientutil.model.ModelHologram.submit(poseStack,
                collector, block, feet, height, mesh, blockYaw(state), 0xFFFFFFFF,"""),

    ("""        // Lift, Off x and Off z, which a sprite now obeys as a model does. Yaw
        // 0 for the same reason the model above passes 0: the block carries no
        // facing of its own, so the definition's Turn is the whole heading.
        feet = MonsterBillboard.stand(feet, 0F, definition);
        // As on the board, but the block has no heading of its own, so Turn is
        // the whole of it -- which is also what decides which way a Doom sheet
        // considers its front. definition can be null, and a null one has no Turn.
        float turn = definition == null ? 0F : definition.turn();""",
     """        // Lift, Off x and Off z, which a sprite now obeys as a model does. The
        // yaw is the block's, for the same reason the model above takes it.
        feet = MonsterBillboard.stand(feet, blockYaw(state), definition);
        // As on the board: the block's heading plus the definition's Turn, which
        // together decide which way a Doom sheet considers its front.
        // definition can be null, and a null one has no Turn.
        float turn = (definition == null ? 0F : definition.turn()) + blockYaw(state);"""),
]

TAIL = ("""    private static void drawMonster(State state, PoseStack poseStack,""",
        """    /**
     * The pedestal's heading in Minecraft's own yaw, for whatever stands on it.
     * <p>
     * {@code toYRot} straight through, with no sign flipped: yaw and FACING
     * share a convention -- south 0, west 90, north 180, east 270 -- so a
     * monster given the block's facing looks the way the block's front points,
     * which is at whoever placed it. That is the OPPOSITE convention to the
     * card's rotation a few lines up, and deliberately so: one is a heading, the
     * other is a correction applied to art that already faced somewhere.
     */
    private static float blockYaw(State state)
    {
        return state.facing == null ? 0F : state.facing.toYRot();
    }

    private static void drawMonster(State state, PoseStack poseStack,""")

for tree in ('mc1211', 'mc262'):
    p = tree + '/src/main/java/de/cas_ual_ty/dueldimension/clientutil/overworld/CardDisplayRenderer.java'
    raw = open(p, 'rb').read().decode('utf-8')
    crlf = '\r\n' in raw
    s = raw.replace('\r\n', '\n')
    for old, new in SHARED + [TAIL]:
        if s.count(old) != 1:
            sys.exit('%s: %d matches for %r' % (p, s.count(old), old[:60]))
        s = s.replace(old, new)
    open(p, 'wb').write((s.replace('\n', '\r\n') if crlf else s).encode('utf-8'))
    print('patched', p)
