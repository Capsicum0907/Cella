package io.github.capsicum0907.cella;

import com.mojang.logging.LogUtils;

import io.github.capsicum0907.cella.client.CellaScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
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

        modEventBus.addListener(Cella::capabilities);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(Cella::screens);
        }

        modContainer.registerConfig(ModConfig.Type.SERVER, CellaConfig.SPEC);
        LOGGER.info("Cella {} loaded.", modContainer.getModInfo().getVersion());
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

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(CellaRegistry.MENU.get(), CellaScreen::new);
    }
}
