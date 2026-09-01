import sys

OLD = """        if(!menuButtons.isEmpty())
        {
            Button first = menuButtons.get(0);
            poseStack.fill(first.getX() - 2, first.getY() - 2, first.getX() + first.getWidth() + 2,
                first.getY() + menuButtons.size() * MENU_ROW, 0xC0000000);
        }

        super.render(poseStack.vanilla(), mouseX, mouseY, partialTick);
        renderPicker(poseStack, mouseX, mouseY);
        renderWaiting(poseStack);
        renderResult(poseStack);

        if(pileView != null)
        {
            renderPileView(poseStack, mouseX, mouseY);
        }"""

NEW = """        // EVERYTHING BELOW IS AN OVERLAY, AND HAS TO SAY SO.
        //
        // The instruments above -- the top bar, the phase row, the hint banner,
        // the sidebar, the log -- are mostly TEXT, and the panels that cover
        // them are mostly fills and blits. On 1.21.1 those are different render
        // types, resolved in whatever order the buffer source iterates them, so
        // drawing a panel after a label does not put it over that label. The
        // whole reported class of bug is that one fact: prompt panels with the
        // sidebar's text showing through them, and a VICTORY band with the log
        // written across it.
        //
        // The seam goes in before the context menu's plate rather than after
        // it, because the plate and the buttons standing on it are drawn by two
        // different people -- the plate here, the buttons inside super.render --
        // and they have to clear the instruments together while keeping their
        // own order. Layering.above is the seam that does not wrap, for exactly
        // this shape; see its javadoc.
        Layering.above(poseStack);
        if(!menuButtons.isEmpty())
        {
            Button first = menuButtons.get(0);
            poseStack.fill(first.getX() - 2, first.getY() - 2, first.getX() + first.getWidth() + 2,
                first.getY() + menuButtons.size() * MENU_ROW, 0xC0000000);
        }

        super.render(poseStack.vanilla(), mouseX, mouseY, partialTick);

        // Each of these is self-contained and stacks over the last, so each one
        // states it. The ORDER is the fix as much as the layering is.
        Layering.foreground(poseStack, () -> renderPicker(poseStack, mouseX, mouseY));
        Layering.foreground(poseStack, () -> renderWaiting(poseStack));
        if(pileView != null)
        {
            Layering.foreground(poseStack, () -> renderPileView(poseStack, mouseX, mouseY));
        }
        // Last, and so highest. The result used to be drawn BEFORE the pile
        // view, which meant a graveyard left open when the last card resolved
        // covered the VICTORY band that ended the duel -- the one thing on the
        // screen that must never be covered by anything except a tooltip.
        Layering.foreground(poseStack, () -> renderResult(poseStack));"""

for tree in ('mc1211', 'mc262'):
    p = tree + '/src/main/java/de/cas_ual_ty/dueldimension/clientutil/EngineDuelScreen.java'
    raw = open(p, 'rb').read().decode('utf-8')
    crlf = '\r\n' in raw
    s = raw.replace('\r\n', '\n')
    if s.count(OLD) != 1:
        sys.exit('%s: %d matches' % (p, s.count(OLD)))
    s = s.replace(OLD, NEW)
    # mc262 refers to Layering by its fully qualified name elsewhere; both trees
    # share the package, so a plain reference resolves in each.
    if 'import de.cas_ual_ty.dueldimension.clientutil.Layering;' not in s \
            and 'package de.cas_ual_ty.dueldimension.clientutil;' not in s:
        sys.exit(p + ': Layering is neither imported nor in package')
    open(p, 'wb').write((s.replace('\n', '\r\n') if crlf else s).encode('utf-8'))
    print('patched', p)
