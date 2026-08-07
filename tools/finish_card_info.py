"""Hand-finishes CardInfoScreen after the mechanical converters.

The converters do the parts that are a pattern. What is left is every place the
old API expressed a colour or a texture as GLOBAL STATE set before a draw:

    RenderSystem.setShaderColor(...)  ->  a tint argument on the blit
    DuelTextures.bindSmooth(tex)      ->  a texture argument on the blit
    ScreenUtil.white()                ->  nothing; there is no colour to reset

plus the handful of renames the converter deliberately leaves alone because
getting them wrong is silent (setScreen, renderBackground, tooltips) and the
widget field accesses it could not see through a subclass.
"""
import io
import re

PATH = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/CardInfoScreen.java"

src = io.open(PATH, encoding="utf-8").read()

# --- screens open differently ------------------------------------------------
src = src.replace("minecraft.setScreen(", "minecraft.setScreenAndShow(")

# --- the screen's own backdrop ----------------------------------------------
src = src.replace("renderBackground(poseStack, 0, 0, 0F);",
                  "extractBackground(poseStack, mouseX, mouseY, partialTick);")

# --- the two card blits ------------------------------------------------------
# Each was three statements: reset the colour, bind the texture, blit. The
# texture is an argument now and the colour was always plain white, so the
# first two lines are the blit's arguments and the reset is nothing at all.
src = src.replace("""        ScreenUtil.white();
        DuelTextures.bindSmooth(DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE));
        DdBlitUtil.blit(poseStack, artX, artY, artW(), artH(),
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1 - DuelTextures.CARD_U0,
            DuelTextures.CARD_V1 - DuelTextures.CARD_V0, 1, 1);""",
"""        DdBlitUtil.blit(poseStack,
            DuelTextures.card(card, (byte)0, DuelTextures.PREVIEW_CARD_SIZE),
            artX, artY, artW(), artH(),
            DuelTextures.CARD_U0, DuelTextures.CARD_V0,
            DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);""")

src = src.replace("""            ScreenUtil.white();
            DuelTextures.bindSmooth(DuelTextures.card(other, (byte)0, DuelTextures.ICON_CARD_SIZE));
            DdBlitUtil.blit(poseStack, cx, cy, cardW, cardH,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0,""",
"""            DdBlitUtil.blit(poseStack,
                DuelTextures.card(other, (byte)0, DuelTextures.ICON_CARD_SIZE),
                cx, cy, cardW, cardH,
                DuelTextures.CARD_U0, DuelTextures.CARD_V0,""")

# The related-card blit's remaining two lines still pass a WIDTH where the new
# call wants the far edge, and a trailing "1, 1" scale pair that is gone.
src = src.replace("""                DuelTextures.CARD_U1 - DuelTextures.CARD_U0,
                DuelTextures.CARD_V1 - DuelTextures.CARD_V0, 1, 1);""",
"""                DuelTextures.CARD_U1, DuelTextures.CARD_V1, DdBlitUtil.NO_TINT);""")

# --- the star, which is a widget and so has accessors now --------------------
src = src.replace("""            NineSlice.draw(poseStack, HubTextures.BUTTON, x, y, width, height,
                isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE, 3);
            int mark = Math.min(width, height) - 5;
            RenderSystem.setShaderColor(1F, 1F, 1F, on ? 1F : 0.35F);
            DdBlitUtil.blit(poseStack, x + (width - mark) / 2, y + (height - mark) / 2,
                mark, mark, 0, 0, 1, 1, 1, 1);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);""",
"""            NineSlice.draw(poseStack, HubTextures.BUTTON, getX(), getY(), getWidth(), getHeight(),
                isHoveredOrFocused() ? NineSlice.HOVER : NineSlice.IDLE, 3);
            int mark = Math.min(getWidth(), getHeight()) - 5;
            // An unlit star was drawn faint by setting the shader colour before
            // the blit and putting it back after. The faintness is the blit's
            // own argument now, so there is nothing to put back.
            DdBlitUtil.fullBlit(poseStack, HubTextures.STAR,
                getX() + (getWidth() - mark) / 2, getY() + (getHeight() - mark) / 2,
                mark, mark, DdBlitUtil.alpha(on ? 1F : 0.35F));""")

# --- the widget field the converter could not reach --------------------------
src = src.replace("addButton.x", "addButton.getX()")

# --- text takes whole pixels -------------------------------------------------
src = src.replace("boxY + i * 10F", "boxY + i * 10")

# --- a tooltip is the screen's to set ----------------------------------------
src = re.sub(r"renderTooltip\(poseStack, (.+?), mouseX, mouseY\);",
             r"poseStack.setTooltipForNextFrame(font, \1, mouseX, mouseY);", src)

# --- imports -----------------------------------------------------------------
src = src.replace("import com.mojang.blaze3d.systems.RenderSystem;\n", "")

io.open(PATH, "w", encoding="utf-8", newline="\n").write(src)
left = len(re.findall(r"setShaderColor|bindSmooth|ScreenUtil\.white", src))
print("CardInfoScreen finished; %d global-state calls left" % left)
