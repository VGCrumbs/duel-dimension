package de.cas_ual_ty.dueldimension.duel.screen.widget;

import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import de.cas_ual_ty.dueldimension.clientutil.widget.ITooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Predicate;


public class LPTextFieldWidget extends EditBox
{
    /**
     * What this field is allowed to hold: a sign, then digits.
     * <p>
     * Forge handed this to {@code EditBox.setFilter}. 26.2 has no filter and no
     * successor to one -- {@code addFormatter} only colours what is already
     * there -- so it is applied by the overrides below instead. It cannot
     * simply be dropped: the screen reads this field back with
     * {@code Integer.valueOf}, and a letter left in it would throw.
     */
    private static final Predicate<String> SIGNED_NUMBER = (text) ->
    {
        if(text.isEmpty())
        {
            return true;
        }
        else
        {
            String pre = text.substring(0, 1);

            if(!pre.equals("+") && !pre.equals("-"))
            {
                return false;
            }

            if(text.length() == 1)
            {
                return true;
            }
            else
            {
                return text.substring(1).matches("\\d+");
            }
        }
    };

    public ITooltip tooltip;

    public LPTextFieldWidget(Font fontrenderer, int x, int y, int width, int height, ITooltip tooltip)
    {
        super(fontrenderer, x, y, width, height, Component.empty());
        this.tooltip = tooltip;
        setMaxLength(6);
    }

    @Override
    public void setValue(String text)
    {
        if(SIGNED_NUMBER.test(text))
        {
            super.setValue(text);
        }
    }

    @Override
    public void insertText(String text)
    {
        String before = getValue();
        int cursor = getCursorPosition();
        super.insertText(text);
        undoIfRefused(before, cursor);
    }

    /**
     * Backspace and delete, which have to be caught out here.
     * <p>
     * Forge checked the filter on deletion as well as insertion -- backspacing
     * over the leading sign left {@code "100"}, which it refused. There is no
     * override that catches that in 26.2: the public {@code deleteChars} and
     * {@code deleteWords} are wrappers nothing internal uses, and the key
     * handler calls the private {@code deleteText} directly. So the guard goes
     * on the one thing above it that is public.
     */
    @Override
    public boolean keyPressed(KeyEvent event)
    {
        String before = getValue();
        int cursor = getCursorPosition();
        boolean handled = super.keyPressed(event);
        undoIfRefused(before, cursor);
        return handled;
    }

    /**
     * Puts an edit back if it left something this field will not hold.
     * <p>
     * 1.19's EditBox asked the filter <em>before</em> writing; 26.2's writes
     * first and has nothing to ask, so the only place left to stand is after
     * the fact. The cursor is restored along with the text, because a refused
     * keystroke must not move it.
     */
    private void undoIfRefused(String before, int cursor)
    {
        if(!SIGNED_NUMBER.test(getValue()))
        {
            super.setValue(before);
            setCursorPosition(cursor);
            setHighlightPos(cursor);
        }
    }

    /**
     * The field, described at twice its size and drawn through a half-scale
     * matrix, so its border and text land on the half-pixels the duel field is
     * measured in.
     * <p>
     * The Forge hook was {@code renderButton}; on an EditBox the 26.2 one is
     * {@code extractWidgetRenderState}, which the class makes public.
     */
    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor ms, int mouseX, int mouseY, float partialTicks)
    {
        int x = getX();
        int y = getY();
        int w = width;
        int h = height;

        // Size before position, unlike the Forge original, which interleaved
        // them: 26.2's EditBox caches where its text goes and recomputes that
        // only from setX/setY, so a width written afterwards would leave the
        // text a pixel out of the box it belongs to.
        width = w * 2 - 2;
        height = h * 2 - 2;
        setX(x * 2 + 1);
        setY(y * 2 + 1);

        ms.pose().pushMatrix();
        ms.pose().scale(0.5F, 0.5F);

        super.extractWidgetRenderState(ms, mouseX * 2, mouseY * 2, partialTicks);

        ms.pose().popMatrix();

        width = w;
        height = h;
        setX(x);
        setY(y);

        if(isMouseOver(mouseX, mouseY))
        {
            tooltip.onTooltip(this, ms, mouseX, mouseY);
        }
    }

    /**
     * Twice the room, because the text inside is drawn at half scale.
     * <p>
     * Forge had to mirror {@code bordered} into a field of its own to answer
     * this, the getter being private; {@code isBordered()} is public in 26.2,
     * so the copy and the {@code setBordered} override that fed it are gone.
     */
    @Override
    public int getInnerWidth()
    {
        return 2 * (isBordered() ? width - 8 : width);
    }
}
