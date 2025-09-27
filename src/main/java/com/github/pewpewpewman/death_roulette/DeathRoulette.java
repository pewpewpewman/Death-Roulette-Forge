package com.github.pewpewpewman.death_roulette;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LogicalSidedProvider;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.function.Supplier;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Death_roulette.MODID)
public class Death_roulette {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "death_roulette";

    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double ITEMLOSSCHANCE = 0.5;

    public Death_roulette() {
        System.out.println("DEATH ROULETTE INIT");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(ServerModEvents.class);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
    public static class ServerModEvents {

        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            Player player = event.getEntity();

            System.out.println("Player " + player.getName().getString() + " respawned!");

            double rng = Math.random();
            if (rng > ITEMLOSSCHANCE) {
                player.getInventory().clearContent();
                System.out.println("Player " + player.getName().getString() + " loss their items!");
            }
        }
    }

    //Packet Handler for Death Roulette - needed for activating logical client side effects
    //on inventory clear
    public class StartupCommon
    {
        public static SimpleChannel simpleChannel;

        public static final byte INVENTORY_CLEARED_PROC_ID = 49;
        public static final byte INVENTORY_SPARED_PROC_ID = 83;

        public static final String MESSAGE_PROTOCOL_VERSION = "1.0";

        public static final ResourceLocation simpleChannelRl = new ResourceLocation(MODID, "drchannel");

        public static void onCommonSetup(FMLCommonSetupEvent event) {
            simpleChannel = NetworkRegistry.newSimpleChannel(
                    simpleChannelRl,
                    () -> MESSAGE_PROTOCOL_VERSION,
                    MESSAGE_PROTOCOL_VERSION::equals,
                    MESSAGE_PROTOCOL_VERSION::equals
            );

            simpleChannel.registerMessage(
                    INVENTORY_CLEARED_PROC_ID,
                    InventoryClearedMessageToClient.class,
                    InventoryClearedMessageToClient::encode,
                    InventoryClearedMessageToClient::decode,

            );

            simpleChannel.registerMessage(
                    INVENTORY_SPARED_PROC_ID,
                    InventorySparedMessageToClient.class,
                    InventorySparedMessageToClient::encode,
                    InventorySparedMessageToClient::decode
                    );

        }

        public static void onServerReceivedClearedMessage(
                final InventoryClearedMessageToClient message,
                Supplier<NetworkEvent.Context> ctxSupplier
        ) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            LogicalSide sideReceived = ctx.getDirection().getReceptionSide();
            ctx.setPacketHandled(true);

            if (sideReceived != LogicalSide.CLIENT) {
                LOGGER.warn("TargetEffectMessageToClient received on wrong side:" + ctx.getDirection().getReceptionSide());
                return;
            }
            if (!message.isMessageIsValid()) {
                LOGGER.warn("TargetEffectMessageToClient was invalid" + message.toString());
                return;
            }
            // we know for sure that this handler is only used on the client side, so it is ok to assume
            //  that the ctx handler is a client, and that Minecraft exists.
            // Packets received on the server side must be handled differently!  See MessageHandlerOnServer

            Optional<ClientWorld> clientWorld = LogicalSidedProvider.CLIENTWORLD.get(sideReceived);
            if (!clientWorld.isPresent()) {
                LOGGER.warn("TargetEffectMessageToClient context could not provide a ClientWorld.");
                return;
            }
        }
    }

    public static class InventoryClearedMessageToClient {

        private boolean messageIsValid;

        private InventoryClearedMessageToClient()
        {
            messageIsValid = false;
        }

        public boolean isMessageIsValid() {
            return messageIsValid;
        }

        public static InventoryClearedMessageToClient decode(FriendlyByteBuf buf) {
            InventoryClearedMessageToClient ret = new InventoryClearedMessageToClient();
            ret.messageIsValid = true;
            return ret;
        }

        public void encode(FriendlyByteBuf buf) {
            if (!messageIsValid) {
                return;
            }
        }
    }

    public static class InventorySparedMessageToClient {

        private boolean messageIsValid;

        private InventorySparedMessageToClient()
        {
            messageIsValid = false;
        }

        public boolean isMessageIsValid() {
            return messageIsValid;
        }

        public static InventorySparedMessageToClient decode(FriendlyByteBuf buf) {
            InventorySparedMessageToClient ret = new InventorySparedMessageToClient();
            ret.messageIsValid = true;
            return ret;
        }
        public void encode(FriendlyByteBuf buf) {}

    }
}
