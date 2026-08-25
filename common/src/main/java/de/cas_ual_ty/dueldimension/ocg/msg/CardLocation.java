package de.cas_ual_ty.dueldimension.ocg.msg;

/**
 * The core's 10-byte loc_info: where a card is.
 * Layout (LE): u8 controller, u8 location, u32 sequence, u32 position.
 * Source: ygopro-core card.h struct loc_info / duel.cpp write(loc_info).
 */
public record CardLocation(int controller, int location, int sequence, int position)
{
}
