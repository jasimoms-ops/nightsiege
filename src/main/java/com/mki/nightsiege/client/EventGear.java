package com.mki.nightsiege.client;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.mixin.client.LayerDefinitionAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.model.monster.zombie.DrownedModel;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The 3D gear of the event monsters (see {@link EventGearShapes}): a copy of the vanilla
 * model whose own cubes are not drawn, with the gear hanging on its bones. The copy is
 * animated from the same render state as the monster, so horns, spikes and halos move with
 * the head, the body and the arms. Glowing parts are drawn a second time full bright.
 *
 * <p>Kept light: no gear on babies, on invisible monsters or beyond 48 blocks.</p>
 */
public final class EventGear {

    /** How the gear model is made: the model class the monster uses, on the gear of a family. */
    public enum Kind {
        ZOMBIE("humanoid"), DROWNED("humanoid"), SKELETON("skeleton"), CREEPER("creeper"), SPIDER("spider");

        final String family;

        Kind(String family) {
            this.family = family;
        }
    }

    private static final double MAX_DISTANCE_SQ = 48.0 * 48.0;
    private static final Map<String, Model<?>> MODELS = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static final Map<net.minecraft.resources.Identifier, net.minecraft.client.renderer.rendertype.RenderType> GLOW = new HashMap<>();

    private EventGear() {
    }

    /** The gear model of one event on one kind of monster, or null when there is none (or it broke). */
    static Model<?> model(String event, Kind kind) {
        String key = event + "/" + kind.name();
        if (FAILED.contains(key)) {
            return null;
        }
        Model<?> model = MODELS.get(key);
        if (model == null) {
            try {
                model = build(event, kind);
            } catch (RuntimeException e) {
                NightSiegeMod.LOGGER.warn("[Night Siege] Event-Ausruestung {} nicht gebaut: {}", key, e.toString());
                model = null;
            }
            if (model == null) {
                FAILED.add(key);
                return null;
            }
            MODELS.put(key, model);
        }
        return model;
    }

    private static Model<?> build(String event, Kind kind) {
        EventGearShapes.Set set = EventGearShapes.SETS.get(event + "/" + kind.family);
        if (set == null || set.boxes().isEmpty()) {
            return null;
        }
        MeshDefinition mesh = switch (kind) {
            case ZOMBIE, DROWNED -> HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
            case SKELETON -> mesh(SkeletonModel.createBodyLayer());
            case CREEPER -> mesh(CreeperModel.createBodyLayer(CubeDeformation.NONE));
            case SPIDER -> mesh(SpiderModel.createSpiderBodyLayer());
        };
        PartDefinition root = mesh.getRoot();
        List<EventGearShapes.Box> boxes = set.boxes();
        for (int i = 0; i < boxes.size(); i++) {
            EventGearShapes.Box b = boxes.get(i);
            PartDefinition bone = root.getChild(b.bone());
            CubeListBuilder cubes = CubeListBuilder.create().texOffs(b.u(), b.v()).mirror(b.mirror())
                    .addBox(b.x(), b.y(), b.z(), b.w(), b.h(), b.d());
            bone.addOrReplaceChild("ns_gear_" + i, cubes, PartPose.offsetAndRotation(b.px(), b.py(), b.pz(), b.rx(), b.ry(), b.rz()));
        }
        ModelPart baked = LayerDefinition.create(mesh, set.width(), set.height()).bakeRoot();
        // Only the gear is drawn: the monster's own cubes stay with the monster.
        Set<ModelPart> gear = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < boxes.size(); i++) {
            gear.add(baked.getChild(boxes.get(i).bone()).getChild("ns_gear_" + i));
        }
        for (ModelPart part : baked.getAllParts()) {
            if (!gear.contains(part)) {
                part.skipDraw = true;
            }
        }
        return switch (kind) {
            case ZOMBIE -> new ZombieModel<>(baked);
            case DROWNED -> new DrownedModel(baked);
            case SKELETON -> new SkeletonModel<>(baked);
            case CREEPER -> new CreeperModel(baked);
            case SPIDER -> new SpiderModel(baked);
        };
    }

    private static MeshDefinition mesh(LayerDefinition layer) {
        return ((LayerDefinitionAccessor) layer).nightsiege$mesh();
    }

    /** The layer that draws the gear of the event shown now. */
    public static class Layer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {

        private final Kind kind;

        public Layer(RenderLayerParent<S, M> parent, Kind kind) {
            super(parent);
            this.kind = kind;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
            if (state.isBaby || state.isInvisible || state.distanceToCameraSq > MAX_DISTANCE_SQ) {
                return;
            }
            String event = EventTextures.shownEvent();
            if (event == null) {
                return;
            }
            Model<? super S> model = (Model<? super S>) model(event, kind);
            if (model == null) {
                return;
            }
            coloredCutoutModelCopyLayerRender(model, EventTextures.gear(event, kind.family, false), poseStack, collector,
                    lightCoords, state, -1, 1);
            collector.order(2).submitModel(model, state, poseStack,
                    GLOW.computeIfAbsent(EventTextures.gear(event, kind.family, true), RenderTypes::eyes),
                    lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        }
    }
}
