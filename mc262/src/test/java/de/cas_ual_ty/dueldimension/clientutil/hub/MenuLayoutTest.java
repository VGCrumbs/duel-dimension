package de.cas_ual_ty.dueldimension.clientutil.hub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Menu view has to fit the panel it is in, and not on top of itself.
 * <p>
 * The first version of it put five preset buttons at {@code bodyTop + 48} —
 * which is exactly where {@link DuelHubScreen#matLayout} starts its colour
 * wheel, because the Menu view borrowed the mat's geometry wholesale. The
 * result was the wheel, the live preview and the preview's own heading all
 * drawn underneath the row of buttons.
 * <p>
 * That is a class of bug a screenshot catches and a compiler never will, so the
 * geometry is a static function and this sweeps it — the same shape as
 * {@code MatLayoutTest}, and for the same reason: an offset that is right at
 * the authored size and wrong at every other one looks correct while you are
 * writing it.
 */
class MenuLayoutTest
{
    private static final int LINE_HEIGHT = 9;

    /** The row of presets and the controls under it must not share any pixels. */
    @Test
    void thePresetsNeverCoverTheControls()
    {
        sweep((layout, at) ->
        {
            assertTrue(layout.previewY() >= layout.presetY() + 18,
                "the controls start inside the preset row" + at);
        });
    }

    @Test
    void nothingCrossesTheBodyAtAnyPanelSize()
    {
        sweep((layout, at) ->
        {
            assertTrue(layout.presetY() + 18 <= layout.bodyBottom(),
                "the preset row runs past the body" + at);
            assertTrue(layout.previewY() + layout.wheel() <= layout.bodyBottom(),
                "the wheel runs past the body" + at);
            assertTrue(layout.previewY() + layout.previewH() <= layout.bodyBottom(),
                "the preview runs past the body" + at);
            assertTrue(layout.hexY() + LINE_HEIGHT <= layout.bodyBottom(),
                "the hex line runs past the body" + at);
        });
    }

    /** Five buttons, and the fifth has to end inside the panel. */
    @Test
    void thePresetRowFitsAcross()
    {
        sweep((layout, at) ->
        {
            int end = 6 + layout.presetStride() * 4 + layout.presetW();
            assertTrue(end <= layout.previewX() + layout.previewW(),
                "the fifth preset runs off the panel" + at);
            assertTrue(layout.presetW() > 0, "a preset button has no width" + at);
        });
    }

    /** The preview sits beside the wheel, not over it. */
    @Test
    void theWheelAndThePreviewDoNotOverlap()
    {
        sweep((layout, at) ->
        {
            assertTrue(layout.previewX() >= 6 + layout.wheel(),
                "the preview overlaps the wheel" + at);
            assertTrue(layout.previewW() > 0, "the preview has no width" + at);
        });
    }

    private interface Check
    {
        void run(DuelHubScreen.MenuLayout layout, String at);
    }

    /**
     * Every panel this screen can be, not the one it was drawn at.
     * <p>
     * The panel is {@code min(460, width - 20)} by {@code min(280, height - 20)},
     * so a small window or a large GUI scale shrinks it — which is the case the
     * authored numbers were never checked against.
     */
    private static void sweep(Check check)
    {
        for(int panelH = 150; panelH <= 280; panelH++)
        {
            for(int panelW = 260; panelW <= 460; panelW += 4)
            {
                check.run(DuelHubScreen.menuLayout(panelW, panelH, LINE_HEIGHT),
                    " at " + panelW + "x" + panelH);
            }
        }
    }
}
