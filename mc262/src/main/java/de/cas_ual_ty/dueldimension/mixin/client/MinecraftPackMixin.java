package de.cas_ual_ty.dueldimension.mixin.client;

import de.cas_ual_ty.dueldimension.clientutil.DdResourcePackFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Arrays;

/**
 * Adds the card-image pack to the client's resource pack list.
 * <p>
 * Forge fired {@code AddPackFindersEvent} for exactly this. Fabric API has no
 * equivalent — {@code ResourceManagerHelper.registerBuiltinResourcePack} only
 * handles packs bundled inside a mod jar, and this one is a folder that fills
 * up while the game runs. So the source is appended to the array the client's
 * {@link PackRepository} is built from.
 * <p>
 * The client's repository specifically, not {@code PackRepository}'s
 * constructor: a server builds one too, and a data pack full of card textures
 * would be both useless and confusing in the world-creation list.
 */
@Mixin(Minecraft.class)
public class MinecraftPackMixin
{
    @ModifyArg(
        method = "<init>",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>"
                + "([Lnet/minecraft/server/packs/repository/RepositorySource;)V"),
        index = 0)
    private RepositorySource[] dueldimension$addCardImagePack(RepositorySource[] sources)
    {
        RepositorySource[] withOurs = Arrays.copyOf(sources, sources.length + 1);
        withOurs[sources.length] = new DdResourcePackFinder();
        return withOurs;
    }
}
