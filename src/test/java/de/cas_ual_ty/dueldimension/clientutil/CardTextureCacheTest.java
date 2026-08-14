package de.cas_ual_ty.dueldimension.clientutil;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eviction rule and the arithmetic under it.
 * <p>
 * {@code sweep(Releaser)} takes the disposal as an argument precisely so this
 * can run: the release is the only part that needs a GPU, and the rule that
 * decides what to release does not.
 */
class CardTextureCacheTest
{
    private static Identifier card(int size, String name)
    {
        return Identifier.fromNamespaceAndPath("dueldimension",
            "textures/item/" + size + "/" + name + ".png");
    }

    /** Records what would have been released, in the order it was chosen. */
    private final List<Identifier> released = new ArrayList<>();

    private final CardTextureCache.Releaser recorder = (id, sizeIndex) -> released.add(id);

    @BeforeEach
    void empty()
    {
        released.clear();
        CardTextureCache.clear();
        assertEquals(0L, CardTextureCache.residentBytes());
    }

    @Test
    void residentBytesAreTheTextureSize()
    {
        CardTextureCache.touch(card(128, "a"), 128);
        // RGBA, one byte a channel, no mipmaps.
        assertEquals(128L * 128L * 4L, CardTextureCache.residentBytes());

        CardTextureCache.touch(card(512, "b"), 512);
        assertEquals(128L * 128L * 4L + 512L * 512L * 4L, CardTextureCache.residentBytes());
    }

    /** Touching a card already resident must not charge for it twice. */
    @Test
    void touchingTwiceCostsOnce()
    {
        CardTextureCache.touch(card(128, "a"), 128);
        CardTextureCache.touch(card(128, "a"), 128);
        assertEquals(128L * 128L * 4L, CardTextureCache.residentBytes());
    }

    @Test
    void nothingIsEvictedInsideTheBudget()
    {
        for(int i = 0; i < 100; i++)
        {
            CardTextureCache.touch(card(128, "c" + i), 128);
        }
        CardTextureCache.sweep(recorder);
        assertTrue(released.isEmpty());
    }

    /**
     * Access order, so the iterator starts at the least recently used — and a
     * card touched again is not the least recently used any more, which is what
     * stops scrolling back and forth over the same region evicting anything.
     */
    @Test
    void theLeastRecentlyTouchedGoesFirst()
    {
        // 48 MB of 128px icons is 750 of them; 800 puts it over.
        for(int i = 0; i < 800; i++)
        {
            CardTextureCache.touch(card(128, "c" + i), 128);
        }
        // Look at the oldest one again, so it is no longer the oldest.
        CardTextureCache.touch(card(128, "c0"), 128);

        CardTextureCache.sweep(recorder);

        assertFalse(released.isEmpty());
        assertFalse(released.contains(card(128, "c0")));
        assertEquals(card(128, "c1"), released.get(0));
        // And it stopped the moment the budget was met, rather than emptying.
        assertTrue(CardTextureCache.residentBytes() <= 48L * 1024L * 1024L);
    }

    /**
     * <b>The reason the pool is split four ways.</b> With one shared budget,
     * twenty-four previews arriving is 24 MB of pressure that lands on whatever
     * was least recently touched — and in a deck editor that is a grid icon on a
     * row the player scrolled past two seconds ago and is about to scroll back
     * to. Evicting it is not free now: it resets the entry, so the card shows a
     * placeholder again.
     */
    @Test
    void aBurstOfPreviewsCannotEvictTheGrid()
    {
        for(int i = 0; i < 700; i++)
        {
            CardTextureCache.touch(card(128, "icon" + i), 128);
        }
        long icons = CardTextureCache.residentBytes();

        // Far more 512px previews than their own 112 MB pool holds.
        for(int i = 0; i < 200; i++)
        {
            CardTextureCache.touch(card(512, "preview" + i), 512);
        }
        CardTextureCache.sweep(recorder);

        assertFalse(released.isEmpty());
        for(Identifier evicted : released)
        {
            assertTrue(evicted.getPath().contains("/512/"),
                "a preview burst evicted " + evicted);
        }
        // Every icon is still there, untouched by the burst.
        assertTrue(CardTextureCache.residentBytes() >= icons);
    }

    /** Each class is swept against its own budget, not against the total. */
    @Test
    void eachClassIsSweptAgainstItsOwnBudget()
    {
        // 8 MB of 64px item icons is 500; 600 puts that class over while the
        // other three are empty and the 192 MB total is nowhere near reached.
        for(int i = 0; i < 600; i++)
        {
            CardTextureCache.touch(card(64, "item" + i), 64);
        }
        CardTextureCache.sweep(recorder);

        assertFalse(released.isEmpty());
        assertTrue(CardTextureCache.residentBytes() <= 8L * 1024L * 1024L);
    }

    /**
     * The size class travels with the eviction, because the release and the
     * status reset have to name the same map. {@code sweep()} passes this
     * straight to {@code CardImageManager.forget}, and a wrong index there would
     * leave the entry saying LOADED for a texture that no longer exists — which
     * is the silent way back to a synchronous decode.
     */
    @Test
    void theSizeClassTravelsWithTheEviction()
    {
        List<Integer> classes = new ArrayList<>();
        for(int i = 0; i < 600; i++)
        {
            CardTextureCache.touch(card(64, "item" + i), 64);
        }
        CardTextureCache.sweep((id, sizeIndex) ->
        {
            released.add(id);
            classes.add(sizeIndex);
        });

        assertFalse(classes.isEmpty());
        for(int sizeIndex : classes)
        {
            assertEquals(CardImageManager.classOf(64), sizeIndex);
        }
    }

    @Test
    void clearingForgetsEverything()
    {
        CardTextureCache.touch(card(128, "a"), 128);
        CardTextureCache.touch(card(512, "b"), 512);
        CardTextureCache.clear();
        assertEquals(0L, CardTextureCache.residentBytes());
    }
}
