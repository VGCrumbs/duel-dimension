package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.ocg.prompt.BoardSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a card shows, and what it must never show.
 * <p>
 * This is the rule the whole 3D board rests on. A card standing in the world can
 * be walked around, crouched under and looked at from below by anyone nearby, so
 * "the renderer only draws the back" is not a good enough guarantee -- the
 * guarantee has to be that there is no art to draw. These pin that the
 * concealment happens here, at the one place all three boards ask.
 */
public class CardFacesTest
{
    /** A slot the way the server sends a concealed one: no identity at all. */
    private static BoardSnapshot.Slot concealed()
    {
        return new BoardSnapshot.Slot(true, 0, true, false, 0, 0, 0, 0, 0, 0, 0, null, false, 0);
    }

    private static BoardSnapshot.Slot faceDownWithCode()
    {
        return new BoardSnapshot.Slot(true, 46986414, true, false, 0, 0, 0, 0, 0, 0, 0, null,
            false, 0);
    }

    @Test
    public void aConcealedSlotShowsItsBack()
    {
        assertTrue(CardFaces.showsBack(concealed(), false));
        assertTrue(CardFaces.showsBack(concealed(), true),
            "a slot with no code has nothing to show even to its owner");
    }

    /**
     * Belt and braces. The server never sends a face-down card's identity, so
     * this case should be unreachable -- but if it ever becomes reachable, the
     * card still has to come out face down, because by then the identity is
     * already on the client and the renderer is the last thing standing between
     * it and the screen.
     */
    @Test
    public void aFaceDownCardStaysFaceDownEvenIfItsIdentityArrives()
    {
        assertTrue(CardFaces.showsBack(faceDownWithCode(), false));
    }

    /** Your own set card, in your own hand, is one you are allowed to look at. */
    @Test
    public void aFaceDownCardInYourOwnHandIsNotHidden()
    {
        assertFalse(CardFaces.showsBack(faceDownWithCode(), true));
    }

    /** A face-up card shows its face, which is the case that must still work. */
    @Test
    public void aFaceUpCardShowsItsFace()
    {
        assertFalse(CardFaces.showsBack(new BoardSnapshot.Slot(true, 46986414, false, false,
            0, 0, 0, 0, 0, 0, 0, null, false, 0), false));
    }

    /**
     * Which textures are already card-shaped decides whether the art is drawn
     * through the letterbox window or the whole texture. Getting it wrong on a
     * back would show the card's own edge printed inside itself.
     */
    @Test
    public void backsAndTheUnknownCardAreAlreadyCardShaped()
    {
        assertTrue(CardFaces.isCardShaped(DuelTextures.COVER));
        assertTrue(CardFaces.isCardShaped(DuelTextures.COVER_OPPONENT));
        assertTrue(CardFaces.isCardShaped(DuelTextures.UNKNOWN));
        assertFalse(CardFaces.isCardShaped(
            ResourceLocation.fromNamespaceAndPath("dueldimension", "textures/duel/sleeves/anything.png")),
            "a sleeve is a square canvas with the card inside the window");
    }
}
