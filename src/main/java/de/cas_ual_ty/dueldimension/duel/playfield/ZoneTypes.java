package de.cas_ual_ty.dueldimension.duel.playfield;

import de.cas_ual_ty.dueldimension.DdDuelRegistries;
import de.cas_ual_ty.dueldimension.DdDuelRegistries;
import de.cas_ual_ty.dueldimension.DdDuelRegistries;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.core.Registry;
import net.minecraft.core.Registry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public class ZoneTypes
{
    
    public static final ZoneType HAND = register("hand", new ZoneType().showFaceDownCardsToOwner());
    public static final ZoneType DECK = register("deck", new ZoneType().secret().keepFocusedAfterInteraction().defaultCardPosition(CardPosition.FD));
    public static final ZoneType SPELL_TRAP = register("spell_trap", new ZoneType().canHaveCounters());
    public static final ZoneType EXTRA_DECK = register("extra_deck", new ZoneType().keepFocusedAfterInteraction());
    public static final ZoneType GRAVEYARD = register("graveyard", new ZoneType().strict().keepFocusedAfterInteraction());
    public static final ZoneType MONSTER = register("monster", new ZoneType().allowSideways().canHaveCounters());
    public static final ZoneType FIELD_SPELL = register("field_spell", new ZoneType().canHaveCounters());
    public static final ZoneType BANISHED = register("banished", new ZoneType().strict().keepFocusedAfterInteraction());
    public static final ZoneType EXTRA = register("extra", new ZoneType().keepFocusedAfterInteraction());
    public static final ZoneType EXTRA_MONSTER_RIGHT = register("extra_monster_right", new ZoneType().noOwner().canHaveCounters());
    public static final ZoneType EXTRA_MONSTER_LEFT = register("extra_monster_left", new ZoneType().noOwner().canHaveCounters());
    
    private static ZoneType register(String name, ZoneType entry)
    {
        return Registry.register(DdDuelRegistries.ZONE_TYPES,
            Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, name), entry);
    }

    /**
     * Loads this class, which is what registers everything in it.
     * <p>
     * The entries are static fields, so they are written to the registry by the
     * class initialiser. Forge needed an event bus here; this needs only to be
     * called.
     */
    public static void register()
    {
    }
}