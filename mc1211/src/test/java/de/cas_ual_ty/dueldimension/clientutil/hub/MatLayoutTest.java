package de.cas_ual_ty.dueldimension.clientutil.hub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Duel Mat view has to fit the panel it is in, whatever size that is.
 * <p>
 * It did not. The wheel was a flat 120 and every offset was written against the
 * authored 460x280 panel -- but the panel is {@code min(460, width - 20)} by
 * {@code min(280, height - 20)}, so at 1282x752 with GUI scale 3 it is 407x230
 * and the body loses fifty units. The wheel then ran out through the bottom of
 * its own inset and under the Apply row, taking the hex code with it.
 * <p>
 * Nothing in the code was wrong in isolation; the numbers were simply offsets
 * from a size the panel had stopped being. So the test sweeps sizes rather than
 * checking the one it was authored at -- which is the size that passed before.
 */
class MatLayoutTest
{
    private static final int LINE_HEIGHT = 9;

    /** The authored layout, which the general one has to reproduce exactly. */
    @Test
    void theFullSizePanelIsUnchanged()
    {
        DuelHubScreen.MatLayout mat = DuelHubScreen.matLayout(460, 280, LINE_HEIGHT);
        assertEquals(120, mat.wheel(), "wheel");
        assertEquals(178, mat.previewX(), "preview x");
        assertEquals(210, mat.previewW(), "preview width");
        assertEquals(92, mat.previewH(), "preview height");
    }

    @Test
    void nothingCrossesTheBodyAtAnyPanelSize()
    {
        for(int panelH = 150; panelH <= 280; panelH++)
        {
            for(int panelW = 260; panelW <= 460; panelW += 4)
            {
                DuelHubScreen.MatLayout mat =
                    DuelHubScreen.matLayout(panelW, panelH, LINE_HEIGHT);
                String at = " at " + panelW + "x" + panelH;
                assertTrue(mat.previewY() + mat.wheel() <= mat.bodyBottom(),
                    "wheel ran out of the body" + at);
                assertTrue(mat.previewY() + mat.previewH() <= mat.bodyBottom(),
                    "preview ran out of the body" + at);
                assertTrue(mat.hexY() + LINE_HEIGHT <= mat.bodyBottom(),
                    "hex code ran out of the body" + at);
            }
        }
    }

    @Test
    void thePreviewStaysInsideThePanelSideways()
    {
        // It is sized by height, so a narrow panel is what pushes it out.
        for(int panelW = 260; panelW <= 460; panelW += 2)
        {
            DuelHubScreen.MatLayout mat = DuelHubScreen.matLayout(panelW, 280, LINE_HEIGHT);
            assertTrue(mat.previewX() + mat.previewW() <= panelW - 10,
                "preview left the panel at width " + panelW);
        }
    }

    @Test
    void theHexSitsUnderThePreviewRatherThanOnIt()
    {
        for(int panelH = 150; panelH <= 280; panelH += 3)
        {
            DuelHubScreen.MatLayout mat = DuelHubScreen.matLayout(460, panelH, LINE_HEIGHT);
            assertTrue(mat.hexY() >= mat.previewY() + mat.previewH(),
                "hex overlapped the preview at height " + panelH);
        }
    }

    @Test
    void theMatKeepsItsShape()
    {
        // custom.png is 1024x448; a preview off that ratio is a skewed mat.
        for(int panelH = 150; panelH <= 280; panelH += 3)
        {
            for(int panelW = 260; panelW <= 460; panelW += 11)
            {
                DuelHubScreen.MatLayout mat =
                    DuelHubScreen.matLayout(panelW, panelH, LINE_HEIGHT);
                float ratio = mat.previewW() / (float)mat.previewH();
                assertTrue(Math.abs(ratio - 1024F / 448F) < 0.08F,
                    "mat was skewed to " + ratio + " at " + panelW + "x" + panelH);
            }
        }
    }

    /**
     * The wheel is as big as the room allows, and no bigger.
     * <p>
     * Deliberately NOT "at least 40 always". A floor that the body cannot hold
     * does not make the wheel usable, it puts it back through the bottom -- so
     * the guarantee is that the wheel spends whatever there is. 180 is where
     * the room for a usable one starts, and every panel the hub actually builds
     * is well above it.
     */
    @Test
    void theWheelSpendsTheRoomItHas()
    {
        for(int panelH = 150; panelH <= 280; panelH++)
        {
            DuelHubScreen.MatLayout mat = DuelHubScreen.matLayout(460, panelH, LINE_HEIGHT);
            int room = mat.bodyBottom() - 6 - mat.previewY();
            assertEquals(Math.min(120, Math.max(1, room)), mat.wheel(),
                "wheel did not take the room at height " + panelH);
            if(panelH >= 180)
            {
                assertTrue(mat.wheel() >= 40, "wheel was cramped at height " + panelH);
            }
        }
    }
}
