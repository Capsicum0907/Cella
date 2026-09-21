package io.github.capsicum0907.cella;

import com.mojang.logging.LogUtils;

import io.github.capsicum0907.cella.client.CellaClientConfig;
import io.github.capsicum0907.cella.client.CellaRenderer;
import io.github.capsicum0907.cella.client.CellaScreen;
import io.github.capsicum0907.cella.client.Measure;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import org.slf4j.Logger;

@Mod(Cella.MODID)
public class Cella {
    public static final String MODID = "cella";

    private static final Logger LOGGER = LogUtils.getLogger();

    public Cella(IEventBus modEventBus, ModContainer modContainer) {
        CellaRegistry.BLOCKS.register(modEventBus);
        CellaRegistry.ITEMS.register(modEventBus);
        CellaRegistry.BLOCK_ENTITIES.register(modEventBus);
        CellaRegistry.MENUS.register(modEventBus);
        CellaRegistry.RECIPES.register(modEventBus);
        CellaRegistry.COMPONENTS.register(modEventBus);
        CellaRegistry.TABS.register(modEventBus);

        modEventBus.addListener(Cella::kindling);
        modEventBus.addListener(Cella::capabilities);
        modEventBus.addListener(Cella::payloads);
        NeoForge.EVENT_BUS.addListener(Cella::left);

        NeoForge.EVENT_BUS.addListener(Expiring::reached);
        NeoForge.EVENT_BUS.addListener(Expiring::tick);

        NeoForge.EVENT_BUS.addListener(Carried::died);
        NeoForge.EVENT_BUS.addListener(Carried::respawned);
        NeoForge.EVENT_BUS.addListener(KeptCommand::register);
        NeoForge.EVENT_BUS.addListener(Blast::tick);
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> {
                    Blast.forget();
                    Expiring.forget();
                });
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(Cella::screens);
            modEventBus.addListener(Cella::renderers);
            modEventBus.addListener(Cella::tooltips);
            onlyOnTheClient(modContainer);
        }

        modContainer.registerConfig(ModConfig.Type.SERVER, CellaConfig.SPEC);
        LOGGER.info("Cella {} loaded.", modContainer.getModInfo().getVersion());
    }

    private static void kindling(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.world.level.block.FireBlock fire =
                    (net.minecraft.world.level.block.FireBlock) net.minecraft.world.level.block.Blocks.FIRE;
            for (Kind kind : Kind.values()) {
                if (kind.trait().burns()) {
                    fire.setFlammable(CellaRegistry.block(kind).get(), 5, 20);
                }
            }
        });
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CellaRegistry.BLOCK_ENTITY.get(),
                (chest, side) -> chest.outlet());
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(Room.TYPE, Room.STREAM_CODEC,
                (room, context) -> Room.remember(context.player().getUUID(), room));

        event.registrar("1").optional().playToClient(Shelf.TYPE, Shelf.STREAM_CODEC,
                (shelf, context) -> ShelfHolder.told(shelf));

        event.registrar("1").optional().playToServer(Peek.TYPE, Peek.STREAM_CODEC,
                (peek, context) -> {
                    if (!(context.player().containerMenu instanceof CellaMenu menu)) {
                        return;
                    }
                    if (peek.open()) {
                        menu.view(peek.index());
                    }
                    int shown = peek.open() ? Peek.LIST : peek.index();
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            (net.minecraft.server.level.ServerPlayer) context.player(),
                            menu.shelf(shown));
                });

        event.registrar("1").optional().playToServer(Edit.TYPE, Edit.STREAM_CODEC,
                (edit, context) -> {
                    if (!(context.player().containerMenu instanceof CellaMenu menu)) {
                        return;
                    }
                    menu.edit(edit, context.player());
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            (net.minecraft.server.level.ServerPlayer) context.player(),
                            menu.shelf(Peek.LIST));
                });

        event.registrar("1").optional().playToServer(Look.TYPE, Look.STREAM_CODEC,
                (look, context) -> {
                    if (context.player().containerMenu instanceof CellaMenu menu) {
                        menu.look(look.looking());
                    }
                });
    }

    private static void left(PlayerEvent.PlayerLoggedOutEvent event) {
        Room.forget(event.getEntity().getUUID());
    }

    private static void onlyOnTheClient(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, CellaClientConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(Measure::tick);
    }

    private static void tooltips(
            net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(Held.class, io.github.capsicum0907.cella.client.FillBar::new);
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(CellaRegistry.MENU.get(), CellaScreen::new);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CellaRegistry.BLOCK_ENTITY.get(), CellaRenderer::new);
    }
}
