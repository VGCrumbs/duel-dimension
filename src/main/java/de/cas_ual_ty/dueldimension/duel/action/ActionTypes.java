package de.cas_ual_ty.dueldimension.duel.action;

import de.cas_ual_ty.dueldimension.DdDuelRegistries;
import de.cas_ual_ty.dueldimension.DdDuelRegistries;
import de.cas_ual_ty.dueldimension.DdDuelRegistries;
import de.cas_ual_ty.dueldimension.DuelDimension;
import net.minecraft.core.Registry;
import net.minecraft.core.Registry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public class ActionTypes
{
    
    public static final ActionType POPULATE = register("populate", new ActionType(PopulateAction::new));
    public static final ActionType MOVE_ON_TOP = register("move_on_top", new ActionType(MoveTopAction::new));
    public static final ActionType SPECIAL_SUMMON = register("special_summon", new ActionType(MoveTopAction::new));
    public static final ActionType SPECIAL_SUMMON_OVERLAY = register("special_summon_overlay", new ActionType(ListAction::new));
    public static final ActionType MOVE_TO_BOTTOM = register("move_to_bottom", new ActionType(MoveBottomAction::new));
    public static final ActionType CHANGE_POSITION = register("change_position", new ActionType(ChangePositionAction::new));
    public static final ActionType SHUFFLE_ZONE = register("shuffle_zone", new ActionType(ShuffleAction::new));
    public static final ActionType SHOW_ZONE = register("show_zone", new ActionType(ShowZoneAction::new));
    public static final ActionType VIEW_ZONE = register("view_zone", new ActionType(ViewZoneAction::new));
    public static final ActionType SHOW_CARD = register("show_card", new ActionType(ShowCardAction::new));
    public static final ActionType ATTACK = register("attack", new ActionType(AttackAction::new));
    public static final ActionType LIST = register("list", new ActionType(ListAction::new));
    public static final ActionType CHANGE_LP = register("change_lp", new ActionType(ChangeLPAction::new));
    public static final ActionType COIN_FLIP = register("coin_flip", new ActionType(CoinFlipAction::new));
    public static final ActionType DICE_ROLL = register("dice_roll", new ActionType(DiceRollAction::new));
    public static final ActionType CHANGE_COUNTERS = register("change_counters", new ActionType(ChangeCountersAction::new));
    public static final ActionType CREATE_TOKEN = register("create_token", new ActionType(CreateTokenAction::new));
    public static final ActionType REMOVE_TOKEN = register("remove_token", new ActionType(RemoveTokenAction::new));
    public static final ActionType CHANGE_PHASE = register("change_phase", new ActionType(ChangePhaseAction::new));
    public static final ActionType END_TURN = register("end_turn", new ActionType(EndTurnAction::new));
    public static final ActionType INIT_SLEEVES = register("init_sleeves", new ActionType(InitSleevesAction::new));
    public static final ActionType SELECT = register("select", new ActionType(SelectAction::new));
    
    private static ActionType register(String name, ActionType entry)
    {
        return Registry.register(DdDuelRegistries.ACTION_TYPES,
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
