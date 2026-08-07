package de.cas_ual_ty.dueldimension.card;

import de.cas_ual_ty.dueldimension.DdComponents;
import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import net.minecraft.world.item.ItemStack;

/**
 * A card that lives on an item stack.
 * <p>
 * Same job as the Forge version, different mechanism: that one read and wrote
 * {@code itemStack.getOrCreateTag()}, and a stack has no tag any more. It reads
 * and writes {@link DdComponents#CARD} instead.
 * <p>
 * One consequence worth knowing. A component is immutable and a stack holds a
 * value, not a reference, so every setter has to put a fresh component back
 * rather than mutate one in place. The Forge version got away with mutating
 * because a CompoundTag on a stack IS the stack's state; here, forgetting to
 * write back would silently drop the change. Hence a single {@code save()} that
 * every setter calls, exactly as before -- the discipline is unchanged even
 * though the reason for it is new.
 */
public class ItemStackCardHolder extends CardHolder
{
    private final ItemStack itemStack;

    public ItemStackCardHolder(ItemStack itemStack)
    {
        super();
        this.itemStack = itemStack;
        read();
    }

    private void read()
    {
        DdComponents.Card stored = itemStack.getOrDefault(DdComponents.CARD,
            DdComponents.Card.EMPTY);
        // A card the database does not know -- because it has not finished
        // loading, or because the pack that added it is gone -- reads as the
        // dummy rather than as null, which is what the tag-based version did.
        Properties known = DdDatabase.PROPERTIES_LIST.get(stored.id());
        card = known == null ? Properties.DUMMY : known;
        imageIndex = stored.imageIndex();
        rarity = stored.rarity();
        code = stored.code();
    }

    private void save()
    {
        itemStack.set(DdComponents.CARD, DdComponents.Card.of(this));
    }

    @Override
    public void setCard(Properties card)
    {
        super.setCard(card);
        save();
    }

    @Override
    public void setImageIndex(byte imageIndex)
    {
        super.setImageIndex(imageIndex);
        save();
    }

    @Override
    public void setRarity(String rarity)
    {
        super.setRarity(rarity);
        save();
    }

    @Override
    public void setCode(String code)
    {
        super.setCode(code);
        save();
    }

    @Override
    public void override(CardHolder cardHolder)
    {
        super.override(cardHolder);
        save();
    }
}
