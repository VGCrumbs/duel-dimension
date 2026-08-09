"""Chaos Disk in the off hand => the Seal is the card you draw.

Applied to the PLAYER's own deck only, at every place a player's deck is handed
to a duel: the NPC challenge and both seats of a PvP duel. An NPC's deck is never
touched -- it has no off hand to hold a disk in.

The off-hand SLOT, not the hand a use() came in on: the disk is worn, not
swung, and by the time a duel starts nobody is holding a use event.
"""
import io

def sub(old, new, label):
    global s
    if new in s:
        print("  skip (already applied):", label)
        return
    assert old in s, "anchor missing: " + label
    s = s.replace(old, new, 1)
    print("  ok:", label)

p = "src/main/java/de/cas_ual_ty/dueldimension/duel/npc/DuelistDuels.java"
s = io.open(p, encoding="utf-8").read()

# ---------- NPC challenge: the player's deck ----------
sub("""        HeadlessDuelRunner.Deck deck0 = playerDeck.cards();""",
    """        HeadlessDuelRunner.Deck deck0 = withChaosDiskPromise(serverPlayer, playerDeck.cards());""",
    "NPC path")

# ---------- PvP: both seats are players ----------
sub("""        HeadlessDuelRunner.Deck deck0 = deckA.cards();
        HeadlessDuelRunner.Deck deck1 = deckB.cards();""",
    """        HeadlessDuelRunner.Deck deck0 = withChaosDiskPromise(first, deckA.cards());
        HeadlessDuelRunner.Deck deck1 = withChaosDiskPromise(second, deckB.cards());""",
    "PvP path")

# ---------- the rule ----------
sub("""    private static void announceToBoth(""",
    """    /**
     * The Chaos Duel Disk's promise: draw The Seal of Orichalcos.
     * <p>
     * Worn in the off hand and holding the Seal somewhere in the deck, a
     * duelist opens with it — the card is moved to sixth from the top, which
     * with the first-turn draw is the card they draw on their first turn.
     * <p>
     * It moves a card the deck already contains. A deck without the Seal is
     * handed back untouched, so the disk cannot conjure one, and the deck stays
     * exactly the size and contents it was built as.
     */
    private static HeadlessDuelRunner.Deck withChaosDiskPromise(ServerPlayer player,
        HeadlessDuelRunner.Deck deck)
    {
        if(player == null || deck == null)
        {
            return deck;
        }
        // The off-hand SLOT, not the hand some use() arrived on: the disk is
        // worn for the duel, and by now nobody is holding an interaction.
        ItemStack offHand = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        if(!offHand.is(de.cas_ual_ty.dueldimension.DdItems.CHAOS_DISK))
        {
            return deck;
        }
        int seal = de.cas_ual_ty.dueldimension.duel.orichalcos.OrichalcosSouls.SEAL_PASSCODE;
        if(!deck.main().contains(seal))
        {
            return deck;
        }
        DuelDimension.log("Chaos Disk: " + player.getGameProfile().name()
            + " will open with The Seal of Orichalcos");
        return deck.guaranteeing(seal);
    }

    private static void announceToBoth(""", "withChaosDiskPromise")

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("done")
