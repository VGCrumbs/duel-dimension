package de.cas_ual_ty.dueldimension.ocg;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Registers Tag Force's Destiny Draw inside the engine, as a Lua chunk run once
 * before the duel starts.
 *
 * <h2>Why this cannot simply move the card when the time comes</h2>
 * {@code OCG_LoadScript} loads <em>and calls</em> its chunk, but
 * {@code interpreter.cpp} wraps that call in {@code ++no_action} — so any Lua
 * that reaches {@code check_action_permission} fails with "Action is not allowed
 * here." {@code Duel.MoveSequence} opens with exactly that check
 * ({@code libduel.cpp:698}), and so do {@code SendtoDeck}, {@code Draw} and
 * {@code SelectYesNo}. A chunk loaded mid-duel therefore cannot touch the deck,
 * and one that tried would fail silently from the player's point of view.
 * <p>
 * <b>So the chunk does not act. It REGISTERS.</b> {@code Duel.RegisterEffect}
 * has no such check ({@code libduel.cpp:81}), and {@code libeffect.cpp} contains
 * no occurrence of the check at all — the whole {@code Effect.*} surface is
 * available. The effect's operation then runs later, during ordinary
 * processing, where every action is permitted. Registration is the only thing
 * that happens under {@code no_action}; the work happens under the engine's own
 * rules, which is also what makes the engine the one place the rule lives.
 *
 * <h2>Why EVENT_PREDRAW</h2>
 * The draw phase runs in three steps ({@code processor.cpp:3346}): the phase is
 * entered, then {@code EVENT_PREDRAW} is raised and any chains it makes are
 * resolved, and only <em>then</em> is the card drawn. So an effect on
 * {@code EVENT_PREDRAW} is guaranteed to resolve before the draw it is meant to
 * change. It is not a trick: 28 of the bundled card scripts use the same event
 * for the same reason.
 *
 * <h2>Sequence 0 is the TOP of the deck</h2>
 * {@code field.cpp} says so in its own comments — sequence 0 is marked
 * {@code //deck top} and pushes to the BACK of {@code list_main}, sequence 1 is
 * {@code //deck bottom} and inserts at the front. The vector reads backwards
 * from the intuition, and {@code draw} confirms it from the other side by taking
 * {@code list_main.back()}. Getting this wrong is the most expensive available
 * mistake: the Destiny Card would go to the bottom of the deck and nothing would
 * report it.
 *
 * <h2>The rule itself is Tag Force's</h2>
 * See the vault note. Life points at or below 4000, the opponent has a monster,
 * you have nothing with more ATK than theirs, and one use per duel. Every clause
 * is a question the engine can answer about its own field, which is why none of
 * it is written in Java: a copy over there would be a second implementation to
 * keep in step, working from a board snapshot that is already a frame old.
 */
public final class DestinyDrawScript
{
    private DestinyDrawScript()
    {
    }

    /**
     * A private effect code for the once-per-duel counter and for the client to
     * recognise the prompt by.
     * <p>
     * Well outside the passcode range so it cannot collide with a real card's
     * id, which is what {@code SetCountLimit} keys its counter on.
     */
    public static final int EFFECT_ID = 0x1DD0DD01;

    /**
     * The effect's description, and the client's only way to recognise it.
     *
     * <h2>Why a description and not the card</h2>
     * The effect is a {@code GlobalEffect} and therefore has no card -- its
     * owner is the core's {@code temp_card} -- so the chain option the engine
     * offers names a card that does not exist. The description is the one field
     * that travels with a chain option and is ours to set: {@code
     * MSG_SELECT_CHAIN} writes {@code (code, location, description, ...)} per
     * option, and {@code DuelMessage.ChainOption} already decodes it.
     * <p>
     * The value is chosen to be unreachable by a real card. A card's
     * description is {@code Stringid(code, n)}, which is {@code code * 16 + n},
     * so with a 32-bit passcode the largest a card can produce is about
     * {@code 0x10_0000_0000}. This sits above that, and reads as "DD".
     */
    public static final long DESCRIPTION = 0x0DD00000000L;

    /**
     * The chunk for one player, or null when there is nothing to register.
     * <p>
     * Null when the player flagged no cards, which is both the common case and
     * the correct one: a duel where neither side nominated anything should cost
     * the engine nothing at all, not an effect that can never fire.
     *
     * @param player absolute player id, 0 or 1
     * @param codes  the passcodes flagged as Destiny Cards
     */
    public static String chunk(int player, Collection<Integer> codes)
    {
        if(codes == null || codes.isEmpty())
        {
            return null;
        }
        // Deduplicated and ordered so the generated text is stable and a test
        // can read it; the engine does not care about either.
        LinkedHashSet<Integer> unique = new LinkedHashSet<>(codes);

        StringBuilder lua = new StringBuilder();
        lua.append("do\n");
        lua.append("  local tp=").append(player).append('\n');
        lua.append("  local flagged={");
        boolean first = true;
        for(int code : unique)
        {
            if(!first)
            {
                lua.append(',');
            }
            lua.append('[').append(code).append("]=true");
            first = false;
        }
        lua.append("}\n");
        // A card is a Destiny Card if the PLAYER flagged its passcode. Asked of
        // the card rather than of a remembered deck slot, because a slot number
        // is meaningless the moment anything shuffles.
        lua.append("  local function isdc(c) return flagged[c:GetCode()]==true end\n");

        // ---- the pinch rule -------------------------------------------------
        lua.append("  local function pinched(e,tpx,eg,ep,ev,re,r,rp)\n");
        // Only on your own draw, and only while you are the one drawing.
        lua.append("    if Duel.GetTurnPlayer()~=tp then return false end\n");
        lua.append("    if Duel.GetLP(tp)>4000 then return false end\n");
        // Once per duel. A flag on the PLAYER rather than SetCountLimit,
        // because a count limit keys on the effect's owning card and a
        // card-less effect has none -- the same reason the type below is what
        // it is. Set in the operation only when the draw is actually taken.
        lua.append("    if Duel.GetFlagEffect(tp,").append(EFFECT_ID)
            .append(")~=0 then return false end\n");
        // The opponent must have something on the board...
        lua.append("    local og=Duel.GetFieldGroup(tp,0,LOCATION_MZONE)\n");
        lua.append("    if og:GetCount()==0 then return false end\n");
        lua.append("    local best=0\n");
        lua.append("    local oc=og:GetFirst()\n");
        lua.append("    while oc do\n");
        lua.append("      local a=oc:GetAttack()\n");
        lua.append("      if a>best then best=a end\n");
        lua.append("      oc=og:GetNext()\n");
        lua.append("    end\n");
        // ...and you must have nothing that beats it. Equal ATK is still a
        // pinch: trading is not answering.
        lua.append("    local mg=Duel.GetFieldGroup(tp,LOCATION_MZONE,0)\n");
        lua.append("    local mc=mg:GetFirst()\n");
        lua.append("    while mc do\n");
        lua.append("      if mc:GetAttack()>best then return false end\n");
        lua.append("      mc=mg:GetNext()\n");
        lua.append("    end\n");
        // And there has to be something left to draw. A player whose flagged
        // cards have all been drawn or milled is not offered a choice that
        // cannot do anything.
        lua.append("    return Duel.IsExistingMatchingCard(isdc,tp,LOCATION_DECK,0,1,nil)\n");
        lua.append("  end\n");

        // ---- the offer, and the draw ----------------------------------------
        lua.append("  local function put(e,tpx,eg,ep,ev,re,r,rp)\n");
        lua.append("    local g=Duel.GetMatchingGroup(isdc,tp,LOCATION_DECK,0,nil)\n");
        lua.append("    if g:GetCount()==0 then return end\n");
        // THE PROMPT LIVES HERE, inside the operation.
        //
        // An operation runs during ordinary processing, so everything the load
        // chunk was forbidden is permitted -- including asking a question. That
        // is what makes a MANDATORY effect offer an OPTIONAL choice, which is
        // the shape this feature needs and the one the engine will actually
        // deliver. The description is the same value the client recognises the
        // prompt by; see DESCRIPTION.
        lua.append("    if not Duel.SelectYesNo(tp,").append(DESCRIPTION)
            .append(") then return end\n");
        // Spent only when TAKEN. Declining is not using it, so a player who
        // says no is asked again next turn while the pinch lasts -- which is
        // what "as long as that situation is not overturned" means.
        lua.append("    Duel.RegisterFlagEffect(tp,").append(EFFECT_ID)
            .append(",0,0,1)\n");
        // RANDOM, which is the whole bet: the player chose how many to flag and
        // therefore chose their own odds. Not a selection -- picking would make
        // it a tutor, and a tutor is a different and much stronger mechanic.
        lua.append("    local c=g:RandomSelect(tp,1):GetFirst()\n");
        lua.append("    if c then Duel.MoveSequence(c,0) end\n");
        lua.append("  end\n");

        // ---- registration ---------------------------------------------------
        lua.append("  local e=Effect.GlobalEffect()\n");
        lua.append("  e:SetDescription(").append(DESCRIPTION).append(")\n");
        // CONTINUOUS, and this is the whole reason the feature works at all.
        //
        // The obvious type is TRIGGER_O -- optional, so the engine asks. It is
        // never collected. process_instant_event gathers trigger_o effects only
        // when their handler is STATUS_EFFECT_ENABLED, and a card-less effect's
        // handler is the core's temp_card, which is never given that status. A
        // trigger registered this way is silently inert: it loads, it
        // registers, its condition is never once called.
        //
        // The continuous loop at the top of the same function has no such gate
        // -- it asks is_activateable directly, and is_activateable skips every
        // handler check for a FIELD_ONLY effect, which is what Duel.RegisterEffect
        // makes a card-less one. So this path reaches the condition, and the
        // optionality moves into the operation as the SelectYesNo above.
        //
        // Verified by DestinyDrawProbeTest, which runs both shapes through a
        // real duel and reports which of them ever reaches its condition.
        lua.append("  e:SetType(EFFECT_TYPE_FIELD+EFFECT_TYPE_CONTINUOUS)\n");
        lua.append("  e:SetCode(EVENT_PREDRAW)\n");
        lua.append("  e:SetCondition(pinched)\n");
        lua.append("  e:SetOperation(put)\n");
        lua.append("  Duel.RegisterEffect(e,tp)\n");
        lua.append("end\n");
        return lua.toString();
    }
}
