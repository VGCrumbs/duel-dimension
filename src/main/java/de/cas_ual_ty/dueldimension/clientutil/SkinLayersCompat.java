package de.cas_ual_ty.dueldimension.clientutil;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional bridge to 3D Skin Layers for player-shaped things which are not
 * players. The mod's own mixins deliberately start from AbstractClientPlayer;
 * an outfit is a second model and a DuelistEntity is a mob, so neither enters
 * that path even though both use a normal 64x64 player skin.
 * <p>
 * Reflection keeps 3D Skin Layers optional. When it is installed, all mesh
 * construction and offsets still come from its public API, including its
 * voxel-size configuration. Without it these calls are no-ops and the same
 * models retain their ordinary flat outer layers.
 */
public final class SkinLayersCompat
{
    private record Key(Identifier texture, boolean slim)
    {
    }

    private record Meshes(Object head, Object body, Object leftArm, Object rightArm,
        Object leftLeg, Object rightLeg)
    {
    }

    private record SkinMeshes(Meshes outer, Meshes base)
    {
    }

    private static final Map<Key, SkinMeshes> CACHE = new ConcurrentHashMap<>();
    private static final java.util.Set<Key> FAILED = ConcurrentHashMap.newKeySet();
    private static final boolean AVAILABLE;
    private static final Object HELPER;
    private static final Method CREATE_MESH;
    private static final Method SET_MESH;
    private static final Method SET_IGNORED;
    private static final Object HEAD;
    private static final Object BODY;
    private static final Object LEFT_ARM;
    private static final Object LEFT_ARM_SLIM;
    private static final Object RIGHT_ARM;
    private static final Object RIGHT_ARM_SLIM;
    private static final Object FIRST_PERSON_LEFT_ARM;
    private static final Object FIRST_PERSON_LEFT_ARM_SLIM;
    private static final Object FIRST_PERSON_RIGHT_ARM;
    private static final Object FIRST_PERSON_RIGHT_ARM_SLIM;
    private static final Object LEFT_LEG;
    private static final Object RIGHT_LEG;
    private static final Object CONFIG;

    static
    {
        Object helper = null;
        Method create = null;
        Method inject = null;
        Method ignore = null;
        Object head = null;
        Object body = null;
        Object leftArm = null;
        Object leftArmSlim = null;
        Object rightArm = null;
        Object rightArmSlim = null;
        Object firstPersonLeftArm = null;
        Object firstPersonLeftArmSlim = null;
        Object firstPersonRightArm = null;
        Object firstPersonRightArmSlim = null;
        Object leftLeg = null;
        Object rightLeg = null;
        Object config = null;
        boolean available = false;
        try
        {
            if(FabricLoader.getInstance().isModLoaded("skinlayers3d"))
            {
                Class<?> api = Class.forName("dev.tr7zw.skinlayers.api.SkinLayersAPI");
                Class<?> mesh = Class.forName("dev.tr7zw.skinlayers.api.Mesh");
                Class<?> meshHelper = Class.forName("dev.tr7zw.skinlayers.api.MeshHelper");
                Class<?> offset = Class.forName("dev.tr7zw.skinlayers.api.OffsetProvider");
                Class<?> injector = Class.forName("dev.tr7zw.skinlayers.accessor.ModelPartInjector");
                Class<?> modelAccessor =
                    Class.forName("dev.tr7zw.skinlayers.accessor.PlayerEntityModelAccessor");
                helper = api.getMethod("getMeshHelper").invoke(null);
                create = meshHelper.getMethod("create3DMesh", NativeImage.class,
                    int.class, int.class, int.class, int.class, int.class,
                    boolean.class, float.class);
                inject = injector.getMethod("setInjectedMesh", mesh, offset);
                ignore = modelAccessor.getMethod("setIgnored", boolean.class);
                head = offset.getField("HEAD").get(null);
                body = offset.getField("BODY").get(null);
                leftArm = offset.getField("LEFT_ARM").get(null);
                leftArmSlim = offset.getField("LEFT_ARM_SLIM").get(null);
                rightArm = offset.getField("RIGHT_ARM").get(null);
                rightArmSlim = offset.getField("RIGHT_ARM_SLIM").get(null);
                firstPersonLeftArm = offset.getField("FIRSTPERSON_LEFT_ARM").get(null);
                firstPersonLeftArmSlim = offset.getField("FIRSTPERSON_LEFT_ARM_SLIM").get(null);
                firstPersonRightArm = offset.getField("FIRSTPERSON_RIGHT_ARM").get(null);
                firstPersonRightArmSlim = offset.getField("FIRSTPERSON_RIGHT_ARM_SLIM").get(null);
                leftLeg = offset.getField("LEFT_LEG").get(null);
                rightLeg = offset.getField("RIGHT_LEG").get(null);
                Class<?> base = Class.forName("dev.tr7zw.skinlayers.SkinLayersModBase");
                // Declared on the public versionless ModBase superclass in
                // 1.11.2; getField deliberately follows that inheritance.
                Field configField = base.getField("config");
                config = configField.get(null);
                available = true;
            }
        }
        catch(ReflectiveOperationException | LinkageError unavailable)
        {
            DuelDimensionLog.warn("3D Skin Layers compatibility could not initialize", unavailable);
        }
        HELPER = helper;
        CREATE_MESH = create;
        SET_MESH = inject;
        SET_IGNORED = ignore;
        HEAD = head;
        BODY = body;
        LEFT_ARM = leftArm;
        LEFT_ARM_SLIM = leftArmSlim;
        RIGHT_ARM = rightArm;
        RIGHT_ARM_SLIM = rightArmSlim;
        FIRST_PERSON_LEFT_ARM = firstPersonLeftArm;
        FIRST_PERSON_LEFT_ARM_SLIM = firstPersonLeftArmSlim;
        FIRST_PERSON_RIGHT_ARM = firstPersonRightArm;
        FIRST_PERSON_RIGHT_ARM_SLIM = firstPersonRightArmSlim;
        LEFT_LEG = leftLeg;
        RIGHT_LEG = rightLeg;
        CONFIG = config;
        AVAILABLE = available;
    }

    private SkinLayersCompat()
    {
    }

    /** Installs the six voxelized outer-skin parts on this particular model. */
    public static void apply(PlayerModel model, Identifier texture, boolean slim)
    {
        if(!AVAILABLE || model == null || texture == null)
        {
            return;
        }
        try
        {
            // This is our model, not the actual player's model. Stop the mod's
            // PlayerModel mixin from replacing these meshes with that player's
            // own skin when the outfit layer copies their render state.
            SET_IGNORED.invoke(model, true);
            Key key = new Key(texture, slim);
            if(FAILED.contains(key))
            {
                return;
            }
            Meshes meshes;
            try
            {
                meshes = CACHE.computeIfAbsent(key, SkinLayersCompat::build).outer();
            }
            catch(RuntimeException buildFailure)
            {
                FAILED.add(key);
                DuelDimensionLog.warn("Could not build 3D skin " + texture, buildFailure);
                return;
            }
            set(model.hat, enabled("enableHat") ? meshes.head() : null, HEAD);
            set(model.jacket, enabled("enableJacket") ? meshes.body() : null, BODY);
            set(model.leftSleeve, enabled("enableLeftSleeve") ? meshes.leftArm() : null,
                slim ? LEFT_ARM_SLIM : LEFT_ARM);
            set(model.rightSleeve, enabled("enableRightSleeve") ? meshes.rightArm() : null,
                slim ? RIGHT_ARM_SLIM : RIGHT_ARM);
            set(model.leftPants, enabled("enableLeftPants") ? meshes.leftLeg() : null, LEFT_LEG);
            set(model.rightPants, enabled("enableRightPants") ? meshes.rightLeg() : null, RIGHT_LEG);
        }
        catch(ReflectiveOperationException compatibilityFailure)
        {
            DuelDimensionLog.warn("Could not apply 3D skin layers to " + texture,
                compatibilityFailure);
        }
    }

    /**
     * Voxelizes an outfit's painted body as well as its conventional outer UVs.
     * An outfit is itself a second model over the player's skin, so pixels in
     * its base regions are clothing, not the body underneath.
     */
    public static void applyOutfit(PlayerModel model, Identifier texture, boolean slim)
    {
        if(!AVAILABLE || model == null || texture == null)
        {
            return;
        }
        try
        {
            SET_IGNORED.invoke(model, true);
            Key key = new Key(texture, slim);
            if(FAILED.contains(key))
            {
                return;
            }
            SkinMeshes skin;
            try
            {
                skin = CACHE.computeIfAbsent(key, SkinLayersCompat::build);
            }
            catch(RuntimeException buildFailure)
            {
                FAILED.add(key);
                DuelDimensionLog.warn("Could not build 3D outfit " + texture, buildFailure);
                return;
            }

            Meshes base = skin.base();
            set(model.head, base.head(), HEAD);
            set(model.body, base.body(), BODY);
            set(model.leftArm, base.leftArm(), slim ? LEFT_ARM_SLIM : LEFT_ARM);
            set(model.rightArm, base.rightArm(), slim ? RIGHT_ARM_SLIM : RIGHT_ARM);
            set(model.leftLeg, base.leftLeg(), LEFT_LEG);
            set(model.rightLeg, base.rightLeg(), RIGHT_LEG);

            Meshes outer = skin.outer();
            set(model.hat, enabled("enableHat") ? outer.head() : null, HEAD);
            set(model.jacket, enabled("enableJacket") ? outer.body() : null, BODY);
            set(model.leftSleeve, enabled("enableLeftSleeve") ? outer.leftArm() : null,
                slim ? LEFT_ARM_SLIM : LEFT_ARM);
            set(model.rightSleeve, enabled("enableRightSleeve") ? outer.rightArm() : null,
                slim ? RIGHT_ARM_SLIM : RIGHT_ARM);
            set(model.leftPants, enabled("enableLeftPants") ? outer.leftLeg() : null, LEFT_LEG);
            set(model.rightPants, enabled("enableRightPants") ? outer.rightLeg() : null, RIGHT_LEG);
        }
        catch(ReflectiveOperationException compatibilityFailure)
        {
            DuelDimensionLog.warn("Could not apply 3D outfit layers to " + texture,
                compatibilityFailure);
        }
    }

    /**
     * Installs sleeve meshes with the mod's first-person scale and arm offsets.
     * This must use a model which is not also submitted in third person: render
     * nodes retain the model, so changing an injected offset after submission
     * would move geometry which has not been drawn yet.
     */
    public static void applyFirstPerson(PlayerModel model, Identifier texture, boolean slim)
    {
        if(!AVAILABLE || model == null || texture == null)
        {
            return;
        }
        try
        {
            SET_IGNORED.invoke(model, true);
            Key key = new Key(texture, slim);
            if(FAILED.contains(key))
            {
                return;
            }
            SkinMeshes skin;
            try
            {
                skin = CACHE.computeIfAbsent(key, SkinLayersCompat::build);
            }
            catch(RuntimeException buildFailure)
            {
                FAILED.add(key);
                DuelDimensionLog.warn("Could not build first-person 3D skin " + texture,
                    buildFailure);
                return;
            }
            set(model.leftArm, skin.base().leftArm(),
                slim ? FIRST_PERSON_LEFT_ARM_SLIM : FIRST_PERSON_LEFT_ARM);
            set(model.rightArm, skin.base().rightArm(),
                slim ? FIRST_PERSON_RIGHT_ARM_SLIM : FIRST_PERSON_RIGHT_ARM);
            set(model.leftSleeve, enabled("enableLeftSleeve") ? skin.outer().leftArm() : null,
                slim ? FIRST_PERSON_LEFT_ARM_SLIM : FIRST_PERSON_LEFT_ARM);
            set(model.rightSleeve, enabled("enableRightSleeve") ? skin.outer().rightArm() : null,
                slim ? FIRST_PERSON_RIGHT_ARM_SLIM : FIRST_PERSON_RIGHT_ARM);
        }
        catch(ReflectiveOperationException compatibilityFailure)
        {
            DuelDimensionLog.warn("Could not apply first-person 3D skin layers to " + texture,
                compatibilityFailure);
        }
    }

    private static SkinMeshes build(Key key)
    {
        try
        {
            var resource = Minecraft.getInstance().getResourceManager().getResource(key.texture())
                .orElseThrow(() -> new IllegalArgumentException("Missing skin " + key.texture()));
            try(InputStream stream = resource.open(); NativeImage image = NativeImage.read(stream))
            {
                if(image.getWidth() != 64 || image.getHeight() != 64)
                {
                    throw new IllegalArgumentException("3D skin must be 64x64: " + key.texture());
                }
                int armWidth = key.slim() ? 3 : 4;
                Meshes outer = new Meshes(
                    mesh(image, 8, 8, 8, 32, 0, false, 0.6F),
                    mesh(image, 8, 12, 4, 16, 32, true, 0F),
                    mesh(image, armWidth, 12, 4, 48, 48, true, -2F),
                    mesh(image, armWidth, 12, 4, 40, 32, true, -2F),
                    mesh(image, 4, 12, 4, 0, 48, true, 0F),
                    mesh(image, 4, 12, 4, 0, 32, true, 0F));
                Meshes base = new Meshes(
                    mesh(image, 8, 8, 8, 0, 0, false, 0.6F),
                    mesh(image, 8, 12, 4, 16, 16, true, 0F),
                    mesh(image, armWidth, 12, 4, 32, 48, true, -2F),
                    mesh(image, armWidth, 12, 4, 40, 16, true, -2F),
                    mesh(image, 4, 12, 4, 16, 48, true, 0F),
                    mesh(image, 4, 12, 4, 0, 16, true, 0F));
                return new SkinMeshes(outer, base);
            }
        }
        catch(Exception failure)
        {
            throw new IllegalStateException("Could not build 3D skin " + key.texture(), failure);
        }
    }

    private static Object mesh(NativeImage image, int width, int height, int depth,
        int textureU, int textureV, boolean mirror, float topPivot) throws ReflectiveOperationException
    {
        return CREATE_MESH.invoke(HELPER, image, width, height, depth,
            textureU, textureV, mirror, topPivot);
    }

    private static void set(ModelPart part, Object mesh, Object offset) throws ReflectiveOperationException
    {
        SET_MESH.invoke(part, mesh, mesh == null ? null : offset);
    }

    private static boolean enabled(String field)
    {
        if(CONFIG == null)
        {
            return true;
        }
        try
        {
            return CONFIG.getClass().getField(field).getBoolean(CONFIG);
        }
        catch(ReflectiveOperationException ignored)
        {
            return true;
        }
    }

    /** Tiny adapter so this optional class does not need its own logger. */
    private static final class DuelDimensionLog
    {
        private static void warn(String message, Throwable error)
        {
            org.apache.logging.log4j.LogManager.getLogger("dueldimension").warn(message, error);
        }
    }
}
