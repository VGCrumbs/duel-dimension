package de.cas_ual_ty.ydm.ocg;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Constants of the ocgcore (EDOPro-core) C API, translated from
 * ocgapi_constants.h of edo9300/ygopro-core (API version 11.0).
 * Keep in sync with the native library version actually shipped.
 */
public final class OcgConstants
{
    private OcgConstants()
    {
    }

    /* --- API version this file was written against --- */
    public static final int VERSION_MAJOR = 11;
    public static final int VERSION_MINOR = 0;

    /* --- Duel creation status (OCG_DuelCreationStatus) --- */
    public static final int DUEL_CREATION_SUCCESS = 0;
    public static final int DUEL_CREATION_NO_OUTPUT = 1;
    public static final int DUEL_CREATION_NOT_CREATED = 2;
    public static final int DUEL_CREATION_NULL_DATA_READER = 3;
    public static final int DUEL_CREATION_NULL_SCRIPT_READER = 4;
    public static final int DUEL_CREATION_INCOMPATIBLE_LUA_API = 5;
    public static final int DUEL_CREATION_NULL_RNG_SEED = 6;

    /* --- Duel process status (OCG_DuelStatus) --- */
    public static final int DUEL_STATUS_END = 0;
    public static final int DUEL_STATUS_AWAITING = 1;
    public static final int DUEL_STATUS_CONTINUE = 2;

    /* --- Log types (OCG_LogTypes) --- */
    public static final int LOG_TYPE_ERROR = 0;
    public static final int LOG_TYPE_FROM_SCRIPT = 1;
    public static final int LOG_TYPE_FOR_DEBUG = 2;
    public static final int LOG_TYPE_UNDEFINED = 3;

    /* --- Locations --- */
    public static final int LOCATION_DECK = 0x01;
    public static final int LOCATION_HAND = 0x02;
    public static final int LOCATION_MZONE = 0x04;
    public static final int LOCATION_SZONE = 0x08;
    public static final int LOCATION_GRAVE = 0x10;
    public static final int LOCATION_REMOVED = 0x20;
    public static final int LOCATION_EXTRA = 0x40;
    public static final int LOCATION_OVERLAY = 0x80;
    public static final int LOCATION_ONFIELD = LOCATION_MZONE | LOCATION_SZONE;

    /* --- Positions --- */
    public static final int POS_FACEUP_ATTACK = 0x1;
    public static final int POS_FACEDOWN_ATTACK = 0x2;
    public static final int POS_FACEUP_DEFENSE = 0x4;
    public static final int POS_FACEDOWN_DEFENSE = 0x8;
    public static final int POS_FACEUP = POS_FACEUP_ATTACK | POS_FACEUP_DEFENSE;
    public static final int POS_FACEDOWN = POS_FACEDOWN_ATTACK | POS_FACEDOWN_DEFENSE;
    public static final int POS_ATTACK = POS_FACEUP_ATTACK | POS_FACEDOWN_ATTACK;
    public static final int POS_DEFENSE = POS_FACEUP_DEFENSE | POS_FACEDOWN_DEFENSE;

    /* --- Card types --- */
    public static final int TYPE_MONSTER = 0x1;
    public static final int TYPE_SPELL = 0x2;
    public static final int TYPE_TRAP = 0x4;
    public static final int TYPE_NORMAL = 0x10;
    public static final int TYPE_EFFECT = 0x20;
    public static final int TYPE_FUSION = 0x40;
    public static final int TYPE_RITUAL = 0x80;
    public static final int TYPE_TRAPMONSTER = 0x100;
    public static final int TYPE_SPIRIT = 0x200;
    public static final int TYPE_UNION = 0x400;
    public static final int TYPE_GEMINI = 0x800;
    public static final int TYPE_TUNER = 0x1000;
    public static final int TYPE_SYNCHRO = 0x2000;
    public static final int TYPE_TOKEN = 0x4000;
    public static final int TYPE_MAXIMUM = 0x8000;
    public static final int TYPE_QUICKPLAY = 0x10000;
    public static final int TYPE_CONTINUOUS = 0x20000;
    public static final int TYPE_EQUIP = 0x40000;
    public static final int TYPE_FIELD = 0x80000;
    public static final int TYPE_COUNTER = 0x100000;
    public static final int TYPE_FLIP = 0x200000;
    public static final int TYPE_TOON = 0x400000;
    public static final int TYPE_XYZ = 0x800000;
    public static final int TYPE_PENDULUM = 0x1000000;
    public static final int TYPE_SPSUMMON = 0x2000000;
    public static final int TYPE_LINK = 0x4000000;

    /* --- Attributes --- */
    public static final int ATTRIBUTE_EARTH = 0x01;
    public static final int ATTRIBUTE_WATER = 0x02;
    public static final int ATTRIBUTE_FIRE = 0x04;
    public static final int ATTRIBUTE_WIND = 0x08;
    public static final int ATTRIBUTE_LIGHT = 0x10;
    public static final int ATTRIBUTE_DARK = 0x20;
    public static final int ATTRIBUTE_DIVINE = 0x40;

    /* --- Monster races (uint64) --- */
    public static final long RACE_WARRIOR = 0x1L;
    public static final long RACE_SPELLCASTER = 0x2L;
    public static final long RACE_FAIRY = 0x4L;
    public static final long RACE_FIEND = 0x8L;
    public static final long RACE_ZOMBIE = 0x10L;
    public static final long RACE_MACHINE = 0x20L;
    public static final long RACE_AQUA = 0x40L;
    public static final long RACE_PYRO = 0x80L;
    public static final long RACE_ROCK = 0x100L;
    public static final long RACE_WINGEDBEAST = 0x200L;
    public static final long RACE_PLANT = 0x400L;
    public static final long RACE_INSECT = 0x800L;
    public static final long RACE_THUNDER = 0x1000L;
    public static final long RACE_DRAGON = 0x2000L;
    public static final long RACE_BEAST = 0x4000L;
    public static final long RACE_BEASTWARRIOR = 0x8000L;
    public static final long RACE_DINOSAUR = 0x10000L;
    public static final long RACE_FISH = 0x20000L;
    public static final long RACE_SEASERPENT = 0x40000L;
    public static final long RACE_REPTILE = 0x80000L;
    public static final long RACE_PSYCHIC = 0x100000L;
    public static final long RACE_DIVINE = 0x200000L;
    public static final long RACE_CREATORGOD = 0x400000L;
    public static final long RACE_WYRM = 0x800000L;
    public static final long RACE_CYBERSE = 0x1000000L;
    public static final long RACE_ILLUSION = 0x2000000L;
    public static final long RACE_CYBORG = 0x4000000L;
    public static final long RACE_MAGICALKNIGHT = 0x8000000L;
    public static final long RACE_HIGHDRAGON = 0x10000000L;
    public static final long RACE_OMEGAPSYCHIC = 0x20000000L;
    public static final long RACE_CELESTIALWARRIOR = 0x40000000L;
    public static final long RACE_GALAXY = 0x80000000L;
    public static final long RACE_YOKAI = 0x4000000000000000L;

    /* --- Link markers --- */
    public static final int LINK_MARKER_BOTTOM_LEFT = 0x1;
    public static final int LINK_MARKER_BOTTOM = 0x2;
    public static final int LINK_MARKER_BOTTOM_RIGHT = 0x4;
    public static final int LINK_MARKER_LEFT = 0x8;
    public static final int LINK_MARKER_RIGHT = 0x20;
    public static final int LINK_MARKER_TOP_LEFT = 0x40;
    public static final int LINK_MARKER_TOP = 0x80;
    public static final int LINK_MARKER_TOP_RIGHT = 0x100;

    /* --- Card queries --- */
    public static final int QUERY_CODE = 0x1;
    public static final int QUERY_POSITION = 0x2;
    public static final int QUERY_ALIAS = 0x4;
    public static final int QUERY_TYPE = 0x8;
    public static final int QUERY_LEVEL = 0x10;
    public static final int QUERY_RANK = 0x20;
    public static final int QUERY_ATTRIBUTE = 0x40;
    public static final int QUERY_RACE = 0x80;
    public static final int QUERY_ATTACK = 0x100;
    public static final int QUERY_DEFENSE = 0x200;
    public static final int QUERY_BASE_ATTACK = 0x400;
    public static final int QUERY_BASE_DEFENSE = 0x800;
    public static final int QUERY_REASON = 0x1000;
    public static final int QUERY_REASON_CARD = 0x2000;
    public static final int QUERY_EQUIP_CARD = 0x4000;
    public static final int QUERY_TARGET_CARD = 0x8000;
    public static final int QUERY_OVERLAY_CARD = 0x10000;
    public static final int QUERY_COUNTERS = 0x20000;
    public static final int QUERY_OWNER = 0x40000;
    public static final int QUERY_STATUS = 0x80000;
    public static final int QUERY_IS_PUBLIC = 0x100000;
    public static final int QUERY_LSCALE = 0x200000;
    public static final int QUERY_RSCALE = 0x400000;
    public static final int QUERY_LINK = 0x800000;
    public static final int QUERY_IS_HIDDEN = 0x1000000;
    public static final int QUERY_COVER = 0x2000000;
    public static final int QUERY_END = 0x80000000;

    /* --- Messages (OCG_DuelGetMessage stream) --- */
    public static final int MSG_RETRY = 1;
    public static final int MSG_HINT = 2;
    public static final int MSG_WAITING = 3;
    public static final int MSG_START = 4;
    public static final int MSG_WIN = 5;
    public static final int MSG_UPDATE_DATA = 6;
    public static final int MSG_UPDATE_CARD = 7;
    public static final int MSG_REQUEST_DECK = 8;
    public static final int MSG_SELECT_BATTLECMD = 10;
    public static final int MSG_SELECT_IDLECMD = 11;
    public static final int MSG_SELECT_EFFECTYN = 12;
    public static final int MSG_SELECT_YESNO = 13;
    public static final int MSG_SELECT_OPTION = 14;
    public static final int MSG_SELECT_CARD = 15;
    public static final int MSG_SELECT_CHAIN = 16;
    public static final int MSG_SELECT_PLACE = 18;
    public static final int MSG_SELECT_POSITION = 19;
    public static final int MSG_SELECT_TRIBUTE = 20;
    public static final int MSG_SORT_CHAIN = 21;
    public static final int MSG_SELECT_COUNTER = 22;
    public static final int MSG_SELECT_SUM = 23;
    public static final int MSG_SELECT_DISFIELD = 24;
    public static final int MSG_SORT_CARD = 25;
    public static final int MSG_SELECT_UNSELECT_CARD = 26;
    public static final int MSG_CONFIRM_DECKTOP = 30;
    public static final int MSG_CONFIRM_CARDS = 31;
    public static final int MSG_SHUFFLE_DECK = 32;
    public static final int MSG_SHUFFLE_HAND = 33;
    public static final int MSG_REFRESH_DECK = 34;
    public static final int MSG_SWAP_GRAVE_DECK = 35;
    public static final int MSG_SHUFFLE_SET_CARD = 36;
    public static final int MSG_REVERSE_DECK = 37;
    public static final int MSG_DECK_TOP = 38;
    public static final int MSG_SHUFFLE_EXTRA = 39;
    public static final int MSG_NEW_TURN = 40;
    public static final int MSG_NEW_PHASE = 41;
    public static final int MSG_CONFIRM_EXTRATOP = 42;
    public static final int MSG_MOVE = 50;
    public static final int MSG_POS_CHANGE = 53;
    public static final int MSG_SET = 54;
    public static final int MSG_SWAP = 55;
    public static final int MSG_FIELD_DISABLED = 56;
    public static final int MSG_SUMMONING = 60;
    public static final int MSG_SUMMONED = 61;
    public static final int MSG_SPSUMMONING = 62;
    public static final int MSG_SPSUMMONED = 63;
    public static final int MSG_FLIPSUMMONING = 64;
    public static final int MSG_FLIPSUMMONED = 65;
    public static final int MSG_CHAINING = 70;
    public static final int MSG_CHAINED = 71;
    public static final int MSG_CHAIN_SOLVING = 72;
    public static final int MSG_CHAIN_SOLVED = 73;
    public static final int MSG_CHAIN_END = 74;
    public static final int MSG_CHAIN_NEGATED = 75;
    public static final int MSG_CHAIN_DISABLED = 76;
    public static final int MSG_CARD_SELECTED = 80;
    public static final int MSG_RANDOM_SELECTED = 81;
    public static final int MSG_BECOME_TARGET = 83;
    public static final int MSG_DRAW = 90;
    public static final int MSG_DAMAGE = 91;
    public static final int MSG_RECOVER = 92;
    public static final int MSG_EQUIP = 93;
    public static final int MSG_LPUPDATE = 94;
    public static final int MSG_UNEQUIP = 95;
    public static final int MSG_CARD_TARGET = 96;
    public static final int MSG_CANCEL_TARGET = 97;
    public static final int MSG_PAY_LPCOST = 100;
    public static final int MSG_ADD_COUNTER = 101;
    public static final int MSG_REMOVE_COUNTER = 102;
    public static final int MSG_ATTACK = 110;
    public static final int MSG_BATTLE = 111;
    public static final int MSG_ATTACK_DISABLED = 112;
    public static final int MSG_DAMAGE_STEP_START = 113;
    public static final int MSG_DAMAGE_STEP_END = 114;
    public static final int MSG_MISSED_EFFECT = 120;
    public static final int MSG_BE_CHAIN_TARGET = 121;
    public static final int MSG_CREATE_RELATION = 122;
    public static final int MSG_RELEASE_RELATION = 123;
    public static final int MSG_TOSS_COIN = 130;
    public static final int MSG_TOSS_DICE = 131;
    public static final int MSG_ROCK_PAPER_SCISSORS = 132;
    public static final int MSG_HAND_RES = 133;
    public static final int MSG_ANNOUNCE_RACE = 140;
    public static final int MSG_ANNOUNCE_ATTRIB = 141;
    public static final int MSG_ANNOUNCE_CARD = 142;
    public static final int MSG_ANNOUNCE_NUMBER = 143;
    public static final int MSG_CARD_HINT = 160;
    public static final int MSG_TAG_SWAP = 161;
    public static final int MSG_RELOAD_FIELD = 162;
    public static final int MSG_AI_NAME = 163;
    public static final int MSG_SHOW_HINT = 164;
    public static final int MSG_PLAYER_HINT = 165;
    public static final int MSG_MATCH_KILL = 170;
    public static final int MSG_CUSTOM_MSG = 180;
    public static final int MSG_REMOVE_CARDS = 190;

    /* --- Duel hints (MSG_HINT) --- */
    public static final int HINT_EVENT = 1;
    public static final int HINT_MESSAGE = 2;
    public static final int HINT_SELECTMSG = 3;
    public static final int HINT_OPSELECTED = 4;
    public static final int HINT_EFFECT = 5;
    public static final int HINT_RACE = 6;
    public static final int HINT_ATTRIB = 7;
    public static final int HINT_CODE = 8;
    public static final int HINT_NUMBER = 9;
    public static final int HINT_CARD = 10;
    public static final int HINT_ZONE = 11;

    /* --- Card hints (MSG_CARD_HINT) --- */
    public static final int CHINT_TURN = 1;
    public static final int CHINT_CARD = 2;
    public static final int CHINT_RACE = 3;
    public static final int CHINT_ATTRIBUTE = 4;
    public static final int CHINT_NUMBER = 5;
    public static final int CHINT_DESC_ADD = 6;
    public static final int CHINT_DESC_REMOVE = 7;

    /* --- Player constants --- */
    public static final int PLAYER_NONE = 2;
    public static final int PLAYER_ALL = 3;

    /* --- Duel phases --- */
    public static final int PHASE_DRAW = 0x01;
    public static final int PHASE_STANDBY = 0x02;
    public static final int PHASE_MAIN1 = 0x04;
    public static final int PHASE_BATTLE_START = 0x08;
    public static final int PHASE_BATTLE_STEP = 0x10;
    public static final int PHASE_DAMAGE = 0x20;
    public static final int PHASE_DAMAGE_CAL = 0x40;
    public static final int PHASE_BATTLE = 0x80;
    public static final int PHASE_MAIN2 = 0x100;
    public static final int PHASE_END = 0x200;

    /* --- Duel option flags (OCG_DuelOptions.flags, uint64) --- */
    public static final long DUEL_TEST_MODE = 0x01L;
    public static final long DUEL_ATTACK_FIRST_TURN = 0x02L;
    public static final long DUEL_USE_TRAPS_IN_NEW_CHAIN = 0x04L;
    public static final long DUEL_6_STEP_BATLLE_STEP = 0x08L;
    public static final long DUEL_PSEUDO_SHUFFLE = 0x10L;
    public static final long DUEL_TRIGGER_WHEN_PRIVATE_KNOWLEDGE = 0x20L;
    public static final long DUEL_SIMPLE_AI = 0x40L;
    public static final long DUEL_RELAY = 0x80L;
    public static final long DUEL_OCG_OBSOLETE_IGNITION = 0x100L;
    public static final long DUEL_1ST_TURN_DRAW = 0x200L;
    public static final long DUEL_1_FACEUP_FIELD = 0x400L;
    public static final long DUEL_PZONE = 0x800L;
    public static final long DUEL_SEPARATE_PZONE = 0x1000L;
    public static final long DUEL_EMZONE = 0x2000L;
    public static final long DUEL_FSX_MMZONE = 0x4000L;
    public static final long DUEL_TRAP_MONSTERS_NOT_USE_ZONE = 0x8000L;
    public static final long DUEL_RETURN_TO_DECK_TRIGGERS = 0x10000L;
    public static final long DUEL_TRIGGER_ONLY_IN_LOCATION = 0x20000L;
    public static final long DUEL_SPSUMMON_ONCE_OLD_NEGATE = 0x40000L;
    public static final long DUEL_CANNOT_SUMMON_OATH_OLD = 0x80000L;
    public static final long DUEL_NO_STANDBY_PHASE = 0x100000L;
    public static final long DUEL_NO_MAIN_PHASE_2 = 0x200000L;
    public static final long DUEL_3_COLUMNS_FIELD = 0x400000L;
    public static final long DUEL_DRAW_UNTIL_5 = 0x800000L;
    public static final long DUEL_NO_HAND_LIMIT = 0x1000000L;
    public static final long DUEL_UNLIMITED_SUMMONS = 0x2000000L;
    public static final long DUEL_INVERTED_QUICK_PRIORITY = 0x4000000L;
    public static final long DUEL_EQUIP_NOT_SENT_IF_MISSING_TARGET = 0x8000000L;
    public static final long DUEL_0_ATK_DESTROYED = 0x10000000L;
    public static final long DUEL_STORE_ATTACK_REPLAYS = 0x20000000L;
    public static final long DUEL_SINGLE_CHAIN_IN_DAMAGE_SUBSTEP = 0x40000000L;
    public static final long DUEL_CAN_REPOS_IF_NON_SUMPLAYER = 0x80000000L;
    public static final long DUEL_TCG_SEGOC_NONPUBLIC = 0x100000000L;
    public static final long DUEL_TCG_SEGOC_FIRSTTRIGGER = 0x200000000L;
    public static final long DUEL_TCG_FAST_EFFECT_IGNITION = 0x400000000L;
    public static final long DUEL_EXTRA_DECK_RITUAL = 0x800000000L;
    public static final long DUEL_NORMAL_SUMMON_FACEUP_DEF = 0x1000000000L;

    /* --- Duel modes (combinations of the above) --- */
    public static final long DUEL_MODE_SPEED = DUEL_3_COLUMNS_FIELD | DUEL_NO_MAIN_PHASE_2 | DUEL_TRAP_MONSTERS_NOT_USE_ZONE | DUEL_TRIGGER_ONLY_IN_LOCATION;
    public static final long DUEL_MODE_RUSH = DUEL_3_COLUMNS_FIELD | DUEL_NO_MAIN_PHASE_2 | DUEL_NO_STANDBY_PHASE | DUEL_1ST_TURN_DRAW | DUEL_INVERTED_QUICK_PRIORITY | DUEL_DRAW_UNTIL_5 | DUEL_NO_HAND_LIMIT | DUEL_UNLIMITED_SUMMONS | DUEL_TRAP_MONSTERS_NOT_USE_ZONE | DUEL_TRIGGER_ONLY_IN_LOCATION | DUEL_EXTRA_DECK_RITUAL;
    public static final long DUEL_MODE_MR1 = DUEL_OCG_OBSOLETE_IGNITION | DUEL_1ST_TURN_DRAW | DUEL_1_FACEUP_FIELD | DUEL_SPSUMMON_ONCE_OLD_NEGATE | DUEL_RETURN_TO_DECK_TRIGGERS | DUEL_CANNOT_SUMMON_OATH_OLD;
    public static final long DUEL_MODE_GOAT = DUEL_MODE_MR1 | DUEL_TCG_FAST_EFFECT_IGNITION | DUEL_USE_TRAPS_IN_NEW_CHAIN | DUEL_6_STEP_BATLLE_STEP | DUEL_TRIGGER_WHEN_PRIVATE_KNOWLEDGE | DUEL_EQUIP_NOT_SENT_IF_MISSING_TARGET | DUEL_0_ATK_DESTROYED | DUEL_STORE_ATTACK_REPLAYS | DUEL_SINGLE_CHAIN_IN_DAMAGE_SUBSTEP | DUEL_CAN_REPOS_IF_NON_SUMPLAYER | DUEL_TCG_SEGOC_NONPUBLIC | DUEL_TCG_SEGOC_FIRSTTRIGGER;
    public static final long DUEL_MODE_MR2 = DUEL_1ST_TURN_DRAW | DUEL_1_FACEUP_FIELD | DUEL_SPSUMMON_ONCE_OLD_NEGATE | DUEL_RETURN_TO_DECK_TRIGGERS | DUEL_CANNOT_SUMMON_OATH_OLD;
    public static final long DUEL_MODE_MR3 = DUEL_PZONE | DUEL_SEPARATE_PZONE | DUEL_SPSUMMON_ONCE_OLD_NEGATE | DUEL_RETURN_TO_DECK_TRIGGERS | DUEL_CANNOT_SUMMON_OATH_OLD;
    public static final long DUEL_MODE_MR4 = DUEL_PZONE | DUEL_EMZONE | DUEL_SPSUMMON_ONCE_OLD_NEGATE | DUEL_RETURN_TO_DECK_TRIGGERS | DUEL_CANNOT_SUMMON_OATH_OLD;
    public static final long DUEL_MODE_MR5 = DUEL_PZONE | DUEL_EMZONE | DUEL_FSX_MMZONE | DUEL_TRAP_MONSTERS_NOT_USE_ZONE | DUEL_TRIGGER_ONLY_IN_LOCATION;

    /* --- Card types forbidden per master rule mode --- */
    public static final int DUEL_MODE_MR1_FORB = TYPE_XYZ | TYPE_PENDULUM | TYPE_LINK;
    public static final int DUEL_MODE_MR2_FORB = TYPE_PENDULUM | TYPE_LINK;
    public static final int DUEL_MODE_MR3_FORB = TYPE_LINK;
    public static final int DUEL_MODE_MR4_FORB = 0;
    public static final int DUEL_MODE_MR5_FORB = 0;

    private static final Map<Integer, String> MSG_NAMES = new HashMap<>();

    static
    {
        for(Field field : OcgConstants.class.getFields())
        {
            if(field.getName().startsWith("MSG_") && Modifier.isStatic(field.getModifiers()) && field.getType() == int.class)
            {
                try
                {
                    MSG_NAMES.put(field.getInt(null), field.getName());
                }
                catch(IllegalAccessException ignored)
                {
                }
            }
        }
    }

    /** Human readable name of a message type, for logging/debugging. */
    public static String msgName(int msgType)
    {
        return MSG_NAMES.getOrDefault(msgType, "MSG_UNKNOWN(" + msgType + ")");
    }
}
