package de.cas_ual_ty.dueldimension.cardbinder;

import de.cas_ual_ty.dueldimension.cardinventory.UUIDCardsManager;
import de.cas_ual_ty.dueldimension.util.DdIOUtil;

import java.io.File;
import java.util.UUID;

/**
 * Port: the Forge tree kept the binders folder on {@code DuelDimension} (set in
 * the {@code @Mod} constructor). That class is the loader-agnostic identity here
 * and does not own folders, so the folder lives with the one feature that uses
 * it — same relative path ({@code ydm_binders}) and same lazy directory
 * creation the Forge init did, so a world written by the Forge build still
 * reads.
 */
public class CardBinderCardsManager extends UUIDCardsManager
{
    public static final File bindersFolder = new File("ydm_binders");

    public CardBinderCardsManager()
    {
        super();
    }

    @Override
    protected File getFile()
    {
        generateUUIDIfNull();
        return CardBinderCardsManager.getBinderFile(getUUID());
    }

    public static File getBinderFile(UUID uuid)
    {
        DdIOUtil.createDirIfNonExistant(bindersFolder);
        return new File(bindersFolder, uuid.toString() + ".json");
    }
}
