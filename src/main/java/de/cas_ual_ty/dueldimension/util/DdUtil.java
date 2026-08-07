package de.cas_ual_ty.dueldimension.util;

import com.google.gson.JsonObject;
import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.properties.*;
import de.cas_ual_ty.dueldimension.duel.playfield.ZoneOwner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public class DdUtil
{
    private static final int[] POW_2 = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024};
    
    public static Properties buildProperties(JsonObject j)
    {
        Properties p0 = new Properties(j);
        
        if(p0.getIsSpell())
        {
            return new SpellProperties(p0, j);
        }
        else if(p0.getIsTrap())
        {
            return new TrapProperties(p0, j);
        }
        else if(p0.getIsMonster())
        {
            MonsterProperties p1 = new MonsterProperties(p0, j);
            
            if(p1.getHasDef())
            {
                p1 = new DefMonsterProperties(p1, j);
                
                if(p1.getHasLevel())
                {
                    return new LevelMonsterProperties(p1, j);
                }
                else if(p1.getIsXyz())
                {
                    return new XyzMonsterProperties(p1, j);
                }
            }
            else if(p1.getIsLink())
            {
                return new LinkMonsterProperties(p1, j);
            }
        }
        
        return p0;
    }
    
    public static String toSimpleString(String s)
    {
        return s.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
    }
    
    public static int getPow2(int pow)
    {
        assert pow >= 0 && pow < DdUtil.POW_2.length;
        return DdUtil.POW_2[pow];
    }
    
    public static UUID createRandomUUID()
    {
        return java.util.UUID.randomUUID();
    }
    
    public static java.util.function.Supplier<IllegalArgumentException> throwNullCapabilityException()
    {
        return () -> new IllegalArgumentException("[" + DuelDimension.MOD_ID + "] Capability can not be null!");
    }
    
    public static int toPow2ConfigValue(int i, int min)
    {
        return DdUtil.getPow2(DdUtil.range(Mth.log2(i), min, DdUtil.POW_2.length - 1));
    }
    
    public static int range(int i, int min, int max)
    {
        return Math.max(min, Math.min(max, i));
    }
    
    @Nullable
    public static InteractionHand getActiveItem(Player player, Item item)
    {
        return DdUtil.getActiveItem(player, (itemStack) -> itemStack.getItem() == item);
    }
    
    @Nullable
    public static InteractionHand getActiveItem(Player player, Predicate<ItemStack> item)
    {
        if(item.test(player.getMainHandItem()))
        {
            return InteractionHand.MAIN_HAND;
        }
        else if(item.test(player.getOffhandItem()))
        {
            return InteractionHand.OFF_HAND;
        }
        else
        {
            return null;
        }
    }
    
    public static ZoneOwner getViewOwner(ZoneOwner owner, ZoneOwner view, ZoneOwner toMap)
    {
        if(!toMap.isPlayer())
        {
            return ZoneOwner.NONE;
        }
        else if(owner.isPlayer())
        {
            return owner;
        }
        else
        {
            if(view == toMap)
            {
                return view;
            }
            else
            {
                return view.opponent();
            }
        }
    }
    
    /*
     * Parked with the cooldown phase. Both of these hang off Forge's
     * COOLDOWN_HOLDER capability, which becomes a data attachment when the
     * cooldown system ports. The bodies are kept verbatim so that port is a
     * matter of changing how the holder is reached, not rewriting the rules.
     *
     * The .get() calls below were .getPath() upstream in two of the four
     * branches of each block -- the config file path, not the commands. It
     * compiled because both are List<String>. Fixed here; Forge still has it.
     *     public static void executeAdmitDefeatCommands(Player winner, Player loser)
     *     {
     *         if(winner.level() instanceof ServerLevel)
     *         {
     *             ServerLevel world = (ServerLevel) winner.level();
     *             MinecraftServer server = world.getServer();
     *             
     *             winner.getCapability(DuelDimension.COOLDOWN_HOLDER).ifPresent(cdWinner ->
     *             {
     *                 loser.getCapability(DuelDimension.COOLDOWN_HOLDER).ifPresent(cdLoser ->
     *                 {
     *                     List<? extends String> commands;
     *                     
     *                     if(cdWinner.isOffCooldown())
     *                     {
     *                         if(cdLoser.isOffCooldown())
     *                         {
     *                             // both off CD
     *                             commands = DuelDimension.commonConfig.defeatBothOffCDCommands.get();
     *                         }
     *                         else
     *                         {
     *                             // winner off CD
     *                             commands = DuelDimension.commonConfig.defeatWinnerOffCDCommands.get();
     *                         }
     *                     }
     *                     else
     *                     {
     *                         if(cdLoser.isOffCooldown())
     *                         {
     *                             // loser off CD
     *                             commands = DuelDimension.commonConfig.defeatLoserOffCDCommands.get();
     *                         }
     *                         else
     *                         {
     *                             // both on CD
     *                             commands = DuelDimension.commonConfig.defeatBothOnCDCommands.get();
     *                         }
     *                     }
     *                     
     *                     if(cdWinner.isOffCooldown())
     *                     {
     *                         cdWinner.setCooldown(DuelDimension.commonConfig.winnerCooldown.get());
     *                     }
     *                     
     *                     if(cdLoser.isOffCooldown())
     *                     {
     *                         cdLoser.setCooldown(DuelDimension.commonConfig.loserCooldown.get());
     *                     }
     *                     
     *                     for(String command : commands)
     *                     {
     *                         command = command.replace("%winner%", winner.getScoreboardName()).replace("%loser%", loser.getScoreboardName());
     *                         
     *                         try
     *                         {
     *                             server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
     *                         }
     *                         catch(Exception e)
     *                         {
     *                             DuelDimension.log("Could not execute command triggered by duel defeat: " + command);
     *                             e.printStackTrace();
     *                         }
     *                     }
     *                 });
     *             });
     *         }
     *     }
     */
    
    /*
     * Parked with the cooldown phase. Both of these hang off Forge's
     * COOLDOWN_HOLDER capability, which becomes a data attachment when the
     * cooldown system ports. The bodies are kept verbatim so that port is a
     * matter of changing how the holder is reached, not rewriting the rules.
     *
     * The .get() calls below were .getPath() upstream in two of the four
     * branches of each block -- the config file path, not the commands. It
     * compiled because both are List<String>. Fixed here; Forge still has it.
     *     public static void executeDrawCommands(Player player1, Player player2)
     *     {
     *         if(player1.level() instanceof ServerLevel)
     *         {
     *             ServerLevel world = (ServerLevel) player1.level();
     *             MinecraftServer server = world.getServer();
     *             
     *             player1.getCapability(DuelDimension.COOLDOWN_HOLDER).ifPresent(cd1 ->
     *             {
     *                 player2.getCapability(DuelDimension.COOLDOWN_HOLDER).ifPresent(cd2 ->
     *                 {
     *                     List<? extends String> commands;
     *                     
     *                     if(cd1.isOffCooldown())
     *                     {
     *                         if(cd2.isOffCooldown())
     *                         {
     *                             // both off CD
     *                             commands = DuelDimension.commonConfig.drawBothOffCDCommands.get();
     *                         }
     *                         else
     *                         {
     *                             // p1 off CD
     *                             commands = DuelDimension.commonConfig.drawPlayer1OffCDCommands.get();
     *                         }
     *                     }
     *                     else
     *                     {
     *                         if(cd2.isOffCooldown())
     *                         {
     *                             // p2 off CD
     *                             commands = DuelDimension.commonConfig.drawPlayer2OffCDCommands.get();
     *                         }
     *                         else
     *                         {
     *                             // both on CD
     *                             commands = DuelDimension.commonConfig.drawBothOnCDCommands.get();
     *                         }
     *                     }
     *                     
     *                     if(cd1.isOffCooldown())
     *                     {
     *                         cd1.setCooldown(DuelDimension.commonConfig.drawCooldown.get());
     *                     }
     *                     
     *                     if(cd2.isOffCooldown())
     *                     {
     *                         cd2.setCooldown(DuelDimension.commonConfig.drawCooldown.get());
     *                     }
     *                     
     *                     for(String command : commands)
     *                     {
     *                         command = command.replace("%player1%", player1.getScoreboardName()).replace("%player2%", player2.getScoreboardName());
     *                         
     *                         try
     *                         {
     *                             server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
     *                         }
     *                         catch(Exception e)
     *                         {
     *                             DuelDimension.log("Could not execute command triggered by duel draw: " + command);
     *                             e.printStackTrace();
     *                         }
     *                     }
     *                 });
     *             });
     *         }
     *     }
     */
}
