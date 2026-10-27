package com.mki.nightsiege.client;

import com.mki.nightsiege.core.CoreStats;
import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.game.CoreNetworking;
import com.mki.nightsiege.game.Json;
import com.mki.nightsiege.game.ModContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import com.mki.nightsiege.game.ClassGear;
import com.mki.nightsiege.game.GuideItem;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Client side: the core window and the key for the ultimate. */
public class NightSiegeClient implements ClientModInitializer {

    /** Fires the ultimate of the player's path. R by default; rebindable under Controls. */
    private static KeyMapping ultimateKey;
    /** Opens the handbook. H by default. */
    private static KeyMapping guideKey;

    public static KeyMapping ultimateKey() {
        return ultimateKey;
    }

    @Override
    public void onInitializeClient() {
        // The Night Siege shader pack (based on Photon) goes into the shaderpacks folder.
        ShaderPackInstaller.install();
        // The message types are registered by the main entrypoint, which also runs here.
        ClientPlayNetworking.registerGlobalReceiver(CoreNetworking.CoreUiPayload.TYPE,
                (payload, context) -> {
                    CoreStats stats = Json.fromJson(payload.json(), CoreStats.class);
                    if (stats == null) {
                        return;
                    }
                    // Network threads must not touch the screen directly.
                    context.client().execute(() -> {
                        Minecraft client = Minecraft.getInstance();
                        CoreScreen open = CoreScreen.openInstance();
                        if (open != null) {
                            open.update(stats);
                        } else {
                            client.setScreenAndShow(new CoreScreen(stats));
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(CoreNetworking.DungeonUiPayload.TYPE,
                (payload, context) -> {
                    com.mki.nightsiege.game.DungeonToolState state =
                            Json.fromJson(payload.json(), com.mki.nightsiege.game.DungeonToolState.class);
                    if (state == null) {
                        return;
                    }
                    context.client().execute(() -> {
                        DungeonToolScreen open = DungeonToolScreen.openInstance();
                        if (open != null) {
                            open.update(state);
                        } else {
                            Minecraft.getInstance().setScreenAndShow(new DungeonToolScreen(state));
                        }
                    });
                });
        // Network ids of boss monsters only hold for one connection.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> {
                    BossLooks.clear();
                    MannedClient.reset();
                    HealthViewClient.clear();
                    DroneClient.clear();
                    NightKeys.setOwned(false);
                });
        ClientPlayNetworking.registerGlobalReceiver(CoreNetworking.ScreenPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    Minecraft client = Minecraft.getInstance();
                    if ("classpick".equals(payload.kind())) {
                        com.mki.nightsiege.core.ClassPickData data =
                                Json.fromJson(payload.json(), com.mki.nightsiege.core.ClassPickData.class);
                        if (data != null) {
                            client.setScreenAndShow(new ClassPickScreen(data));
                        }
                    } else if ("classpick_update".equals(payload.kind())) {
                        com.mki.nightsiege.core.ClassPickData data =
                                Json.fromJson(payload.json(), com.mki.nightsiege.core.ClassPickData.class);
                        if (data != null && client.gui.screen() instanceof ClassPickScreen open) {
                            open.update(data);
                        }
                    } else if ("nightstats".equals(payload.kind()) || "nightstats_now".equals(payload.kind())) {
                        com.mki.nightsiege.core.NightStatsData stats =
                                Json.fromJson(payload.json(), com.mki.nightsiege.core.NightStatsData.class);
                        if (stats != null) {
                            if ("nightstats_now".equals(payload.kind())) {
                                client.setScreenAndShow(new NightStatsScreen(stats));
                            } else {
                                NightStatsScreen.queue(stats);
                            }
                        }
                    } else if ("spotter".equals(payload.kind())) {
                        com.mki.nightsiege.core.SpotterData data =
                                Json.fromJson(payload.json(), com.mki.nightsiege.core.SpotterData.class);
                        if (data != null && client.player != null) {
                            client.setScreenAndShow(new SpotterScreen(data));
                        }
                    } else if ("drones".equals(payload.kind())) {
                        DroneClient.set(payload.json());
                    } else if ("healthview".equals(payload.kind())) {
                        HealthViewClient.set(payload.json());
                    } else if ("towers".equals(payload.kind())) {
                        // The engineer's tower menu (27 October 2026): opened by the remote, refreshed after a pick.
                        com.mki.nightsiege.core.TowerMenuData data =
                                Json.fromJson(payload.json(), com.mki.nightsiege.core.TowerMenuData.class);
                        if (client.gui.screen() instanceof TowerMenuScreen open) {
                            open.refresh(data);
                        } else if (data != null && data.open && client.player != null) {
                            client.setScreenAndShow(new TowerMenuScreen(data));
                        }
                    } else if ("wheel".equals(payload.kind())) {
                        // Fähigkeit 3 held: the wheel (27 October 2026)
                        WheelClient.open(Json.fromJson(payload.json(), com.mki.nightsiege.core.WheelData.class));
                    } else if ("brewmenu".equals(payload.kind())) {
                        com.mki.nightsiege.core.BrewMenuData data =
                                Json.fromJson(payload.json(), com.mki.nightsiege.core.BrewMenuData.class);
                        if (data != null) {
                            client.setScreenAndShow(new BrewMenuScreen(data));
                        }
                    } else if ("bosses".equals(payload.kind())) {
                        BossLooks.set(payload.json());
                    } else if ("crate".equals(payload.kind())) {
                        com.mki.nightsiege.core.CrateRoll roll =
                                Json.fromJson(payload.json(), com.mki.nightsiege.core.CrateRoll.class);
                        if (roll != null) {
                            client.setScreenAndShow(new CrateScreen(roll));
                        }
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(CoreNetworking.EventSkyPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        com.mki.nightsiege.game.EventAtmosphere.setClient(payload.event(), payload.fade())));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                com.mki.nightsiege.game.EventAtmosphere.resetClient();
                return;
            }
            com.mki.nightsiege.game.EventAtmosphere.clientShaderMode = ShaderSupport.mode();
            com.mki.nightsiege.game.EventAtmosphere.tickClient();
            FogWisps.tick(client);
            EventAura.tick(client);
        });
        net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance()
                .register(ModContent.FOG_WISP, sprites -> new FogWisps.Provider(sprites));
        ClientPlayNetworking.registerGlobalReceiver(CoreNetworking.UltHudPayload.TYPE,
                (payload, context) -> {
                    UltState state = Json.fromJson(payload.json(), UltState.class);
                    context.client().execute(() -> {
                        UltHud.update(state);
                        NightKeys.setOwned(state != null && state.keys);
                    });
                });
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ModContent.id("ultimate"),
                (gui, delta) -> UltHud.draw(gui, ultimateKey));

        KeyMapping.Category category = KeyMapping.Category.register(ModContent.id("main"));
        ultimateKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.nightsiege.ability1", GLFW.GLFW_KEY_Q, category));   // Fähigkeit 1: the ultimate (27 Oct 2026)
        guideKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.nightsiege.guide", GLFW.GLFW_KEY_H, category));
        GainClient.init(category);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(NightStatsScreen::tick);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.mki.nightsiege.game.KitProjectile.TYPE, net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        GuideItem.opener = () -> Minecraft.getInstance().setScreenAndShow(new GuideScreen());
        // Kit gadgets and the remote: which key they need, with the player's own bindings.
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            com.mki.nightsiege.core.KeyHint hint = stack.is(ModContent.REMOTE)
                    ? com.mki.nightsiege.core.KeyHint.REMOTE
                    : ModContent.STAFF != null && stack.is(ModContent.STAFF)
                    ? com.mki.nightsiege.core.KeyHint.STAFF
                    : com.mki.nightsiege.core.KeyHint.forGadget(com.mki.nightsiege.game.Gadgets.of(stack));
            Component line = KeyHints.line(hint);
            if (line != null) {
                lines.add(line);
            }
        });
        // Shield energy (Simon 26.10.): a short line on every kit shield.
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)
                    && com.mki.nightsiege.game.KitService.isKit(stack)) {
                lines.add(Component.translatable("nightsiege.tooltip.shield_energy").withStyle(ChatFormatting.AQUA));
            }
        });
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            List<String> paths = ClassGear.pathsFor(stack.getItem());
            if (paths.isEmpty()) {
                return;
            }
            Component names = Component.empty();
            for (int i = 0; i < paths.size(); i++) {
                if (i > 0) {
                    names = names.copy().append(", ");
                }
                names = names.copy().append(Component.translatable("nightsiege.path." + paths.get(i)));
            }
            lines.add(Component.translatable("nightsiege.tooltip.class_item", names).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("nightsiege.tooltip.class_item_hint").withStyle(ChatFormatting.GRAY));
        });
        // Event looks: hoods and rags over zombies and skeletons, the 3D gear on every event monster.
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register(
                NightSiegeClient::eventLayers);
        // Manned towers (Simon, 26 October 2026): the attack button fires the tower.
        ClientTickEvents.START_CLIENT_TICK.register(MannedClient::tick);
        // The drone of a fallen defender (Simon, 26 October 2026): own steering and a brass frame.
        ClientTickEvents.START_CLIENT_TICK.register(DroneClient::tick);
        // The summoner's staff: held left click repeats its attack (27 October 2026).
        ClientTickEvents.START_CLIENT_TICK.register(StaffClient::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ModContent.id("drone"),
                (gui, delta) -> DroneClient.draw(gui));
        // The wheel of Fähigkeit 3 is a HUD layer, so walking goes on while it is open (27 October 2026).
        ClientTickEvents.START_CLIENT_TICK.register(WheelOverlay::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ModContent.id("wheel"),
                (gui, delta) -> WheelOverlay.draw(gui));
        // Guardian: a left click while blocking is the shield action. Minecraft drops
        // attack clicks while an item is in use, so they are taken here first.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null || !client.player.isUsingItem()
                    || !client.player.getUseItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) {
                return;
            }
            boolean pressed = false;
            while (client.options.keyAttack.consumeClick()) {
                pressed = true;
            }
            if (pressed && ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
                ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload("shield"));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (guideKey.consumeClick()) {
                Minecraft.getInstance().setScreenAndShow(new GuideScreen());
            }
            while (ultimateKey.consumeClick()) {
                if (ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
                    ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload("ult"));
                }
            }
        });
    }

    /** The vanilla monsters with an event look (texture, glow, and gear where the model allows). */
    private static final java.util.Set<net.minecraft.world.entity.EntityType<?>> EVENT_MOBS = java.util.Set.of(
            net.minecraft.world.entity.EntityTypes.ZOMBIE, net.minecraft.world.entity.EntityTypes.HUSK,
            net.minecraft.world.entity.EntityTypes.DROWNED, net.minecraft.world.entity.EntityTypes.SKELETON,
            net.minecraft.world.entity.EntityTypes.STRAY, net.minecraft.world.entity.EntityTypes.BOGGED,
            net.minecraft.world.entity.EntityTypes.PARCHED, net.minecraft.world.entity.EntityTypes.WITHER_SKELETON,
            net.minecraft.world.entity.EntityTypes.CREEPER, net.minecraft.world.entity.EntityTypes.SPIDER,
            net.minecraft.world.entity.EntityTypes.CAVE_SPIDER, net.minecraft.world.entity.EntityTypes.PILLAGER,
            net.minecraft.world.entity.EntityTypes.VINDICATOR, net.minecraft.world.entity.EntityTypes.EVOKER,
            net.minecraft.world.entity.EntityTypes.ILLUSIONER, net.minecraft.world.entity.EntityTypes.WITCH,
            net.minecraft.world.entity.EntityTypes.RAVAGER, net.minecraft.world.entity.EntityTypes.BREEZE,
            net.minecraft.world.entity.EntityTypes.PHANTOM, net.minecraft.world.entity.EntityTypes.BLAZE,
            net.minecraft.world.entity.EntityTypes.PIGLIN_BRUTE, net.minecraft.world.entity.EntityTypes.ZOMBIFIED_PIGLIN,
            net.minecraft.world.entity.EntityTypes.MAGMA_CUBE, net.minecraft.world.entity.EntityTypes.HOGLIN,
            net.minecraft.world.entity.EntityTypes.CAMEL_HUSK);

    /** True for every monster that takes part in the event looks (for the particle aura too). */
    public static boolean eventMob(net.minecraft.world.entity.EntityType<?> type) {
        return EVENT_MOBS.contains(type) || EventTextures.fromEventMod(type);
    }

    /** Clothes, glow and gear layers of the event monsters. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void eventLayers(net.minecraft.world.entity.EntityType<?> type,
                                    net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> renderer,
                                    net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.RegistrationHelper helper,
                                    net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
        if (!eventMob(type)) {
            return;
        }
        net.minecraft.client.renderer.entity.RenderLayerParent parent = (net.minecraft.client.renderer.entity.RenderLayerParent) renderer;
        try {
            if (type == net.minecraft.world.entity.EntityTypes.ZOMBIE || type == net.minecraft.world.entity.EntityTypes.HUSK) {
                net.minecraft.client.model.monster.zombie.DrownedModel model = new net.minecraft.client.model.monster.zombie.DrownedModel(
                        context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.DROWNED_OUTER_LAYER));
                java.util.function.Predicate<net.minecraft.client.renderer.entity.state.LivingEntityRenderState> adult = state -> !state.isBaby;
                helper.register(new EventClothingLayer(parent, model, "zombie", adult));
            } else if (type == net.minecraft.world.entity.EntityTypes.SKELETON || type == net.minecraft.world.entity.EntityTypes.WITHER_SKELETON
                    || type == net.minecraft.world.entity.EntityTypes.PARCHED) {
                net.minecraft.client.model.monster.skeleton.SkeletonModel model = new net.minecraft.client.model.monster.skeleton.SkeletonModel(
                        context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.STRAY_OUTER_LAYER));
                java.util.function.Predicate<net.minecraft.client.renderer.entity.state.LivingEntityRenderState> any = state -> true;
                helper.register(new EventClothingLayer(parent, model, "skeleton", any));
            }
            if (EVENT_MOBS.contains(type)) {
                helper.register(new EventGear.Glow(renderer));
            }
            EventGear.Kind kind = EventGear.kindOf(renderer.getModel());
            if (kind != null) {
                helper.register(new EventGear.Layer(parent, kind));
            }
        } catch (RuntimeException e) {
            com.mki.nightsiege.NightSiegeMod.LOGGER.warn("[Night Siege] Event-Ebenen fuer {} nicht angelegt: {}", type, e.toString());
        }
    }
}
