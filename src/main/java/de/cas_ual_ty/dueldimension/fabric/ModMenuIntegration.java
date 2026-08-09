package de.cas_ual_ty.dueldimension.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.cas_ual_ty.dueldimension.clientutil.SealConfigScreen;

/**
 * The mod's entry in ModMenu's list, and the screen behind its config button.
 * <p>
 * <b>Nothing else in the mod references this class, and nothing should.</b>
 * ModMenu is a compile-only dependency, so these two imports do not resolve at
 * runtime unless it is installed. That is safe only because the loader keeps
 * entrypoint values as plain strings and classloads them on {@code getOrCreate}
 * — so with ModMenu absent, this class is never loaded and the missing types are
 * never looked up. A single reference from a class that IS loaded would turn
 * that into a NoClassDefFoundError on startup for everyone without ModMenu.
 */
public class ModMenuIntegration implements ModMenuApi
{
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory()
    {
        return SealConfigScreen::new;
    }
}
