package de.cas_ual_ty.dueldimension.clientutil.hub;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.duel.match.LobbyMessages;
import de.cas_ual_ty.dueldimension.duel.match.MatchConfig;
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

    private int panelY()
    {
        return Math.max(20, height / 2 - 110);
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
        y += 22;

        HubWidgets.TextureButton life = new HubWidgets.TextureButton(x, y, w, 18,
            Component.literal("Life Points:  " + room.config().lifePoints()),
            pressed -> cycle(MatchConfig.LIFE_POINT_CHOICES, room.config().lifePoints(),
                value -> propose(new MatchConfig(room.config().banlistId(), value,
                    room.config().format(), room.config().turnSeconds()))));
        life.active = host;
        addRenderableWidget(life);
        y += 22;

        HubWidgets.TextureButton format = new HubWidgets.TextureButton(x, y, w, 18,
            Component.literal("Format:  " + (room.config().format() == MatchConfig.Format.SINGLE
                ? "Single duel" : "Match, best of 3")),
            pressed -> propose(new MatchConfig(room.config().banlistId(), room.config().lifePoints(),
                room.config().format() == MatchConfig.Format.SINGLE
                    ? MatchConfig.Format.MATCH_BEST_OF_THREE : MatchConfig.Format.SINGLE,
                room.config().turnSeconds())));
        format.active = host;
        addRenderableWidget(format);
        y += 22;

        HubWidgets.TextureButton timer = new HubWidgets.TextureButton(x, y, w, 18,
            Component.literal("Turn timer:  " + (room.config().turnSeconds() == 0
                ? "none" : room.config().turnSeconds() + "s")),
            pressed -> cycle(MatchConfig.TIMER_CHOICES, room.config().turnSeconds(),
                value -> propose(new MatchConfig(room.config().banlistId(),
                    room.config().lifePoints(), room.config().format(), value))));
        timer.active = host;
        addRenderableWidget(timer);
        y += 34;

        boolean mine = room.host() ? room.hostReady() : room.guestReady();
        boolean canReady = room.problems().isEmpty();
        HubWidgets.TextureButton ready = new HubWidgets.TextureButton(x, y, w - 84, 20,
            Component.literal(mine ? "Not ready" : "Ready"), pressed ->
                DuelDimension.channel.sendToServer(new LobbyMessages.Ready(!mine)));
        // A deck that will not do under the chosen list cannot be declared
        // ready with: this is the moment that is worth finding out.
        ready.active = canReady || mine;
        addRenderableWidget(ready);

        addRenderableWidget(new HubWidgets.TextureButton(x + w - 80, y, 80, 20,
            Component.literal("Leave"), pressed ->
        {
            DuelDimension.channel.sendToServer(new LobbyMessages.Leave());
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
        propose(new MatchConfig(next, room.config().lifePoints(), room.config().format(),
            room.config().turnSeconds()));
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
        DuelDimension.channel.sendToServer(new LobbyMessages.Configure(config));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(poseStack);
        int x = panelX();
        int y = panelY();
        NineSlice.draw(poseStack, HubTextures.PANEL, x, y, panelW(), 200);

        String title = "Duel Lobby";
        font.drawShadow(poseStack, title, x + (panelW() - font.width(title)) / 2F, y + 10, 0xFFF4D089);

        // Who is in the room and who has committed. The host is named first
        // because the host is the one changing things.
        String hostLine = (room.hostReady() ? "[ready] " : "[  ...  ] ") + room.hostName() + "  (host)";
        String guestLine = (room.guestReady() ? "[ready] " : "[  ...  ] ") + room.guestName();
        font.drawShadow(poseStack, hostLine, x + 12, y + 24,
            room.hostReady() ? 0xFF7CE38B : 0xFFC2C9D6);
        font.drawShadow(poseStack, guestLine, x + 12 + panelW() / 2 - 12, y + 24,
            room.guestReady() ? 0xFF7CE38B : 0xFFC2C9D6);

        super.render(poseStack, mouseX, mouseY, partialTick);

        // Why this player cannot be ready, if they cannot. Said here rather
        // than at the duel, which is too late to do anything about it.
        int noticeY = y + 176;
        if(room.problems().isEmpty())
        {
            font.drawShadow(poseStack, "Your deck is legal for these settings",
                x + 12, noticeY, 0xFF7CE38B);
        }
        else
        {
            font.drawShadow(poseStack, room.problems().get(0), x + 12, noticeY, 0xFFFF8A80);
            if(room.problems().size() > 1)
            {
                font.drawShadow(poseStack, "and " + (room.problems().size() - 1) + " more",
                    x + 12, noticeY + 10, 0xFFFF8A80);
            }
        }
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
