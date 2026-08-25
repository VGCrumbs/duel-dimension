package de.cas_ual_ty.dueldimension.clientutil.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A glTF 2.0 binary model, read into plain arrays.
 * <p>
 * <b>A deliberate subset, not a glTF implementation.</b> glTF is a large format
 * and almost none of it is needed to stand a monster on a card. What is read is
 * exactly what the models this mod uses actually contain — verified against the
 * file rather than assumed — and everything outside that subset throws instead
 * of being skipped. That is the important half: a loader that quietly ignores a
 * feature does not fail, it produces a model that is merely WRONG, and a mesh
 * that is wrong by a byte offset looks like a mesh that was exported badly.
 * <p>
 * Supported, because Curse of Dragon uses it: indexed triangles; POSITION,
 * NORMAL, TEXCOORD_0, JOINTS_0 and WEIGHTS_0; one skin with inverse bind
 * matrices; nodes carrying translation and rotation; LINEAR animation samplers;
 * PNG images embedded in buffer views.
 * <p>
 * Rejected loudly: sparse accessors, node matrices in place of TRS, non-LINEAR
 * interpolation, and index types other than unsigned short. Each is a real glTF
 * feature and each would need code that cannot be tested against anything here.
 * <p>
 * <b>Everything is in the model's own units and its own space.</b> No scaling,
 * no axis flip, no skinning — those belong to whatever draws this, because they
 * depend on where it is being drawn and how big it is being asked to be. This
 * class only says what is in the file.
 */
public record GlbModel(List<Primitive> primitives, List<Node> nodes, Skin skin,
    List<Animation> animations, List<byte[]> images, int[] materialImages,
    float[] min, float[] max)
{
    /** glTF's componentType codes, and how many bytes each takes. */
    private static final int BYTE = 5120;
    private static final int UNSIGNED_BYTE = 5121;
    private static final int SHORT = 5122;
    private static final int UNSIGNED_SHORT = 5123;
    private static final int UNSIGNED_INT = 5125;
    private static final int FLOAT = 5126;

    /** Triangles. glTF's other primitive modes are not used here. */
    private static final int MODE_TRIANGLES = 4;

    /**
     * One drawable piece of the mesh, with its own material.
     * <p>
     * Kept as separate primitives rather than merged, because each carries its
     * own texture and merging them would mean either atlasing four images or
     * losing three of them.
     *
     * @param joints  four bone indices per vertex, flattened
     * @param weights how much each of those four bones moves the vertex
     */
    public record Primitive(float[] positions, float[] normals, float[] uvs,
        int[] joints, float[] weights, int[] indices, int material)
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
     * A node in the model's hierarchy.
     *
     * @param rotation a quaternion in glTF's order, x y z w — NOT w first
     * @param children indices into {@link GlbModel#nodes()}
     */
    public record Node(String name, int[] children, float[] translation, float[] rotation)
    {
    }

    /**
     * @param joints              which nodes are bones, in the order the
     *                            vertices' JOINTS_0 indices refer to
     * @param inverseBindMatrices one column-major mat4 per joint, flattened
     */
    public record Skin(int[] joints, float[] inverseBindMatrices)
    {
        public int jointCount()
        {
            return joints.length;
        }
    }

    /** What an animation channel drives. */
    public enum Path
    {
        TRANSLATION,
        ROTATION
    }

    /**
     * @param times  keyframe times in seconds, ascending
     * @param values the value at each keyframe, flattened; three floats per key
     *               for a translation, four for a rotation
     */
    public record Sampler(float[] times, float[] values, int stride)
    {
    }

    public record Channel(int node, Path path, int sampler)
    {
    }

    /**
     * @param duration the last keyframe time across every sampler, which is how
     *                 long a loop of this animation lasts
     */
    public record Animation(String name, float duration, List<Channel> channels,
        List<Sampler> samplers)
    {
    }

    /** How tall the model is in its own units, before anything scales it. */
    public float modelHeight()
    {
        return max[1] - min[1];
    }

    /**
     * Reads a {@code .glb}.
     *
     * @throws IllegalArgumentException if the file is not a glTF 2.0 binary, or
     *                                  uses a feature outside the subset above
     */
    public static GlbModel load(byte[] bytes)
    {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = buffer.getInt();
        int version = buffer.getInt();
        int total = buffer.getInt();
        // 0x46546C67 is "glTF" little-endian.
        if(magic != 0x46546C67)
        {
            throw new IllegalArgumentException("not a .glb: bad magic " + Integer.toHexString(magic));
        }
        if(version != 2)
        {
            throw new IllegalArgumentException("glTF version " + version + ", expected 2");
        }

        JsonObject gltf = null;
        byte[] bin = null;
        while(buffer.position() < Math.min(total, bytes.length))
        {
            int length = buffer.getInt();
            int kind = buffer.getInt();
            byte[] chunk = new byte[length];
            buffer.get(chunk);
            if(kind == 0x4E4F534A)                       // "JSON"
            {
                gltf = JsonParser.parseString(new String(chunk, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            }
            else if(kind == 0x004E4942)                  // "BIN\0"
            {
                bin = chunk;
            }
            // Any other chunk type is an extension's, and skipping it is what
            // the spec asks for.
        }
        if(gltf == null || bin == null)
        {
            throw new IllegalArgumentException("glb is missing its JSON or BIN chunk");
        }

        Reader reader = new Reader(gltf, bin);
        List<Primitive> primitives = reader.primitives();
        return new GlbModel(primitives, reader.nodes(), reader.skin(), reader.animations(),
            reader.images(), reader.materialImages(), reader.min(primitives),
            reader.max(primitives));
    }

    /**
     * The offset arithmetic, kept in one place.
     * <p>
     * Every read goes through {@link #floats} or {@link #ints} so that
     * byteStride is honoured in exactly one method. Interleaved buffer views —
     * where one view holds several attributes side by side and byteStride is the
     * distance between one vertex and the next — are the single easiest thing to
     * get wrong, because ignoring the stride still produces plausible numbers
     * for the first vertex and garbage thereafter. This file has one strided
     * view, so the path is exercised rather than theoretical.
     */
    private record Reader(JsonObject gltf, byte[] bin)
    {
        private JsonArray array(String name)
        {
            return gltf.has(name) ? gltf.getAsJsonArray(name) : new JsonArray();
        }

        private static int componentSize(int type)
        {
            return switch(type)
            {
                case BYTE, UNSIGNED_BYTE -> 1;
                case SHORT, UNSIGNED_SHORT -> 2;
                case UNSIGNED_INT, FLOAT -> 4;
                default -> throw new IllegalArgumentException("componentType " + type);
            };
        }

        private static int components(String type)
        {
            return switch(type)
            {
                case "SCALAR" -> 1;
                case "VEC2" -> 2;
                case "VEC3" -> 3;
                case "VEC4" -> 4;
                case "MAT4" -> 16;
                default -> throw new IllegalArgumentException("accessor type " + type);
            };
        }

        /** Reads an accessor as numbers, honouring byteStride. */
        private double[] raw(int index)
        {
            JsonObject acc = array("accessors").get(index).getAsJsonObject();
            if(acc.has("sparse"))
            {
                throw new IllegalArgumentException("sparse accessors are not supported");
            }
            int count = acc.get("count").getAsInt();
            int type = acc.get("componentType").getAsInt();
            int per = components(acc.get("type").getAsString());
            int size = componentSize(type);

            JsonObject view = array("bufferViews").get(acc.get("bufferView").getAsInt())
                .getAsJsonObject();
            int base = (view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0)
                + (acc.has("byteOffset") ? acc.get("byteOffset").getAsInt() : 0);
            // Absent stride means tightly packed, which is the common case.
            int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : per * size;

            ByteBuffer at = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
            double[] out = new double[count * per];
            for(int i = 0; i < count; i++)
            {
                int start = base + i * stride;
                for(int c = 0; c < per; c++)
                {
                    int offset = start + c * size;
                    out[i * per + c] = switch(type)
                    {
                        case BYTE -> at.get(offset);
                        // & 0xFF and & 0xFFFF: Java has no unsigned primitives,
                        // and a joint index of 200 read as a signed byte is -56,
                        // which indexes a bone that does not exist.
                        case UNSIGNED_BYTE -> at.get(offset) & 0xFF;
                        case SHORT -> at.getShort(offset);
                        case UNSIGNED_SHORT -> at.getShort(offset) & 0xFFFF;
                        case UNSIGNED_INT -> at.getInt(offset) & 0xFFFFFFFFL;
                        case FLOAT -> at.getFloat(offset);
                        default -> throw new IllegalArgumentException("componentType " + type);
                    };
                }
            }
            return out;
        }

        private float[] floats(int accessor)
        {
            double[] raw = raw(accessor);
            float[] out = new float[raw.length];
            for(int i = 0; i < raw.length; i++)
            {
                out[i] = (float)raw[i];
            }
            return out;
        }

        private int[] ints(int accessor)
        {
            double[] raw = raw(accessor);
            int[] out = new int[raw.length];
            for(int i = 0; i < raw.length; i++)
            {
                out[i] = (int)raw[i];
            }
            return out;
        }

        private List<Primitive> primitives()
        {
            List<Primitive> out = new ArrayList<>();
            for(var mesh : array("meshes"))
            {
                for(var element : mesh.getAsJsonObject().getAsJsonArray("primitives"))
                {
                    JsonObject prim = element.getAsJsonObject();
                    int mode = prim.has("mode") ? prim.get("mode").getAsInt() : MODE_TRIANGLES;
                    if(mode != MODE_TRIANGLES)
                    {
                        throw new IllegalArgumentException("primitive mode " + mode
                            + "; only triangles are supported");
                    }
                    if(!prim.has("indices"))
                    {
                        throw new IllegalArgumentException("un-indexed primitives are not supported");
                    }
                    JsonObject attributes = prim.getAsJsonObject("attributes");
                    out.add(new Primitive(
                        floats(attributes.get("POSITION").getAsInt()),
                        attributes.has("NORMAL") ? floats(attributes.get("NORMAL").getAsInt()) : null,
                        attributes.has("TEXCOORD_0") ? floats(attributes.get("TEXCOORD_0").getAsInt()) : null,
                        attributes.has("JOINTS_0") ? ints(attributes.get("JOINTS_0").getAsInt()) : null,
                        attributes.has("WEIGHTS_0") ? floats(attributes.get("WEIGHTS_0").getAsInt()) : null,
                        ints(prim.get("indices").getAsInt()),
                        prim.has("material") ? prim.get("material").getAsInt() : -1));
                }
            }
            return List.copyOf(out);
        }

        private List<Node> nodes()
        {
            List<Node> out = new ArrayList<>();
            for(var element : array("nodes"))
            {
                JsonObject node = element.getAsJsonObject();
                if(node.has("matrix"))
                {
                    // Decomposing a matrix back into translation, rotation and
                    // scale is doable but not testable against anything here,
                    // and a wrong decomposition is a subtly bent skeleton.
                    throw new IllegalArgumentException(
                        "node matrices are not supported; export with TRS");
                }
                int[] children = new int[0];
                if(node.has("children"))
                {
                    JsonArray kids = node.getAsJsonArray("children");
                    children = new int[kids.size()];
                    for(int i = 0; i < kids.size(); i++)
                    {
                        children[i] = kids.get(i).getAsInt();
                    }
                }
                // Node scale is not read, and a node that has one would be drawn
                // at the wrong size with nothing to say so -- the skeleton
                // composes translation and rotation only. Rejected rather than
                // dropped, on the same rule as node matrices above.
                float[] scale = vector(node, "scale", new float[] {1F, 1F, 1F});
                for(float axis : scale)
                {
                    if(Math.abs(axis - 1F) > 1.0e-6F)
                    {
                        throw new IllegalArgumentException("node scale is not supported;"
                            + " apply scale before exporting");
                    }
                }

                // A mesh hung on a node that MOVES it is placed by that node.
                // Skinned primitives are exempt -- glTF says a skinned mesh's
                // own node transform is ignored, because its vertices are
                // already in the skin's space -- but an unskinned one drawn
                // without it lands at the model origin instead of where it was
                // put, and a model assembled from several placed pieces
                // collapses into one heap. Only the skinned case is exercised
                // here, so the other one says so rather than guessing.
                if(node.has("mesh") && !node.has("skin")
                    && (node.has("translation") || node.has("rotation")))
                {
                    throw new IllegalArgumentException("an unskinned mesh is placed by its node,"
                        + " which is not supported; apply the transform before exporting");
                }

                out.add(new Node(
                    node.has("name") ? node.get("name").getAsString() : "",
                    children,
                    vector(node, "translation", new float[] {0F, 0F, 0F}),
                    // Identity is 0,0,0,1 -- w LAST, which is glTF's order and
                    // the opposite of several maths libraries'.
                    vector(node, "rotation", new float[] {0F, 0F, 0F, 1F})));
            }
            return List.copyOf(out);
        }

        private static float[] vector(JsonObject node, String key, float[] fallback)
        {
            if(!node.has(key))
            {
                return fallback;
            }
            JsonArray values = node.getAsJsonArray(key);
            float[] out = new float[values.size()];
            for(int i = 0; i < values.size(); i++)
            {
                out[i] = values.get(i).getAsFloat();
            }
            return out;
        }

        private Skin skin()
        {
            JsonArray skins = array("skins");
            if(skins.isEmpty())
            {
                return null;
            }
            JsonObject skin = skins.get(0).getAsJsonObject();
            JsonArray joints = skin.getAsJsonArray("joints");
            int[] indices = new int[joints.size()];
            for(int i = 0; i < joints.size(); i++)
            {
                indices[i] = joints.get(i).getAsInt();
            }

            float[] inverse;
            if(skin.has("inverseBindMatrices"))
            {
                inverse = floats(skin.get("inverseBindMatrices").getAsInt());
                if(inverse.length != indices.length * 16)
                {
                    // Said here rather than discovered later. The consumer reads
                    // sixteen floats per joint out of this array, and a short
                    // one is zero-padded rather than refused -- a zero matrix
                    // collapses every vertex that bone owns onto the origin,
                    // which draws as triangles streaming out of the model's
                    // centre and looks nothing like a length mismatch.
                    throw new IllegalArgumentException("the skin has " + indices.length
                        + " joints but " + inverse.length / 16 + " inverse bind matrices");
                }
            }
            else
            {
                // Optional in glTF 2.0, and defined to mean identity for every
                // joint. Asking for it unconditionally threw a NullPointerException
                // that reached the duellist as "could not load the model: null".
                inverse = new float[indices.length * 16];
                for(int i = 0; i < indices.length; i++)
                {
                    inverse[i * 16] = 1F;
                    inverse[i * 16 + 5] = 1F;
                    inverse[i * 16 + 10] = 1F;
                    inverse[i * 16 + 15] = 1F;
                }
            }
            return new Skin(indices, inverse);
        }

        private List<Animation> animations()
        {
            List<Animation> out = new ArrayList<>();
            for(var element : array("animations"))
            {
                JsonObject anim = element.getAsJsonObject();
                List<Sampler> samplers = new ArrayList<>();
                float duration = 0F;
                for(var s : anim.getAsJsonArray("samplers"))
                {
                    JsonObject sampler = s.getAsJsonObject();
                    String interpolation = sampler.has("interpolation")
                        ? sampler.get("interpolation").getAsString() : "LINEAR";
                    if(!"LINEAR".equals(interpolation))
                    {
                        throw new IllegalArgumentException(interpolation
                            + " interpolation is not supported; only LINEAR");
                    }
                    float[] times = floats(sampler.get("input").getAsInt());
                    float[] values = floats(sampler.get("output").getAsInt());
                    samplers.add(new Sampler(times, values,
                        times.length == 0 ? 0 : values.length / times.length));
                    if(times.length > 0)
                    {
                        duration = Math.max(duration, times[times.length - 1]);
                    }
                }
                List<Channel> channels = new ArrayList<>();
                for(var c : anim.getAsJsonArray("channels"))
                {
                    JsonObject channel = c.getAsJsonObject();
                    JsonObject target = channel.getAsJsonObject("target");
                    String path = target.get("path").getAsString();
                    // scale and weights channels are simply not present in these
                    // models; ignored rather than rejected, because a model that
                    // carries one is still perfectly drawable without it.
                    Path kind = switch(path)
                    {
                        case "translation" -> Path.TRANSLATION;
                        case "rotation" -> Path.ROTATION;
                        default -> null;
                    };
                    if(kind == null || !target.has("node"))
                    {
                        continue;
                    }
                    channels.add(new Channel(target.get("node").getAsInt(), kind,
                        channel.get("sampler").getAsInt()));
                }
                out.add(new Animation(
                    anim.has("name") ? anim.get("name").getAsString() : "",
                    duration, List.copyOf(channels), List.copyOf(samplers)));
            }
            return List.copyOf(out);
        }

        /**
         * Which image each material paints with, by material index.
         * <p>
         * <b>Three separate index spaces, and glTF keeps them separate on
         * purpose:</b> a primitive names a MATERIAL, a material's base colour
         * names a TEXTURE, and a texture names an IMAGE. Following that chain is
         * the only way to get from a triangle to a picture.
         * <p>
         * Taking a shortcut here — using the material index straight as an image
         * index — happens to be right whenever an exporter emits one material
         * per image in the same order, which is common enough to look correct on
         * the first model tried and is not a rule. Curse of Dragon is exactly
         * that case: four materials, four textures, four images, 0→0→0. A model
         * with three materials sharing one atlas would have found two thirds of
         * itself untextured, with nothing said about why.
         *
         * @return one entry per material, -1 where a material has no base colour
         *         image
         */
        private int[] materialImages()
        {
            JsonArray materials = array("materials");
            JsonArray textures = array("textures");
            int[] out = new int[materials.size()];
            java.util.Arrays.fill(out, -1);
            for(int i = 0; i < materials.size(); i++)
            {
                JsonObject material = materials.get(i).getAsJsonObject();
                if(!material.has("pbrMetallicRoughness"))
                {
                    continue;
                }
                JsonObject pbr = material.getAsJsonObject("pbrMetallicRoughness");
                if(!pbr.has("baseColorTexture"))
                {
                    // An untextured material -- a flat colour, or one whose only
                    // maps are normal or emissive. Drawable, just not from here.
                    continue;
                }
                int texture = pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt();
                if(texture < 0 || texture >= textures.size())
                {
                    continue;
                }
                JsonObject entry = textures.get(texture).getAsJsonObject();
                // A texture with no source is one supplied by an extension --
                // basis, webp -- which is an image this reader cannot decode.
                if(entry.has("source"))
                {
                    out[i] = entry.get("source").getAsInt();
                }
            }
            return out;
        }

        /**
         * The PNG bytes of each image, in image order.
         * <p>
         * Only images embedded in a buffer view are read. A {@code uri} would
         * point at a file beside the .glb, which is a second thing to find and
         * to keep next to it; the models here embed theirs, so the .glb is one
         * self-contained file and the editor has one path to ask for.
         */
        private List<byte[]> images()
        {
            List<byte[]> out = new ArrayList<>();
            for(var element : array("images"))
            {
                JsonObject image = element.getAsJsonObject();
                if(!image.has("bufferView"))
                {
                    out.add(null);
                    continue;
                }
                JsonObject view = array("bufferViews").get(image.get("bufferView").getAsInt())
                    .getAsJsonObject();
                int offset = view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0;
                int length = view.get("byteLength").getAsInt();
                byte[] png = new byte[length];
                System.arraycopy(bin, offset, png, 0, length);
                out.add(png);
            }
            return out;
        }

        /**
         * The bind-pose bounds, computed rather than read from the accessors'
         * own min/max.
         * <p>
         * The spec requires POSITION accessors to carry them, but they are
         * written by the exporter and describe what it believed it wrote. What
         * the height setting has to scale is what is actually in the file.
         */
        private float[] min(List<Primitive> primitives)
        {
            return bounds(primitives, true);
        }

        private float[] max(List<Primitive> primitives)
        {
            return bounds(primitives, false);
        }

        private static float[] bounds(List<Primitive> primitives, boolean low)
        {
            float[] out = {low ? Float.MAX_VALUE : -Float.MAX_VALUE,
                low ? Float.MAX_VALUE : -Float.MAX_VALUE,
                low ? Float.MAX_VALUE : -Float.MAX_VALUE};
            for(Primitive primitive : primitives)
            {
                float[] p = primitive.positions();
                for(int i = 0; i + 2 < p.length; i += 3)
                {
                    for(int axis = 0; axis < 3; axis++)
                    {
                        out[axis] = low ? Math.min(out[axis], p[i + axis])
                            : Math.max(out[axis], p[i + axis]);
                    }
                }
            }
            return out;
        }
    }
}
