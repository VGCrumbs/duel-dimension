package de.cas_ual_ty.dueldimension.duel.action;

import de.cas_ual_ty.dueldimension.DdDuelRegistries;
import de.cas_ual_ty.dueldimension.DdDuelRegistries;
import de.cas_ual_ty.dueldimension.DdDuelRegistries;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.core.Registry;
import net.minecraft.core.Registry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public class ActionIcons
{
    
    public static final ActionIcon TO_TOP_OF_DECK_FD = register("to_top_of_deck_fd", ActionIcons.create("to_top_of_deck_fd"));
    public static final ActionIcon TO_BOTTOM_OF_DECK_FD = register("to_bottom_of_deck_fd", ActionIcons.create("to_bottom_of_deck_fd"));
    public static final ActionIcon TO_TOP_OF_DECK_ATK = register("to_top_of_deck_atk", ActionIcons.create("to_top_of_deck_atk"));
    public static final ActionIcon NORMAL_SUMMON = register("normal_summon", ActionIcons.create("normal_summon"));
    public static final ActionIcon SET = register("set", ActionIcons.create("set"));
    public static final ActionIcon SPECIAL_SUMMON_ATK = register("special_summon_atk", ActionIcons.create("special_summon_atk"));
    public static final ActionIcon SPECIAL_SUMMON_DEF = register("special_summon_def", ActionIcons.create("special_summon_def"));
    public static final ActionIcon SPECIAL_SUMMON_SET = register("special_summon_set", ActionIcons.create("special_summon_set"));
    public static final ActionIcon ATK_TO_DEF = register("atk_to_def", ActionIcons.create("atk_to_def"));
    public static final ActionIcon ATK_TO_SET = register("atk_to_set", ActionIcons.create("atk_to_set"));
    public static final ActionIcon DEF_SET_TO_ATK = register("def_set_to_atk", ActionIcons.create("def_set_to_atk"));
    public static final ActionIcon SET_TO_DEF = register("set_to_def", ActionIcons.create("set_to_def"));
    public static final ActionIcon DEF_TO_SET = register("def_to_set", ActionIcons.create("def_to_set"));
    public static final ActionIcon BANISH_ATK = register("banish_atk", ActionIcons.create("banish_atk"));
    public static final ActionIcon BANISH_FD = register("banish_fd", ActionIcons.create("banish_fd"));
    public static final ActionIcon ACTIVATE_SPELL_TRAP = register("activate_spell_trap", ActionIcons.create("activate_spell_trap"));
    public static final ActionIcon SET_SPELL_TRAP = register("set_spell_trap", ActionIcons.create("set_spell_trap"));
    public static final ActionIcon OVERLAY = register("overlay", ActionIcons.create("overlay"));
    public static final ActionIcon UNDERLAY = register("underlay", ActionIcons.create("underlay"));
    public static final ActionIcon SPECIAL_SUMMON_OVERLAY_ATK = register("special_summon_overlay_atk", ActionIcons.create("special_summon_overlay_atk"));
    public static final ActionIcon SPECIAL_SUMMON_OVERLAY_DEF = register("special_summon_overlay_def", ActionIcons.create("special_summon_overlay_def"));
    public static final ActionIcon ADD_TO_HAND = register("add_to_hand", ActionIcons.create("add_to_hand"));
    public static final ActionIcon SHUFFLE_DECK = register("shuffle_deck", ActionIcons.create("shuffle_deck"));
    public static final ActionIcon SHUFFLE_HAND = register("shuffle_hand", ActionIcons.create("shuffle_hand"));
    public static final ActionIcon VIEW_DECK = register("view_deck", ActionIcons.create("view_deck"));
    public static final ActionIcon SHOW_HAND = register("show_hand", ActionIcons.create("show_hand"));
    public static final ActionIcon SHOW_DECK = register("show_deck", ActionIcons.create("show_deck"));
    public static final ActionIcon SHOW_CARD = register("show_card", ActionIcons.create("show_card"));
    public static final ActionIcon MOVE = register("move", ActionIcons.create("move"));
    public static final ActionIcon TO_GRAVEYARD = register("to_graveyard", ActionIcons.create("to_graveyard"));
    public static final ActionIcon ATTACK = register("attack", ActionIcons.create("attack"));
    public static final ActionIcon ATTACK_DIRECTLY = register("attack_directly", ActionIcons.create("attack_directly"));
    public static final ActionIcon TO_EXTRA_ATK = register("to_extra_atk", ActionIcons.create("to_extra_atk"));
    public static final ActionIcon TO_EXTRA_FD = register("to_extra_fd", ActionIcons.create("to_extra_fd"));
    public static final ActionIcon REMOVE_TOKEN_ATK = register("remove_token_atk", ActionIcons.create("remove_token_atk"));
    public static final ActionIcon REMOVE_TOKEN_DEF = register("remove_token_def", ActionIcons.create("remove_token_def"));
    
    public static final ActionIcon ALL_TO_GRAVEYARD = register("all_to_graveyard", ActionIcons.create("all_to_graveyard"));
    public static final ActionIcon BANISH_ALL_ATK = register("banish_all_atk", ActionIcons.create("banish_all_atk"));
    public static final ActionIcon BANISH_ALL_FD = register("banish_all_fd", ActionIcons.create("banish_all_fd"));
    public static final ActionIcon SPECIAL_SUMMON_TOKEN_ATK = register("special_summon_token_atk", ActionIcons.create("special_summon_token_atk"));
    public static final ActionIcon SPECIAL_SUMMON_TOKEN_DEF = register("special_summon_token_def", ActionIcons.create("special_summon_token_def"));
    
    private static ActionIcon register(String name, ActionIcon entry)
    {
        return Registry.register(DdDuelRegistries.ACTION_ICONS,
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
    
    public static ActionIcon create(String name)
    {
        return new ActionIcon(Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/action_icons/" + name + ".png"), 64);
    }
}