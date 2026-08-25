package de.cas_ual_ty.dueldimension.mc1211;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import net.minecraft.resources.ResourceLocation;

/**
 * The only file in the 1.21.1 platform, and it exists to fail loudly if the
 * toolchain is wrong.
 * <p>
 * Phase 0 of the two-version split ends here. Everything before it is
 * rearrangement that could be checked by building 26.2; this is the first thing
 * that could not, because it asks three questions that only a real 1.21.1
 * compile can answer:
 * <ul>
 * <li><b>Does Loom resolve two Minecraft versions in one build?</b> 26.2 and
 * 1.21.1 disagree about almost everything a Loom block configures — 26.2 ships
 * unobfuscated with no mappings and no remap step, 1.21.1 needs both — so this
 * is not obviously true until it compiles.</li>
 * <li><b>Is {@code common} usable from here?</b> It is compiled to Java 21
 * against no Minecraft at all. {@link OcgConstants} below is the shared rules
 * engine's, reached from a 1.21.1 module with nothing version-specific in
 * between.</li>
 * <li><b>Do Mojang names work?</b> {@link ResourceLocation} is spelled the way
 * the existing codebase spells it. Under Yarn this line would not compile, and
 * every ported file would need translating on top of the version differences it
 * already has.</li>
 * </ul>
 * <p>
 * It is deliberately not an entrypoint and is registered nowhere: a mod that
 * does nothing should not announce itself. Delete it once real code lands here
 * — at that point the compiler is asking these questions on every file anyway.
 */
public final class ToolchainProof
{
    /**
     * A face-up attack position, from the shared engine constants.
     * <p>
     * Named rather than inlined so the reference survives the compiler: a
     * constant that is never read is a dependency that javac can discard, and
     * the point of this file is the dependency.
     */
    public static final int FACE_UP_ATTACK = OcgConstants.POS_FACEUP_ATTACK;

    /** An identifier, spelled as 1.21.1 spells it. */
    public static final ResourceLocation MARKER =
        ResourceLocation.fromNamespaceAndPath("dueldimension", "toolchain_proof");

    private ToolchainProof()
    {
    }

    /** Both halves in one expression, so neither can be optimised away. */
    public static String describe()
    {
        return MARKER + " ocg:" + FACE_UP_ATTACK;
    }
}
