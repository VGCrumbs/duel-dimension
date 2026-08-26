package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import net.minecraft.resources.ResourceLocation;

/**
 * Duel-screen textures, and card images at a resolution the screen actually
 * needs.
 * <p>
 * The board art is EDOPro's own (AGPL, see LICENSE.md beside the files). Card
 * art comes from the mod's image pipeline, but requested at an explicit size
 * rather than the {@code cardMainImageSize} config value — that setting exists
 * to keep item icons cheap and defaults to 64px, which is illegibly blurry
 * when a card is drawn large on a duel field or in a preview panel.
 */
public final class DuelTextures
{
    /**
     * The Master Rule 5 playmat. EDOPro keeps one mat per rules era and picks
     * by duel_field; field4 is the modern one, and the *-transparent variant
     * is only swapped in when a face-up Field Spell's own art is drawn
     * underneath it.
     */
    public static final ResourceLocation FIELD =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/field4.png");
    public static final ResourceLocation FIELD_TRANSPARENT =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/field-transparent4.png");
    /**
     * Card backs: EDOPro uses a different one per side (tCover[controler]), and
     * the pair is kept for that reason even though the player's chosen back
     * currently goes on both.
     * <p>
     * <b>Assigned, not computed, and that is deliberate.</b> A dozen call sites
     * across the duel screen ask {@code texture.equals(DuelTextures.COVER)} to
     * decide whether art is already card-shaped and should be drawn through the
     * full 0..1 UV range rather than the {@link #CARD_U0} letterbox window.
     * Those tests keep working precisely because they compare against whatever
     * the field holds <em>now</em>: the same field is the only producer of a
     * back, so producer and predicate cannot disagree. Turning either into a
     * method would break every one of them silently.
     * <p>
     * {@code volatile} because they are written from a settings button and read
     * from the render thread, which is the rule {@code DuelClientState} follows
     * for every static that crosses threads. A dropped {@code final} costs no
     * caller a recompile — an {@code ResourceLocation} is built by a method call, so
     * it was never a compile-time constant and was never inlined anywhere.
     *
     * @see CardBacks
     */
    // The anime back, matching CardBacks.ALL's first entry, which is the
    // default. These are overwritten the moment the stored choice is read --
    // but "the moment" is not zero, and a frame drawn before it showed the
    // other back. Two places naming one default is the cost of the field being
    // assigned rather than computed; the paragraph above is why it is.
    public static volatile ResourceLocation COVER =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/backs/anime.png");
    public static volatile ResourceLocation COVER_OPPONENT =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/backs/anime.png");

    /**
     * Prints both sides' cards on the given backs.
     * <p>
     * Two arguments although there is one choice today: {@link CardBacks} hands
     * the same texture twice, and a per-side choice is then a change to that
     * one call rather than to this shape.
     */
    public static void setCovers(ResourceLocation own, ResourceLocation opponent)
    {
        COVER = own;
        COVER_OPPONENT = opponent;
    }
    /**
     * The mute button's two states, side by side: cell 0 the speaker, cell 1
     * the speaker crossed out. A 256x256 sheet because that is the size every
     * widget texture here is and the one {@code DdBlitUtil} divides by.
     */
    public static final ResourceLocation MUSIC_ICONS =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/duel/music.png");

    /** Fallback art for a card whose image is missing or still downloading. */
    public static final ResourceLocation UNKNOWN =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/unknown.png");
    /** The life-point bar frame; the fill inside it is drawn procedurally. */
    public static final ResourceLocation LP_FRAME =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/lpf.png");
    public static final ResourceLocation BACKDROP =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/bg.png");
    /** 5x4 atlas of 64px digits, used for chain-link and counter badges. */
    public static final ResourceLocation NUMBERS =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/number.png");
    /**
     * A card square for the zones the playmat does not cover: the two extra
     * monster zones and the four piles. Drawn as a texture rather than as line
     * geometry so it goes through the same path as the mat and the cards.
     */
    public static final ResourceLocation SLOT =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/slot.png");
    /** The same square lit, for a zone the core is currently offering. */
    public static final ResourceLocation SLOT_ACTIVE =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/slot_active.png");
    /**
     * The side of a card stack: 16x2, a white row over a gray row, tiled
     * vertically once per card so a pile's edge reads as many thin cards.
     */
    public static final ResourceLocation STACK_SIDE =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/stackside.png");
    /**
     * Plain white, for tinted screen-space shapes (the attack line). Flat
     * colour geometry has to go through the textured path here: the
     * position_color path has never rendered on this screen.
     */
    public static final ResourceLocation WHITE =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/white.png");
    /**
     * The phase indicator, as plain PNGs so the art can be repainted without
     * touching code. The case is a slim chrome housing with six bays; each
     * atlas is six columns by two rows -- the top row idle, the bottom row lit
     * with glowing letters for the current phase. Blue is used on your turn,
     * red on the opponent's.
     */
    public static final ResourceLocation PHASE_CASE =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/phase_case.png");
    public static final ResourceLocation PHASE_BLUE =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/phase_blue.png");
    public static final ResourceLocation PHASE_RED =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/phase_red.png");
    /**
     * The coin, as two frames side by side: tails on the left, heads on the
     * right. Replaceable art -- drop a new file here and the flip uses it.
     */
    public static final ResourceLocation COIN =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/coin.png");
    /**
     * The marks on the two Spell/Trap zones that are also Pendulum Zones.
     * <p>
     * Under MR5 -- which is the mode every duel here runs -- ocgcore sets
     * DUEL_PZONE without DUEL_SEPARATE_PZONE, so a scale occupies backrow zone
     * 0 or 4 rather than a zone of its own. Nothing on the playmat says which
     * two those are, so the zones say it.
     * <p>
     * One colour per zone, not both on both: blue is the left scale and red
     * the right, the way a Pendulum card prints them.
     */
    /**
     * The two colours a Pendulum card prints its scales in: blue on the left,
     * red on the right.
     * <p>
     * Opaque, with the alpha left to the caller -- the world board fades its
     * whole mat at the end of a duel and the flat screen does not.
     */
    public static final int PENDULUM_BLUE = 0xFF5698E0;
    public static final int PENDULUM_RED = 0xFFD65852;

    public static final ResourceLocation PENDULUM_ZONE_LEFT = ResourceLocation.fromNamespaceAndPath(
        DuelDimension.MOD_ID, "textures/duel/pendulum_zone_left.png");
    public static final ResourceLocation PENDULUM_ZONE_RIGHT = ResourceLocation.fromNamespaceAndPath(
        DuelDimension.MOD_ID, "textures/duel/pendulum_zone_right.png");

    /** The field spell zone's own square, marked with a compass rose. */
    public static final ResourceLocation FIELD_SPELL =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/field_spell.png");
    /**
     * Digits 0-9 in the reference's stack-indicator style: bold, white, heavily
     * outlined. EDOPro's own number.png carries whole numbers 1 to 20 with no
     * zero, so it cannot spell an arbitrary pile count; this matches its look
     * while composing.
     */
    public static final ResourceLocation DIGITS =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/digits.png");
    /**
     * EDOPro's own equip mark, laid over the partner of a hovered equip card
     * ({@code drawing.cpp}:404, {@code imageManager.tEquip}).
     */
    public static final ResourceLocation EQUIP =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/equip.png");
    public static final ResourceLocation ACT =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/act.png");
    public static final ResourceLocation ATTACK =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/attack.png");
    public static final ResourceLocation CHAIN =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/chain.png");
    public static final ResourceLocation NEGATED =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/negated.png");
    public static final ResourceLocation TARGET =
        ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/duel/target.png");

    /**
     * Cards drawn on the field.
     * <p>
     * A card on the table is perhaps 70 screen pixels wide, but Minecraft's GUI
     * scale multiplies that by up to 4 on a large display, and the perspective
     * enlarges the near row further. 512 keeps the art sharp at those sizes and
     * costs one 1&nbsp;MB texture per distinct card in the duel.
     * <p>
     * The preview deliberately requests the same size: the pipeline caches one
     * file per (card, size), so sharing the number means the field and the
     * preview share a single texture instead of generating two.
     */
    public static final int FIELD_CARD_SIZE = 512;
    /** The preview panel draws a card several hundred pixels tall. */
    public static final int PREVIEW_CARD_SIZE = 512;

    /**
     * The size card icons are fetched at in the deck editor's grids.
     * <p>
     * Twice the 64 the rest of the mod uses for its "main" images, and drawn at
     * the same size on screen: the grids show a card at around forty pixels
     * across, and a 64-pixel source has barely more detail than that to give.
     * Fetching at 128 and letting the GPU scale it down costs a little memory
     * and makes the art legible.
     */
    public static final int ICON_CARD_SIZE = 128;

    /** EDOPro's cover.png is 480x700; keep that ratio wherever we draw a card. */
    public static final float CARD_ASPECT = 480F / 700F;

    /**
     * The mod stores card images letterboxed inside a square: measured across
     * every cached image, the card occupies u 0.199..0.801 and v 0.0625..0.9375
     * (aspect 0.6875, matching the printed card). Drawing the whole square into
     * a card-shaped quad squeezes the art; sampling this window instead keeps
     * it true. EDOPro's own textures (cover, unknown) are already card-shaped
     * and use the full range.
     */
    public static final float CARD_U0 = 0.19922F;
    public static final float CARD_U1 = 0.80078F;
    public static final float CARD_V0 = 0.0625F;
    public static final float CARD_V1 = 0.9375F;

    /**
     * The mod already ships an icon per duel action for its manual mode;
     * reusing them gives the command menu a symbol beside every entry.
     * Reposition has two icons because the action differs by current position.
     */
    public static ResourceLocation commandIcon(int command, boolean faceDown, boolean attackPosition)
    {
        // Where the reference has its own symbol, use it: act.png is the mark
        // EDOPro lays over an activatable card and attack.png the one it bobs
        // over an attacker. It ships no others -- its command menu is text-only
        // (ShowMenu builds plain buttons), so summon, set and reposition have
        // no EDOPro art to take and keep the mod's own icons.
        if(command == de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.COMMAND_ACTIVATE)
        {
            return ACT;
        }
        if(command == de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.COMMAND_ATTACK)
        {
            return ATTACK;
        }
        String name = switch(command)
        {
            case de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.COMMAND_SUMMON -> "normal_summon";
            case de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.COMMAND_SPSUMMON -> "special_summon_atk";
            case de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.COMMAND_MSET -> "set_to_def";
            case de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.COMMAND_SSET -> "set_spell_trap";
            case de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.COMMAND_REPOS ->
                faceDown ? "def_set_to_atk" : attackPosition ? "atk_to_def" : "def_set_to_atk";
            case de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.COMMAND_ATTACK -> "attack";
            case de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.COMMAND_LIST -> "show_card";
            case de.cas_ual_ty.dueldimension.ocg.prompt.CardCommands.PHASE_SHUFFLE -> "shuffle_hand";
            default -> null;
        };
        return name == null ? null
            : ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/action_icons/" + name + ".png");
    }

    private DuelTextures()
    {
    }

    /**
     * The artwork index a duel wire value asks for, as an index this card
     * actually has.
     * <p>
     * A duel carries the artwork chosen for a copy as a plain int, and it
     * arrives from a deck that may have been built when the card had more
     * artwork than the database now lists. {@code adjustImageIndex} already
     * folds an index the card does not have back to the printed one; this adds
     * the range check that has to happen before the narrowing to byte, because
     * an int of 256 would otherwise become the perfectly valid byte 0 by
     * accident rather than by decision, and one of 200 a negative.
     */
    public static byte artIndex(Properties properties, int art)
    {
        if(art <= 0 || art > Byte.MAX_VALUE)
        {
            return 0;
        }
        return properties.adjustImageIndex((byte)art);
    }

    /**
     * A card image at an explicit size. Requesting a size the pipeline has not
     * produced yet kicks off its generation and yields a placeholder in the
     * meantime, exactly as the rest of the mod behaves.
     */
    public static ResourceLocation card(Properties properties, byte imageIndex, int size)
    {
        return card(properties, imageIndex, size, false);
    }

    /**
     * The same cached card PNG through the resource pack's bilinear path.
     * A separate identifier matters: samplers belong to loaded textures in
     * 26.2, not to individual draws, so this keeps field cards crisp while a
     * heavily downscaled preview uses linear minification.
     */
    public static ResourceLocation cardSmooth(Properties properties, byte imageIndex, int size)
    {
        return card(properties, imageIndex, size, true);
    }

    // There is no cardUnowned. An unowned card is the SAME image as an owned
    // one, greyed by the draw -- see UnownedPipelines -- so ownership is an
    // argument the three call sites pass to their blit rather than a third
    // identifier over the same file. That identifier cost a second, CPU-built
    // copy of every unowned card's PNG, and a card shown greyed in a grid and
    // owned in a preview cost two decodes and two resident textures instead of
    // one.

    private static ResourceLocation card(Properties properties, byte imageIndex, int size, boolean smooth)
    {
        String image = ImageHandler.getReplacementImage(properties, imageIndex, size);
        // While the pipeline is still fetching (or gave up on) a card, show
        // the reference client's own "unknown card" art rather than the mod's
        // loading placeholder, which reads as a broken card on a duel field.
        if(image.endsWith("card_loading") || image.endsWith("card_failed"))
        {
            return UNKNOWN;
        }
        ResourceLocation __id = ResourceLocation.fromNamespaceAndPath(DuelDimension.MOD_ID,
            (smooth ? DdCardResourcePack.SMOOTH_PATH_PREFIX : DdCardResourcePack.PATH_PREFIX)
                + image + ".png");
        // Through the manager, never straight to the caller. An ResourceLocation that
        // reaches a blit before the manager has registered a texture under it
        // is read, decoded and uploaded inline on the render thread, which is
        // the hitch; the manager hands back the placeholder instead and a
        // worker has the art ready a frame or two later. It is also what makes
        // the texture releasable -- see CardTextureCache.
        return CardImageManager.getTextureCard(__id, size);
    }
}
