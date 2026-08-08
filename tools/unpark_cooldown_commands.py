"""Un-parks DdUtil's two reward-command methods onto the cooldown attachment.

The bodies were kept verbatim in a comment block precisely so this would be a
matter of changing how the holder is reached, not rewriting the rules. Two
changes, both mechanical:

    player.getCapability(DuelDimension.COOLDOWN_HOLDER).ifPresent(cd -> { .. })
        ->  CooldownHolder cd = Cooldowns.get(player); ..

    DuelDimension.commonConfig.x.get()  ->  DuelDimension.commonConfig().x.get()

The nesting goes with the ifPresent. Two nested Optionals existed only because
a capability might be absent; an attachment never is, so the rules run for a
pair of players rather than for a pair of maybe-players. That is the one
behavioural difference and it is a fix: on Forge, a failure to attach on either
player skipped the rewards silently.
"""
import io
import re

PATH = "src/main/java/de/cas_ual_ty/dueldimension/util/DdUtil.java"

DEFEAT = '''    /**
     * The commands a server runs when a duel is decided.
     * <p>
     * Which set depends on whether each player is off cooldown, so a server can
     * pay out for a real duel and not for the tenth rematch in five minutes.
     * The winner and loser are substituted into the command text by name.
     */
    public static void executeAdmitDefeatCommands(Player winner, Player loser)
    {
        if(winner.level() instanceof ServerLevel world)
        {
            MinecraftServer server = world.getServer();

            CooldownHolder cdWinner = Cooldowns.get(winner);
            CooldownHolder cdLoser = Cooldowns.get(loser);

            List<? extends String> commands;

            if(cdWinner.isOffCooldown())
            {
                if(cdLoser.isOffCooldown())
                {
                    // both off CD
                    commands = DuelDimension.commonConfig().defeatBothOffCDCommands.get();
                }
                else
                {
                    // winner off CD
                    commands = DuelDimension.commonConfig().defeatWinnerOffCDCommands.get();
                }
            }
            else
            {
                if(cdLoser.isOffCooldown())
                {
                    // loser off CD
                    commands = DuelDimension.commonConfig().defeatLoserOffCDCommands.get();
                }
                else
                {
                    // both on CD
                    commands = DuelDimension.commonConfig().defeatBothOnCDCommands.get();
                }
            }

            if(cdWinner.isOffCooldown())
            {
                cdWinner.setCooldown(DuelDimension.commonConfig().winnerCooldown.get());
            }

            if(cdLoser.isOffCooldown())
            {
                cdLoser.setCooldown(DuelDimension.commonConfig().loserCooldown.get());
            }

            runCommands(server, commands, winner, loser);
        }
    }

    /** The same, for a draw: neither player is the winner. */
    public static void executeDrawCommands(Player player1, Player player2)
    {
        if(player1.level() instanceof ServerLevel world)
        {
            MinecraftServer server = world.getServer();

            CooldownHolder cd1 = Cooldowns.get(player1);
            CooldownHolder cd2 = Cooldowns.get(player2);

            List<? extends String> commands;

            if(cd1.isOffCooldown())
            {
                if(cd2.isOffCooldown())
                {
                    // both off CD
                    commands = DuelDimension.commonConfig().drawBothOffCDCommands.get();
                }
                else
                {
                    // p1 off CD
                    commands = DuelDimension.commonConfig().drawPlayer1OffCDCommands.get();
                }
            }
            else
            {
                if(cd2.isOffCooldown())
                {
                    // p2 off CD
                    commands = DuelDimension.commonConfig().drawPlayer2OffCDCommands.get();
                }
                else
                {
                    // both on CD
                    commands = DuelDimension.commonConfig().drawBothOnCDCommands.get();
                }
            }

            if(cd1.isOffCooldown())
            {
                cd1.setCooldown(DuelDimension.commonConfig().drawCooldown.get());
            }

            if(cd2.isOffCooldown())
            {
                cd2.setCooldown(DuelDimension.commonConfig().drawCooldown.get());
            }

            runCommands(server, commands, player1, player2);
        }
    }

    /**
     * Runs each command as the server, with the two players' names substituted.
     * <p>
     * Shared by the two above, which ran identical loops. A command that throws
     * is logged and the rest still run: one bad line in a config should not cost
     * a player the rest of their reward.
     */
    private static void runCommands(MinecraftServer server, List<? extends String> commands,
        Player winner, Player loser)
    {
        for(String command : commands)
        {
            command = command.replace("%winner%", winner.getScoreboardName())
                .replace("%loser%", loser.getScoreboardName());

            try
            {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    command);
            }
            catch(Exception e)
            {
                DuelDimension.log("Could not execute command triggered by duel defeat: " + command);
                e.printStackTrace();
            }
        }
    }
'''

src = io.open(PATH, encoding="utf-8").read()

# Both parked blocks are comments running from "/*\n     * Parked with the
# cooldown phase" to the closing "*/". Replace the pair with the real code.
pattern = re.compile(r"    /\*\s*\n\s*\* Parked with the cooldown phase\..*?\*/\s*\n", re.S)
blocks = pattern.findall(src)
if len(blocks) != 2:
    raise SystemExit("expected 2 parked blocks, found %d" % len(blocks))

src = pattern.sub("", src, count=1)          # drop the first
src = pattern.sub(DEFEAT, src, count=1)      # the second becomes both methods

io.open(PATH, "w", encoding="utf-8", newline="\n").write(src)
print("DdUtil: cooldown commands un-parked")
