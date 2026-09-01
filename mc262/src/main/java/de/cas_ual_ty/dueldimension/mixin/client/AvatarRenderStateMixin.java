package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.character.CharacterLook;
import de.cas_ual_ty.dueldimension.clientutil.character.CharacterCarrier;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives a player's render state somewhere to carry their character.
 * <p>
 * The state is REUSED between frames and between players — the renderer keeps
 * one per entity and fills it in — so this field is written on every extraction
 * rather than only when there is a character to record. Writing it only when
 * somebody has one would leave the last player's character on the next player
 * who has none.
 */
@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements CharacterCarrier
{
    @Unique
    private CharacterLook dueldimension$look;

    @Override
    public CharacterLook dueldimension$character()
    {
        return dueldimension$look;
    }

    @Override
    public void dueldimension$setCharacter(CharacterLook look)
    {
        dueldimension$look = look;
    }

    @Unique
    private boolean dueldimension$sprint;

    @Override
    public boolean dueldimension$sprinting()
    {
        return dueldimension$sprint;
    }

    @Override
    public void dueldimension$setSprinting(boolean sprinting)
    {
        dueldimension$sprint = sprinting;
    }

    @Unique
    private boolean dueldimension$ride;

    @Override
    public boolean dueldimension$riding()
    {
        return dueldimension$ride;
    }

    @Override
    public void dueldimension$setRiding(boolean riding)
    {
        dueldimension$ride = riding;
    }

    @Unique
    private boolean dueldimension$coast;

    @Override
    public boolean dueldimension$coasting()
    {
        return dueldimension$coast;
    }

    @Override
    public void dueldimension$setCoasting(boolean coasting)
    {
        dueldimension$coast = coasting;
    }

    @Unique
    private boolean dueldimension$move;

    @Override
    public boolean dueldimension$moving()
    {
        return dueldimension$move;
    }

    @Override
    public void dueldimension$setMoving(boolean moving)
    {
        dueldimension$move = moving;
    }

    @Unique
    private boolean dueldimension$back;

    @Override
    public boolean dueldimension$backwards()
    {
        return dueldimension$back;
    }

    @Override
    public void dueldimension$setBackwards(boolean backwards)
    {
        dueldimension$back = backwards;
    }

    @Unique
    private float dueldimension$swingAt = -1F;
    @Unique
    private net.minecraft.world.phys.Vec3 dueldimension$camera =
        net.minecraft.world.phys.Vec3.ZERO;

    @Override
    public float dueldimension$swing()
    {
        return dueldimension$swingAt;
    }

    @Override
    public void dueldimension$setSwing(float seconds)
    {
        dueldimension$swingAt = seconds;
    }

    @Override
    public net.minecraft.world.phys.Vec3 dueldimension$eye()
    {
        return dueldimension$camera;
    }

    @Override
    public void dueldimension$setEye(net.minecraft.world.phys.Vec3 eye)
    {
        dueldimension$camera = eye;
    }
}
