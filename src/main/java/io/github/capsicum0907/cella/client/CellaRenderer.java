package io.github.capsicum0907.cella.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import java.util.EnumMap;
import java.util.Map;

import io.github.capsicum0907.cella.Cella;
import io.github.capsicum0907.cella.CellaBlock;
import io.github.capsicum0907.cella.CellaBlockEntity;
import io.github.capsicum0907.cella.Kind;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;

public class CellaRenderer implements BlockEntityRenderer<CellaBlockEntity> {
    private static final Map<Kind, Material> SHEETS = new EnumMap<>(Kind.class);

    static {
        for (Kind kind : Kind.values()) {
            SHEETS.put(kind, new Material(Sheets.CHEST_SHEET,
                    ResourceLocation.fromNamespaceAndPath(Cella.MODID,
                            "entity/chest/" + kind.id())));
        }
    }

    private final ModelPart bottom;
    private final ModelPart lid;
    private final ModelPart lock;

    public CellaRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(ModelLayers.CHEST);
        this.bottom = root.getChild("bottom");
        this.lid = root.getChild("lid");
        this.lock = root.getChild("lock");
    }

    @Override
    public void render(CellaBlockEntity chest, float partial, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(
                -chest.getBlockState().getValue(CellaBlock.FACING).toYRot()));
        pose.translate(-0.5F, -0.5F, -0.5F);

        float swung = chest.getOpenNess(partial);
        swung = 1.0F - swung;
        swung = 1.0F - swung * swung * swung;
        lid.xRot = -(swung * (float) (Math.PI / 2));
        lock.xRot = lid.xRot;

        VertexConsumer buffer = SHEETS.get(chest.kind())
                .buffer(buffers, RenderType::entityCutout);
        lid.render(pose, buffer, light, overlay);
        lock.render(pose, buffer, light, overlay);
        bottom.render(pose, buffer, light, overlay);
        pose.popPose();
    }
}
