package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.TrapKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Good things between the sieges: the aurora on a quiet night and the trader
 * caravan that now and then stops at the core.
 */
public final class BonusEvents {

    private final Supplier<SiegeSave> save;
    private final Supplier<SiegeConfig> config;
    private final PerkService perks;
    private final Random random = new Random();
    private long rolledAuroraDay = Long.MIN_VALUE;
    private long rolledCaravanDay = Long.MIN_VALUE;
    private boolean aurora;
    /** Started by command: lasts until a siege starts or the command ends it. */
    private boolean forcedAurora;
    private int auroraTicks;

    public BonusEvents(Supplier<SiegeSave> save, Supplier<SiegeConfig> config, PerkService perks) {
        this.save = save;
        this.config = config;
        this.perks = perks;
    }

    public boolean auroraActive() {
        return aurora;
    }

    /**
     * @param siegeActive a siege is running right now
     * @param siegeDue    the countdown is done, a siege comes next
     */
    public void tick(MinecraftServer server, boolean siegeActive, boolean siegeDue) {
        boolean quiet = !siegeActive && !siegeDue;
        ServerLevel overworld = server.overworld();
        long clock = overworld.getOverworldClockTime();
        long day = Math.floorDiv(clock, 24000L);
        int time = (int) Math.floorMod(clock, 24000L);

        if (aurora) {
            boolean over = forcedAurora ? siegeActive : !quiet || time < 13000 || time >= 23000;
            if (over) {
                endAurora(server);
            } else {
                tickAurora(server);
            }
        } else if (time >= 13000 && time < 13200 && rolledAuroraDay != day) {
            rolledAuroraDay = day;
            if (quiet && random.nextDouble() < config.get().auroraChance) {
                startAurora(server, false);
            }
        }

        if (time >= 1000 && time < 1200 && rolledCaravanDay != day) {
            rolledCaravanDay = day;
            SiegeSave s = save.get();
            if (quiet && day - s.lastCaravanDay >= config.get().caravanMinDays
                    && random.nextDouble() < config.get().caravanChance) {
                if (spawnCaravan(server) != null) {
                    s.lastCaravanDay = day;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // aurora
    // ------------------------------------------------------------------

    public void startAurora(MinecraftServer server, boolean forced) {
        aurora = true;
        forcedAurora = forced;
        auroraTicks = 0;
        ServerLevel overworld = server.overworld();
        EventAtmosphere.setServer(SiegeManager.dimensionName(overworld), EventAtmosphere.AURORA, 400);
        for (ServerPlayer player : overworld.players()) {
            player.sendSystemMessage(Component.translatable("nightsiege.bonus.aurora_start")
                    .withStyle(ChatFormatting.GREEN));
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.AMBIENT, 0.8f, 0.6f);
        }
    }

    public void endAurora(MinecraftServer server) {
        if (!aurora) {
            return;
        }
        aurora = false;
        ServerLevel overworld = server.overworld();
        String dim = SiegeManager.dimensionName(overworld);
        if (EventAtmosphere.AURORA.equals(EventAtmosphere.serverEvent(dim))) {
            EventAtmosphere.setServer(dim, "", 400);
        }
    }

    /** Near a core the aurora heals and brings a point of experience every minute. */
    private void tickAurora(MinecraftServer server) {
        auroraTicks++;
        boolean point = auroraTicks % (config.get().auroraPointSeconds * 20) == 0;
        if (auroraTicks % 40 != 0 && !point) {
            return;
        }
        for (ServerPlayer player : server.overworld().players()) {
            if (!player.isAlive() || player.isSpectator() || !perks.inRange(player)) {
                continue;
            }
            if (auroraTicks % 40 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, false, true));
            }
            if (point && config.get().skillsEnabled) {
                save.get().skillsOf(player.getUUID().toString()).award(1);
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                        Component.translatable("nightsiege.bonus.aurora_point").withStyle(ChatFormatting.GREEN)));
            }
        }
    }

    // ------------------------------------------------------------------
    // caravan
    // ------------------------------------------------------------------

    /** Spawns the caravan near the first core. Returns the trader, or null if there was no room. */
    public WanderingTrader spawnCaravan(MinecraftServer server) {
        List<CoreRecord> cores = save.get().cores;
        if (cores == null || cores.isEmpty()) {
            return null;
        }
        CoreRecord core = cores.get(random.nextInt(cores.size()));
        ServerLevel world = SiegeManager.levelByName(server, core.dimension);
        if (world == null) {
            return null;
        }
        BlockPos anchor = new BlockPos(core.x, core.y, core.z);
        BlockPos spot = MobFactory.findSpawnSpot(world, anchor, config.get(), random, -8.0);
        if (spot == null) {
            return null;
        }
        Entity entity = create(world, "wandering_trader");
        if (!(entity instanceof WanderingTrader trader)) {
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        trader.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360f, 0f);
        trader.setDespawnDelay(24000);
        trader.setCustomName(Component.translatable("nightsiege.bonus.caravan_name").withStyle(ChatFormatting.GOLD));
        trader.setCustomNameVisible(true);
        trader.overrideOffers(offers(world));
        world.addFreshEntity(trader);
        for (int i = 0; i < 2; i++) {
            Entity llamaEntity = create(world, "trader_llama");
            if (llamaEntity instanceof TraderLlama llama) {
                llama.snapTo(spot.getX() + 0.5 + (i == 0 ? 1.5 : -1.5), spot.getY(), spot.getZ() + 0.5, 0f, 0f);
                llama.setDespawnDelay(24000);
                world.addFreshEntity(llama);
                llama.setLeashedTo(trader, true);
            } else if (llamaEntity != null) {
                llamaEntity.discard();
            }
        }
        world.playSound(null, spot, SoundEvents.WANDERING_TRADER_REAPPEARED, SoundSource.NEUTRAL, 1.0f, 1.0f);
        Component text = Component.translatable("nightsiege.bonus.caravan_arrived").withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : world.players()) {
            player.sendSystemMessage(text);
        }
        return trader;
    }

    private static Entity create(ServerLevel world, String id) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(id));
        return type == null ? null : type.create(world, EntitySpawnReason.EVENT);
    }

    /** Four traps, a dungeon map and four good things from the wider world. */
    private MerchantOffers offers(ServerLevel world) {
        MerchantOffers offers = new MerchantOffers();
        List<TrapKind> traps = new ArrayList<>(List.of(TrapKind.values()));
        Collections.shuffle(traps, random);
        for (TrapKind kind : traps.subList(0, Math.min(4, traps.size()))) {
            Item item = ModContent.TRAP_ITEMS.get(kind);
            if (item != null) {
                offers.add(offer(trapPrice(kind), new ItemStack(item, 2), 4));
            }
        }
        offers.add(offer(10, new ItemStack(DungeonContent.MAP), 2));

        List<MerchantOffer> goods = new ArrayList<>();
        addGood(goods, "golden_apple", 2, 8, 3);
        addGood(goods, "ender_pearl", 4, 6, 4);
        addGood(goods, "diamond", 1, 10, 3);
        addGood(goods, "experience_bottle", 8, 6, 4);
        addGood(goods, "totem_of_undying", 1, 40, 1);
        addGood(goods, "name_tag", 1, 6, 2);
        addGood(goods, "saddle", 1, 5, 2);
        addGood(goods, "spectral_arrow", 16, 4, 4);
        addGood(goods, "firework_rocket", 8, 3, 4);
        addGood(goods, "netherite_scrap", 1, 32, 1);
        ItemStack mending = mendingBook(world);
        if (!mending.isEmpty()) {
            goods.add(offer(30, mending, 1));
        }
        Collections.shuffle(goods, random);
        offers.addAll(goods.subList(0, Math.min(4, goods.size())));
        return offers;
    }

    private static void addGood(List<MerchantOffer> goods, String id, int count, int price, int uses) {
        Identifier key = Identifier.withDefaultNamespace(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            return;
        }
        goods.add(offer(price, new ItemStack(BuiltInRegistries.ITEM.getValue(key), count), uses));
    }

    private static MerchantOffer offer(int emeralds, ItemStack result, int uses) {
        Item emerald = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("emerald"));
        return new MerchantOffer(new ItemCost(emerald, Math.min(64, emeralds)), result, uses, 5, 0.05f);
    }

    private static ItemStack mendingBook(ServerLevel world) {
        try {
            Optional<Holder.Reference<Enchantment>> mending =
                    world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.MENDING);
            return mending.map(holder -> EnchantmentHelper.createBook(new EnchantmentInstance(holder, 1)))
                    .orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private static int trapPrice(TrapKind kind) {
        return switch (kind) {
            case SPIKE, SNARE -> 3;
            case MINE -> 4;
            case FLAME, POISON -> 5;
            case TURRET, TESLA -> 8;
        };
    }
}
