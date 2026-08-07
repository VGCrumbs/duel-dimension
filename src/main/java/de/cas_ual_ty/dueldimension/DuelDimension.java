package de.cas_ual_ty.dueldimension;

import de.cas_ual_ty.dueldimension.cardbinder.CardBinderMessages;
import de.cas_ual_ty.dueldimension.cardbinder.UUIDHolder;
import de.cas_ual_ty.dueldimension.cardinventory.JsonCardsManager;
import de.cas_ual_ty.dueldimension.carditeminventory.CIIMessages;
import de.cas_ual_ty.dueldimension.cardsupply.CardSupplyMessages;
import de.cas_ual_ty.dueldimension.deckbox.DeckBoxItem;
import de.cas_ual_ty.dueldimension.deckbox.DeckHolder;
import de.cas_ual_ty.dueldimension.deckbox.ItemHandlerDeckHolder;
import de.cas_ual_ty.dueldimension.duel.FindDecksEvent;
import de.cas_ual_ty.dueldimension.duel.action.ActionIcon;
import de.cas_ual_ty.dueldimension.duel.action.ActionIcons;
import de.cas_ual_ty.dueldimension.duel.action.ActionType;
import de.cas_ual_ty.dueldimension.duel.action.ActionTypes;
import de.cas_ual_ty.dueldimension.duel.network.DuelMessage;
import de.cas_ual_ty.dueldimension.duel.network.DuelMessageHeaderType;
import de.cas_ual_ty.dueldimension.duel.network.DuelMessageHeaders;
import de.cas_ual_ty.dueldimension.duel.network.DuelMessages;
import de.cas_ual_ty.dueldimension.duel.playfield.ZoneType;
import de.cas_ual_ty.dueldimension.duel.playfield.ZoneTypes;
import de.cas_ual_ty.dueldimension.serverutil.DdCommand;
import de.cas_ual_ty.dueldimension.simplebinder.SimpleBinderItem;
import de.cas_ual_ty.dueldimension.task.WorkerManager;
import de.cas_ual_ty.dueldimension.util.CooldownHolder;
import de.cas_ual_ty.dueldimension.util.ISidedProxy;
import de.cas_ual_ty.dueldimension.util.YDMItemHandler;
import de.cas_ual_ty.dueldimension.util.DdIOUtil;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.registries.RegistryBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Random;
import java.util.function.Supplier;

@Mod(DuelDimension.MOD_ID)
public class DuelDimension
{
    public static final String MOD_ID = "dueldimension";
    public static final String MOD_ID_UP = DuelDimension.MOD_ID.toUpperCase();
    public static final String PROTOCOL_VERSION = "1";
    
    private static final Logger LOGGER = LogManager.getLogger();
    
    public static DuelDimension instance;
    public static ISidedProxy proxy;
    public static Random random;
    public static CreativeModeTab ydmItemGroup;
    public static CreativeModeTab cardsItemGroup;
    public static CreativeModeTab setsItemGroup;
    
    public static ForgeConfigSpec commonConfigSpec;
    public static CommonConfig commonConfig;
    
    public static String dbSourceUrl;
    
    public static File mainFolder;
    public static File cardsFolder;
    public static File setsFolder;
    public static File distributionsFolder;
    public static File raritiesFolder;
    public static File bindersFolder;
    
    public static SimpleChannel channel;
    
    public static Capability<UUIDHolder> UUID_HOLDER = CapabilityManager.get(new CapabilityToken<>() {});
    public static Capability<YDMItemHandler> CARD_ITEM_INVENTORY = CapabilityManager.get(new CapabilityToken<>() {});
    public static Capability<CooldownHolder> COOLDOWN_HOLDER = CapabilityManager.get(new CapabilityToken<>() {});
    
    public static Supplier<IForgeRegistry<ActionIcon>> actionIconRegistry;
    public static Supplier<IForgeRegistry<ZoneType>> zoneTypeRegistry;
    public static Supplier<IForgeRegistry<ActionType>> actionTypeRegistry;
    public static Supplier<IForgeRegistry<DuelMessageHeaderType>> duelMessageHeaderRegistry;
    public static volatile boolean continueTasks = true;
    public static volatile boolean forceTaskStop = false;
    
    public DuelDimension()
    {
        DuelDimension.instance = this;
        DuelDimension.proxy = DistExecutor.unsafeRunForDist(
                () -> de.cas_ual_ty.dueldimension.clientutil.ClientProxy::new,
                () -> de.cas_ual_ty.dueldimension.serverutil.ServerProxy::new);
        DuelDimension.random = new Random();
        DuelDimension.ydmItemGroup = new DdItemGroup(DuelDimension.MOD_ID, DdItems.CARD_BACK);
        DuelDimension.cardsItemGroup = new DdItemGroup(DuelDimension.MOD_ID + ".cards", DdItems.BLANC_CARD)
        {
            @Override
            public boolean hasSearchBar()
            {
                return true;
            }
        }.setBackgroundSuffix("item_search.png");
        DuelDimension.setsItemGroup = new DdItemGroup(DuelDimension.MOD_ID + ".sets", DdItems.BLANC_SET)
        {
            @Override
            public boolean hasSearchBar()
            {
                return true;
            }
        }.setBackgroundSuffix("item_search.png");
        
        Pair<CommonConfig, ForgeConfigSpec> common = new ForgeConfigSpec.Builder().configure(CommonConfig::new);
        DuelDimension.commonConfig = common.getLeft();
        DuelDimension.commonConfigSpec = common.getRight();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, DuelDimension.commonConfigSpec);
        
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::init);
        bus.addListener(this::modConfig);
        bus.addListener(this::newRegistry);
        bus.addListener(this::entityAttributes);
        DuelDimension.proxy.registerModEventListeners(bus);
        
        DdBlocks.register(bus);
        DdItems.register(bus);
        DdContainerTypes.register(bus);
        DdEntityTypes.register(bus);
        DdSounds.register(bus);
        DdTileEntityTypes.register(bus);
        ActionIcons.register(bus);
        ZoneTypes.register(bus);
        ActionTypes.register(bus);
        DuelMessageHeaders.register(bus);
        
        bus = MinecraftForge.EVENT_BUS;
        // see: https://github.com/MinecraftForge/MinecraftForge/pull/6954
        // need to write directly to nbt for now
        bus.addGenericListener(ItemStack.class, this::attachItemStackCapabilities);
        bus.addGenericListener(Entity.class, this::attachPlayerCapabilities);
        bus.addListener(this::playerClone);
        bus.addListener(this::playerTick);
        bus.addListener(this::playerLoggedIn);
        bus.addListener(this::playerLoggedOut);
        bus.addListener(this::playerRespawned);
        bus.addListener(this::playerChangedDimension);
        bus.addListener(this::registerCommands);
        bus.addListener(this::findDecks);
        bus.addListener(this::serverStopped);
        bus.addListener(this::serverTick);
        bus.addListener(this::serverStarted);
        DuelDimension.proxy.registerForgeEventListeners(bus);
        
        DuelDimension.proxy.preInit();
        initFolders();
    }
    
    private void init(FMLCommonSetupEvent event)
    {
        DuelDimension.channel = NetworkRegistry.newSimpleChannel(new ResourceLocation(DuelDimension.MOD_ID, "main"),
                () -> DuelDimension.PROTOCOL_VERSION,
                DuelDimension.PROTOCOL_VERSION::equals,
                DuelDimension.PROTOCOL_VERSION::equals);
        
        initFiles();
        
        int index = 0;
        DuelDimension.channel.registerMessage(index++, CardBinderMessages.ChangePage.class, CardBinderMessages.ChangePage::encode, CardBinderMessages.ChangePage::decode, CardBinderMessages.ChangePage::handle);
        DuelDimension.channel.registerMessage(index++, CardBinderMessages.ChangeSearch.class, CardBinderMessages.ChangeSearch::encode, CardBinderMessages.ChangeSearch::decode, CardBinderMessages.ChangeSearch::handle);
        DuelDimension.channel.registerMessage(index++, de.cas_ual_ty.dueldimension.set.PackMessages.OpenPack.class, de.cas_ual_ty.dueldimension.set.PackMessages.OpenPack::encode, de.cas_ual_ty.dueldimension.set.PackMessages.OpenPack::decode, de.cas_ual_ty.dueldimension.set.PackMessages.OpenPack::handle);
        DuelDimension.channel.registerMessage(index++, de.cas_ual_ty.dueldimension.shop.ShopMessages.OpenShop.class, de.cas_ual_ty.dueldimension.shop.ShopMessages.OpenShop::encode, de.cas_ual_ty.dueldimension.shop.ShopMessages.OpenShop::decode, de.cas_ual_ty.dueldimension.shop.ShopMessages.OpenShop::handle);
        DuelDimension.channel.registerMessage(index++, de.cas_ual_ty.dueldimension.shop.ShopMessages.SyncPoints.class, de.cas_ual_ty.dueldimension.shop.ShopMessages.SyncPoints::encode, de.cas_ual_ty.dueldimension.shop.ShopMessages.SyncPoints::decode, de.cas_ual_ty.dueldimension.shop.ShopMessages.SyncPoints::handle);
        DuelDimension.channel.registerMessage(index++, de.cas_ual_ty.dueldimension.shop.ShopMessages.Buy.class, de.cas_ual_ty.dueldimension.shop.ShopMessages.Buy::encode, de.cas_ual_ty.dueldimension.shop.ShopMessages.Buy::decode, de.cas_ual_ty.dueldimension.shop.ShopMessages.Buy::handle);
        DuelDimension.channel.registerMessage(index++, CardBinderMessages.UpdatePage.class, CardBinderMessages.UpdatePage::encode, CardBinderMessages.UpdatePage::decode, CardBinderMessages.UpdatePage::handle);
        DuelDimension.channel.registerMessage(index++, CardBinderMessages.UpdateList.class, CardBinderMessages.UpdateList::encode, CardBinderMessages.UpdateList::decode, CardBinderMessages.UpdateList::handle);
        DuelDimension.channel.registerMessage(index++, CardBinderMessages.IndexClicked.class, CardBinderMessages.IndexClicked::encode, CardBinderMessages.IndexClicked::decode, CardBinderMessages.IndexClicked::handle);
        DuelDimension.channel.registerMessage(index++, CardBinderMessages.IndexDropped.class, CardBinderMessages.IndexDropped::encode, CardBinderMessages.IndexDropped::decode, CardBinderMessages.IndexDropped::handle);
        DuelDimension.channel.registerMessage(index++, CardSupplyMessages.RequestCard.class, CardSupplyMessages.RequestCard::encode, CardSupplyMessages.RequestCard::decode, CardSupplyMessages.RequestCard::handle);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.SelectRole.class, DuelMessages.SelectRole::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.UpdateRole.class, DuelMessages.UpdateRole::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.UpdateDuelState.class, DuelMessages.UpdateDuelState::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.RequestFullUpdate.class, DuelMessages.RequestFullUpdate::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.RequestReady.class, DuelMessages.RequestReady::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.UpdateReady.class, DuelMessages.UpdateReady::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.SendAvailableDecks.class, DuelMessages.SendAvailableDecks::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.RequestDeck.class, DuelMessages.RequestDeck::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.SendDeck.class, DuelMessages.SendDeck::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.ChooseDeck.class, DuelMessages.ChooseDeck::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.DeckAccepted.class, DuelMessages.DeckAccepted::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.DuelAction.class, DuelMessages.DuelAction::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.RequestDuelAction.class, DuelMessages.RequestDuelAction::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.AllDuelActions.class, DuelMessages.AllDuelActions::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.SendMessageToServer.class, DuelMessages.SendMessageToServer::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.SendMessageToClient.class, DuelMessages.SendMessageToClient::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.SendAllMessagesToClient.class, DuelMessages.SendAllMessagesToClient::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.SendAdmitDefeat.class, DuelMessages.SendAdmitDefeat::new);
        DuelMessage.register(DuelDimension.channel, index++, DuelMessages.SendOfferDraw.class, DuelMessages.SendOfferDraw::new);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.ShowPrompt.class,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.ShowPrompt::encode,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.ShowPrompt::decode,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.ShowPrompt::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.AnswerPrompt.class,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.AnswerPrompt::encode,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.AnswerPrompt::decode,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.AnswerPrompt::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelUpdate.class,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelUpdate::encode,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelUpdate::decode,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.DuelUpdate::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetPlayMat.class,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetPlayMat::encode,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetPlayMat::decode,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetPlayMat::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentPlayMat.class,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentPlayMat::encode,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentPlayMat::decode,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.OpponentPlayMat::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.Surrender.class,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.Surrender::encode,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.Surrender::decode,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.Surrender::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetChainPreference.class,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetChainPreference::encode,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetChainPreference::decode,
                de.cas_ual_ty.dueldimension.ocg.prompt.PromptMessages.SetChainPreference::handle);
        DuelDimension.channel.registerMessage(index++, CIIMessages.SetPage.class, CIIMessages.SetPage::encode, CIIMessages.SetPage::decode, CIIMessages.SetPage::handle);
        DuelDimension.channel.registerMessage(index++, CIIMessages.ChangePage.class, CIIMessages.ChangePage::encode, CIIMessages.ChangePage::decode, CIIMessages.ChangePage::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.Sync.class,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.Sync::encode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.Sync::decode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.Sync::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.SaveDeck.class,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.SaveDeck::encode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.SaveDeck::decode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.SaveDeck::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.CreateDeck.class,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.CreateDeck::encode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.CreateDeck::decode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.CreateDeck::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.RenameDeck.class,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.RenameDeck::encode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.RenameDeck::decode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.RenameDeck::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.DeleteDeck.class,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.DeleteDeck::encode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.DeleteDeck::decode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.DeleteDeck::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.CopyRecipe.class,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.CopyRecipe::encode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.CopyRecipe::decode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.CopyRecipe::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.SetActiveDeck.class,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.SetActiveDeck::encode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.SetActiveDeck::decode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.SetActiveDeck::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.OpenLobby.class,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.OpenLobby::encode,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.OpenLobby::decode,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.OpenLobby::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.CloseLobby.class,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.CloseLobby::encode,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.CloseLobby::decode,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.CloseLobby::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Configure.class,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Configure::encode,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Configure::decode,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Configure::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Ready.class,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Ready::encode,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Ready::decode,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Ready::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Leave.class,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Leave::encode,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Leave::decode,
                de.cas_ual_ty.dueldimension.duel.match.LobbyMessages.Leave::handle);
        DuelDimension.channel.registerMessage(index++,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.ToggleFavourite.class,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.ToggleFavourite::encode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.ToggleFavourite::decode,
                de.cas_ual_ty.dueldimension.duel.profile.ProfileMessages.ToggleFavourite::handle);

        DuelDimension.proxy.init();
        WorkerManager.init();
    }
    
    public void initFolders()
    {
        DuelDimension.mainFolder = new File("ydm_db");
        DuelDimension.cardsFolder = new File(DuelDimension.mainFolder, "cards");
        DuelDimension.setsFolder = new File(DuelDimension.mainFolder, "sets");
        DuelDimension.distributionsFolder = new File(DuelDimension.mainFolder, "distributions");
        DuelDimension.raritiesFolder = new File(DuelDimension.mainFolder, "rarities");
        
        DuelDimension.bindersFolder = new File("ydm_binders");
        DdIOUtil.createDirIfNonExistant(DuelDimension.bindersFolder);
        
        DuelDimension.proxy.initFolders();
    }
    
    private void initFiles()
    {
        DuelDimension.proxy.initFiles();
        DdIOUtil.setAgent();
        DdDatabase.initDatabase();
    }
    
    private void attachItemStackCapabilities(AttachCapabilitiesEvent<ItemStack> event)
    {
        if(event.getObject().getItem() == DdItems.CARD_BINDER.get())
        {
            attachCapability(event, new UUIDHolder(event.getObject()::getOrCreateTag), UUID_HOLDER, "uuid_holder", true);
        }
        if(event.getObject().getItem() instanceof SimpleBinderItem)
        {
            SimpleBinderItem item = (SimpleBinderItem) event.getObject().getItem();
            YDMItemHandler handler = new YDMItemHandler(item.binderSize, event.getObject()::getOrCreateTag);
            attachCapability(event, handler, CARD_ITEM_INVENTORY, "card_item_inventory", true);
        }
        if(event.getObject().getItem() == DdItems.OPENED_SET.get())
        {
            attachCapability(event, new YDMItemHandler(0, event.getObject()::getOrCreateTag), CARD_ITEM_INVENTORY, "card_item_inventory", true);
        }
        if(event.getObject().getItem() instanceof DeckBoxItem)
        {
            attachCapability(event, new YDMItemHandler(DeckHolder.TOTAL_SIZE_WITH_EXTRAS, event.getObject()::getOrCreateTag), CARD_ITEM_INVENTORY, "card_item_inventory", true);
        }
    }
    
    private void attachPlayerCapabilities(AttachCapabilitiesEvent<Entity> event)
    {
        if(event.getObject() instanceof Player)
        {
            Player player = (Player) event.getObject();
            attachCapability(event, new CooldownHolder(), COOLDOWN_HOLDER, "cooldown_holder", false);
        }
    }
    
    private static <T extends Tag, C extends INBTSerializable<T>> void attachCapability(AttachCapabilitiesEvent<?> event, C capData, Capability<C> capability, String name, boolean invalidate)
    {
        LazyOptional<C> optional = LazyOptional.of(() -> capData);
        ICapabilitySerializable<T> provider = new ICapabilitySerializable<T>()
        {
            @Override
            public <S> LazyOptional<S> getCapability(Capability<S> cap, Direction side)
            {
                if(cap == capability)
                {
                    return optional.cast();
                }
                
                return LazyOptional.empty();
            }
            
            @Override
            public T serializeNBT()
            {
                return capData.serializeNBT();
            }
            
            @Override
            public void deserializeNBT(T tag)
            {
                capData.deserializeNBT(tag);
            }
        };
        
        event.addCapability(new ResourceLocation(MOD_ID, name), provider);
        
        if(invalidate)
        {
            event.addListener(optional::invalidate);
        }
    }
    
    private void playerClone(PlayerEvent.Clone event)
    {
        final Player original = event.getOriginal();
        final Player current = event.getEntity();
        
        original.revive();
        
        original.getCapability(COOLDOWN_HOLDER).ifPresent(originalCD ->
        {
            current.getCapability(COOLDOWN_HOLDER).ifPresent(currentCD ->
            {
                currentCD.deserializeNBT(original.serializeNBT());
            });
        });
        
        original.discard();
    }
    
    /**
     * A joining player is told what they own, and their balance with it.
     * <p>
     * The client starts each session knowing nothing, so this is what makes the
     * deck editor show a real collection rather than an invented one.
     */
    private void playerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event)
    {
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
        {
            de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.saveAndSync(player);
            DuelDimension.channel.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new de.cas_ual_ty.dueldimension.shop.ShopMessages.SyncPoints(
                    de.cas_ual_ty.dueldimension.shop.DuelPoints.get(player)));
        }
    }

    /**
     * A leaving player's duel is ended and their cached profile dropped.
     * <p>
     * A duel is a conversation: the session thread is sitting waiting for an
     * answer that is never going to come now, and the other seat would wait
     * with it forever. Ending it is the only honest outcome.
     */
    private void playerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event)
    {
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
        {
            de.cas_ual_ty.dueldimension.duel.match.DuelLobby.forget(player);
            de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.abandon(player);
            de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.save(player);
            de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.forget(player);
        }
    }

    /** A respawned player has a new client-side world; tell it what it owns again. */
    private void playerRespawned(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event)
    {
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
        {
            de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.sync(player);
        }
    }

    private void playerChangedDimension(
        net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
        {
            de.cas_ual_ty.dueldimension.duel.profile.DuelProfiles.sync(player);
        }
    }

    private void playerTick(TickEvent.PlayerTickEvent event)
    {
        if(event.phase == TickEvent.Phase.END)
        {
            event.player.getCapability(COOLDOWN_HOLDER).ifPresent(CooldownHolder::tick);
        }
    }
    
    private void serverStarted(net.minecraftforge.event.server.ServerStartedEvent event)
    {
        de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.maybeStartSelfTest(event.getServer());
    }

    private void serverTick(TickEvent.ServerTickEvent event)
    {
        if(event.phase == TickEvent.Phase.END)
        {
            // Duels run on their own threads; this is where what they produced
            // is handed back to the game thread.
            de.cas_ual_ty.dueldimension.duel.npc.DuelistDuels.tick(event.getServer());
            de.cas_ual_ty.dueldimension.duel.match.DuelInvites.tick(event.getServer());
        }
    }
    
    private void entityAttributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event)
    {
        event.put(DdEntityTypes.DUELIST.get(),
            de.cas_ual_ty.dueldimension.duel.npc.DuelistEntity.createAttributes().build());
    }
    
    private void registerCommands(RegisterCommandsEvent event)
    {
        DdCommand.registerCommand(event.getDispatcher());
        de.cas_ual_ty.dueldimension.duel.match.DuelCommand.register(event.getDispatcher());
        de.cas_ual_ty.dueldimension.shop.DuelPointsCommand.register(event.getDispatcher());
    }
    
    private void modConfig(ModConfigEvent event)
    {
        if(event.getConfig().getSpec() == DuelDimension.commonConfigSpec)
        {
            DuelDimension.log("Baking common config!");
            DuelDimension.dbSourceUrl = DuelDimension.commonConfig.dbSourceUrl.get();
        }
    }
    
    private void findDecks(FindDecksEvent event)
    {
        Player player = event.getEntity();
        
        ItemStack itemStack;
        DeckHolder dh;
        
        for(int i = 0; i < player.getInventory().getContainerSize(); ++i)
        {
            itemStack = player.getInventory().getItem(i);
            
            if(itemStack.getItem() instanceof DeckBoxItem deckBoxItem)
            {
                dh = new ItemHandlerDeckHolder(deckBoxItem.getItemHandler(itemStack));
                
                if(!dh.isEmpty())
                {
                    event.addDeck(dh, itemStack);
                }
            }
        }
        
        itemStack = player.getOffhandItem();
        if(itemStack.getItem() instanceof DeckBoxItem deckBoxItem)
        {
            dh = new ItemHandlerDeckHolder(deckBoxItem.getItemHandler(itemStack));
            
            if(!dh.isEmpty())
            {
                event.addDeck(dh, itemStack);
            }
        }
    }
    
    private void newRegistry(NewRegistryEvent event)
    {
        DuelDimension.actionIconRegistry = event.create(new RegistryBuilder<ActionIcon>().setName(new ResourceLocation(DuelDimension.MOD_ID, "action_icons")).setMaxID(511));
        DuelDimension.zoneTypeRegistry = event.create(new RegistryBuilder<ZoneType>().setName(new ResourceLocation(DuelDimension.MOD_ID, "zone_types")).setMaxID(511));
        DuelDimension.actionTypeRegistry = event.create(new RegistryBuilder<ActionType>().setName(new ResourceLocation(DuelDimension.MOD_ID, "action_types")).setMaxID(511));
        DuelDimension.duelMessageHeaderRegistry = event.create(new RegistryBuilder<DuelMessageHeaderType>().setName(new ResourceLocation(DuelDimension.MOD_ID, "duel_message_headers")).setMaxID(63));
    }
    
    private void serverStopped(ServerStoppedEvent event)
    {
        synchronized(JsonCardsManager.LOADED_MANAGERS)
        {
            for(JsonCardsManager m : JsonCardsManager.LOADED_MANAGERS)
            {
                m.safe(() ->
                {
                });
            }
        }
    }
    
    public static void log(String s)
    {
        DuelDimension.LOGGER.info("[" + DuelDimension.MOD_ID + "] " + s);
    }
    
    public static void debug(String s)
    {
        DuelDimension.LOGGER.debug("[" + DuelDimension.MOD_ID + "_debug] " + s);
    }
    
    public static void debug(Object s)
    {
        if(s == null)
        {
            DuelDimension.debug("null");
        }
        else
        {
            DuelDimension.debug(s.toString());
        }
    }
}
