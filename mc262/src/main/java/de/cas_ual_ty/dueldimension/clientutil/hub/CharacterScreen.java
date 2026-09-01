package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.character.CharacterLook;
import de.cas_ual_ty.dueldimension.character.CharacterRamps;
import de.cas_ual_ty.dueldimension.clientutil.character.CharacterEdits;
import de.cas_ual_ty.dueldimension.clientutil.character.CharacterModels;
import de.cas_ual_ty.dueldimension.clientutil.character.CharacterPreview;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Making a character: every choice on one screen, beside the character itself.
 * <p>
 * <b>The creator and the wardrobe are one screen, not two.</b> The DS game split
 * them — parts when you start, colours later from a wardrobe — because it had
 * two small screens and a story reason to. Neither applies here: changing a
 * hairstyle and changing its colour are the same act, and doing them in
 * different places means leaving one to judge the other.
 * <p>
 * <b>The model is the preview.</b> Not a portrait of it: the same mesh, the same
 * palette rewrite and the same animation the world draws, turned by dragging, so
 * there is no second rendering path to disagree with the first. What is on the
 * left of this screen is what other players will see.
 * <p>
 * Every change is applied and sent as it is made rather than on a Save button.
 * A character is not a form to fill in, and the model is worn or not worn
 * independently of what it looks like — see the Profile tab for that switch.
 */
public class CharacterScreen extends Screen
{
    private static final int WIDTH = 460;
    private static final int HEIGHT = 300;
    private static final int PAD = 10;
    /** The preview pane on the left; the rest is choices. */
    private static final int STAGE_W = 176;

    /**
     * Where the right-hand column's rows sit, measured from {@code top}.
     * <p>
     * Written down rather than accumulated. The layout was a running {@code y}
     * that {@code rebuild} and the draw both had to step through identically,
     * and the moment the two diverged the colour wheel came down on top of the
     * tab row underneath it. Constants cannot drift apart.
     */
    private static final int ROW_GENDER = 32;
    private static final int ROW_SLOTS = 58;
    private static final int ROW_TONES = 144;
    private static final int ROW_TONE_SWATCH = 162;
    private static final int ROW_TARGETS = 172;
    private static final int ROW_LEGEND = 240;

    /**
     * The wheel's own corner, well clear of the column beside it.
     * <p>
     * It is 84 across with a slider ten past that, so it reaches
     * {@code WHEEL_X + 106} — which is why the targets below stack downward in
     * one narrow column instead of running across the panel into it.
     */
    private static final int WHEEL_X = 310;
    private static final int WHEEL_Y = 150;
    private static final int WHEEL_SIZE = 84;
    private static final int SLIDER_W = 12;

    /** The four slots, in the order the creator offers them. */
    private static final String[] SLOTS = {"face", "hair", "wear", "disc"};
    private static final String[] LABELS = {"Face", "Hair", "Outfit", "Duel Disk"};

    private final Screen parent;
    private final CharacterPreview preview = new CharacterPreview();

    private int left;
    private int top;
    /**
     * Which of the three colours the wheel is editing.
     * <p>
     * Three targets and one wheel rather than three wheels: they are the same
     * question asked about different parts of the same person, and a screen
     * with three colour wheels on it is a screen nobody can find anything on.
     */
    private enum Painting
    {
        // EYES sits between the skin and the outfit rather than at the end:
        // the column reads down the character, and eyes come before clothes.
        HAIR, SKIN, EYES, OUTFIT
    }

    private Painting painting = Painting.HAIR;
    private MatColourPicker colours;

    public CharacterScreen(Screen parent)
    {
        super(Component.literal("Character"));
        this.parent = parent;
    }

    private CharacterLook look()
    {
        return CharacterEdits.look();
    }

    private void change(CharacterLook look)
    {
        CharacterEdits.set(look);
        rebuild();
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        colours = new MatColourPicker(left + WHEEL_X, top + WHEEL_Y, WHEEL_SIZE, SLIDER_W,
            chosen());
        rebuild();
    }

    private void rebuild()
    {
        clearWidgets();
        int x = left + STAGE_W + PAD * 2;
        int inner = WIDTH - STAGE_W - PAD * 3;
        int y = top + ROW_GENDER;

        // Gender first: it changes which fifteen of everything the rest means,
        // so it reads as the question it is rather than as one row of four.
        addRenderableWidget(new HubWidgets.TabButton(x, y, inner / 2 - 2, 18,
            Component.literal("Female"), () -> look().female(),
            pressed -> change(look().withGender('f'))));
        addRenderableWidget(new HubWidgets.TabButton(x + inner / 2 + 2, y, inner / 2 - 2, 18,
            Component.literal("Male"), () -> !look().female(),
            pressed -> change(look().withGender('m'))));
        y = top + ROW_SLOTS;

        for(int i = 0; i < SLOTS.length; i++)
        {
            String slot = SLOTS[i];
            addRenderableWidget(new HubWidgets.TextureButton(x + inner - 46, y, 20, 16,
                Component.literal("<"), pressed -> step(slot, -1)));
            addRenderableWidget(new HubWidgets.TextureButton(x + inner - 22, y, 20, 16,
                Component.literal(">"), pressed -> step(slot, 1)));
            y += 20;
        }

        y = top + ROW_TONES;
        // The three the game ships, as themselves. They are not three colours
        // -- each is a hand-authored 32-entry table with its own shading -- so
        // they stay presets rather than becoming three positions on the wheel,
        // and pressing one clears any mix rather than approximating it.
        for(int tone = 1; tone <= CharacterLook.TONES; tone++)
        {
            int which = tone;
            addRenderableWidget(new HubWidgets.TabButton(x + (tone - 1) * 34, y, 30, 16,
                Component.literal(Integer.toString(tone)),
                () -> look().tone() == which && !look().mixedSkin(),
                pressed ->
                {
                    change(look().withTone(which));
                    if(painting == Painting.SKIN)
                    {
                        colours.setColour(chosen());
                    }
                }));
        }

        // DOWN THE COLUMN, not across it. Across, the third of them ran under
        // the colour wheel -- the two were laid out from different origins and
        // nothing said they had to agree. Stacked, they end where the wheel
        // begins and the arithmetic is visible in one place.
        Painting[] targets = Painting.values();
        String[] names = {"Hair", "Skin", "Eyes", "Outfit"};
        for(int i = 0; i < targets.length; i++)
        {
            Painting which = targets[i];
            addRenderableWidget(new HubWidgets.TabButton(x, top + ROW_TARGETS + i * 22,
                WHEEL_X - (STAGE_W + PAD * 2) - 14, 18,
                Component.literal(names[i]), () -> painting == which, pressed ->
                {
                    painting = which;
                    colours.setColour(chosen());
                    rebuild();
                }));
        }

        addRenderableWidget(new HubWidgets.TextureButton(left + WIDTH - PAD - 80,
            top + HEIGHT - 28, 80, 20, Component.literal("Done"), pressed -> onClose()));
    }

    private void step(String slot, int by)
    {
        int now = look().part(slot);
        int next = (now - 1 + by + CharacterLook.PARTS) % CharacterLook.PARTS + 1;
        change(look().withPart(slot, next));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        float partialTick)
    {
        // The dim Forge's renderBackground drew, not extractBackground: that
        // BLURS in 26.2, and the blur is once per frame -- see PackOpeningScreen.
        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        NineSlice.draw(graphics, HubTextures.PANEL, left, top, WIDTH, HEIGHT);
        graphics.text(font, "Character", left + PAD, top + PAD, MenuInk.title(),
            MenuInk.shadow());

        // The model, drawn by the same code the world uses.
        NineSlice.draw(graphics, HubTextures.PANEL_INSET, left + PAD, top + PAD + 18,
            STAGE_W, HEIGHT - PAD * 2 - 26);
        preview.render(graphics, look(), left + PAD + 4, top + PAD + 22,
            STAGE_W - 8, HEIGHT - PAD * 2 - 34);

        int x = left + STAGE_W + PAD * 2;
        int inner = WIDTH - STAGE_W - PAD * 3;
        int y = top + ROW_SLOTS;
        for(int i = 0; i < SLOTS.length; i++)
        {
            graphics.text(font, LABELS[i], x + 4, y + 4, MenuInk.label(), MenuInk.shadow());
            String count = look().part(SLOTS[i]) + " / " + CharacterLook.PARTS;
            graphics.text(font, count, x + inner - 52 - font.width(count), y + 4,
                MenuInk.body(), MenuInk.shadow());
            y += 20;
        }
        // The presets drawn as what they are. A number tells you nothing about
        // a skin tone; the colour does.
        for(int tone = 1; tone <= CharacterLook.TONES; tone++)
        {
            int[] shipped = CharacterModels.tone(look().withTone(tone));
            if(shipped != null)
            {
                graphics.fill(x + (tone - 1) * 34 + 3, top + ROW_TONE_SWATCH,
                    x + (tone - 1) * 34 + 27, top + ROW_TONE_SWATCH + 3,
                    0xFF000000 | shipped[CharacterRamps.RAMP / 2]);
            }
        }
        graphics.text(font, "SKIN", x + 108, top + ROW_TONES + 4,
            MenuInk.dim(), MenuInk.shadow());

        // The wheel, and the ramp it produces. The ramp is the honest preview:
        // a colour is not applied flat, it is the middle of a gradient from
        // black to white, and that is what lands on the character.
        colours.render(graphics);
        // The ramp that will actually land on the character, which for skin is
        // one of the game's own re-tinted rather than a curve through the
        // colour. Showing the curve here would promise something else.
        int[] ramp = painting == Painting.SKIN
            ? CharacterModels.skinPreview(look(), colours.colour())
            : CharacterRamps.ramp(colours.colour());
        // Under the wheel rather than beside it, because it is what the wheel
        // is currently promising and reads as its own caption.
        for(int i = 0; i < ramp.length; i++)
        {
            graphics.fill(left + WHEEL_X + i * 4, top + ROW_LEGEND,
                left + WHEEL_X + i * 4 + 4, top + ROW_LEGEND + 10,
                0xFF000000 | ramp[i]);
        }
        // ONLY WHERE IT SAYS SOMETHING THE TAB DOES NOT. It used to caption every
        // target, which meant "Hair" printed under a button already labelled
        // Hair -- the same word twice, a few pixels apart. Skin is the one that
        // carries more than its own name: which of the three presets this is, or
        // that it has been mixed away from all of them.
        if(painting == Painting.SKIN)
        {
            graphics.text(font, look().mixedSkin()
                    ? "Skin, mixed" : "Skin, preset " + look().tone(),
                x, top + ROW_LEGEND, MenuInk.dim(), MenuInk.shadow());
        }

        // Widgets last: retained mode draws in the order it is told, so the
        // buttons have to be described after the panel they sit on.
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        if(colours != null && colours.mouseClicked(event.x(), event.y()))
        {
            applyColour();
            return true;
        }
        if(preview.mouseClicked(event.x(), event.y()))
        {
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)
    {
        if(colours != null && colours.mouseDragged(event.x(), event.y()))
        {
            applyColour();
            return true;
        }
        if(preview.mouseDragged(dragX))
        {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event)
    {
        if(colours != null)
        {
            colours.mouseReleased();
        }
        preview.mouseReleased();
        return super.mouseReleased(event);
    }

    /** What the wheel should be showing for whatever it is pointed at. */
    private int chosen()
    {
        return switch(painting)
        {
            case HAIR -> look().hairRgb();
            case OUTFIT -> look().wearRgb();
            // A character on a preset has no mixed skin colour of its own, so
            // the wheel opens on what that preset actually looks like -- the
            // middle of its ramp -- rather than on black.
            case SKIN -> look().mixedSkin() ? look().skinRgb() : presetSkin();
            // Same reasoning as the skin: a face that has not been retinted
            // still HAS an eye colour, and the wheel should open on it rather
            // than on black.
            case EYES -> look().tintedEyes() ? look().eyeRgb()
                : CharacterModels.eyePreview(look());
        };
    }

    /** The middle of the shipped ramp this character's tone names. */
    private int presetSkin()
    {
        int[] ramp = CharacterModels.tone(look());
        return ramp == null ? 0xE8B89C : ramp[CharacterRamps.RAMP / 2];
    }

    private void applyColour()
    {
        int rgb = colours.colour();
        change(switch(painting)
        {
            case HAIR -> look().withHairRgb(rgb);
            case OUTFIT -> look().withWearRgb(rgb);
            case SKIN -> look().withSkinRgb(rgb);
            case EYES -> look().withEyeRgb(rgb);
        });
    }

    @Override
    public void onClose()
    {
        if(minecraft != null)
        {
            minecraft.setScreenAndShow(parent);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
