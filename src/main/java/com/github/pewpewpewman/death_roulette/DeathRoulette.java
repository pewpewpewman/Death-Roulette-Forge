package com.github.pewpewpewman.death_roulette;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import java.util.List;
import java.util.Map;


// The value here should match an entry in the META-INF/mods.toml file
@Mod(DeathRoulette.MODID)
public class DeathRoulette {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "death_roulette";

    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    private static double RESET_CHANCE = 0.0;
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    private static final RegistryObject<SoundEvent> INVENTORY_CLEARED_SOUND = registerSoundEvents("inventory_cleared");
    private static final RegistryObject<SoundEvent> INVENTORY_SPARE_SOUND = registerSoundEvents("inventory_spared");


    public DeathRoulette() {
        System.out.println("DEATH ROULETTE INIT");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(ServerModEvents.class);

        //Register sounds
        registerSounds(modEventBus);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "death_roulette-common.toml");


    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        //LOGGER.info("HELLO FROM COMMON SETUP");

        RESET_CHANCE = Config.RESET_CHANCE.get();
        //System.out.println("RESET CHANCE: " + RESET_CHANCE);
    }

    public static void registerSounds(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }

    private static RegistryObject<SoundEvent> registerSoundEvents(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, name)));
    }


    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
    public static class ServerModEvents {

        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            //Ignore death roulette on beating the ender dragon
            if (event.isEndConquered()) return;

            Player player_ = event.getEntity();
            if (!(player_ instanceof ServerPlayer player)) return;

            //Variables for sound and who hears the resulting sound
            RegistryObject<SoundEvent> soundToPlay;
            List<ServerPlayer> hearers;

            //System.out.println("Player " + player.getName().getString() + " respawned!");

            double rng = Math.random();
            //System.out.println("RNG " + rng);

            if (rng < RESET_CHANCE) {

                //Clear out inventory
                player.getInventory().clearContent();

                //Clear Curios if they're included
                if (ModList.get().isLoaded("curios")) {

                    //Code copied from CuriosCommand
                    CuriosApi.getCuriosHelper().getCuriosHandler(player).ifPresent(handler -> {
                        Map<String, ICurioStacksHandler> curios = handler.getCurios();

                        for (String id : curios.keySet()) {
                            ICurioStacksHandler stacksHandler = curios.get(id);
                            for (int i = 0; i < stacksHandler.getSlots(); i++) {
                                stacksHandler.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                                stacksHandler.getCosmeticStacks().setStackInSlot(i, ItemStack.EMPTY);
                            }
                        }
                    });

                }
                //System.out.println("Player " + player.getName().getString() + " loss their items!");

                //Make whole server hear item loss noise
                soundToPlay = INVENTORY_CLEARED_SOUND;
                hearers = player.getServer().getPlayerList().getPlayers();

                //Make clear text appear
                player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("death_roulette.inventory_cleared_action_bar").withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD)));
                //player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 100, 4));


                //Tell entire server about this
                for(ServerPlayer serverPlayer : player.getServer().getPlayerList().getPlayers()) {
                    serverPlayer.sendSystemMessage(
                            Component.translatable("death_roulette.inventory_cleared_server_message", player.getDisplayName()).withStyle(ChatFormatting.RED),
                            false);
                }
            }
            else {
                //Play sound only for player who died
                soundToPlay = INVENTORY_SPARE_SOUND;
                hearers = List.of(player);
            }

            //Play Sound(s)
            for (ServerPlayer iterPlayer : hearers)
            {
                iterPlayer.connection.send(new ClientboundSoundPacket(
                        soundToPlay.getHolder().get(),
                        SoundSource.AMBIENT,
                        iterPlayer.position().x(),
                        iterPlayer.position().y(),
                        iterPlayer.position().z(),
                        1.0f,
                        1.0f,
                        0
                ));
            }

        }
    }


}
