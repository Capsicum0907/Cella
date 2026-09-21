package io.github.capsicum0907.cella.data;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.capsicum0907.cella.Cella;
import io.github.capsicum0907.cella.Kind;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

public class ChestAtlas implements DataProvider {
    private static final String ATLAS = "chests";

    private final PackOutput.PathProvider path;

    public ChestAtlas(PackOutput output) {
        this.path = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "atlases");
    }

    @Override
    public String getName() {
        return "Chest Atlas: " + Cella.MODID;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        JsonArray sources = new JsonArray();
        for (Kind kind : Kind.values()) {
            JsonObject single = new JsonObject();
            single.addProperty("type", "minecraft:single");
            single.addProperty("resource",
                    ResourceLocation.fromNamespaceAndPath(Cella.MODID,
                            "entity/chest/" + kind.id()).toString());
            sources.add(single);
        }
        JsonObject atlas = new JsonObject();
        atlas.addProperty("__comment", "Adds " + names() + " to the chest atlas.");
        atlas.add("sources", sources);
        return DataProvider.saveStable(output, atlas,
                path.json(ResourceLocation.withDefaultNamespace(ATLAS)));
    }

    private static String names() {
        return Stream.of(Kind.values()).map(Kind::id).collect(Collectors.joining(", "));
    }
}
