package de.cas_ual_ty.dueldimension.card;

import de.cas_ual_ty.dueldimension.DdDatabase;
import de.cas_ual_ty.dueldimension.card.properties.Properties;
import de.cas_ual_ty.dueldimension.util.JsonKeys;
import net.minecraft.nbt.CompoundTag;

/**
 * A held card, to and from Minecraft's NBT.
 * <p>
 * The last thing keeping {@link CardHolder} — and through it most of the card
 * model — pinned to one Minecraft version. Everything else about a held copy is
 * four fields and no opinion about the game; this is the part that genuinely
 * cannot be shared, because the accessors below do not exist in the same shape
 * on both targets. {@code getLongOr}, {@code getByteOr} and {@code getStringOr}
 * are 26.2's; before 1.21.5 the getters returned values directly and there was
 * no {@code *Or} family at all.
 * <p>
 * So the platform owns the encoding and the card owns the data. The keys are
 * unchanged, so a world or a packet written by the old code reads here.
 */
public final class CardHolderNbt
{
    private CardHolderNbt()
    {
    }

    /**
     * A card from a tag.
     * <p>
     * An unknown id becomes {@link Properties#DUMMY} rather than null, which is
     * what the constructor this replaces did: a card whose database entry has
     * gone is still a card-shaped thing in a slot, and a null here would be a
     * crash somewhere else later.
     */
    public static CardHolder read(CompoundTag nbt)
    {
        Properties card = DdDatabase.PROPERTIES_LIST.get(nbt.getLongOr(JsonKeys.ID, 0L));

        if(card == null)
        {
            card = Properties.DUMMY;
        }

        return new CardHolder(card,
            nbt.getByteOr(JsonKeys.IMAGE_INDEX, (byte) 0),
            nbt.getStringOr(JsonKeys.RARITY, ""),
            nbt.getStringOr(JsonKeys.CODE, ""));
    }

    /**
     * A card into a tag.
     * <p>
     * The dummy writes no id, exactly as before — absent means "no card" and
     * writing 0 would make it a card with id zero.
     */
    public static void write(CardHolder holder, CompoundTag nbt)
    {
        if(holder.getCard() != Properties.DUMMY)
        {
            nbt.putLong(JsonKeys.ID, holder.getCard().getId());
        }

        nbt.putByte(JsonKeys.IMAGE_INDEX, holder.getImageIndex());
        nbt.putString(JsonKeys.RARITY, holder.getRarity());
        nbt.putString(JsonKeys.CODE, holder.getCode());
    }
}
