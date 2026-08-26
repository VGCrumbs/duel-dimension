package de.cas_ual_ty.dueldimension.clientutil.hub;

import de.cas_ual_ty.dueldimension.duel.match.LobbyMessages;
import de.cas_ual_ty.dueldimension.duel.match.MatchConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.cas_ual_ty.dueldimension.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The room two players agree a duel in.
 * <p>
 * The challenger owns the settings and the guest watches them change; both must
 * say ready, and any change by the host takes both readies back. Everything
 * shown here is the server's copy — the screen proposes and redraws whatever
 * comes back, so a guest cannot set anything by pretending to be the host.
 */
public class DuelLobbyScreen extends Screen
{
    private LobbyMessages.OpenLobby room;

    public DuelLobbyScreen(LobbyMessages.OpenLobby room)
    {
        super(Component.literal("Duel Lobby"));
        this.room = room;
    }

    /** Takes a fresh copy of the room and redraws it. */
    public void update(LobbyMessages.OpenLobby fresh)
    {
        this.room = fresh;
        rebuildWidgets();
    }

    private int panelW()
    {
        return Math.min(300, width - 40);
    }

    private int panelX()
    {
        return (width - panelW()) / 2;
    }

    /**
     * Tall enough for the rows it actually has: five settings, the ready row
     * and the notice. Written as a sum rather than a round number because a row
     * was added once and the panel did not grow with it.
     */
    private int panelH()
    {
        return 40 + SETTING_ROWS * ROW_H + 34 + 20 + 16;
    }

    private static final int SETTING_ROWS = 5;
    private static final int ROW_H = 22;

    private int panelY()
    {
        return Math.max(20, (height - panelH()) / 2);
    }

    @Override
    protected void init()
    {
        int x = panelX() + 12;
        int w = panelW() - 24;
        int y = panelY() + 40;
        boolean host = room.host();

        // Only the host gets working controls. The guest sees the same rows
        // greyed, so both read the same screen and it is obvious who decides.
        HubWidgets.TextureButton banlist = new HubWidgets.TextureButton(x, y, w, 18,
            Component.literal("Banlist:  " + banlistName()), pressed -> cycleBanlist());
        banlist.active = host;
        addRenderableWidget(banlist);
        y += ROW_H;

        HubWidgets.TextureButton life = new HubWidgets.TextureButton(x, y, w, 18,
            Component.literal("Life Points:  " + room.config().lifePoints()),
            pressed -> cycle(MatchConfig.LIFE_POINT_CHOICES, room.config().lifePoints(),
                value -> propose(room.config().withLifePoints(value))));
        life.active = host;
        addRenderableWidget(life);
        y += ROW_H;

        HubWidgets.TextureButton format = new HubWidgets.TextureButton(x, y, w, 18,
            Component.literal("Format:  " + (room.config().format() == MatchConfig.Format.SINGLE
                ? "Single duel" : "Match, best of 3")),
            pressed -> propose(room.config().withFormat(
                room.config().format() == MatchConfig.Format.SINGLE
                    ? MatchConfig.Format.MATCH_BEST_OF_THREE : MatchConfig.Format.SINGLE)));
        format.active = host;
        addRenderableWidget(format);
        y += ROW_H;

        HubWidgets.TextureButton timer = new HubWidgets.TextureButton(x, y, w, 18,
            Component.literal("Turn timer:  " + (room.config().turnSeconds() == 0
                ? "none" : room.config().turnSeconds() + "s")),
            pressed -> cycle(MatchConfig.TIMER_CHOICES, room.config().turnSeconds(),
                value -> propose(room.config().withTurnSeconds(value))));
        timer.active = host;
        addRenderableWidget(timer);
        y += ROW_H;

        // Where the duel is played. Not a rule and not negotiable mid-duel: a
        // board that cannot be sited falls back to the screen on its own, so
        // this row promises a preference rather than a guarantee.
        HubWidgets.TextureButton where = new HubWidgets.TextureButton(x, y, w, 18,
            Component.literal("Played on:  " + room.config().presentation().label()),
            pressed -> propose(room.config().withPresentation(
                room.config().isOverworld() ? MatchConfig.Presentation.SCREEN
                    : MatchConfig.Presentation.OVERWORLD)));
        where.active = host;
        addRenderableWidget(where);
        y += 34;

        boolean mine = room.host() ? room.hostReady() : room.guestReady();
        boolean canReady = room.problems().isEmpty();
        HubWidgets.TextureButton ready = new HubWidgets.TextureButton(x, y, w - 84, 20,
            Component.literal(mine ? "Not ready" : "Ready"), pressed ->
                ClientPlayNetworking.send(new LobbyMessages.Ready(!mine)));
        // A deck that will not do under the chosen list cannot be declared
        // ready with: this is the moment that is worth finding out.
        ready.active = canReady || mine;
        addRenderableWidget(ready);

        addRenderableWidget(new HubWidgets.TextureButton(x + w - 80, y, 80, 20,
            Component.literal("Leave"), pressed ->
        {
            ClientPlayNetworking.send(new LobbyMessages.Leave());
            onClose();
        }));
    }

    private String banlistName()
    {
        int index = room.banlistIds().indexOf(room.config().banlistId());
        return index >= 0 && index < room.banlistNames().size()
            ? room.banlistNames().get(index) : room.config().banlistId();
    }

    private void cycleBanlist()
    {
        if(room.banlistIds().isEmpty())
        {
            return;
        }
        int index = room.banlistIds().indexOf(room.config().banlistId());
        String next = room.banlistIds().get((index + 1) % room.banlistIds().size());
        propose(room.config().withBanlist(next));
    }

    /** Steps to the next offered value, wrapping. */
    private void cycle(int[] choices, int current, java.util.function.IntConsumer apply)
    {
        for(int i = 0; i < choices.length; i++)
        {
            if(choices[i] == current)
            {
                apply.accept(choices[(i + 1) % choices.length]);
                return;
            }
        }
        apply.accept(choices[0]);
    }

    private void propose(MatchConfig config)
    {
        ClientPlayNetworking.send(new LobbyMessages.Configure(config));
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics vanillaGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 26.2 draws screens by EXTRACTING a render state; 1.21.1 draws
        // immediately from render(). The body below is unchanged -- it is
        // handed the compatibility surface over the real GuiGraphics.
        GuiGraphicsExtractor extractor = new GuiGraphicsExtractor(vanillaGraphics);

        // The dim Forge's renderBackground drew, not extractBackground: that
        // BLURS in 26.2, the blur is once-per-frame, and the frame a screen
        // opens over another that already asked for it took the client down.
        // Same decision as EngineDuelScreen, for the same crash.
        extractor.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        int x = panelX();
        int y = panelY();
        NineSlice.draw(extractor, HubTextures.PANEL, x, y, panelW(), panelH());

        String title = "Duel Lobby";
        extractor.text(font, title, x + (panelW() - font.width(title)) / 2, y + 10, 0xFFF4D089, true);

        // Who is in the room and who has committed. The host is named first
        // because the host is the one changing things.
        String hostLine = (room.hostReady() ? "[ready] " : "[  ...  ] ") + room.hostName() + "  (host)";
        String guestLine = (room.guestReady() ? "[ready] " : "[  ...  ] ") + room.guestName();
        extractor.text(font, hostLine, x + 12, y + 24,
            room.hostReady() ? 0xFF7CE38B : 0xFFC2C9D6, true);
        extractor.text(font, guestLine, x + 12 + panelW() / 2 - 12, y + 24,
            room.guestReady() ? 0xFF7CE38B : 0xFFC2C9D6, true);

        super.render(extractor.vanilla(), mouseX, mouseY, partialTick);

        // Why this player cannot be ready, if they cannot. Said here rather
        // than at the duel, which is too late to do anything about it.
        int noticeY = y + panelH() - 24;
        if(room.problems().isEmpty())
        {
            extractor.text(font, "Your deck is legal for these settings",
                x + 12, noticeY, 0xFF7CE38B, true);
        }
        else
        {
            extractor.text(font, room.problems().get(0), x + 12, noticeY, 0xFFFF8A80, true);
            if(room.problems().size() > 1)
            {
                extractor.text(font, "and " + (room.problems().size() - 1) + " more",
                    x + 12, noticeY + 10, 0xFFFF8A80, true);
            }
        }
    }

    /**
     * No background from vanilla, because this screen draws before
     * {@code super.render} and vanilla draws the background from inside it.
     * <p>
     * In a level that background is the BLUR and nothing else -- the panorama
     * and {@code renderMenuBackground} are both gated on there being no level --
     * so leaving it in place blurs everything this screen has already put down,
     * which is the whole interface. 26.2 refuses it too, in the same words:
     * <blockquote>fillGradient, not extractBackground: that one blurs.</blockquote>
     * The dim, where this screen wants one, is its own and goes down first.
     */
    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics vanillaGraphics,
        int mouseX, int mouseY, float partialTick)
    {
    }


    @Override
    public void onClose()
    {
        if(minecraft != null)
        {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
