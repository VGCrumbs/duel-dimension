package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.clientutil.DuelClientState;
import de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Duel Hub, opened with {@code Y}.
 * <p>
 * One place for everything that is not a duel: who you are, what you play, and
 * how it looks. In particular it is now the <em>only</em> place the duel mat is
 * chosen — the in-duel selector is gone, because a setting that can be changed
 * from two places has no single source of truth and the duel screen is the
 * worse of the two: it is the one moment a player is busy.
 * <p>
 * Every surface here is a PNG. Nothing is a filled rectangle and no vanilla
 * widget art is used, so the whole hub can be reskinned by replacing files
 * under {@code textures/gui}.
 */
public class DuelHubScreen extends Screen
{
    /** The hub's sections, in tab order. */
    public enum Section
    {
        PROFILE("Profile"),
        DECKS("Decks"),
        SETTINGS("Settings");

        private final String label;

        Section(String label)
        {
            this.label = label;
        }

        public String label()
        {
            return label;
        }
    }

    private static final int WIDTH = 420;
    private static final int HEIGHT = 260;
    private static final int TAB_W = 92;
    private static final int TAB_H = 22;
    private static final int PAD = 10;

    /** Remembered across openings, so returning to the hub lands where you left. */
    private static Section section = Section.PROFILE;

    private int left;
    private int top;
    private MatColourPicker matPicker;

    public DuelHubScreen()
    {
        super(Component.literal("Duel Hub"));
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        rebuild();
    }

    private void rebuild()
    {
        clearWidgets();

        int tabX = left + PAD;
        for(Section candidate : Section.values())
        {
            Section target = candidate;
            addRenderableWidget(new HubWidgets.TabButton(tabX, top + PAD, TAB_W, TAB_H,
                Component.literal(candidate.label()), () -> section == target, pressed ->
            {
                section = target;
                rebuild();
            }));
            tabX += TAB_W + 4;
        }

        int bodyTop = top + PAD + TAB_H + 8;
        if(section == Section.SETTINGS)
        {
            matPicker = new MatColourPicker(left + PAD + 6, bodyTop + 26, 120, 14,
                DuelClientState.matColour());
            addRenderableWidget(new HubWidgets.TextureButton(left + PAD + 6, top + HEIGHT - 32,
                96, 20, Component.literal("Apply"), pressed -> applyMat()));
            addRenderableWidget(new HubWidgets.TextureButton(left + PAD + 108, top + HEIGHT - 32,
                96, 20, Component.literal("Reset"), pressed ->
            {
                matPicker.setColour(DuelClientState.DEFAULT_MAT_COLOUR);
                applyMat();
            }));
        }
        else if(section == Section.DECKS)
        {
            matPicker = null;
            addRenderableWidget(new HubWidgets.TextureButton(left + PAD + 6, top + HEIGHT - 32,
                120, 20, Component.literal("Edit Deck"), pressed ->
            {
                if(minecraft != null)
                {
                    minecraft.setScreen(new DeckEditorScreen(this));
                }
            }));
        }
        else
        {
            matPicker = null;
        }

        addRenderableWidget(new HubWidgets.TextureButton(left + WIDTH - PAD - 80,
            top + HEIGHT - 32, 80, 20, Component.literal("Close"), pressed -> onClose()));
    }

    private void applyMat()
    {
        if(matPicker == null)
        {
            return;
        }
        DuelClientState.setMatColour(matPicker.colour());
        // The other duelist draws your mat on their far half, so the colour has
        // to travel; it is a preference, not hidden information.
        DuelDimension.channel.sendToServer(
            new PromptMessages.SetPlayMat(DuelClientState.matColourId()));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(poseStack);
        NineSlice.draw(poseStack, HubTextures.PANEL, left, top, WIDTH, HEIGHT);

        int bodyTop = top + PAD + TAB_H + 8;
        int bodyHeight = HEIGHT - (bodyTop - top) - 40;
        NineSlice.draw(poseStack, HubTextures.PANEL_INSET, left + PAD, bodyTop,
            WIDTH - PAD * 2, bodyHeight);

        switch(section)
        {
            case PROFILE -> renderProfile(poseStack, bodyTop);
            case DECKS -> renderDecks(poseStack, bodyTop);
            case SETTINGS -> renderSettings(poseStack, bodyTop);
        }

        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    private void renderProfile(PoseStack poseStack, int bodyTop)
    {
        int x = left + PAD + 10;
        int y = bodyTop + 10;
        font.drawShadow(poseStack, "Profile", x, y, 0xFFF4D089);
        y += 16;
        String name = minecraft != null && minecraft.player != null
            ? minecraft.player.getGameProfile().getName() : "-";
        font.drawShadow(poseStack, "Duelist: " + name, x, y, 0xFFE6EAF2);
        y += 12;
        font.drawShadow(poseStack, "Active deck: " + DuelClientState.activeDeckName(), x, y, 0xFFC2C9D6);
        y += 18;
        // Stats arrive with the profile packet; until then this states plainly
        // that it is not wired rather than showing a convincing zero.
        font.drawShadow(poseStack, "Statistics are not recorded yet.", x, y, 0xFF7A8090);
    }

    private void renderDecks(PoseStack poseStack, int bodyTop)
    {
        int x = left + PAD + 10;
        int y = bodyTop + 10;
        font.drawShadow(poseStack, "Decks", x, y, 0xFFF4D089);
        y += 16;
        font.drawShadow(poseStack, "Saved Recipes", x, y, 0xFFE6EAF2);
        y += 12;
        font.drawShadow(poseStack, "  (none yet)", x, y, 0xFF7A8090);
        y += 16;
        font.drawShadow(poseStack, "Structure Decks", x, y, 0xFFE6EAF2);
        y += 12;
        font.drawShadow(poseStack, "  (none unlocked)", x, y, 0xFF7A8090);
        y += 18;
        font.drawShadow(poseStack, "Editing: " + EditorState.deck().name()
            + "  (" + EditorState.deck().main().size() + " main)", x, y, 0xFF7A8090);
    }

    private void renderSettings(PoseStack poseStack, int bodyTop)
    {
        int x = left + PAD + 10;
        font.drawShadow(poseStack, "Duel Mat", x, bodyTop + 10, 0xFFF4D089);
        if(matPicker != null)
        {
            matPicker.render(poseStack);
            // The preview is the real mat texture under the chosen tint, so
            // what is shown here is exactly what reaches the table.
            matPicker.renderPreview(poseStack, left + PAD + 168, bodyTop + 30, 210, 92);
            String hex = String.format("#%06X", matPicker.colour());
            font.drawShadow(poseStack, hex, left + PAD + 168, bodyTop + 128, 0xFFC2C9D6);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(matPicker != null && matPicker.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        if(matPicker != null && matPicker.mouseDragged(mouseX, mouseY))
        {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if(matPicker != null)
        {
            matPicker.mouseReleased();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
