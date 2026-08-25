package de.cas_ual_ty.dueldimension.clientutil;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of the image pipeline that can be settled without a GPU: the order
 * requests come off the queue, the enqueue-once rule, and the scroll-velocity
 * gate's arithmetic.
 * <p>
 * These run without the loader threads. {@code CardImageManager.init()} is what
 * starts them and nothing here calls it, which is exactly why it is an explicit
 * call rather than a static initialiser — a test can then watch the queue fill
 * up without four workers racing to empty it.
 */
class CardImageManagerTest
{
    private static Identifier card(String name)
    {
        return Identifier.fromNamespaceAndPath("dueldimension",
            "textures/item/128/" + name + ".png");
    }

    @BeforeEach
    void emptyTheQueues()
    {
        CardImageManager.toLoad.clear();
        for(java.util.Map<Identifier, CardImageManager.preloadStatus> map : CardImageManager.tMap)
        {
            map.clear();
        }
    }

    @Test
    void aRequestGetsThePlaceholderAndIsQueued()
    {
        assertSame(DuelTextures.UNKNOWN, CardImageManager.getTextureCard(card("a"), 128));
        assertEquals(1, CardImageManager.queued());
    }

    /**
     * The whole supersede mechanism. {@code image_manager.cpp}:725 pushes the
     * front and :458-459 pops it, so the newest request decodes first and a
     * stale one sinks to the bottom and is served last. A FIFO here is what
     * makes a flick feel laggy: it serves the rows already flown past before the
     * ones the player landed on.
     */
    @Test
    void theNewestRequestIsServedFirst()
    {
        CardImageManager.getTextureCard(card("first"), 128);
        CardImageManager.getTextureCard(card("second"), 128);
        CardImageManager.getTextureCard(card("newest"), 128);

        List<String> order = new ArrayList<>();
        for(CardImageManager.LoadParameter queued : CardImageManager.toLoad)
        {
            order.add(queued.id().getPath());
        }
        assertEquals(List.of("textures/item/128/newest.png",
            "textures/item/128/second.png",
            "textures/item/128/first.png"), order);
    }

    /**
     * {@code image_manager.cpp}:707-708. The entry flips to LOADING in the same
     * breath as the push, so a card drawn two hundred frames running enqueues
     * once and the other 199 calls are a hash lookup.
     */
    @Test
    void aCardDrawnEveryFrameIsQueuedOnce()
    {
        for(int frame = 0; frame < 200; frame++)
        {
            assertSame(DuelTextures.UNKNOWN, CardImageManager.getTextureCard(card("a"), 128));
        }
        assertEquals(1, CardImageManager.queued());
    }

    /** The same card at two sizes is two textures, so it is two requests. */
    @Test
    void thesameCardAtTwoSizesIsTwoRequests()
    {
        CardImageManager.getTextureCard(card("a"), 128);
        CardImageManager.getTextureCard(card("a"), 512);
        assertEquals(2, CardImageManager.queued());
    }

    /**
     * Which of the four maps a size lands in. Upper bounds rather than equality,
     * because {@code cardInfoImageSize} and friends are user-set anywhere in
     * 16..1024 and every one of them still has to land somewhere.
     */
    @Test
    void everySizeLandsInAClass()
    {
        assertEquals(0, CardImageManager.classOf(16));
        assertEquals(0, CardImageManager.classOf(64));
        assertEquals(1, CardImageManager.classOf(65));
        assertEquals(1, CardImageManager.classOf(128));
        assertEquals(2, CardImageManager.classOf(200));
        assertEquals(2, CardImageManager.classOf(256));
        assertEquals(3, CardImageManager.classOf(512));
        assertEquals(3, CardImageManager.classOf(1024));
    }

    /**
     * {@code drawing.cpp}:1378, which is 0.6 rows per millisecond — ten rows per
     * 16.6 ms frame. The threshold is stated against elapsed time so it means
     * the same thing whatever the frame rate, which is the property a
     * rows-per-frame constant would not have.
     */
    @Test
    void theGateOpensBelowTenRowsAFrame()
    {
        float frame60 = 1000F / 60F;
        assertTrue(CardImageManager.drawThumb(0, 0, frame60));
        assertTrue(CardImageManager.drawThumb(0, 1, frame60));
        assertTrue(CardImageManager.drawThumb(20, 11, frame60));
        assertFalse(CardImageManager.drawThumb(0, 30, frame60));
    }

    @Test
    void theGateMeansTheSameThingAtEveryFrameRate()
    {
        float frame60 = 1000F / 60F;
        float frame120 = 1000F / 120F;
        // 480 rows a second, expressed in each frame rate's own units: eight
        // rows in a 16.6 ms frame, four in an 8.3 ms one. Admitted in both.
        assertTrue(CardImageManager.drawThumb(0, 8, frame60));
        assertTrue(CardImageManager.drawThumb(0, 4, frame120));
        // 960 rows a second, likewise. Refused in both.
        assertFalse(CardImageManager.drawThumb(0, 16, frame60));
        assertFalse(CardImageManager.drawThumb(0, 8, frame120));
    }

    /**
     * Strictly less than, as EDOPro writes it, so exactly ten rows in exactly
     * one 60 fps frame is refused rather than admitted.
     */
    @Test
    void theThresholdItselfIsRefused()
    {
        float frame60 = 1000F / 60F;
        assertTrue(CardImageManager.drawThumb(0, 9, frame60));
        assertFalse(CardImageManager.drawThumb(0, 10, frame60));
    }

    /**
     * The coupling {@code sweep} depends on, and the one nothing in the type
     * system enforces.
     * <p>
     * Releasing a texture removes it from {@code TextureManager.byPath}. If the
     * status map still said LOADED, {@code getTextureCard} would go on handing
     * the Identifier out, the blit would miss, and MC would read, decode and
     * upload it inline on the render thread — silently restoring the exact
     * hitch this class removes. So forgetting must make the card askable again.
     */
    @Test
    void forgettingACardMakesItAskableAgain()
    {
        CardImageManager.getTextureCard(card("a"), 128);
        assertEquals(1, CardImageManager.queued());
        // Drawn again while it is still in flight: no second request.
        CardImageManager.getTextureCard(card("a"), 128);
        assertEquals(1, CardImageManager.queued());

        CardImageManager.forget(card("a"), CardImageManager.classOf(128));
        CardImageManager.getTextureCard(card("a"), 128);
        assertEquals(2, CardImageManager.queued());
    }

    /**
     * {@code image_manager.cpp}:386-388 and :733-737. A card whose file is
     * missing is answered once and then left alone, or every frame would queue
     * a decode that is going to fail again.
     */
    @Test
    void aFailedCardIsNotAskedForAgain()
    {
        int index = CardImageManager.classOf(128);
        CardImageManager.getTextureCard(card("gone"), 128);
        CardImageManager.toLoad.clear();
        CardImageManager.tMap[index].put(card("gone"), CardImageManager.preloadStatus.FAILED);

        for(int frame = 0; frame < 50; frame++)
        {
            assertSame(DuelTextures.UNKNOWN, CardImageManager.getTextureCard(card("gone"), 128));
        }
        assertEquals(0, CardImageManager.queued());
    }

    /**
     * A card already on the GPU is handed straight back, which is the whole
     * steady state: one hash lookup and nothing else, for every card on screen,
     * every frame.
     */
    @Test
    void aLoadedCardIsHandedStraightBack()
    {
        Identifier ready = card("ready");
        CardImageManager.tMap[CardImageManager.classOf(512)]
            .put(ready, CardImageManager.preloadStatus.LOADED);
        // The caller's own object back, not merely an equal one: CardShopScreen
        // asks whether what it got IS the placeholder, by reference.
        assertSame(ready, CardImageManager.getTextureCard(ready, 512));
        assertEquals(0, CardImageManager.queued());
    }

    /** Direction does not matter; scrolling back is as fast as scrolling on. */
    @Test
    void theGateIsSymmetric()
    {
        float frame60 = 1000F / 60F;
        assertFalse(CardImageManager.drawThumb(0, 30, frame60));
        assertFalse(CardImageManager.drawThumb(30, 0, frame60));
        assertTrue(CardImageManager.drawThumb(0, 3, frame60));
        assertTrue(CardImageManager.drawThumb(3, 0, frame60));
    }
}
