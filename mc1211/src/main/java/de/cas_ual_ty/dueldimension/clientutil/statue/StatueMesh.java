package de.cas_ual_ty.dueldimension.clientutil.statue;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * One model out of the Dawn of Destiny reward screen, as exported from the disc.
 * <p>
 * Geometry is in the model's OWN authored coordinates, <b>Y-up</b>, exactly as it
 * sits in the {@code .igb}. Where each one goes on the screen is not baked in;
 * that lives in {@link StatueScene}, because those numbers were read out of
 * {@code menu_m_03}'s own {@code igTransform} matrices and are worth keeping
 * legible rather than folded into vertex data.
 * <p>
 * Written by {@code tools/export_statue_meshes.py} in the Xbox toolchain.
 */
public record StatueMesh(List<Part> parts)
{
    /**
     * A single drawable piece: one texture, one material, one triangle list.
     *
     * @param positions 3 floats per vertex
     * @param normals   3 floats per vertex, matching {@code positions}
     * @param uvs       2 floats per vertex, V already flipped to top-left origin
     * @param indices   3 ints per triangle
     */
    public record Part(String name, ResourceLocation texture, ResourceLocation env, Material material,
        float[] positions, float[] normals, float[] uvs, int[] indices)
    {
        public int vertexCount()
        {
            return positions.length / 3;
        }

        public int triangleCount()
        {
            return indices.length / 3;
        }
    }

    /**
     * Alchemy's fixed-function material, in the slot order the files actually use.
     * <p>
     * <b>Slot 8 is specular, not emission.</b> The igb-blender importer labels it
     * emission, which drove a specular colour into an emission input and rendered
     * every one of these models white. Slot 7 is the real emission and is black on
     * every material in these files, which is why it is not carried here at all.
     *
     * @param shininess the Blinn-Phong exponent, slot 4
     */
    public record Material(float shininess, float[] diffuse, float[] ambient, float[] specular)
    {
        /**
         * Converts the Blinn-Phong exponent to a roughness in [0,1].
         * <p>
         * This is a DOUBLE conversion and it is easy to get half right. The
         * exponent maps to a microfacet alpha as {@code a = sqrt(2/(n+2))}, and
         * a perceptual roughness squares to give alpha -- so the roughness is
         * {@code sqrt(a)}, not {@code a}. Stopping at alpha makes n=50 come out
         * at 0.196 instead of 0.443, which is a near-mirror; every flat
         * upward-facing surface then blows out white and it reads as a lighting
         * bug rather than a unit error.
         * <p>
         * Not used by the flat-shaded path yet. Kept because it is the piece of
         * the conversion that is genuinely easy to get wrong.
         */
        public float roughness()
        {
            if(shininess <= 0F)
            {
                return 1F;
            }
            double alpha = Math.sqrt(2.0 / (shininess + 2.0));
            return (float)Math.min(1.0, Math.sqrt(alpha));
        }
    }

    public int triangleCount()
    {
        int total = 0;
        for(Part part : parts)
        {
            total += part.triangleCount();
        }
        return total;
    }
}
