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

/**
 * Entry point. {@link #MODID} must match {@code mod_id} in gradle.properties,
 * which is what the generated neoforge.mods.toml is filled from.
 */
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
        NeoForge.EVENT_BUS.addListener(KeptCommand::register);
        NeoForge.EVENT_BUS.addListener(Blast::tick);
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> Blast.forget());
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(Cella::screens);
            modEventBus.addListener(Cella::renderers);
            modEventBus.addListener(Cella::tooltips);
            onlyOnTheClient(modContainer);
        }

        modContainer.registerConfig(ModConfig.Type.SERVER, CellaConfig.SPEC);
        LOGGER.info("Cella {} loaded.", modContainer.getModInfo().getVersion());
    }

    /**
     * The larva burns, and nothing else does.
     *
     * <p>⚠ <b>Backwards from how the ladder reads.</b> "Imperfect gains fire resistance"
     * cannot be implemented by giving Imperfect anything: only blocks handed to
     * {@code FireBlock#setFlammable} burn at all, and no Cella was ever handed to it, so
     * every form was already fireproof and the step meant nothing. What makes it true is
     * registering Laravel — the larva catches, and everything above it is simply what a
     * block is when left alone.
     *
     * <p>The two numbers are the ones a wooden chest has: how readily it catches and how
     * long it goes on burning.
     */
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

    /**
     * What a hopper or a pipe sees: <b>the whole chest</b>, every page of it.
     *
     * <p>Not the window. Which page a player happens to be looking at is a fact about
     * that player, and a hopper that could only reach it would be nonsense — it would
     * mean the chest changed size depending on who was standing nearby.
     *
     * <p>No side argument: every face is the same chest.
     */
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CellaRegistry.BLOCK_ENTITY.get(),
                (chest, side) -> chest.contents());
    }

    /**
     * One message, in one direction: how much a player's screen can show.
     *
     * <p>See {@link Room}. Marked optional so that a client without this mod - or with an
     * older one - connects rather than being turned away over a layout hint; a player who
     * never says gets the chest's own shape.
     */
    private static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(Room.TYPE, Room.STREAM_CODEC,
                (room, context) -> Room.remember(context.player().getUUID(), room));
    }

    /** A window that has gone is a window there is nothing to remember about. */
    private static void left(PlayerEvent.PlayerLoggedOutEvent event) {
        Room.forget(event.getEntity().getUUID());
    }

    /**
     * The two things that only exist on a client, kept behind a method so that loading
     * this class on a dedicated server does not go looking for them.
     */
    private static void onlyOnTheClient(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, CellaClientConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(Measure::tick);
    }

    /** The drawn fill bar; the figures beside it are ordinary tooltip lines. */
    private static void tooltips(
            net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(Held.class, io.github.capsicum0907.cella.client.FillBar::new);
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(CellaRegistry.MENU.get(), CellaScreen::new);
    }

    /** The chest is drawn rather than modelled; see {@link CellaRenderer}. */
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CellaRegistry.BLOCK_ENTITY.get(), CellaRenderer::new);
    }
}
