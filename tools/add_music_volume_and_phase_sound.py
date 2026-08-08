"""A music volume slider in the duel, and EDOPro's phase-change sound.

The click on a phase was vanilla's UI_BUTTON_CLICK: SlimPhaseButton extends
Button and never overrode playDownSound, so pressing a phase sounded like
pressing a Minecraft button. It now plays the supplied sound instead.

The volume is a third stored preference beside the track and the mute. It is
read fresh every tick rather than baked into the sound instance, so dragging
the slider is heard while dragging.
"""
import io, json, collections

# ---------- 1. the sound ----------
p = "src/main/resources/assets/dueldimension/sounds.json"
d = json.load(io.open(p, encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
d["duel.phasechange"] = collections.OrderedDict([
    ("category", "player"),
    ("sounds", [collections.OrderedDict([
        ("name", "dueldimension:duel/phasechange"),
        ("stream", False),
    ])]),
])
io.open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
print("1. sounds.json: duel.phasechange")

p = "src/main/java/de/cas_ual_ty/dueldimension/DdSounds.java"
s = io.open(p, encoding="utf-8").read()
old = '    public static final SoundEvent EQUIP = register("duel.equip");'
new = '''    public static final SoundEvent EQUIP = register("duel.equip");

    /**
     * Pressing a phase on the phase bar.
     * <p>
     * Distinct from {@link #PHASE}, which is the engine announcing that the
     * phase HAS changed (MSG_NEW_PHASE). This one is the button answering the
     * press, in place of Minecraft's own click.
     */
    public static final SoundEvent PHASE_CHANGE = register("duel.phasechange");'''
assert old in s, "DdSounds anchor"
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("2. DdSounds: PHASE_CHANGE")

# ---------- 2. the phase button uses it ----------
p = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/EngineDuelScreen.java"
s = io.open(p, encoding="utf-8").read()
old = """        @Override
        protected void extractContents(GuiGraphicsExtractor poseStack, int mouseX, int mouseY, float partialTick)
        {
            BoardSnapshot board = currentBoard();
            // Pointing at a reachable phase previews it as lit.
            int state = isHoveredOrFocused() ? PHASE_LIT : phaseState(board, index);
            drawPhaseCell(poseStack, getX(), getY(), width, height, index, state,
                board.turnPlayer() == 0);
        }
    }"""
new = """        @Override
        protected void extractContents(GuiGraphicsExtractor poseStack, int mouseX, int mouseY, float partialTick)
        {
            BoardSnapshot board = currentBoard();
            // Pointing at a reachable phase previews it as lit.
            int state = isHoveredOrFocused() ? PHASE_LIT : phaseState(board, index);
            drawPhaseCell(poseStack, getX(), getY(), width, height, index, state,
                board.turnPlayer() == 0);
        }

        /**
         * The duel's own phase-change sound, not Minecraft's button click.
         * <p>
         * Button plays UI_BUTTON_CLICK from here and this never overrode it, so
         * changing phase in a Yu-Gi-Oh duel sounded like pressing a button in
         * the options menu.
         */
        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
            sounds.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                DdSounds.PHASE_CHANGE, 1F, 1F));
        }
    }"""
assert old in s, "SlimPhaseButton anchor"
s = s.replace(old, new, 1)

# ---------- 3. the volume slider, on its own row above the chain button ----------
old = """        // Mute. The icon says which state pressing it leaves you in the way"""
new = """        // Music volume, on its own row: a slider needs the width, and it is
        // the one control here a player adjusts by feel rather than by
        // pressing once.
        addRenderableWidget(new MusicVolumeSlider(SIDEBAR_PAD, height - 66,
            SIDEBAR_W - SIDEBAR_PAD * 2, 16));

        // Mute. The icon says which state pressing it leaves you in the way"""
assert old in s, "music slider anchor"
s = s.replace(old, new, 1)

old = """    /** A flat, compact button so the phase row reads as one strip. */"""
new = """    /**
     * The duel's music volume.
     * <p>
     * Its own control rather than a link to the game's sliders: the duel is
     * where a player notices the music is too loud against the effect sounds,
     * and sending them to the options menu mid-duel to fix it is the wrong
     * answer. It scales the mod's track only; Minecraft's own Jukebox slider
     * still applies on top, as it does to everything.
     */
    private static class MusicVolumeSlider extends net.minecraft.client.gui.components.AbstractSliderButton
    {
        MusicVolumeSlider(int x, int y, int w, int h)
        {
            super(x, y, w, h, Component.empty(), DuelMusic.volume());
            updateMessage();
        }

        @Override
        protected void updateMessage()
        {
            setMessage(Component.literal("Music  " + Math.round(value * 100F) + "%"));
        }

        @Override
        protected void applyValue()
        {
            DuelMusic.setVolume((float)value);
        }

        /**
         * Silent. A slider that clicks on every step of a drag is noise, and
         * the thing being dragged is already the feedback.
         */
        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sounds)
        {
        }
    }

    /** A flat, compact button so the phase row reads as one strip. */"""
assert old in s, "SlimPhaseButton class anchor"
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("3. EngineDuelScreen: phase sound + volume slider")
