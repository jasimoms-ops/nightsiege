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

    /** Tor 3.6: the tooltips last received (the server leaves them out while they stay the same). */
    private static List<com.mki.nightsiege.core.AbilityTip> lastTips = new java.util.ArrayList<>();

    /** Fires the ultimate of the player's path. R by default; rebindable under Controls. */
    private static KeyMapping ultimateKey;
    /** Opens the handbook. H by default. */
    private static KeyMapping guideKey;

    /** A vanilla HUD part drawn that many pixels higher (0: where it always is). */
    private static void lifted(net.minecraft.resources.Identifier vanilla, java.util.function.IntSupplier lift) {
        HudElementRegistry.replaceElement(vanilla, original -> (gui, delta) -> {
            int up = lift.getAsInt();
            if (up <= 0) {
                original.extractRenderState(gui, delta);
                return;
            }
            gui.pose().pushMatrix();
            gui.pose().translate(0, -up);
            original.extractRenderState(gui, delta);
            gui.pose().popMatrix();
        });
    }

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

        // Network ids of boss monsters only hold for one connection.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> {
                    BossLooks.clear();
                    MannedClient.reset();
                    HealthViewClient.clear();
                    PartyHealthHud.clear();
                    PetHud.clear();
                    DroneClient.clear();
                    NightKeys.setOwned(false);
                    PlaytestScreen.clear();
                    WarriorHud.clear();
                    RageFx.clear();
                    UltHud.update(null);   // else the swing ring stays on a server without the mod
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
                    } else if ("classpick_close".equals(payload.kind())) {
                        if (client.gui.screen() instanceof ClassPickScreen) {
                            client.setScreenAndShow(null);   // the war drum's round ended
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
                    } else if ("kit".equals(payload.kind())) {
                        // The kit page (update 6): the core window's tab draws it.
                        KitPages.receive(Json.fromJson(payload.json(), com.mki.nightsiege.core.KitPageView.class));
                    } else if ("hall".equals(payload.kind())) {
                        // The Ruhmeshalle (31 October 2026): the core window's tab draws it.
                        HallPages.receive(Json.fromJson(payload.json(), com.mki.nightsiege.core.HallView.class));
                    } else if ("spotter".equals(payload.kind())) {
                        com.mki.nightsiege.core.SpotterData data =
                                Json.fromJson(payload.json(), com.mki.nightsiege.core.SpotterData.class);
                        if (data != null && client.player != null) {
                            client.setScreenAndShow(new SpotterScreen(data));
                        }
                    } else if ("warleader".equals(payload.kind()) || "warleader_update".equals(payload.kind())) {
                        // The war leader's screen (Kriegsführer, 29 October 2026).
                        WarLeaderScreen.receive(Json.fromJson(payload.json(), com.mki.nightsiege.core.WarLeaderData.class),
                                "warleader".equals(payload.kind()));
                    } else if ("warleader_close".equals(payload.kind())) {
                        WarLeaderScreen.closeAll();
                    } else if ("dronerig".equals(payload.kind())) {
                        DroneClient.setRig(payload.json());
                    } else if ("drones".equals(payload.kind())) {
                        DroneClient.set(payload.json());
                    } else if ("healthview".equals(payload.kind())) {
                        HealthViewClient.set(payload.json());
                    } else if ("partyhealth".equals(payload.kind())) {
                        PartyHealthHud.set(payload.json());   // Tor 2: the healers' party list
                    } else if ("pets".equals(payload.kind())) {
                        PetHud.set(payload.json());   // Z2: every companion in a row with its life
                    } else if ("warrior".equals(payload.kind())) {
                        WarriorHud.set(payload.json());   // Kampfgeist and the combo chain (Simon 05.11.)
                    } else if ("rage".equals(payload.kind())) {
                        RageFx.set(payload.json());   // Tor 4.10: the berserker's Blutrausch, a red vignette
                    } else if ("towers".equals(payload.kind())) {
                        // The tower menu left with the remote (Tor 4b); an old server's menu is ignored.
                    } else if ("wheel".equals(payload.kind())) {
                        // The wheel is gone (27 October 2026: the ability bar instead); an old server's wheel is ignored.
                    } else if ("brewmenu".equals(payload.kind())) {
                        com.mki.nightsiege.core.BrewMenuData data =
                                Json.fromJson(payload.json(), com.mki.nightsiege.core.BrewMenuData.class);
                        if (data != null) {
                            client.setScreenAndShow(new BrewMenuScreen(data));
                        }
                    } else if ("bosses".equals(payload.kind())) {
                        BossLooks.set(payload.json());
                    } else if ("playtest_tree".equals(payload.kind())) {
                        CoreScreen.showOwnTree = true;   // the core window that comes next shows the own tree
                    } else if ("playtest".equals(payload.kind()) || "playtest_update".equals(payload.kind())) {
                        // The playtest window (28 October 2026): opened when asked, else only refreshed.
                        PlaytestScreen.receive(Json.fromJson(payload.json(), com.mki.nightsiege.core.PlaytestData.class),
                                "playtest".equals(payload.kind()));
                    } else if ("crate".equals(payload.kind())) {
                        com.mki.nightsiege.core.CrateRoll roll =
                                Json.fromJson(payload.json(), com.mki.nightsiege.core.CrateRoll.class);
                        if (roll != null) {
                            client.setScreenAndShow(new CrateScreen(roll));
                        }
                    } else if ("crate_all".equals(payload.kind())) {
                        // "Alle öffnen" (Tor 9.1 h): one overview of every crate opened.
                        com.mki.nightsiege.core.CrateSummary summary =
                                Json.fromJson(payload.json(), com.mki.nightsiege.core.CrateSummary.class);
                        if (summary != null) {
                            client.setScreenAndShow(new CrateSummaryScreen(summary));
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
                        if (state != null && state.tipsKept) {
                            state.tips = lastTips;   // Tor 3.6: unchanged tooltips are not sent again
                        } else if (state != null) {
                            lastTips = state.tips;
                        }
                        UltHud.update(state);
                        NightKeys.setOwned(state != null && state.keys);
                    });
                });
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ModContent.id("ultimate"),
                (gui, delta) -> UltHud.draw(gui, ultimateKey));
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ModContent.id("party_health"),
                (gui, delta) -> PartyHealthHud.draw(gui));   // Tor 2: the healers' party list
        // Kit HUD (Simon, 28 October 2026): while the kit is worn, its own panel takes the place of the
        // vanilla hotbar and bars; chat, item name, action bar and subtitles move up when they would
        // run into it.
        ClientTickEvents.END_CLIENT_TICK.register(KitHud::tick);
        // Tor 3.2c: at night the mod's short messages show above the action bar instead of in the chat.
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register(
                (text, overlay) -> overlay || !KitHud.toBar(text));
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ModContent.id("kit_hud"),
                (gui, delta) -> KitHud.draw(gui, ultimateKey));
        // Z2 (Simon 12.11.): every companion in its own row left of the bar, after the bar (it reads its place)
        HudElementRegistry.attachElementAfter(ModContent.id("kit_hud"), ModContent.id("pet_rows"),
                (gui, delta) -> PetHud.draw(gui));
        for (net.minecraft.resources.Identifier vanilla : List.of(VanillaHudElements.HOTBAR,
                VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL)) {
            HudElementRegistry.replaceElement(vanilla, original -> (gui, delta) -> {
                if (!KitHud.active()) {
                    original.extractRenderState(gui, delta);
                }
            });
        }
        // Tor 9b (Simon 09.11.: hearts and hunger "more vanilla"): with the kit, vanilla draws its own hearts
        // (rows, golden absorption, shaking), armour, hunger and air, only moved up above the action bar.
        for (net.minecraft.resources.Identifier vanilla : List.of(VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR,
                VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH)) {
            boolean hearts = vanilla.equals(VanillaHudElements.HEALTH_BAR);
            HudElementRegistry.replaceElement(vanilla, original -> (gui, delta) -> {
                if (KitHud.hidesVanillaBars()) {
                    return;
                }
                int up = KitHud.vanillaLift();
                if (hearts && KitHud.active()) {
                    KitHud.vanillaHearts++;   // Z3: with the kit in the own heart sprites (HeartSpriteMixin)
                }
                if (up <= 0) {
                    original.extractRenderState(gui, delta);
                    return;
                }
                gui.pose().pushMatrix();
                gui.pose().translate(0, -up);
                original.extractRenderState(gui, delta);
                gui.pose().popMatrix();
            });
        }
        lifted(VanillaHudElements.CHAT, KitHud::chatLift);
        lifted(VanillaHudElements.HELD_ITEM_TOOLTIP, KitHud::overlayLift);
        // Kit HUD v2 (29 October 2026): with the kit, the action bar's text shows in the middle arch instead.
        HudElementRegistry.replaceElement(VanillaHudElements.OVERLAY_MESSAGE, original -> (gui, delta) -> {
            if (!KitHud.active()) {
                original.extractRenderState(gui, delta);
            }
        });
        lifted(VanillaHudElements.SUBTITLES, KitHud::subtitleLift);

        KeyMapping.Category category = KeyMapping.Category.register(ModContent.id("main"));
        ultimateKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.nightsiege.ult", com.mki.nightsiege.core.AbilityKeys.Key.ULT.glfw(), category));   // X (Tor 3.1)
        guideKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.nightsiege.guide", GLFW.GLFW_KEY_H, category));
        GainClient.init(category);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(NightStatsScreen::tick);
        ClientTickEvents.END_CLIENT_TICK.register(WarLeaderScreen::tickClosed);   // the war leader's screen back after another one
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.mki.nightsiege.game.KitProjectile.TYPE, net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        GuideItem.opener = () -> Minecraft.getInstance().setScreenAndShow(new GuideScreen());
        // Kit gadgets and the remote: which key they need, with the player's own bindings.
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            com.mki.nightsiege.core.KeyHint hint = ModContent.STAFF != null && stack.is(ModContent.STAFF)
                    ? com.mki.nightsiege.core.KeyHint.STAFF
                    : stack.getItem() instanceof com.mki.nightsiege.game.HealerStaffItem
                    ? com.mki.nightsiege.core.KeyHint.HEALER_STAFF
                    : com.mki.nightsiege.core.KeyHint.forGadget(com.mki.nightsiege.game.Gadgets.of(stack));
            Component line = KeyHints.line(hint);
            if (line != null) {
                lines.add(line);
            }
            if (stack.getItem() instanceof com.mki.nightsiege.game.HealerStaffItem) {
                // Tor 4.7: what one bolt deals, how far and how often (the kit writes the damage on the staff).
                lines.add(Component.translatable("nightsiege.tooltip.healer_staff",
                        String.format(java.util.Locale.ROOT, "%.1f", com.mki.nightsiege.game.HealerStaffItem.boltDamage(stack)),
                        (int) com.mki.nightsiege.core.NightKit.HEALER_BOLT_RANGE,
                        String.format(java.util.Locale.ROOT, "%.2f", 20.0 / com.mki.nightsiege.core.NightKit.HEALER_BOLT_PAUSE))
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        });
        // Shield energy (Simon 26.10.): a short line on every kit shield.
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)
                    && com.mki.nightsiege.game.KitService.isKit(stack)) {
                lines.add(Component.translatable("nightsiege.tooltip.shield_energy").withStyle(ChatFormatting.AQUA));
            }
        });
        // The tanks' mace (Simon 05.11.): hard and slow, and no smash attack from a fall.
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (com.mki.nightsiege.game.KitService.isKitMace(stack)) {
                lines.add(Component.translatable("nightsiege.tooltip.kit_mace").withStyle(ChatFormatting.AQUA));
            }
        });
        // Event looks: hoods and rags over zombies and skeletons, the 3D gear on every event monster.
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register(
                NightSiegeClient::eventLayers);
        // Manned towers (Simon, 26 October 2026): the attack button fires the tower.
        ClientTickEvents.START_CLIENT_TICK.register(MannedClient::tick);
        // The drone of a fallen defender (Simon, 26 October 2026): own steering and a brass frame.
        ClientTickEvents.START_CLIENT_TICK.register(DroneClient::tick);
        ClientTickEvents.END_CLIENT_TICK.register(DroneClient::followRig);
        // The summoner's staff: held left click repeats its attack (27 October 2026).
        ClientTickEvents.START_CLIENT_TICK.register(StaffClient::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ModContent.id("drone"),
                (gui, delta) -> DroneClient.draw(gui));
        // The warrior's combo chain: four dots under the crosshair (Simon 05.11.).
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ModContent.id("warrior_combo"),
                (gui, delta) -> WarriorHud.draw(gui));
        // Tor 4.10: the red vignette of the Blutrausch, the swing ring at the crosshair, the axe's aim mark.
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ModContent.id("rage_vignette"),
                (gui, delta) -> RageFx.draw(gui));
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, ModContent.id("strike_ring"),
                (gui, delta) -> StrikeRing.draw(gui));
        ClientTickEvents.END_CLIENT_TICK.register(AxeAim::tick);
        // The wheel of Fähigkeit 3 is a HUD layer, so walking goes on while it is open (27 October 2026).
        // The wheel is gone (27 October 2026): the ability bar left of the off hand instead (UltHud).
        // The shield action (block + left click) is gone (Tor 3.1, "Nur Tasten"): the shield throw is on Q.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (guideKey.consumeClick()) {
                Minecraft.getInstance().setScreenAndShow(new GuideScreen());
            }
            while (ultimateKey.consumeClick()) {
                if (ClientPlayNetworking.canSend(CoreNetworking.CoreActionPayload.TYPE)) {
                    // X: the ultimate; the second ultimate of a crate is on R (GainClient, Tor 3.2b).
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
        // The iron golem only for the frost jarl's and ember colossus's glow and gear (Bosse 2); no event aura.
        boolean golem = type == net.minecraft.world.entity.EntityTypes.IRON_GOLEM;
        if (!eventMob(type) && !golem) {
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
            if (EVENT_MOBS.contains(type) || golem) {
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
