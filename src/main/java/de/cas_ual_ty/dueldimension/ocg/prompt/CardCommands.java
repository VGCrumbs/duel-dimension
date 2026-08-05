package de.cas_ual_ty.dueldimension.ocg.prompt;

import de.cas_ual_ty.dueldimension.ocg.OcgConstants;
import de.cas_ual_ty.dueldimension.ocg.text.DescriptionTable;

/**
 * Port of EDOPro's per-card command bitmask.
 * <p>
 * Values and names are taken verbatim from {@code gframe/game.h} (COMMAND_*),
 * the flags are set exactly where {@code gframe/duelclient.cpp} sets
 * {@code pcard->cmdFlag} while reading MSG_SELECT_IDLECMD / MSG_SELECT_BATTLECMD,
 * and {@link #MENU_ORDER} plus {@link #label} reproduce
 * {@code ClientField::ShowMenu} in {@code gframe/event_handler.cpp} — including
 * its conditional captions.
 * <p>
 * Note there is no {@code card->get_menu()} in ygopro-core: the core reports
 * which cards may be summoned/set/activated/attack as lists in the idle and
 * battle command messages, and the <em>client</em> folds those lists into this
 * bitmask. We do the same, on the server, so the client never needs the raw
 * message stream.
 */
public final class CardCommands
{
    // gframe/game.h:816-825
    public static final int COMMAND_ACTIVATE = 0x0001;
    public static final int COMMAND_SUMMON = 0x0002;
    public static final int COMMAND_SPSUMMON = 0x0004;
    public static final int COMMAND_MSET = 0x0008;
    public static final int COMMAND_SSET = 0x0010;
    public static final int COMMAND_REPOS = 0x0020;
    public static final int COMMAND_ATTACK = 0x0040;
    public static final int COMMAND_LIST = 0x0080;
    public static final int COMMAND_OPERATION = 0x0100;
    public static final int COMMAND_RESET = 0x0200;

    /** The order ShowMenu stacks the buttons in, top to bottom. */
    public static final int[] MENU_ORDER = {
        COMMAND_ACTIVATE, COMMAND_SUMMON, COMMAND_SPSUMMON, COMMAND_MSET, COMMAND_SSET,
        COMMAND_REPOS, COMMAND_ATTACK, COMMAND_LIST, COMMAND_OPERATION, COMMAND_RESET
    };

    /** Vertical step between menu entries: ShowMenu uses Scale(21). */
    public static final int MENU_ROW_HEIGHT = 21;

    // System strings used by ShowMenu, from config/strings.conf.
    private static final int STRING_ACTIVATE = 1150;
    private static final int STRING_SUMMON = 1151;
    private static final int STRING_SPSUMMON = 1152;
    private static final int STRING_SET = 1153;
    private static final int STRING_FLIP_SUMMON = 1154;
    private static final int STRING_TO_DEFENSE = 1155;
    private static final int STRING_TO_ATTACK = 1156;
    private static final int STRING_ATTACK = 1157;
    private static final int STRING_VIEW = 1158;
    private static final int STRING_ST_SET = 1159;
    private static final int STRING_RESOLVE_EFFECT = 1161;
    private static final int STRING_RESET_EFFECT = 1162;

    private static final int TYPE_MONSTER = 0x1;

    private CardCommands()
    {
    }

    /**
     * The caption ShowMenu would put on this command's button.
     *
     * @param cardType TYPE_* bitfield of the card the menu belongs to
     * @param position POS_* of that card, for the reposition caption
     */
    public static String label(int command, int cardType, int position, DescriptionTable text)
    {
        return switch(command)
        {
            case COMMAND_ACTIVATE -> text.systemString(STRING_ACTIVATE);
            case COMMAND_SUMMON -> text.systemString(STRING_SUMMON);
            case COMMAND_SPSUMMON -> text.systemString(STRING_SPSUMMON);
            case COMMAND_MSET -> text.systemString(STRING_SET);
            // event_handler.cpp: non-monsters read "Set", monsters "S/T Set".
            case COMMAND_SSET -> text.systemString(
                (cardType & TYPE_MONSTER) == 0 ? STRING_SET : STRING_ST_SET);
            case COMMAND_REPOS -> text.systemString(
                (position & OcgConstants.POS_FACEDOWN) != 0 ? STRING_FLIP_SUMMON
                    : (position & OcgConstants.POS_ATTACK) != 0 ? STRING_TO_DEFENSE : STRING_TO_ATTACK);
            case COMMAND_ATTACK -> text.systemString(STRING_ATTACK);
            case COMMAND_LIST -> text.systemString(STRING_VIEW);
            case COMMAND_OPERATION -> text.systemString(STRING_RESOLVE_EFFECT);
            case COMMAND_RESET -> text.systemString(STRING_RESET_EFFECT);
            default -> "";
        };
    }

    /** Index of a command in the menu order, for stable sorting. */
    public static int menuIndex(int command)
    {
        for(int i = 0; i < MENU_ORDER.length; i++)
        {
            if(MENU_ORDER[i] == command)
            {
                return i;
            }
        }
        return MENU_ORDER.length;
    }
}
