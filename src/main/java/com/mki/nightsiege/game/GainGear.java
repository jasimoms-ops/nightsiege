package com.mki.nightsiege.game;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.SkillValues;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The crate gains with a mechanic of their own (version B2): the items they put in
 * the kit, what those items do, the special arrows, and what keeps running in the
 * night (fleeing and lured monsters, oil, the shield lantern, homing arrows, the
 * bodyguard chain and the war banner).
 *
 * <p>Everything only works for defenders of a running night; the items only exist in
 * the night kit anyway.</p>
 */
public final class GainGear {

    // ------------------------------------------------------------------
    // night state
    // ------------------------------------------------------------------

    /** Monsters that may not pick a target until this game time (flash, horn, lure). */
    private static final Map<UUID, Long> CALM = new HashMap<>();
    /** Monsters running from a war horn: until when, and from where. */
    private static final Map<UUID, Away> FLEE = new HashMap<>();
    /** Monsters drawn to a lure arrow: until when, and to where. */
    private static final Map<UUID, Away> LURED = new HashMap<>();
    /** Players under the cloak: monsters do not see them until this game time. */
    private static final Map<UUID, Long> HIDDEN = new HashMap<>();
    /** Protected ally → the guardian who wears the bodyguard chain for them. */
    private static final Map<UUID, UUID> BODYGUARD = new HashMap<>();
    /** Guardians who used their trowel tonight. */
    private static final Set<UUID> TROWEL_USED = new HashSet<>();
    /** Engineers who used their spare battery tonight. */
    private static final Set<UUID> BATTERY_USED = new HashSet<>();
    /** Oil puddles of tonight. */
    private static final List<Oil> OIL = new ArrayList<>();
    /** The light block that follows each shield lantern. */
    private static final Map<UUID, BlockPos> LANTERN = new HashMap<>();
    /** Homing arrows and the tick they stop steering. */
    private static final Map<UUID, Long> HOMING = new HashMap<>();
    /** War banners standing tonight. */
    private static final List<Banner> BANNERS = new ArrayList<>();

    private record Away(long until, Vec3 point) {
    }

    private static final class Oil {
        final Vec3 center;
        final long until;
        long burnUntil;

        Oil(Vec3 center, long until) {
            this.center = center;
            this.until = until;
        }
    }

    private record Banner(String dimension, BlockPos pos, java.util.UUID owner) {
    }

    public static final double OIL_RADIUS = 3.0;
    public static final double BANNER_RADIUS = 10.0;
    public static final float BANNER_FACTOR = 1.1f;
    public static final float BODYGUARD_SHARE = 0.2f;

    private GainGear() {
    }

    /** Counts nights (and their ends), so per-night marks on traps can tell nights apart. */
    private static int nightId;

    public static int nightId() {
        return nightId;
    }

    /** A new night, or the end of one: everything of the last night is forgotten. */
    public static void reset() {
        nightId++;
        CALM.clear();
        FLEE.clear();
        LURED.clear();
        HIDDEN.clear();
        BODYGUARD.clear();
        TROWEL_USED.clear();
        BATTERY_USED.clear();
        OIL.clear();
        LANTERN.clear();
        HOMING.clear();
        BANNERS.clear();
        AlchemyZones.reset();
    }

    /**
     * The war banners a player has standing go (a new one is set, or his kit ends after a
     * dungeon or the war camp: every kit brings a fresh banner, and the old ones stood on).
     */
    public static void removeBanners(ServerPlayer player) {
        if (player == null || BANNERS.isEmpty()) {
            return;
        }
        java.util.Iterator<Banner> it = BANNERS.iterator();
        while (it.hasNext()) {
            Banner banner = it.next();
            if (!player.getUUID().equals(banner.owner())) {
                continue;
            }
            ServerLevel level = player.level().getServer() == null ? null
                    : SiegeManager.levelByName(player.level().getServer(), banner.dimension());
            if (level != null && TempBlocks.isTemp(level, banner.pos())) {
                TempBlocks.remove(level, banner.pos());
            }
            it.remove();
        }
    }

    /** End of the night: the night's blocks go, the copper golems go home. */
    public static void endNight(MinecraftServer server) {
        reset();
        TempBlocks.clearAll(server);
        CopperHelper.removeAll(server);
        SecondUlts.reset();
    }

    // ------------------------------------------------------------------
    // the kit
    // ------------------------------------------------------------------

    private static int lvl(KitService kits, PlayerSkills skills, SkillClass clazz, String id) {
        return kits.gainLevel(skills, clazz, id);
    }

    /** The B2 gadgets of a class's kit, added to the items the kit hands out. */
    public static void addKitItems(KitService kits, PlayerSkills skills, SkillClass clazz, String way, List<ItemStack> out) {
        // Balance update 2 (Simon 29.10.): crate gains with an ability of their own are on the gain keys
        // (GainKeys), the arrow kinds in the quiver; what is thrown, eaten or set down stays an item.
        if (lvl(kits, skills, clazz, "wurfspeere") > 0) {
            out.add(Gadgets.make(Items.IRON_SPEAR, "wurfspeer", 4));
        }
        if (lvl(kits, skills, clazz, "kriegsbanner") > 0) {
            out.add(Gadgets.make(Items.BANNER.red(), "kriegsbanner", 1));
        }
        if (clazz == SkillClass.WARRIOR) {
            int axes = lvl(kits, skills, clazz, "wurfbeile");
            if (axes > 0) {
                out.add(Gadgets.make(Items.IRON_AXE, "wurfbeil", 2 * axes));
            }
        } else if (clazz == SkillClass.GUARDIAN) {
            if (lvl(kits, skills, clazz, "barrikaden") > 0) {
                out.add(Gadgets.make(Items.OAK_FENCE, "barrikade", 3));
            }
        } else if (clazz == SkillClass.ENGINEER) {
            // The reusable flash grenade for every engineer (Simon, 28 October 2026; it was the
            // alchemist's alone since 26 October): one, it comes back after 7 s (Simon 29.10., was 15 s).
            out.add(Gadgets.make(Items.FIREWORK_STAR, "alchemie_blende", 1));
            SiegeManager m = SiegeManager.get();
            // The demolition expert's spyglass: the bird's-eye view (Simon, 26 October 2026).
            if (m != null && SkillTree.SUB_DEMOLITION.equals(m.skillTree().subclassOf(skills))) {
                out.add(Gadgets.make(Items.SPYGLASS, "feldstecher", 1));
            }
        }
    }

    /** Tower shield (blocks 270°) and light shield (full speed while blocking). */
    public static void decorateShield(ItemStack shield, boolean tower, boolean light) {
        if (tower) {
            BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
            if (blocks != null) {
                List<BlocksAttacks.DamageReduction> wider = new ArrayList<>();
                for (BlocksAttacks.DamageReduction r : blocks.damageReductions()) {
                    wider.add(new BlocksAttacks.DamageReduction(135f, r.type(), r.base(), r.factor()));
                }
                shield.set(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(blocks.blockDelaySeconds(),
                        blocks.disableCooldownScale(), wider, blocks.itemDamage(), blocks.bypassedBy(),
                        blocks.blockSound(), blocks.disableSound()));
            }
        }
        if (light) {
            shield.set(DataComponents.USE_EFFECTS, new UseEffects(true, true, 1.0f));
        }
    }

    // ------------------------------------------------------------------
    // using the gadgets
    // ------------------------------------------------------------------

    /** Gadgets that do their thing on a right click in the air (or on a block). */
    private static final Set<String> USE_ANYWHERE = Set.of("wurfspeer", "wurfbeil", "blendgranate", "alchemie_blende", "feldstecher", "oelfass",
            "kletterhaken", "kampfhorn", "tarnumhang", "signalhorn", "mauerkelle", "ersatzbatterie", "leibwaechter",
            "rueckrufpfeife", "geisterross", "futternapf", "totenglocke");
    /** The summoner's gadgets (their work is in {@link Companions#useGadget}). */
    private static final Set<String> SUMMONER_GADGETS = Set.of("rueckrufpfeife", "geisterross", "futternapf", "totenglocke");
    /** Gadgets that are set down on a block. */
    private static final Set<String> PLACE_ON_BLOCK = Set.of("kriegsbanner", "barrikade");

    /** Right click in the air. */
    public static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String id = Gadgets.of(stack);
        if (id.isEmpty() || !USE_ANYWHERE.contains(id)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server && level instanceof ServerLevel world) {
            use(server, world, stack, id);
        }
        return InteractionResult.SUCCESS;
    }

    /** Right click on a block: the placed gadgets go down, the others work as in the air. */
    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        String id = Gadgets.of(stack);
        if (id.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!USE_ANYWHERE.contains(id) && !PLACE_ON_BLOCK.contains(id)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server && level instanceof ServerLevel world) {
            if (PLACE_ON_BLOCK.contains(id)) {
                if (!night(server)) {
                    return InteractionResult.FAIL;
                }
                boolean placed = id.equals("kriegsbanner") ? placeBanner(server, world, hit) : placeBarricade(server, world, hit);
                if (placed && !server.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            } else {
                use(server, world, stack, id);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Right click on an entity: the bodyguard chain ties the guardian to an ally. */
    public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity target) {
        ItemStack stack = player.getItemInHand(hand);
        String gadget = Gadgets.of(stack);
        if (SUMMONER_GADGETS.contains(gadget)) {
            // The lead and the saddle must not leash or saddle the companion clicked: they work as in the air.
            if (player instanceof ServerPlayer server && level instanceof ServerLevel world) {
                use(server, world, stack, gadget);
            }
            return InteractionResult.SUCCESS;
        }
        if (!"leibwaechter".equals(gadget)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer guard && level instanceof ServerLevel world) {
            if (!night(guard)) {
                return InteractionResult.FAIL;
            }
            if (!(target instanceof ServerPlayer ally) || !SiegeManager.get().isDefending(ally)) {
                bar(guard, Component.translatable("nightsiege.gadget.leibwaechter.not_ally"));
                return InteractionResult.FAIL;
            }
            link(guard, world, ally);
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean night(ServerPlayer player) {
        SiegeManager m = SiegeManager.get();
        if (m != null && m.isDefending(player)) {
            return true;
        }
        bar(player, Component.translatable("nightsiege.gadget.night_only"));
        return false;
    }

    private static void use(ServerPlayer player, ServerLevel level, ItemStack stack, String id) {
        if (!night(player)) {
            return;
        }
        if (player.getCooldowns().isOnCooldown(stack)) {
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        switch (id) {
            case "wurfspeer" -> throwIt(player, level, stack, KitProjectile.SPEAR, 1.9f, creative);
            case "wurfbeil" -> throwIt(player, level, stack, KitProjectile.AXE, 1.6f, creative);
            case "blendgranate" -> throwIt(player, level, stack, KitProjectile.FLASH, 1.4f, creative);
            case "alchemie_blende" -> throwAlchemistFlash(player, level, stack);
            case "feldstecher" -> {
                SiegeManager m = SiegeManager.get();
                if (m != null) {
                    m.kits().traps().openSpotter(player);
                }
            }
            case "oelfass" -> throwIt(player, level, stack, KitProjectile.OIL, 1.0f, creative);
            case "leibwaechter" -> bar(player, Component.translatable("nightsiege.gadget.leibwaechter.hint"));
            case "ersatzbatterie" -> {
                if (battery(player, level) && !creative) {
                    stack.shrink(1);
                }
            }
            default -> {
                // An item from before the gain keys (balance update 2): it still does its thing.
                int pause = useAbility(player, level, id);
                if (pause > 0) {
                    player.getCooldowns().addCooldown(stack, pause);
                }
            }
        }
    }

    /**
     * A gain ability (balance update 2, Simon 29.10.: the crate gains with an ability of their own
     * are on the gain keys, no item in the kit): does what its item did. Returns the pause in ticks,
     * 0 for none, -1 when it did nothing (nothing in sight, used up tonight).
     */
    public static int useAbility(ServerPlayer player, ServerLevel level, String id) {
        return switch (id) {
            case "kletterhaken" -> grapple(player, level);
            case "kampfhorn" -> warHorn(player, level);
            case "tarnumhang" -> cloak(player, level);
            case "signalhorn" -> signal(player, level);
            case "mauerkelle" -> trowel(player, level) ? 0 : -1;
            case "ersatzbatterie" -> battery(player, level) ? 0 : -1;
            case "leibwaechter" -> bodyguardKey(player, level);
            case "oelfass" -> {
                throwFree(player, level, Gadgets.make(Items.INK_SAC, "oelfass", 1), KitProjectile.OIL, 1.0f);
                yield 20;
            }
            case "rueckrufpfeife", "geisterross", "futternapf", "totenglocke" ->
                    Companions.get() == null ? -1 : Companions.get().useGadgetKey(player, level, id);
            default -> -1;
        };
    }

    /** True when a once-a-night gain ability was used tonight (the trowel, the spare battery). */
    public static boolean usedTonight(ServerPlayer player, String id) {
        return switch (id) {
            case "mauerkelle" -> TROWEL_USED.contains(player.getUUID());
            case "ersatzbatterie" -> BATTERY_USED.contains(player.getUUID());
            default -> false;
        };
    }

    /** A thrown thing that is not taken from a stack (the oil barrel on its key). */
    private static void throwFree(ServerPlayer player, ServerLevel level, ItemStack look, String kind, float speed) {
        KitProjectile projectile = new KitProjectile(level, player, look, kind);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, speed, 0.8f);
        level.addFreshEntity(projectile);
        level.playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    /**
     * The bodyguard chain on its key: ties the guardian to the defender he looks at (up to 16
     * blocks), else to the nearest one within 6 blocks.
     */
    private static int bodyguardKey(ServerPlayer guard, ServerLevel level) {
        SiegeManager m = SiegeManager.get();
        if (m == null) {
            return -1;
        }
        Vec3 eye = guard.getEyePosition();
        Vec3 look = guard.getLookAngle();
        ServerPlayer best = null;
        double bestDot = 0.96;
        ServerPlayer near = null;
        double nearSq = 36.0;
        for (ServerPlayer other : level.players()) {
            if (other == guard || !other.isAlive() || other.isSpectator() || !m.isDefending(other)) {
                continue;
            }
            Vec3 to = other.position().add(0, other.getBbHeight() * 0.6, 0).subtract(eye);
            double dist = to.length();
            if (dist < 16.0 && dist > 1.0e-3) {
                double dot = to.scale(1.0 / dist).dot(look);
                if (dot > bestDot && guard.hasLineOfSight(other)) {
                    bestDot = dot;
                    best = other;
                }
            }
            double sq = other.distanceToSqr(guard);
            if (sq < nearSq) {
                nearSq = sq;
                near = other;
            }
        }
        ServerPlayer ally = best != null ? best : near;
        if (ally == null) {
            bar(guard, Component.translatable("nightsiege.gadget.leibwaechter.not_ally"));
            return -1;
        }
        link(guard, level, ally);
        return 40;
    }

    /** The bodyguard chain: the guardian takes a share of what the ally takes. */
    private static void link(ServerPlayer guard, ServerLevel world, ServerPlayer ally) {
        BODYGUARD.values().removeIf(g -> g.equals(guard.getUUID()));
        BODYGUARD.put(ally.getUUID(), guard.getUUID());
        world.playSound(null, guard.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 0.8f);
        bar(guard, Component.translatable("nightsiege.gadget.leibwaechter.linked", ally.getDisplayName()));
        ally.sendSystemMessage(Component.translatable("nightsiege.gadget.leibwaechter.ally", guard.getDisplayName())
                .withStyle(ChatFormatting.AQUA));
    }

    private static void throwIt(ServerPlayer player, ServerLevel level, ItemStack stack, String kind, float speed, boolean creative) {
        KitProjectile projectile = new KitProjectile(level, player, stack.copyWithCount(1), kind);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, speed, 0.8f);
        level.addFreshEntity(projectile);
        if (kind.equals(KitProjectile.SPEAR) || kind.equals(KitProjectile.AXE)) {
            level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1f, 1.1f);
        } else {
            level.playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8f, 0.6f);
        }
        player.getCooldowns().addCooldown(stack, 10);
        if (!creative) {
            stack.shrink(1);
        }
    }

    /**
     * The engineer's flash grenade (every engineer since 28 October 2026): thrown like the other,
     * but never used up (7 s pause since Simon 29.10.; the crate gain "Blendgranaten" is gone).
     */
    private static void throwAlchemistFlash(ServerPlayer player, ServerLevel level, ItemStack stack) {
        KitProjectile projectile = new KitProjectile(level, player, stack.copyWithCount(1), KitProjectile.FLASH_ALCHEMIST);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 1.4f, 0.8f);
        level.addFreshEntity(projectile);
        level.playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8f, 0.6f);
        double pause = SkillValues.get("potion_tower", "flashpause", 140);
        player.getCooldowns().addCooldown(stack, (int) Math.max(40, Math.round(pause)));
    }

    /** Grappling hook: pulls the player to the block they look at, 20 s pause. */
    private static int grapple(ServerPlayer player, ServerLevel level) {
        HitResult hit = player.pick(24.0, 0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            bar(player, Component.translatable("nightsiege.gadget.kletterhaken.miss"));
            return -1;
        }
        Vec3 to = hit.getLocation().subtract(player.position());
        double dist = to.length();
        Vec3 pull = to.normalize().scale(Math.min(2.4, 0.6 + dist * 0.16)).add(0, 0.45, 0);
        player.setDeltaMovement(pull);
        player.hurtMarked = true;
        player.resetFallDistance();
        drawLine(level, player.getEyePosition(), hit.getLocation(), ParticleTypes.CRIT);
        level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1f, 0.7f);
        return 400;
    }

    /** War horn: monsters within 8 blocks run away for 3 s, 60 s pause. */
    private static int warHorn(ServerPlayer player, ServerLevel level) {
        long until = level.getGameTime() + 60;
        for (Mob mob : enemies(level, player.position(), 8.0)) {
            mob.setTarget(null);
            CALM.put(mob.getUUID(), until);
            FLEE.put(mob.getUUID(), new Away(until, player.position()));
        }
        level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1.2f, 1.3f);
        level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.2, player.getZ(), 1, 0, 0, 0, 0);
        return 1200;
    }

    /** Cloak: 3 s invisible, monsters lose the player, 45 s pause. */
    private static int cloak(ServerPlayer player, ServerLevel level) {
        long until = level.getGameTime() + 60;
        HIDDEN.put(player.getUUID(), until);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, true));
        for (Mob mob : enemies(level, player.position(), 32.0)) {
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 1.4f);
        return 900;
    }

    /** Signal horn: allies see the guardian through walls for 10 s, 60 s pause. */
    private static int signal(ServerPlayer player, ServerLevel level) {
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false, true));
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2f, 1f);
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1f, 1f);
        return 1200;
    }

    /** Trowel: broken base blocks within 5 blocks come back, once a night. */
    private static boolean trowel(ServerPlayer player, ServerLevel level) {
        if (!TROWEL_USED.add(player.getUUID())) {
            bar(player, Component.translatable("nightsiege.gadget.once_a_night"));
            return false;
        }
        int put = SiegeManager.get().restoreBaseNear(player.blockPosition(), 5.0);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7f, 1.4f);
        bar(player, Component.translatable("nightsiege.gadget.mauerkelle.done", put));
        return true;
    }

    /** Spare battery: fills the remote, once a night. */
    private static boolean battery(ServerPlayer player, ServerLevel level) {
        if (!BATTERY_USED.add(player.getUUID())) {
            bar(player, Component.translatable("nightsiege.gadget.once_a_night"));
            return false;
        }
        SiegeManager.get().kits().traps().fillRemote(player);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(), 25, 0.3, 0.5, 0.3, 0.1);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
        return true;
    }

    /** The war banner stands on the clicked block (or on its side as a wall banner). */
    private static boolean placeBanner(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
        Direction face = hit.getDirection();
        BlockPos pos = hit.getBlockPos().relative(face);
        BlockState state;
        if (face == Direction.UP) {
            int rotation = Mth.floor((player.getYRot() + 180.0f) * 16.0f / 360.0f + 0.5f) & 15;
            state = Blocks.BANNER.red().defaultBlockState().setValue(BannerBlock.ROTATION, rotation);
        } else if (face.getAxis().isHorizontal()) {
            state = Blocks.WALL_BANNER.red().defaultBlockState().setValue(WallBannerBlock.FACING, face);
        } else {
            return false;
        }
        if (!state.canSurvive(level, pos) || !TempBlocks.place(level, pos, state)) {
            bar(player, Component.translatable("nightsiege.gadget.no_room"));
            return false;
        }
        removeBanners(player);   // one war banner per player: the last one goes (Simon 29.10.: "Kriegsbanner dupliziert sich")
        BANNERS.add(new Banner(SiegeManager.dimensionName(level), pos, player.getUUID()));
        level.playSound(null, pos, SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 1.5, 0.5, 1.5, 0);
        SiegeManager.get().broadcastDefenders(Component.translatable("nightsiege.gadget.kriegsbanner.placed",
                player.getDisplayName()).withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** A barricade: three fences across, two high, in front of the clicked face. */
    private static boolean placeBarricade(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
        BlockPos base = hit.getBlockPos().relative(hit.getDirection());
        Direction side = player.getDirection().getClockWise();
        List<BlockPos> set = new ArrayList<>();
        for (int h = 0; h < 2; h++) {
            for (int k = -1; k <= 1; k++) {
                BlockPos pos = base.relative(side, k).above(h);
                if (TempBlocks.place(level, pos, Blocks.OAK_FENCE.defaultBlockState())) {
                    set.add(pos);
                }
            }
        }
        if (set.isEmpty()) {
            bar(player, Component.translatable("nightsiege.gadget.no_room"));
            return false;
        }
        for (BlockPos pos : set) {
            BlockState shaped = Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos);
            level.setBlock(pos, shaped, Block.UPDATE_ALL);
            TempBlocks.remember(level, pos, shaped);
        }
        level.playSound(null, base, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------------
    // thrown things
    // ------------------------------------------------------------------

    /** A kit projectile hit something: an entity, or a block (then {@code block} is set). */
    public static void onProjectileHit(ServerLevel level, KitProjectile projectile, Entity hit, Vec3 at, BlockHitResult block) {
        Entity owner = projectile.getOwner();
        ServerPlayer thrower = owner instanceof ServerPlayer p ? p : null;
        switch (projectile.kind()) {
            case KitProjectile.AXE -> {
                if (hit instanceof LivingEntity victim && victim instanceof Enemy) {
                    victim.hurtServer(level, level.damageSources().thrown(projectile, owner), 8.0f);
                    level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.8f);
                }
            }
            case KitProjectile.SPEAR -> {
                if (hit instanceof LivingEntity victim && victim instanceof Enemy) {
                    victim.hurtServer(level, level.damageSources().thrown(projectile, owner), 5.0f);
                    Vec3 push = projectile.getDeltaMovement().multiply(1, 0, 1).normalize();
                    victim.setDeltaMovement(victim.getDeltaMovement().add(push.scale(1.6)).add(0, 0.4, 0));
                    victim.hurtMarked = true;
                }
            }
            case KitProjectile.FLASH -> flash(level, at);
            case KitProjectile.FLASH_ALCHEMIST -> flashAlchemist(level, at);
            case KitProjectile.ZONE -> AlchemyZones.land(level, projectile,
                    block != null ? new Vec3(at.x, block.getBlockPos().relative(block.getDirection()).getY(), at.z) : at);
            case KitProjectile.ACID -> {
                SiegeManager m = SiegeManager.get();
                if (m != null) {
                    m.kits().traps().onAcidLanded(level, projectile,
                            block != null ? new Vec3(at.x, block.getBlockPos().relative(block.getDirection()).getY(), at.z) : at);
                }
            }
            case KitProjectile.OIL -> {
                Vec3 spot = block != null ? Vec3.atBottomCenterOf(block.getBlockPos().relative(block.getDirection())) : at;
                OIL.add(new Oil(spot, level.getGameTime() + 1200));
                level.playSound(null, BlockPos.containing(spot), SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1f, 0.6f);
                level.sendParticles(ParticleTypes.SQUID_INK, spot.x, spot.y + 0.2, spot.z, 40, 1.5, 0.1, 1.5, 0.02);
            }
            case KitProjectile.LURE -> lure(level, at, thrower);
            case KitProjectile.SHELL, KitProjectile.BREW -> {
                SiegeManager m = SiegeManager.get();
                if (m != null) {
                    m.kits().traps().onTowerShot(level, projectile, hit, at);
                }
            }
            default -> {
            }
        }
    }

    /** Flash grenade: monsters within 4 blocks lose their target for 2 s. */
    private static void flash(ServerLevel level, Vec3 at) {
        long until = level.getGameTime() + 40;
        for (Mob mob : enemies(level, at, 4.0)) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            CALM.put(mob.getUUID(), until);
            mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, false));
        }
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, 60, 0.6, 0.6, 0.6, 0.25);
        level.sendParticles(ParticleTypes.WHITE_SMOKE, at.x, at.y + 0.3, at.z, 30, 1.2, 0.5, 1.2, 0.02);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.5f, 1.4f);
    }

    /**
     * The alchemist's flash grenade (Simon, 26 October 2026): monsters within 5 blocks are
     * blinded for 4 s and lose their target, and may not pick a new one meanwhile.
     */
    private static void flashAlchemist(ServerLevel level, Vec3 at) {
        double radius = SkillValues.get("potion_tower", "flashradius", 5.0);
        int ticks = (int) Math.round(SkillValues.get("potion_tower", "flashtime", 80));
        blind(level, at, radius, ticks, true);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, 80, 0.8, 0.8, 0.8, 0.3);
        level.sendParticles(ParticleTypes.WHITE_SMOKE, at.x, at.y + 0.3, at.z, 40, radius * 0.35, 0.5, radius * 0.35, 0.02);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.8f, 1.6f);
    }

    /**
     * Monsters around a spot lose their target and may not pick a new one for a while
     * (the alchemist's flash grenade, the demolition expert's smoke shells).
     */
    public static void blind(ServerLevel level, Vec3 at, double radius, int ticks, boolean blindness) {
        long until = level.getGameTime() + ticks;
        for (Mob mob : enemies(level, at, radius)) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            CALM.put(mob.getUUID(), until);
            if (blindness) {
                mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, true));
            }
            mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 1, false, false));
        }
    }

    /** A patch of burning ground (the fire salvo): monsters in it catch fire until it goes out. */
    public static void burnPatch(ServerLevel level, Vec3 center, long ticks) {
        long now = level.getGameTime();
        Oil oil = new Oil(center, now + ticks);
        oil.burnUntil = now + ticks;
        OIL.add(oil);
        level.playSound(null, BlockPos.containing(center), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.2f, 0.7f);
    }

    /** Monsters near a spot run away from it for a while and attack nobody (the soulcatcher's fright). */
    public static int scare(ServerLevel level, Vec3 center, double radius, int ticks) {
        long until = level.getGameTime() + ticks;
        int n = 0;
        for (Mob mob : enemies(level, center, radius)) {
            mob.setTarget(null);
            CALM.put(mob.getUUID(), until);
            FLEE.put(mob.getUUID(), new Away(until, center));
            n++;
        }
        return n;
    }

    /** Lure arrow (archer's second ult): monsters within 8 blocks go for the spot for 5 s. */
    private static void lure(ServerLevel level, Vec3 at, ServerPlayer thrower) {
        long until = level.getGameTime() + 100;
        for (Mob mob : enemies(level, at, 8.0)) {
            mob.setTarget(null);
            CALM.put(mob.getUUID(), until);
            LURED.put(mob.getUUID(), new Away(until, at));
            mob.getNavigation().moveTo(at.x, at.y, at.z, 1.3);
        }
        level.sendParticles(ParticleTypes.NOTE, at.x, at.y + 1, at.z, 12, 1.0, 0.6, 1.0, 1.0);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, at.x, at.y + 0.5, at.z, 10, 1.5, 0.5, 1.5, 0);
        level.playSound(null, BlockPos.containing(at), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.5f, 1.6f);
    }

    // ------------------------------------------------------------------
    // special arrows
    // ------------------------------------------------------------------

    /** An arrow came into the world: special arrows get their tag, homing arrows start steering. */
    public static void onArrowLoad(Entity entity) {
        if (!(entity instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof ServerPlayer player)
                || arrow.entityTags().contains("nightsiege_turret")) {
            return;
        }
        String kind = Gadgets.arrowKind(arrow.getPickupItemStackOrigin());
        if (kind.isEmpty() && !arrow.entityTags().contains("ns_shot")) {
            // The quiver (balance update 2): a fresh shot takes the picked kind. The tag keeps an arrow
            // that loads again with its chunk from taking another one.
            arrow.addTag("ns_shot");
            kind = takeQuiverShot(player, arrow.level().getGameTime());
        }
        if (!kind.isEmpty()) {
            arrow.addTag("ns_arrow_" + kind);
        }
        if (CrateEffects.gainLevel(player, "lenkpfeile") > 0) {
            HOMING.put(arrow.getUUID(), arrow.level().getGameTime() + 80L);
        }
    }

    /** A player's arrow struck a block (from the arrow mixin). */
    public static void onArrowHitBlock(AbstractArrow arrow, BlockHitResult hit) {
        if (!(arrow.level() instanceof ServerLevel level)) {
            return;
        }
        String kind = kindOf(arrow);
        if (kind.equals("light")) {
            BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
            TempBlocks.place(level, pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
            level.sendParticles(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
            arrow.discard();
        } else if (kind.equals("climb")) {
            Direction face = hit.getDirection();
            if (face.getAxis().isHorizontal()) {
                BlockPos start = hit.getBlockPos().relative(face);
                BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, face);
                int placed = 0;
                for (int i = 0; i < 6; i++) {
                    BlockPos pos = start.below(i);
                    if (!ladder.canSurvive(level, pos) || !TempBlocks.place(level, pos, ladder)) {
                        break;
                    }
                    placed++;
                }
                if (placed > 0) {
                    level.playSound(null, start, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1f, 1.2f);
                    arrow.discard();
                }
            }
        } else if (kind.equals("storm")) {
            storm(level, hit.getLocation(), arrow.getOwner());
            arrow.discard();
        }
    }

    /** The kind of a player's special arrow (light, climb, storm), read from its tag or its item. */
    private static String kindOf(AbstractArrow arrow) {
        for (String k : new String[]{"light", "climb", "storm", "spectral"}) {
            if (arrow.entityTags().contains("ns_arrow_" + k)) {
                return k;
            }
        }
        if (!(arrow.getOwner() instanceof Player)) {
            return "";
        }
        return Gadgets.arrowKind(arrow.getPickupItemStackOrigin());
    }

    /** A player's arrow hit a monster: the spectral kind from the quiver makes it glow for 10 s. */
    public static void onArrowHitEntity(AbstractArrow arrow, LivingEntity victim) {
        if (victim != null && arrow.entityTags().contains("ns_arrow_spectral")) {
            victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
        }
    }

    /** For the gametest. */
    public static String debugArrow(AbstractArrow arrow) {
        return "tags=" + arrow.entityTags() + " kind=" + kindOf(arrow) + " owner=" + arrow.getOwner()
                + " item=" + arrow.getPickupItemStackOrigin() + " ground=" + ((com.mki.nightsiege.mixin.ArrowAccessor) arrow).nightsiege$isInGround();
    }

    /** Storm arrow: monsters within 3 blocks whirl up. */
    private static void storm(ServerLevel level, Vec3 at, Entity owner) {
        for (Mob mob : enemies(level, at, 3.0)) {
            Vec3 away = mob.position().subtract(at).multiply(1, 0, 1);
            Vec3 push = away.lengthSqr() < 1e-4 ? Vec3.ZERO : away.normalize().scale(0.35);
            mob.setDeltaMovement(push.x, 1.0, push.z);
            mob.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, BlockPos.containing(at), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1f, 1f);
    }

    // ------------------------------------------------------------------
    // damage
    // ------------------------------------------------------------------

    /** Damage factors: the war banner for attackers, the bodyguard chain for victims. */
    public static float damageFactor(LivingEntity victim, DamageSource source) {
        float factor = 1f;
        if (source.getEntity() instanceof ServerPlayer attacker && victim instanceof Enemy && nearBanner(attacker)) {
            factor *= BANNER_FACTOR;
        }
        if (victim instanceof ServerPlayer ally && guardOf(ally) != null) {
            factor *= 1f - BODYGUARD_SHARE;
        }
        return factor;
    }

    /** After damage: storm arrows burst on the monster, the bodyguard takes his share. */
    public static void afterDamage(LivingEntity victim, DamageSource source, float taken) {
        if (source.getDirectEntity() instanceof AbstractArrow arrow && kindOf(arrow).equals("storm")
                && victim.level() instanceof ServerLevel level) {
            storm(level, victim.position(), arrow.getOwner());
        }
        if (source.getDirectEntity() instanceof AbstractArrow arrow && victim instanceof Enemy) {
            onArrowHitEntity(arrow, victim);   // the quiver's spectral arrows (balance update 2)
        }
        if (taken > 0 && victim instanceof ServerPlayer ally && victim.level() instanceof ServerLevel level) {
            ServerPlayer guard = guardOf(ally);
            if (guard != null && source.getEntity() != guard) {
                float share = taken * BODYGUARD_SHARE / (1f - BODYGUARD_SHARE);
                guard.hurtServer(level, level.damageSources().magic(), share);
            }
        }
    }

    private static ServerPlayer guardOf(ServerPlayer ally) {
        UUID id = BODYGUARD.get(ally.getUUID());
        if (id == null || !(ally.level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer guard = level.getServer().getPlayerList().getPlayer(id);
        SiegeManager m = SiegeManager.get();
        if (guard == null || m == null || !m.isDefending(guard) || !m.isDefending(ally)
                || guard.level() != ally.level() || guard.distanceToSqr(ally) > 32 * 32) {
            return null;
        }
        return guard;
    }

    private static boolean nearBanner(ServerPlayer player) {
        if (BANNERS.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        String dim = SiegeManager.dimensionName(level);
        for (Banner banner : BANNERS) {
            if (banner.dimension().equals(dim)
                    && player.distanceToSqr(Vec3.atCenterOf(banner.pos())) <= BANNER_RADIUS * BANNER_RADIUS) {
                BlockState state = level.getBlockState(banner.pos());
                if (state.is(Blocks.BANNER.red()) || state.is(Blocks.WALL_BANNER.red())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // targets
    // ------------------------------------------------------------------

    /** False while a monster is calmed, lured or fleeing, or the player is cloaked. */
    public static boolean mayTarget(Mob mob, LivingEntity target) {
        if (CALM.isEmpty() && HIDDEN.isEmpty()) {
            return true;
        }
        long now = mob.level().getGameTime();
        Long calm = CALM.get(mob.getUUID());
        if (calm != null && now < calm) {
            return false;
        }
        Long hidden = target == null ? null : HIDDEN.get(target.getUUID());
        return hidden == null || now >= hidden;
    }

    // ------------------------------------------------------------------
    // ticking
    // ------------------------------------------------------------------

    /** Every tick of a running night. */
    public static void tick(ServerLevel level, List<ServerPlayer> upright) {
        long now = level.getGameTime();
        tickHoming(level, now);
        if (now % 5 == 0) {
            tickMonsters(level, now);
            tickLanterns(level, upright);
            CopperHelper.tick(level, upright);
        }
        if (now % 10 == 0) {
            tickOil(level, now);
        }
        if (now % 20 == 0) {
            tickChains(level);
        }
        if (now % 200 == 0) {
            TempBlocks.flush();
        }
    }

    private static void tickHoming(ServerLevel level, long now) {
        if (HOMING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Long>> it = HOMING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Entity e = level.getEntity(entry.getKey());
            if (!(e instanceof AbstractArrow arrow) || arrow.isRemoved() || now > entry.getValue()
                    || ((com.mki.nightsiege.mixin.ArrowAccessor) arrow).nightsiege$isInGround()) {
                it.remove();
                continue;
            }
            Vec3 v = arrow.getDeltaMovement();
            double speed = v.length();
            if (speed < 0.3) {
                continue;
            }
            Vec3 dir = v.scale(1.0 / speed);
            Mob best = null;
            double bestScore = Double.MAX_VALUE;
            for (Mob mob : enemies(level, arrow.position().add(dir.scale(5)), 7.0)) {
                Vec3 to = mob.getBoundingBox().getCenter().subtract(arrow.position());
                double d = to.length();
                if (d < 0.5 || to.scale(1.0 / d).dot(dir) < 0.6) {
                    continue;
                }
                if (d < bestScore) {
                    bestScore = d;
                    best = mob;
                }
            }
            if (best != null) {
                Vec3 to = best.getBoundingBox().getCenter().subtract(arrow.position()).normalize();
                Vec3 steered = dir.scale(0.82).add(to.scale(0.18)).normalize().scale(speed);
                arrow.setDeltaMovement(steered);
                arrow.hurtMarked = true;
            }
        }
    }

    private static void tickMonsters(ServerLevel level, long now) {
        CALM.values().removeIf(until -> now >= until);
        HIDDEN.values().removeIf(until -> now >= until);
        moveAll(level, now, FLEE, true);
        moveAll(level, now, LURED, false);
        if (!HIDDEN.isEmpty()) {
            for (UUID id : HIDDEN.keySet()) {
                ServerPlayer hidden = level.getServer().getPlayerList().getPlayer(id);
                if (hidden == null) {
                    continue;
                }
                for (Mob mob : enemies(level, hidden.position(), 32.0)) {
                    if (mob.getTarget() == hidden) {
                        mob.setTarget(null);
                    }
                }
            }
        }
    }

    private static void moveAll(ServerLevel level, long now, Map<UUID, Away> map, boolean flee) {
        Iterator<Map.Entry<UUID, Away>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Away> entry = it.next();
            Entity e = level.getEntity(entry.getKey());
            if (!(e instanceof Mob mob) || !mob.isAlive() || now >= entry.getValue().until()) {
                it.remove();
                continue;
            }
            mob.setTarget(null);
            Vec3 point = entry.getValue().point();
            if (flee) {
                Vec3 away = mob.position().subtract(point).multiply(1, 0, 1);
                Vec3 dir = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                Vec3 goal = mob.position().add(dir.scale(8));
                mob.getNavigation().moveTo(goal.x, mob.getY(), goal.z, 1.5);
            } else if (mob.position().distanceToSqr(point) > 2.0) {
                mob.getNavigation().moveTo(point.x, point.y, point.z, 1.3);
            }
        }
    }

    private static void tickLanterns(ServerLevel level, List<ServerPlayer> upright) {
        Set<UUID> carrying = new HashSet<>();
        for (ServerPlayer player : upright) {
            if (CrateEffects.gainLevel(player, "schildlaterne") <= 0 || player.level() != level) {
                continue;
            }
            carrying.add(player.getUUID());
            BlockPos head = player.blockPosition().above();
            BlockPos old = LANTERN.get(player.getUUID());
            if (head.equals(old)) {
                continue;
            }
            if (old != null && TempBlocks.isTemp(level, old)) {
                TempBlocks.remove(level, old);
            }
            LANTERN.remove(player.getUUID());
            if (level.getBlockState(head).isAir()
                    && TempBlocks.place(level, head, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 14))) {
                LANTERN.put(player.getUUID(), head);
            }
        }
        Iterator<Map.Entry<UUID, BlockPos>> it = LANTERN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BlockPos> entry = it.next();
            if (!carrying.contains(entry.getKey())) {
                if (TempBlocks.isTemp(level, entry.getValue())) {
                    TempBlocks.remove(level, entry.getValue());
                }
                it.remove();
            }
        }
    }

    private static void tickOil(ServerLevel level, long now) {
        Iterator<Oil> it = OIL.iterator();
        DustParticleOptions dark = new DustParticleOptions(0x1A1410, 1.6f);
        while (it.hasNext()) {
            Oil oil = it.next();
            boolean burning = now < oil.burnUntil;
            if (now >= oil.until && !burning || oil.burnUntil > 0 && !burning) {
                it.remove();
                continue;
            }
            List<Mob> inside = new ArrayList<>();
            for (Mob mob : enemies(level, oil.center, OIL_RADIUS)) {
                if (Math.abs(mob.getY() - oil.center.y) < 2.0) {
                    inside.add(mob);
                }
            }
            if (!burning) {
                for (Mob mob : inside) {
                    if (mob.isOnFire()) {
                        oil.burnUntil = now + 100;
                        burning = true;
                        level.playSound(null, BlockPos.containing(oil.center), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.2f, 0.7f);
                        break;
                    }
                }
            }
            if (burning) {
                for (Mob mob : inside) {
                    mob.igniteForSeconds(6f);
                }
                level.sendParticles(ParticleTypes.FLAME, oil.center.x, oil.center.y + 0.2, oil.center.z, 30, 1.6, 0.2, 1.6, 0.02);
            } else {
                for (Mob mob : inside) {
                    mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2, false, false));
                }
                level.sendParticles(dark, oil.center.x, oil.center.y + 0.1, oil.center.z, 14, 1.5, 0.02, 1.5, 0);
            }
        }
    }

    private static void tickChains(ServerLevel level) {
        if (BODYGUARD.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, UUID> entry : BODYGUARD.entrySet()) {
            ServerPlayer ally = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (ally == null || ally.level() != level) {
                continue;
            }
            ServerPlayer guard = guardOf(ally);
            if (guard != null) {
                drawLine(level, guard.position().add(0, 1, 0), ally.position().add(0, 1, 0), ParticleTypes.WAX_ON);
            }
        }
    }

    // ------------------------------------------------------------------
    // the quiver (key G) and the double jump
    // ------------------------------------------------------------------

    /**
     * The quiver (balance update 2, Simon 29.10.: "Jeder sollte den Köcher haben, Pfeilarten nur noch
     * über Köcher"): every archer has it. The kit holds one stack of normal arrows; every arrow kind
     * won from a crate is a number of shots in the quiver. The kind picked on the ability bar flies
     * with the next shots until its shots run out, then the normal arrows again.
     */
    public static final class Quiver {
        /** The kind for the next shots ("" for the normal arrows). */
        public String current = "";
        /** Shots left of each kind. */
        public final Map<String, Integer> shots = new java.util.LinkedHashMap<>();
        /** The game time and kind of the last shot that took a kind (a volley takes it once). */
        long lastTick = -1L;
        String lastKind = "";
    }

    /** The arrow kinds of the quiver: id, crate gain, shots per night, colour. */
    private record ArrowKind(String id, String gain, int shots, int color, String icon) {
    }

    private static final List<ArrowKind> ARROW_KINDS = List.of(
            new ArrowKind("light", "lichtpfeile", 16, 0xFFE36B, "minecraft:glowstone_dust"),
            new ArrowKind("spectral", "spektralpfeile", 16, 0xF2D46B, "minecraft:spectral_arrow"),
            new ArrowKind("climb", "kletterpfeile", 4, 0x9C6B3A, "minecraft:ladder"),
            new ArrowKind("storm", "sturmpfeile", 8, 0xBFE9FF, "minecraft:wind_charge"));

    private static final Map<UUID, Quiver> QUIVERS = new HashMap<>();

    /** A new kit: the quiver holds the shots of every arrow kind the archer has won. */
    public static void fillQuiver(ServerPlayer player, KitService kits, PlayerSkills skills) {
        if (skills == null || skills.clazz() != SkillClass.ARCHER) {
            QUIVERS.remove(player.getUUID());
            return;
        }
        Quiver q = new Quiver();
        for (ArrowKind kind : ARROW_KINDS) {
            if (kits.gainLevel(skills, SkillClass.ARCHER, kind.gain()) > 0) {
                q.shots.put(kind.id(), kind.shots());
            }
        }
        QUIVERS.put(player.getUUID(), q);
    }

    /** The kit ends: the quiver empties. */
    public static void emptyQuiver(ServerPlayer player) {
        QUIVERS.remove(player.getUUID());
    }

    /** For the tests and the HUD: the quiver of a player, or null. */
    public static Quiver quiverOf(ServerPlayer player) {
        return QUIVERS.get(player.getUUID());
    }

    private static ArrowKind arrowKind(String id) {
        for (ArrowKind kind : ARROW_KINDS) {
            if (kind.id().equals(id)) {
                return kind;
            }
        }
        return null;
    }

    /** The id of the archer's one arrow key on the bar. */
    public static final String QUIVER_SLOT = "quiver";

    /**
     * The archer's one arrow key (Simon 30.10.: "pfeile und die tränke auf eine taste … dann kann man
     * scrollen um zu ändern"): it shows the kind for the next shots; held, the wheel changes it; a tap
     * goes back to the normal arrows. Null without any kind won.
     */
    public static com.mki.nightsiege.core.UltState.Slot quiverSlot(ServerPlayer player) {
        Quiver q = QUIVERS.get(player.getUUID());
        if (q == null || q.shots.isEmpty()) {
            return null;
        }
        com.mki.nightsiege.core.UltState.Slot slot = new com.mki.nightsiege.core.UltState.Slot();
        slot.id = QUIVER_SLOT;
        ArrowKind kind = q.current.isEmpty() ? null : arrowKind(q.current);
        slot.icon = kind == null ? "minecraft:arrow" : kind.icon();
        slot.name = "nightsiege.arrow." + (kind == null ? "normal" : kind.id());
        if (kind != null) {
            slot.sub = "nightsiege.wheel.count";
            slot.subArgs.add(String.valueOf(q.shots.getOrDefault(kind.id(), 0)));
        } else {
            slot.sub = "nightsiege.wheel.scroll_hint";
        }
        slot.current = kind != null;
        slot.scroll = true;
        return slot;
    }

    /** The arrow key held and the wheel turned: the next (or the one before) kind with shots left, normal arrows among them. */
    public static void cycleQuiver(ServerPlayer player, int direction) {
        Quiver q = QUIVERS.get(player.getUUID());
        if (q == null) {
            return;
        }
        List<String> kinds = new ArrayList<>();
        kinds.add("");
        for (ArrowKind kind : ARROW_KINDS) {
            if (q.shots.getOrDefault(kind.id(), 0) > 0) {
                kinds.add(kind.id());
            }
        }
        if (kinds.size() == 1) {
            bar(player, Component.translatable("nightsiege.gadget.koecher.one"));
            return;
        }
        int at = Math.max(0, kinds.indexOf(q.current));
        String next = kinds.get(Math.floorMod(at + (direction < 0 ? -1 : 1), kinds.size()));
        q.current = next;
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.5f, 1.8f);
        Component name = Component.translatable("nightsiege.arrow." + (next.isEmpty() ? "normal" : next));
        bar(player, next.isEmpty() ? Component.translatable("nightsiege.gadget.koecher.now", name)
                : Component.translatable("nightsiege.gadget.koecher.now_left", name, q.shots.getOrDefault(next, 0)));
    }

    /** A tap on the arrow key: back to the normal arrows. */
    public static void normalArrows(ServerPlayer player) {
        Quiver q = QUIVERS.get(player.getUUID());
        if (q == null || q.current.isEmpty()) {
            return;
        }
        q.current = "";
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
        bar(player, Component.translatable("nightsiege.gadget.koecher.now", Component.translatable("nightsiege.arrow.normal")));
    }

    /** A short press of the choice key without the bar: the next arrow kind with shots left. */
    public static void cycleQuiver(ServerPlayer player) {
        Quiver q = QUIVERS.get(player.getUUID());
        if (q == null) {
            return;
        }
        List<String> kinds = new ArrayList<>();
        kinds.add("");
        for (Map.Entry<String, Integer> e : q.shots.entrySet()) {
            if (e.getValue() > 0) {
                kinds.add(e.getKey());
            }
        }
        if (kinds.size() == 1) {
            bar(player, Component.translatable("nightsiege.gadget.koecher.one"));
            return;
        }
        int at = Math.max(0, kinds.indexOf(q.current));
        pickQuiver(player, kinds.get((at + 1) % kinds.size()));
    }

    /**
     * The archer's choices on the ability bar: the normal arrows and every kind with shots left
     * (how many there are); the one for the next shots is the current one. Null with nothing to pick.
     */
    public static com.mki.nightsiege.core.WheelData quiverWheel(ServerPlayer player) {
        Quiver q = QUIVERS.get(player.getUUID());
        if (q == null || q.shots.isEmpty()) {
            return null;
        }
        com.mki.nightsiege.core.WheelData wheel = new com.mki.nightsiege.core.WheelData();
        wheel.title = "nightsiege.wheel.quiver";
        wheel.hint = "nightsiege.wheel.quiver.hint";
        com.mki.nightsiege.core.WheelData.Option normal = new com.mki.nightsiege.core.WheelData.Option();
        normal.id = "";
        normal.icon = "minecraft:arrow";
        normal.name = "nightsiege.arrow.normal";
        normal.current = q.current.isEmpty();
        wheel.options.add(normal);
        for (ArrowKind kind : ARROW_KINDS) {
            Integer left = q.shots.get(kind.id());
            if (left == null) {
                continue;
            }
            com.mki.nightsiege.core.WheelData.Option o = new com.mki.nightsiege.core.WheelData.Option();
            o.id = kind.id();
            o.icon = kind.icon();
            o.name = "nightsiege.arrow." + kind.id();
            o.sub = "nightsiege.wheel.count";
            o.subArgs.add(String.valueOf(left));
            o.current = kind.id().equals(q.current);
            o.open = left > 0;
            wheel.options.add(o);
        }
        return wheel;
    }

    /** A pick on the bar: that arrow kind flies with the next shots ("" for the normal arrows). */
    public static void pickQuiver(ServerPlayer player, String key) {
        Quiver q = QUIVERS.get(player.getUUID());
        if (q == null || key == null) {
            return;
        }
        if (!key.isEmpty() && q.shots.getOrDefault(key, 0) <= 0) {
            bar(player, Component.translatable("nightsiege.gadget.koecher.empty",
                    Component.translatable("nightsiege.arrow." + key)));
            return;
        }
        // The kind picked again: back to the normal arrows.
        q.current = key.equals(q.current) ? "" : key;
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
        Component name = Component.translatable("nightsiege.arrow." + (q.current.isEmpty() ? "normal" : q.current));
        bar(player, q.current.isEmpty() ? Component.translatable("nightsiege.gadget.koecher.now", name)
                : Component.translatable("nightsiege.gadget.koecher.now_left", name, q.shots.getOrDefault(q.current, 0)));
    }

    /**
     * A shot of an archer: the picked kind goes with it (once per tick, so a volley or a double shot
     * takes one shot of the kind), and when its shots run out the normal arrows come back.
     */
    private static String takeQuiverShot(ServerPlayer player, long now) {
        Quiver q = QUIVERS.get(player.getUUID());
        if (q == null) {
            return "";
        }
        if (q.lastTick == now && !q.lastKind.isEmpty()) {
            return q.lastKind;   // the other arrows of the same volley
        }
        if (q.current.isEmpty()) {
            return "";
        }
        int left = q.shots.getOrDefault(q.current, 0);
        if (left <= 0) {
            q.current = "";
            return "";
        }
        String kind = q.current;
        q.shots.put(kind, left - 1);
        q.lastTick = now;
        q.lastKind = kind;
        if (left - 1 <= 0) {
            q.current = "";
            bar(player, Component.translatable("nightsiege.gadget.koecher.out", Component.translatable("nightsiege.arrow." + kind)));
        }
        return kind;
    }

    /** The client jumped a second time in the air: no fall damage from before, a puff of cloud. */
    public static void onDoubleJump(ServerPlayer player) {
        if (CrateEffects.gainLevel(player, "sprungstiefel") <= 0 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    static List<Mob> enemies(ServerLevel level, Vec3 center, double radius) {
        AABB box = new AABB(center, center).inflate(radius);
        List<Mob> out = new ArrayList<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive() && !Companions.isCompanion(m))) {
            if (mob.position().distanceToSqr(center) <= radius * radius) {
                out.add(mob);
            }
        }
        return out;
    }

    private static void drawLine(ServerLevel level, Vec3 from, Vec3 to, net.minecraft.core.particles.SimpleParticleType type) {
        Vec3 d = to.subtract(from);
        int steps = Math.max(2, (int) (d.length() * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(d.scale(i / (double) steps));
            level.sendParticles(type, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    static void bar(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }

    /** For the gametest: uses the gadget with this id from the player's inventory, as a right click would. */
    public static boolean testUse(ServerPlayer player, String gadgetId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (gadgetId.equals(Gadgets.of(stack)) && player.level() instanceof ServerLevel level) {
                use(player, level, stack, gadgetId);
                return true;
            }
        }
        return false;
    }

    /** For the gametest: sets a placed gadget (banner, barricade) on top of a block. */
    public static boolean testPlace(ServerPlayer player, String gadgetId, BlockPos on) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(on).add(0, 0.5, 0), Direction.UP, on, false);
        return gadgetId.equals("kriegsbanner") ? placeBanner(player, level, hit) : placeBarricade(player, level, hit);
    }

    /** For the gametest: how many war banners and oil puddles stand. */
    public static String debugState() {
        return "banners=" + BANNERS.size() + " oil=" + OIL.size() + " calm=" + CALM.size() + " lured=" + LURED.size()
                + " flee=" + FLEE.size() + " guards=" + BODYGUARD.size();
    }
}
