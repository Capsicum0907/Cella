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

/**
 * Draws the chest.
 *
 * <p><b>Vanilla's model, our pixels.</b> The three parts — bottom, lid and lock — are
 * baked from {@code ModelLayers.CHEST}, which the game already registers, so the shape
 * and its unwrap come free and correct. What is ours is the sheet they are drawn with,
 * added to the chest atlas by {@code assets/minecraft/atlases/chests.json}: every pack
 * contributing that path is merged rather than overriding it, so vanilla's own chests
 * are untouched.
 *
 * <p>The transform is vanilla's too — turn about the middle of the block by the facing,
 * then put the origin back — because the parts are built in the coordinates that expects.
 *
 * <p>How far the lid has swung comes from the block entity, which is told by a block
 * event and counts openers rather than holding a flag - see {@code CellaBlockEntity}.
 */
public class CellaRenderer implements BlockEntityRenderer<CellaBlockEntity> {
    /** One sheet per kind, looked up once rather than built on every frame. */
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

        // Vanilla's easing: the lid leaves fast and settles slowly, which is what makes
        // it look hinged rather than driven.
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
