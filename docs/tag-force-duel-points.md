# Tag Force duel-point rewards and result presentation

Research date: 2026-08-08

## Scope and confidence

"Tag Force DP" is not one immutable formula. The series keeps the same assessment-driven structure but changes values and occasionally conditions between releases. This note therefore treats the original *GX Tag Force*, *GX Tag Force 3*, and *Tag Force Special* as distinct rule sets. Values must not be mixed accidentally.

Source confidence:

- **High:** values and conditions in the complete Japanese TF3 and TF Special Duel Ranking tables.
- **High:** the original game's manual for DP's role in progression and the observed result-screen structure.
- **Medium:** the English community transcription of the original game's 83 bonuses. It is useful and mostly agrees with TF3, but a few contemporary forum posts contain conflicting early observations.
- **Observed:** result-screen layout and flow from captured gameplay footage rather than a written specification.

## The system in one sentence

At duel end, Tag Force evaluates a bank of independent conditions against statistics accumulated during the duel, adds every qualifying line item, optionally adds first-time/high-score awards, and presents the result as a summary plus pageable detailed breakdown before committing the player back to the overworld.

This is an **additive assessment system**, not simply "win = N, loss = M." A win unlocks most assessment lines; a loss or draw generally receives only the loss/draw and turn awards in the older games.

## Economy and progression role

DP is the spendable currency for card packs and other purchases. In the original game's manual, earning DP also contributes to Duelist Level progression, and increasing level expands the available pack selection. Later games explicitly show both DP and EXP on the result screen.

This creates three linked loops:

1. Duel behavior produces assessment bonuses.
2. The result page explains why the player received that amount.
3. DP buys cards, while EXP/level unlocks more of the shop and can itself create a level-up DP bonus.

## Shared evaluation rules

- Qualifying bonuses stack unless the title says otherwise.
- Threshold bonuses can stack. For example, TF3 awards both low-LP and extremely-low-LP when the winner finishes at 100 LP or less; low-deck and zero-deck work the same way.
- Multiplicative rows mean `base value × recorded count`, not a multiplier on the whole duel total.
- TF Special caps each individual bonus line at 999 DP.
- Win-only bonuses are suppressed on a loss or draw. TF3 explicitly says that only Loss, Draw, and Turn survive those outcomes (with a possible special exception around level-up).
- Turn bonus is the durable participation reward. In the original game it is 2 DP per turn on a normal win, but 1 DP per turn for a draw or a game lost inside a match. TF3 documents 2 per turn; TF Special documents 2 per turn.
- The opponent/deck-level bonus is the main difficulty-scaled base award. It rises from fixed opponent bands in TF1 to `deck level × 90` in TF3 and `deck level × 100` in TF Special.
- A title may apply an economy modifier outside the displayed assessment rules. TF Special gives new saves a temporary early-game boost to win earnings.

## Evolution across representative releases

| Component | Original GX Tag Force | GX Tag Force 3 | Tag Force Special |
|---|---:|---:|---:|
| Clear/story progress | 2 × clears | 2 × clears | 8 × cleared partners |
| Player level | 2 × level | 2 × level | 4 × level |
| Completed challenges | 2 × completed | 2 × completed | not listed |
| Loss | 10 | 10 | 10 |
| Draw | 50 | 50 | 50 |
| Turns | 2 × turns; reduced to 1 × in some non-win cases | 2 × turns | 2 × turns |
| Tag win | 50 | 50 | 200 |
| Network win | uncertain | 50 | value undocumented in table |
| Match win | 50 | 50 | 50 |
| Level-up | 400 | 400 | 500 |
| Opponent difficulty | 25–250 fixed bands | 90 × enemy deck level | 100 × enemy deck level |
| First-ever bonus item | 30 × newly discovered items | 30 × newly discovered items | 50 × newly discovered items |

For tag duels, TF3 and TF Special use the opposing pair's average deck level, rounded down, for the Duelist Bonus. One secondary TF Special farming page instead says the lower opponent is used; the dedicated 70-item Duel Ranking table is the more specific source and should be preferred.

## Tag Force 3 assessment table

TF3 is a useful middle-series reference because its table is substantially complete, retains the original GX-scale economy, and removes most ambiguity in the original English community transcription.

### Outcome, progression, and pacing

| # | Result item | Amount | Condition |
|---:|---|---:|---|
| 001 | Clear Bonus | 2 × clears | Win; story clear count |
| 002 | Level Bonus | 2 × level | Win; current Duelist Level |
| 003 | Challenge Bonus | 2 × completed | Win; completed challenge count |
| 004 | Loss | 10 | Lose |
| 005 | Draw Bonus | 50 | Draw |
| 006 | Turn Bonus | 2 × turns | Duel turn count |
| 007 | Tag Victory | 50 | Win a tag duel |
| 008 | Network Victory | 50 | Win a network duel |
| 009 | Match Victory | 50 | Win the match |
| 010 | Level Up | 400 | Duel result raises the player a level |
| 011 | Duelist Bonus | 90 × deck level | Win; opposing deck level, opposing pair's floored average in tag |

### Win method and finish state

| # | Result item | Amount | Condition |
|---:|---|---:|---|
| 012 | Deck-out Victory | 20 | Opponent fails a required draw |
| 013 | Quick Finish | 10 | Win within 5 turns |
| 014 | Reversal Finish | 10 | Winner began the winning turn behind in LP |
| 015 | Opponent-turn Finish | 20 | Win during opponent's turn |
| 016 | Partner Finish | 10 | Tag partner deals/causes the finish |
| 017 | Low LP | 20 | Win at 1000 LP or less |
| 018 | Extremely Low LP | 100 | Win at 100 LP or less; stacks with 017 |
| 019 | LP Keep | 10 | LP never decreased, including costs |
| 020 | Over 20,000 LP | 20 | LP exceeded 20,000 at any time |
| 021 | Konami Bonus | 573 | Win at exactly 5730 LP |
| 022 | Low Deck | 20 | Win with 5 or fewer cards in deck |
| 023 | Zero Deck | 100 | Win with 0 cards in deck; stacks with 022 |

### Card use, summons, chains, and damage

| # | Result item | Amount | Condition |
|---:|---|---:|---|
| 024 | Spell Use | 2 × uses | Use spells; source leaves the minimum threshold uncertain |
| 025 | Trap Use | amount uncertain | Use traps; original transcription says 2 × uses after 10+ |
| 026 | No Spells | 15 | Activate no spells |
| 027 | No Traps | 15 | Activate no traps |
| 028 | Fusion Summon | 4 × summons | Fusion Summon |
| 029 | Ritual Summon | 4 × summons | Ritual Summon |
| 030 | Tribute/Advance Summon | 4 × summons | Tribute Summon |
| 031 | No Special Summon | 10 | No Special Summon, including one forced by opponent |
| 032 | Chain | 2 × chain length | Make a chain of at least 3 |
| 033 | Maximum ATK | 1 per 500 ATK | Reach at least 3000 ATK; tracked up to 65,535 |
| 034 | Maximum Damage | 1 per 250 damage | Deal at least 3000 in one instance; tracked up to 65,535 |
| 035 | LP Differential | 1 per 250 LP | Fall behind by the required amount; exact sampling/threshold is uncertain |
| 036 | Reflected Damage | approximately 1 per 50 | Reflect sufficient damage in one instance; threshold is uncertain |
| 037 | Exact 0 LP | 20 | Damage leaves opponent at exactly 0 |
| 038 | Battle Damage Only | 10 | All damage dealt was battle damage |
| 039 | Effect Damage Only | 20 | All damage dealt was effect damage |

### Interaction counters and board achievements

| # | Result item | Amount | Condition |
|---:|---|---:|---|
| 040 | Battle Destruction | 4 × count | Destroy enough opposing monsters by battle; 8 is confirmed sufficient |
| 041 | Effect Destruction | likely 8 × count | Destroy enough opposing monsters by effect |
| 042 | Banish | 4 × count | Banish enough opposing cards; 6 is confirmed sufficient |
| 043 | Hand Destruction | 4 × count | Make opponent discard enough cards |
| 044 | Deck Destruction | amount uncertain | Remove enough cards from opponent's deck |
| 045 | Return to Hand | 4 × count | Return enough cards to opponent's hand |
| 046 | Lucky | amount uncertain | At least 3 favorable coin outcomes |
| 047 | Spell Counter | amount uncertain | Accumulate/use Spell Counters |
| 048 | Union | 6 × equips | Equip Union monsters at least 5 times |
| 049 | Same Name | 10 | Control 3 same-name non-token monsters simultaneously |
| 050 | Full Monster Zones | 10 | Control 5 monsters simultaneously |
| 051 | Lock All Monster Zones | 60 | Make all opponent monster zones unusable |
| 052 | Key Card | 20 × cards | Destroy, banish, or in some cases take control of an opponent key card |

### Alternative wins and novelty achievements

| # | Result item | Amount | Condition |
|---:|---|---:|---|
| 053 | Exodia Victory | 50 | Win by Exodia's effect |
| 054 | Destiny Board Victory | 100 | Win by Destiny Board |
| 055 | Final Countdown Victory | 50 | Win by Final Countdown |
| 056 | Last Battle Victory | 50 | Win by Last Turn/Last Battle effect |
| 057 | Skull Servant Victory | 1 | Finish by Skull Servant direct attack |
| 058 | Perfectly Ultimate Great Moth | 30 | Properly summon it |
| 059 | Wall Shadow | 10 | Summon it |
| 060 | Gate Guardian | 10 | Summon it |
| 061 | Blue-Eyes Ultimate Dragon | 20 | Fusion Summon it |
| 062 | Metalzoa | 10 | Summon it |
| 063 | Red-Eyes Black Metal Dragon | 10 | Summon it |
| 064 | Valkyrion | 30 | Summon it |
| 065 | Dark Sage | 30 | Summon it |
| 066 | XYZ-Dragon Cannon | 20 | Summon it |
| 067 | Exodia Necross | 30 | Summon it |
| 068 | Ojama King | 20 | Fusion Summon it |
| 069 | Mokey Mokey King | 20 | Fusion Summon it |
| 070 | The Wicked Dreadroot | 20 | Summon it |
| 071 | Chimeratech Overdragon | 20 | Fusion Summon it |
| 072 | Mega Ton Magical Cannon | 5 | Use it |
| 073 | Yu-Jo Friendship | 5 | Use it |
| 074 | Dark Scorpion Combination | 10 | Use it |
| 075 | Ojama Delta Hurricane!! | 10 | Use it |
| 076 | Blasting the Ruins | 5 | Use it |
| 077 | Law of the Normal | 10 | Use it |
| 078 | Inferno Tempest | 5 | Use it |
| 079 | Fuh-Rin-Ka-Zan | 5 | Use it |
| 080 | Elemental Burst | 10 | Use it |
| 081 | Dark Scorpion Retreat | 30 | Use it |
| 082 | Illusion Gate | 10 | Use it |
| 083 | New Bonus Item | 30 × new items | First-ever completion of any assessment item |

The original GX game is extremely close to this structure. Confirmed differences include Reversal Finish 20 instead of 10, Over 20,000 LP 100 instead of 20, a 10-card Low Deck threshold instead of 5 in the best English transcription, Effect Damage Only 30 instead of 20, and some novelty-summon values. This is why the original and TF3 should remain separate presets even though both expose 83 bonus slots.

## Tag Force Special assessment table

TF Special condenses the list to 70 entries, removes many one-card novelty awards, adds mechanics introduced after GX, and substantially raises common-play rewards.

| # | Result item | Amount | Condition |
|---:|---|---:|---|
| 001 | Clear | 8 × clears | Win; cleared partner count |
| 002 | Level | 4 × level | Win; Duelist Level |
| 003 | Loss | 10 | Lose |
| 004 | Draw | 50 | Draw |
| 005 | Turns | 2 × turns | Turn count |
| 006 | Tag Victory | 200 | Win a tag duel |
| 007 | Network Victory | undocumented | Win a network duel |
| 008 | Match Victory | 50 | Win a match |
| 009 | Level Up | 500 | Level up from duel EXP |
| 010 | Duelist | 100 × deck level | Win; opposing level, opposing pair's floored average in tag |
| 011 | Exodia Victory | 100 | Win by Exodia |
| 012 | Destiny Board Victory | 200 | Win by Destiny Board; also earns full S/T zones |
| 013 | Deck-out Victory | 50 | Opponent cannot draw |
| 014 | Final Countdown Victory | 100 | Win by Final Countdown |
| 015 | Last Battle Victory | 100 | Win by Last Turn/Last Battle |
| 016 | Vennominaga Victory | 100 | Win by Vennominaga's effect |
| 017 | Exodius Victory | 100 | Win by Exodius's effect |
| 018 | Quick Finish | 20 | Win within 5 turns |
| 019 | Reversal Finish | 20 | Begin winning turn behind in LP |
| 020 | Opponent-turn Finish | 50 | Win on opponent's turn |
| 021 | Partner Finish | 100 | Partner produces finish in tag |
| 022 | Low LP | 50 | Win at 1000 LP or less |
| 023 | Extremely Low LP | 200 | Win at 100 LP or less; stacks with 022 |
| 024 | No LP Decrease | 50 | LP never fell, including costs |
| 025 | Over 20,000 LP | 50 | Exceed 20,000 LP at any time |
| 026 | Konami | 573 | Win at exactly 5730 LP |
| 027 | Low Deck | 50 | Win with 5 or fewer cards in deck |
| 028 | Zero Deck | 200 | Win with 0 cards; stacks with 027 |
| 029 | Spell Use | 2 × uses | Use spells |
| 030 | Trap Use | 2 × uses | Use traps |
| 031 | No Spells | 150 | Use no spells |
| 032 | No Traps | 150 | Use no traps |
| 033 | Fusion Summon | 10 × summons | Fusion Summon |
| 034 | Ritual Summon | 50 × summons | Ritual Summon |
| 035 | Advance Summon | 10 × summons | Tribute/Advance Summon |
| 036 | Synchro Summon | 1 × summons | Synchro Summon |
| 037 | Xyz Summon | 1 × summons | Xyz Summon |
| 038 | Pendulum Summon | 1 × summons | Pendulum Summon |
| 039 | No Special Summon | 200 | No Special Summon, including forced summons |
| 040 | Chain | 2 × chain length | Chain length at least 3 |
| 041 | Maximum ATK | 1 per 50 ATK | Highest ATK, capped at 65,535 for scoring |
| 042 | Maximum Damage | 1 per 250 damage | One hit/instance at least 3000; capped at 65,535 |
| 043 | LP Differential | 1 per 250 LP | Maximum deficit; exact sampling is still questioned by source |
| 044 | Reflected Damage | 1 per 50 damage | Maximum reflected-damage instance; minimum uncertain |
| 045 | Exact 0 LP | 200 | Damage leaves opponent exactly at 0 |
| 046 | Battle Damage Only | 30 | Deal only battle damage |
| 047 | Effect Damage Only | 60 | Deal only effect damage |
| 048 | First Damage | 10 × turn number | Be first to inflict damage; multiplier is that turn number |
| 049 | Battle Destruction | 8 × count | Destroy opponent-controlled monster by battle |
| 050 | Defensive Battle Destruction | 8 × count | Destroy an attacking opposing monster in battle |
| 051 | Mutual Destruction | 10 × count | Both battling monsters are destroyed |
| 052 | Effect Destruction | 8 × count | Destroy opponent-controlled monster by effect |
| 053 | Banish | 8 × count | Banish opponent card |
| 054 | Hand Destruction | 8 × count | Make opponent discard |
| 055 | Deck Destruction | 8 × count | Remove opponent deck cards |
| 056 | Return to Hand | 8 × count | Return card to opponent's hand |
| 057 | Lucky | 111 × successes | At least one favorable coin result |
| 058 | Spell Counters | 6 × counters | Place Spell Counters |
| 059 | Union | 6 × equips | Equip Union monster |
| 060 | LV Monster | 6 × summons | Special Summon via an LV monster's level-up behavior |
| 061 | Zone Movement | 6 × uses | Use an effect that moves monster zones |
| 062 | Gemini | 6 × summons | Perform a Gemini Summon |
| 063 | Alien/A-Counter | 8 × counters | Place A-Counters |
| 064 | Crystal Beast | 6 × placements | Place Crystal Beast in S/T zone |
| 065 | Same Name | 50 | Control 3 same-name non-token monsters simultaneously |
| 066 | Full Monster Zones | 100 | Control 5 monsters simultaneously |
| 067 | Lock All Monster Zones | 200 | Make every opponent monster zone unusable |
| 068 | Full Spell/Trap Zones | 100 | Occupy all 5 S/T zones simultaneously |
| 069 | Graveyard Activation | 6 × uses | Activate effects in graveyard, including recruiters |
| 070 | New Bonus Item | 50 × new items | First-ever completion of an assessment item |

Every TF Special row is individually capped at 999 DP. This matters especially for deck level 10, maximum ATK, lucky-coin, and large count bonuses.

## Result-screen construction

### Original GX Tag Force

Observed sequence:

1. The field settles and a large `YOU WIN`/loss overlay appears.
2. The losing character animation/stinger plays.
3. A full-screen yellow checkerboard result scene replaces the duel field.
4. A centered capsule header reads `DUEL RESULT`.
5. The summary table has two columns: `Categories` and `DP acquired`.
6. Rows include `Duel Bonus`, `New High Score Bonus`, then a divider and `Total`.
7. DP values are right-aligned and end in a colored `DP` suffix.
8. Large left/right arrows frame the table. The footer labels adjacent detail pages such as `New High Score Bonus` and `Duel Bonus`; the center prompt is `X: next`.
9. Confirm returns to the overworld, where the persistent balance reflects the award.

The recorded example displayed Duel Bonus 629 DP, New High Score Bonus 100 DP, Total 729 DP. This demonstrates that record awards are a separate subtotal rather than merely another ordinary duel-bonus line.

### Tag Force Special

The later presentation preserves the same information architecture but changes the skin:

- split blue/red technical background rather than yellow checkerboard;
- top `DUEL RESULT` bar;
- table columns `Item` and `DP acquired`;
- summary `Duel Bonus` and `Total` rows;
- a separate `EXP obtained` line below the DP table;
- left/right paging to the Duel Bonus breakdown and Duel Ranking pages;
- center confirm/next action.

The recorded example displayed Duel Bonus 1645 DP, Total 1645 DP, and 20 EXP.

### Information architecture to preserve

The important design is not the exact checkerboard texture. It is the hierarchy:

`outcome stinger → earnings summary → pageable line-item explanation → records/ranking → confirm and return`

That hierarchy lets the player first understand the result, then inspect the arithmetic, then see longer-term progress without crowding one screen.

## Required implementation model for Duel Dimension

### Server-authoritative metrics

The server should accumulate a `DuelMetrics` object from the same ordered OCGCore message stream that drives the duel. At minimum it needs:

- outcome, winner seat, win reason, match/tag context, and finishing seat;
- turn number/current turn player and LP at the start of each turn;
- initial, current, minimum, and maximum LP for every seat;
- LP costs, recovery, battle/effect/reflected/direct damage, maximum single damage, and exact lethal;
- initial/current deck sizes and cards removed from deck;
- spell/trap uses and activations;
- Normal, Flip, Tribute, Fusion, Ritual, Synchro, Xyz, Pendulum, other Special, and Token summons;
- chain sizes and controller;
- battle/effect destructions, mutual destructions, banishes, discards, mills, and returns to hand;
- maximum final ATK used by the duel, coin/dice outcomes, counters, equips, and zone occupancy/locks;
- activated/summoned card codes for special victory and novelty rules;
- opponent deck difficulty, player level/story progress, previously discovered bonus IDs, and prior high scores.

Some of this is already directly represented by typed messages (`NewTurn`, `Damage`, `Recover`, `PayLpCost`, `LpUpdate`, `Summoning`, `SpSummoning`, `FlipSummoning`, `Chaining`, `Battle`, `Move`, `TossCoin`, `TossDice`, and `Win`). Precise damage classification, summon subtype, effect ownership, key-card rules, and certain special wins will require either richer decoding or carefully correlated state transitions. They must not be inferred from client animation state.

### Pure reward evaluation

Use data rather than one monolithic conditional:

```text
DuelRewardRule
  id, displayName, generation/preset
  outcomeRequirement
  predicate(metrics, profile, opponent)
  amount(metrics, profile, opponent)
  perLineCap
  category

DuelRewardBreakdown
  outcome
  lineItems[] { ruleId, label, amount, newDiscovery, newRecord }
  duelBonusSubtotal
  recordBonusSubtotal
  total
  previousBalance
  newBalance
  expObtained
```

The evaluator should be pure and unit-tested against fixed metric fixtures. The server then persists DP exactly once per contest, sends the completed breakdown to the owning client, and syncs the new balance. The client only renders and animates the already-decided result.

### Result UI state

Implemented on 2026-08-08: the old flat 1000/500 PvP payment and chat-only report were replaced by a server-authoritative assessment. PvP uses the full Duel Dimension preset, NPC duels use the same visible rules at 65%, and forfeits receive only outcome plus elapsed-turn points. The award is committed once per contest before its payload is sent.

The client now splits the result into explicit states:

1. `OUTCOME_STINGER` — existing victory/defeat presentation.
2. `SUMMARY` — result, contest/match score, total, line count, and old-to-new balance.
3. `BONUS_DETAILS` — page through every earned line item.
4. `COMPLETE` — return to the world after user confirmation.

Persistent first-time bonuses, high scores, Duel Ranking, and EXP remain later extensions rather than being implied by the first implementation.

The DP transaction should occur server-side before the summary is sent so disconnecting cannot duplicate or erase the award. The result payload needs a contest/session ID, and the server needs a paid flag/idempotency check because a match can span multiple games and network updates can be retried.

## Implemented Duel Dimension preset

The implementation uses **TF Special as the structural baseline**—modern summon coverage and an explained additive assessment—but uses tuned Duel Dimension values instead of copying its economy blindly.

Reasons:

- Duel Dimension currently prices a base pack at roughly 150 DP, starts a player at 500 DP, and awards 1000/500 for PvP. Raw TF3 values would be very small relative to that shop; raw TF Special values can already exceed the current flat win payout.
- Minecraft PvP creates farming/collusion risks absent from ordinary NPC progression. Difficulty bonus cannot trust a player-supplied "deck level."
- NPC duels currently award zero specifically to prevent farming, while Tag Force's primary loop is NPC dueling. That policy decision must be made before balancing assessment values.
- Behavioral bonuses should enrich ordinary play, not make stalling, self-damage scripts, or coin-loop decks the dominant economy strategy.

A safe first implementation slice would be outcome + turns + quick/reversal/opponent-turn + LP/deck state + spell/trap/no-special + summon-type + damage-type bonuses. Defer key cards, novelty card IDs, zone locks, and high-score records until the underlying telemetry is explicit and tested.

## Open questions that require a product decision

- Are DP rewards intended for PvP only, NPC duels, or both with separate multipliers/cooldowns?
- Should the preset feel like low-value GX/TF3 or high-value TF Special relative to the existing shop?
- Does a best-of-three pay per game (Tag Force-like result cadence) or once per whole match (current behavior)?
- Should losers receive only Tag Force's small Loss + Turn amount, or retain the current generous 500 DP participation award?
- Do we want Duelist Level/EXP and a persistent Duel Ranking now, or initially only DP line items?
- Should first-time/new-record awards be one-time persistent achievements per player?

## Sources

- Original game manual: <https://ms.yugipedia.com/e/eb/Yu-Gi-Oh%21_GX_Tag_Force_Manual.pdf>
- Original GX Tag Force bonus transcription: <https://yugioh.fandom.com/wiki/Yu-Gi-Oh%21_GX_Tag_Force%3A_Bonus/Challenges>
- Contemporary original-game bonus discussion: <https://gamefaqs.gamespot.com/boards/930803-yu-gi-oh-gx-tag-force/40799092>
- Tag Force 3 Duel Ranking bonus table: <https://w.atwiki.jp/1548908-tf3/pages/118.html>
- Tag Force Special Duel Ranking bonus table: <https://w.atwiki.jp/tfsp/pages/99.html>
- Tag Force Special DP/economy page: <https://w.atwiki.jp/tfsp/pages/25.html>
- Original result-screen footage: <https://www.youtube.com/watch?v=RPjZrgqa8xU>
- Tag Force Special result-screen footage: <https://www.youtube.com/watch?v=hiaUZM4HlMY>
