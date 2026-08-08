"""Plays music through a duel, with a mute button and a track setting.

Tied to the DUEL, not to the duel screen. The screen is closed and reopened
constantly while a duel runs -- an opponent's turn reopens it -- and starting
the track from the top each time would be unlistenable. DuelClientState already
owns "a duel is happening" for exactly this reason (the playback queue lives
there so it survives the screen), so the music hangs off the same events:
it starts with the first update of a duel and stops when the duel is over or
the state is reset.

DuelMusic.start() is idempotent, which is what makes "call it on every update"
the simple correct thing rather than a thing needing a flag.
"""
import io

# ---------- 1. the duel's lifecycle starts and stops it ----------
P = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/DuelClientState.java"
s = io.open(P, encoding="utf-8").read()

old = """        if(item.over())
        {
            over = true;
            won = item.won();
            result = item.result();
            overSince = System.currentTimeMillis();
            prompt = null;
        }
    }"""
new = """        if(item.over())
        {
            over = true;
            won = item.won();
            result = item.result();
            overSince = System.currentTimeMillis();
            prompt = null;
            // The duel is decided; the music has nothing left to carry.
            DuelMusic.stop();
        }
        else
        {
            // Idempotent, so calling it for every update of the duel is the
            // whole of "start the music when a duel starts" -- there is no
            // separate began-a-duel event on the client to hang it off, and a
            // second call while it is already playing does nothing.
            DuelMusic.start();
        }
    }"""
assert old in s, "apply() over-branch anchor"
s = s.replace(old, new, 1)

old = """        log.clear();
        pending.clear();
        animations.clear();
    }"""
new = """        log.clear();
        pending.clear();
        animations.clear();
        // Whatever is left of a duel is being thrown away, including its music.
        DuelMusic.stop();
    }"""
assert old in s, "reset() anchor"
s = s.replace(old, new, 1)
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("1. DuelClientState: music follows the duel")

# ---------- 2. the mute button, on the duel screen ----------
P = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/EngineDuelScreen.java"
s = io.open(P, encoding="utf-8").read()

old = """        addRenderableWidget(Button.builder(Component.literal(chainPreference.label()), pressed ->
        {
            chainPreference = chainPreference.next();
            ClientPlayNetworking.send(new PromptMessages.SetChainPreference(chainPreference));
            rebuild();
        }).bounds(SIDEBAR_PAD, height - 44, SIDEBAR_W - SIDEBAR_PAD * 2, 18).build());"""
new = """        // Narrowed to leave the music button room on the same row: both are
        // duel-long preferences and there is no reason to spend two rows of a
        // sidebar the card preview wants on them.
        addRenderableWidget(Button.builder(Component.literal(chainPreference.label()), pressed ->
        {
            chainPreference = chainPreference.next();
            ClientPlayNetworking.send(new PromptMessages.SetChainPreference(chainPreference));
            rebuild();
        }).bounds(SIDEBAR_PAD, height - 44, SIDEBAR_W - SIDEBAR_PAD * 2 - MUSIC_BUTTON, 18)
            .build());

        // Mute. The icon says which state pressing it leaves you in the way
        // every mute button does -- crossed out means the music is off -- and
        // the choice is remembered, so a player who duels in silence does not
        // have to say so again next time.
        de.cas_ual_ty.dueldimension.clientutil.widget.TextureButton music =
            new de.cas_ual_ty.dueldimension.clientutil.widget.TextureButton(
                SIDEBAR_W - SIDEBAR_PAD - MUSIC_BUTTON + 2, height - 44,
                MUSIC_BUTTON - 2, 18, Component.empty(), pressed ->
        {
            DuelMusic.toggleMuted();
            rebuild();
        });
        // Cell 1 of the sheet is the crossed-out speaker, cell 0 the plain one.
        music.setTexture(DuelTextures.MUSIC_ICONS, DuelMusic.muted() ? 16 : 0, 0, 16, 16);
        addRenderableWidget(music);"""
assert old in s, "chain button anchor"
s = s.replace(old, new, 1)

old = "    private static final int SIDEBAR_PAD = 6;"
new = """    private static final int SIDEBAR_PAD = 6;

    /** Width the mute button takes out of the chain button's row. */
    private static final int MUSIC_BUTTON = 22;"""
assert old in s, "SIDEBAR_PAD anchor"
s = s.replace(old, new, 1)
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("2. EngineDuelScreen: mute button")

# ---------- 3. the texture ----------
P = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/DuelTextures.java"
s = io.open(P, encoding="utf-8").read()
old = """    /** Fallback art for a card whose image is missing or still downloading. */"""
new = """    /**
     * The mute button's two states, side by side: cell 0 the speaker, cell 1
     * the speaker crossed out. A 256x256 sheet because that is the size every
     * widget texture here is and the one {@code DdBlitUtil} divides by.
     */
    public static final Identifier MUSIC_ICONS =
        Identifier.fromNamespaceAndPath(DuelDimension.MOD_ID, "textures/gui/duel/music.png");

    /** Fallback art for a card whose image is missing or still downloading. */"""
assert old in s, "DuelTextures anchor"
s = s.replace(old, new, 1)
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("3. DuelTextures: MUSIC_ICONS")

# ---------- 4. the track setting, under the mat colour ----------
P = "src/main/java/de/cas_ual_ty/dueldimension/clientutil/hub/DuelHubScreen.java"
s = io.open(P, encoding="utf-8").read()

old = """            addRenderableWidget(new HubWidgets.TextureButton(left + PAD + 108, top + HEIGHT - 32,
                96, 20, Component.literal("Reset"), pressed ->
            {
                matPicker.setColour(de.cas_ual_ty.dueldimension.clientutil.DuelClientState.DEFAULT_MAT_COLOUR);
                applyMat();
            }));"""
new = """            addRenderableWidget(new HubWidgets.TextureButton(left + PAD + 108, top + HEIGHT - 32,
                96, 20, Component.literal("Reset"), pressed ->
            {
                matPicker.setColour(de.cas_ual_ty.dueldimension.clientutil.DuelClientState.DEFAULT_MAT_COLOUR);
                applyMat();
            }));

            // ---- duel music, under the mat ----
            // Both take effect immediately rather than waiting on Apply: Apply
            // is the mat's, because a colour is dragged and needs a moment to
            // settle on, while these are single choices that are their own
            // confirmation. Changing the track mid-duel swaps it there and then.
            de.cas_ual_ty.dueldimension.clientutil.DuelMusic.Track current =
                de.cas_ual_ty.dueldimension.clientutil.DuelMusic.track();
            HubWidgets.TextureButton trackButton = new HubWidgets.TextureButton(
                left + PAD + 168, bodyTop + 162, 130, 18,
                Component.literal(current.label()), pressed ->
            {
                java.util.List<de.cas_ual_ty.dueldimension.clientutil.DuelMusic.Track> all =
                    de.cas_ual_ty.dueldimension.clientutil.DuelMusic.TRACKS;
                de.cas_ual_ty.dueldimension.clientutil.DuelMusic.setTrack(
                    all.get((all.indexOf(de.cas_ual_ty.dueldimension.clientutil.DuelMusic.track())
                        + 1) % all.size()));
                rebuild();
            });
            // One track is not a choice, so the button says so rather than
            // looking pressable and doing nothing.
            trackButton.active =
                de.cas_ual_ty.dueldimension.clientutil.DuelMusic.TRACKS.size() > 1;
            trackButton.setTooltipLines(trackButton.active
                ? java.util.List.of("The music a duel is played to")
                : java.util.List.of("The music a duel is played to",
                    "Only one track is installed"));
            addRenderableWidget(trackButton);

            boolean quiet = de.cas_ual_ty.dueldimension.clientutil.DuelMusic.muted();
            HubWidgets.TextureButton muteButton = new HubWidgets.TextureButton(
                left + PAD + 302, bodyTop + 162, 68, 18,
                Component.literal(quiet ? "Muted" : "On"), pressed ->
            {
                de.cas_ual_ty.dueldimension.clientutil.DuelMusic.toggleMuted();
                rebuild();
            });
            muteButton.setLabelColour(quiet ? 0xFF8A93A3 : 0xFFF4D089);
            muteButton.setTooltipLines(java.util.List.of(
                quiet ? "Duels are played in silence" : "Music plays during a duel",
                "The same switch as the one in a duel"));
            addRenderableWidget(muteButton);"""
assert old in s, "settings Reset button anchor"
s = s.replace(old, new, 1)

old = """            String hex = String.format("#%06X", matPicker.colour());
            graphics.text(font, hex, left + PAD + 168, bodyTop + 128, 0xFFC2C9D6, true);
        }
    }"""
new = """            String hex = String.format("#%06X", matPicker.colour());
            graphics.text(font, hex, left + PAD + 168, bodyTop + 128, 0xFFC2C9D6, true);
        }
        // The heading its two buttons sit under. Drawn here rather than built
        // as a widget because it is a label, and this panel draws its own.
        graphics.text(font, "Duel Music", left + PAD + 168, bodyTop + 150, 0xFFF4D089, true);
    }"""
assert old in s, "settingsPanel hex anchor"
s = s.replace(old, new, 1)
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("4. DuelHubScreen: track + mute under the mat colour")
