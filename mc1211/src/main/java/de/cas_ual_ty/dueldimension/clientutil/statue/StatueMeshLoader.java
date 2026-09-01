package de.cas_ual_ty.dueldimension.clientutil.statue;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the {@code .ddm} meshes exported off the Dawn of Destiny disc.
 * <p>
 * The format is deliberately dull, and <b>big-endian</b> so that
 * {@link DataInputStream} reads it without byte-swapping:
 * <pre>
 * "DDM1"                          magic
 * u16                             part count
 *   u16 + utf8                    part name
 *   u16 + utf8                    base texture id
 *   u16 + utf8                    sphere-map texture id, empty when none
 *   f32                           shininess
 *   f32[3] f32[3] f32[3]          diffuse, ambient, specular
 *   u32                           vertex count
 *     f32[8]                      px py pz  nx ny nz  u v
 *   u32                           triangle count
 *     u32[3]                      indices
 * </pre>
 * Loaded once and cached. These are static props read out of a 2004 disc; they
 * do not change between frames and there is no reason to parse them twice.
 */
public final class StatueMeshLoader
{
    private static final org.apache.logging.log4j.Logger LOGGER =
        org.apache.logging.log4j.LogManager.getLogger();

    private static final byte[] MAGIC = {'D', 'D', 'M', '1'};
    /** Sanity ceilings, so a corrupt file fails loudly instead of allocating forever. */
    private static final int MAX_PARTS = 64;
    private static final int MAX_VERTICES = 1 << 20;

    private static final Map<String, StatueMesh> CACHE = new HashMap<>();

    private StatueMeshLoader()
    {
    }

    /** Drops the cache, so a resource reload picks up a re-export. */
    public static synchronized void clear()
    {
        CACHE.clear();
    }

    /**
     * @param name one of {@code podium}, {@code crown}, {@code obelisk},
     *             {@code slifer}, {@code ra}
     * @return the mesh, or null if it is missing or malformed -- callers draw
     *         nothing rather than taking the screen down
     */
    public static synchronized StatueMesh get(String name)
    {
        StatueMesh cached = CACHE.get(name);
        if(cached != null)
        {
            return cached;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
            de.cas_ual_ty.dueldimension.DuelDimension.MOD_ID,
            "models/statue/" + name + ".ddm");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
            .getResource(id);
        if(resource.isEmpty())
        {
            LOGGER.warn("statue mesh {} is missing", id);
            return null;
        }
        try(InputStream raw = resource.get().open();
            DataInputStream in = new DataInputStream(new BufferedInputStream(raw)))
        {
            StatueMesh mesh = read(in);
            CACHE.put(name, mesh);
            return mesh;
        }
        catch(IOException | RuntimeException e)
        {
            LOGGER.warn("statue mesh {} could not be read", id, e);
            return null;
        }
    }

    private static StatueMesh read(DataInputStream in) throws IOException
    {
        byte[] magic = new byte[4];
        in.readFully(magic);
        for(int i = 0; i < 4; i++)
        {
            if(magic[i] != MAGIC[i])
            {
                throw new IOException("not a .ddm");
            }
        }
        int partCount = in.readUnsignedShort();
        if(partCount > MAX_PARTS)
        {
            throw new IOException("implausible part count " + partCount);
        }
        List<StatueMesh.Part> parts = new ArrayList<>(partCount);
        for(int p = 0; p < partCount; p++)
        {
            String name = utf(in);
            String texture = utf(in);
            String env = utf(in);
            float shininess = in.readFloat();
            float[] diffuse = triple(in);
            float[] ambient = triple(in);
            float[] specular = triple(in);

            int vertexCount = in.readInt();
            if(vertexCount < 0 || vertexCount > MAX_VERTICES)
            {
                throw new IOException("implausible vertex count " + vertexCount);
            }
            float[] positions = new float[vertexCount * 3];
            float[] normals = new float[vertexCount * 3];
            float[] uvs = new float[vertexCount * 2];
            for(int v = 0; v < vertexCount; v++)
            {
                positions[v * 3] = in.readFloat();
                positions[v * 3 + 1] = in.readFloat();
                positions[v * 3 + 2] = in.readFloat();
                normals[v * 3] = in.readFloat();
                normals[v * 3 + 1] = in.readFloat();
                normals[v * 3 + 2] = in.readFloat();
                uvs[v * 2] = in.readFloat();
                uvs[v * 2 + 1] = in.readFloat();
            }

            int triangleCount = in.readInt();
            if(triangleCount < 0 || triangleCount > MAX_VERTICES)
            {
                throw new IOException("implausible triangle count " + triangleCount);
            }
            int[] indices = new int[triangleCount * 3];
            for(int i = 0; i < indices.length; i++)
            {
                int index = in.readInt();
                if(index < 0 || index >= vertexCount)
                {
                    throw new IOException("index " + index + " outside " + vertexCount);
                }
                indices[i] = index;
            }

            parts.add(new StatueMesh.Part(name,
                ResourceLocation.parse(texture),
                env.isEmpty() ? null : ResourceLocation.parse(env),
                new StatueMesh.Material(shininess, diffuse, ambient, specular),
                positions, smoothNormals(positions, normals), uvs, indices));
        }
        return new StatueMesh(List.copyOf(parts));
    }

    /**
     * Averages the normals of every vertex sharing a position.
     *
     * <h2>Why the file's normals shade flat</h2>
     * The exporter welds on the whole vertex TUPLE -- position, normal and UV
     * together -- because that is what a vertex buffer needs. One corner of a
     * model therefore appears once per distinct normal or UV meeting there, and
     * a corner where a UV seam runs, or where the source authored hard edges,
     * comes out as several vertices at the same point carrying different
     * normals. Lighting those independently is what makes a curved surface read
     * as facets.
     * <p>
     * This does not re-index anything: the split vertices stay split, because
     * the UVs that split them are still different and merging them would tear
     * the texture. Only the NORMALS are averaged, over every vertex at the same
     * position, so the shading crosses the seam smoothly while the texture does
     * not.
     *
     * <h2>The position key</h2>
     * Quantised to a thousandth of a unit before hashing. Exact float equality
     * would work for vertices the exporter genuinely duplicated -- the bits are
     * copied -- but not for two that arrived from different source primitives
     * and differ in the last place, and those are exactly the seams worth
     * closing. The models are tens of units across, so a thousandth cannot merge
     * two points that were meant to be apart.
     *
     * @return a new array; the caller's is not modified
     */
    private static float[] smoothNormals(float[] positions, float[] normals)
    {
        int count = positions.length / 3;
        java.util.Map<Long, float[]> sums = new java.util.HashMap<>(count * 2);
        long[] keys = new long[count];
        for(int v = 0; v < count; v++)
        {
            long key = key(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2]);
            keys[v] = key;
            float[] sum = sums.computeIfAbsent(key, k -> new float[3]);
            sum[0] += normals[v * 3];
            sum[1] += normals[v * 3 + 1];
            sum[2] += normals[v * 3 + 2];
        }

        float[] out = new float[normals.length];
        for(int v = 0; v < count; v++)
        {
            float[] sum = sums.get(keys[v]);
            double length = Math.sqrt(sum[0] * sum[0] + sum[1] * sum[1] + sum[2] * sum[2]);
            if(length < 1.0E-6)
            {
                // Opposed normals that cancelled -- a zero-thickness fin, or a
                // point where a surface folds back on itself. Averaging says
                // nothing there, so the vertex keeps the normal it came with.
                out[v * 3] = normals[v * 3];
                out[v * 3 + 1] = normals[v * 3 + 1];
                out[v * 3 + 2] = normals[v * 3 + 2];
                continue;
            }
            out[v * 3] = (float)(sum[0] / length);
            out[v * 3 + 1] = (float)(sum[1] / length);
            out[v * 3 + 2] = (float)(sum[2] / length);
        }
        return out;
    }

    /** A position rounded to a thousandth, packed into one long. */
    private static long key(float x, float y, float z)
    {
        long qx = Math.round(x * 1000.0);
        long qy = Math.round(y * 1000.0);
        long qz = Math.round(z * 1000.0);
        return (qx * 73_856_093L) ^ (qy * 19_349_663L) ^ (qz * 83_492_791L);
    }

    private static String utf(DataInputStream in) throws IOException
    {
        int length = in.readUnsignedShort();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static float[] triple(DataInputStream in) throws IOException
    {
        return new float[] {in.readFloat(), in.readFloat(), in.readFloat()};
    }
}
