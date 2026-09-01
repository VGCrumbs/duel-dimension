package de.cas_ual_ty.dueldimension.clientutil.character;

import de.cas_ual_ty.dueldimension.character.CharacterLook;

/**
 * A render state that knows which character its player is wearing.
 * <p>
 * Minecraft's render state carries no identity — no UUID, no name beyond the
 * tag — because by the time it is drawn the entity is gone. So the look is put
 * on it during extraction, which is the one moment both halves are in hand, and
 * read back when the model is drawn. The duel disk already travels this way for
 * the same reason.
 * <p>
 * Implemented on {@code AvatarRenderState} by a mixin. Nothing else implements
 * it, and a state that has not been through extraction answers null — which is
 * what a player with no character should look like.
 */
public interface CharacterCarrier
{
    CharacterLook dueldimension$character();

    void dueldimension$setCharacter(CharacterLook look);

    /**
     * Whether the player is sprinting, which is what picks the run animation.
     * <p>
     * Carried for the same reason the look is: 26.2's render state has no such
     * field, and `walkAnimationSpeed` -- the obvious substitute -- cannot tell
     * the two apart. It is the distance the player moved this tick times four
     * and clamped to one, so an ordinary walk already reads 0.86 and a sprint
     * reads 1.00. Any threshold between them is a hair's breadth wide and on
     * the wrong side of it a walking duellist runs on the spot.
     * <p>
     * Sprinting is a state Minecraft keeps deliberately -- it is what the FOV
     * shift and the footstep particles key off -- so it is the honest signal.
     */
    boolean dueldimension$sprinting();

    void dueldimension$setSprinting(boolean sprinting);

    /**
     * Whether they are sitting on something, which is what picks the seated
     * clip and suppresses the crouch and the reversed walk with it.
     * <p>
     * 1.21.1 asks {@code player.isPassenger()} in the renderer. Here the
     * renderer has a state and not a player, so the question is asked during
     * extraction along with the rest.
     */
    boolean dueldimension$riding();

    void dueldimension$setRiding(boolean riding);

    /**
     * Whether the ground is moving them rather than being walked on.
     * <p>
     * The ANSWER and not the ingredients: {@code CharacterRenderer.carried}
     * needs the block under them and their speed last tick, and both are
     * questions about an entity that is gone by the time this is drawn. Asked
     * during extraction and carried, like everything else here.
     */
    boolean dueldimension$coasting();

    void dueldimension$setCoasting(boolean coasting);

    /**
     * Whether they moved at all this tick, which is what ends the walk cycle.
     * <p>
     * Not {@code walkAnimationSpeed}, which is eased -- {@code update(f, 0.4F)}
     * -- and so decays towards zero over several ticks after the keys are let
     * go. A threshold on it keeps the walk running for most of a second after a
     * duellist has stopped. The position told the truth the whole time, and the
     * extraction is standing where the position still exists.
     */
    boolean dueldimension$moving();

    void dueldimension$setMoving(boolean moving);

    /**
     * Whether they are travelling backwards relative to the way they face.
     * <p>
     * Carried for the reason the sprint flag is: the render state has no
     * memory of where the player was last tick, and the walk cycle has to be
     * run the other way round when they are backing up.
     */
    boolean dueldimension$backwards();

    void dueldimension$setBackwards(boolean backwards);

    /**
     * How far into a swing this player is, in seconds, or -1 when not swinging.
     * <p>
     * Carried rather than derived, for the reason everything else here is: by
     * the time the model is drawn the entity is gone, and a swing is a fact
     * about the entity.
     */
    float dueldimension$swing();

    void dueldimension$setSwing(float seconds);

    /**
     * Where the camera is, which is what turns a camera-relative vertex back
     * into a world position for the light lookup.
     */
    net.minecraft.world.phys.Vec3 dueldimension$eye();

    void dueldimension$setEye(net.minecraft.world.phys.Vec3 eye);
}
