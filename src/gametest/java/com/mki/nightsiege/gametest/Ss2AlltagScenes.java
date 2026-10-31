package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.TrapKind;
import com.mki.nightsiege.game.ArmorBreak;
import com.mki.nightsiege.game.KitBlocks;
import com.mki.nightsiege.game.KitService;
import com.mki.nightsiege.game.MobFactory;
import com.mki.nightsiege.game.ModContent;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.TempBlocks;
import com.mki.nightsiege.game.TrapRecord;
import com.mki.nightsiege.game.TrapService;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Survival safety 2, everyday play (Simon 31.10.): outside any night a zombie's hit cracks no armour,
 * a tesla coil, a spike trap and an arrow tower beside a zombie stay quiet, and nobody mans a tower
 * that is not his; in a test night (an eclipse) the traps fire, a Night Siege zombie cracks the armour,
 * a kit cobblestone set and mined gives nothing, a kit block (and a kit torch on it) left standing is
 * gone after the night without drops, a drunk kit potion leaves no glass bottle, the free traps are
 * kit items; the eclipse's gameplay values hold only near the core. Log lines start with
 * {@code [ss2-alltag]}.
 */
final class Ss2AlltagScenes {

    private Ss2AlltagScenes() {
    }

    private static void log(String line) {
        System.out.println("[ss2-alltag] " + line);
    }

    private static String drops(ServerLevel level, BlockPos at, double r) {
        Map<String, Integer> out = new HashMap<>();
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(r))) {
            out.merge(e.getItem().getItem().toString(), e.getItem().getCount(), Integer::sum);
        }
        return out.isEmpty() ? "keine" : out.toString();
    }

    private static void clearDrops(ServerLevel level, BlockPos at, double r) {
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(r))) {
            e.discard();
        }
    }

    private static int count(Inventory inv, Item item, Boolean kit) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && (kit == null || KitService.isKit(s) == kit)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** The first kit stack of this item in the inventory, or -1. */
    private static int kitSlot(Inventory inv, Item item) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && KitService.isKit(s)) {
                return i;
            }
        }
        return -1;
    }

    /** Puts this stack into the main hand (what was there goes to its slot). */
    private static void toHand(ServerPlayer player, int slot) {
        Inventory inv = player.getInventory();
        ItemStack s = inv.getItem(slot);
        int hand = inv.getSelectedSlot();
        if (slot == hand) {
            return;
        }
        inv.setItem(slot, inv.getItem(hand));
        inv.setItem(hand, s);
    }

    /** A survival player sets the block in his hand on top of {@code floor} (the server's own way). */
    private static boolean placeOn(ServerPlayer player, ServerLevel level, BlockPos floor) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        ItemStack held = player.getMainHandItem();
        return player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private static Zombie zombie(ServerLevel level, double x, double y, double z, boolean own) {
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
        if (zombie == null) {
            return null;
        }
        zombie.snapTo(x, y, z, 0f, 0f);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        zombie.addTag("ss2_alltag");
        if (own) {
            zombie.addTag(MobFactory.TAG);   // a monster of Night Siege
        }
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static Zombie find(ServerLevel level, UUID id) {
        Entity e = id == null ? null : level.getEntity(id);
        return e instanceof Zombie z ? z : null;
    }

    private static String health(ServerLevel level, UUID[] ids) {
        StringBuilder out = new StringBuilder();
        for (UUID id : ids) {
            Zombie z = find(level, id);
            out.append(z == null ? "weg" : String.format(java.util.Locale.ROOT, "%.1f/%.1f", z.getHealth(), z.getMaxHealth())).append(' ');
        }
        return out.toString().trim();
    }

    private static void heal(ServerLevel level, UUID[] ids) {
        for (UUID id : ids) {
            Zombie z = find(level, id);
            if (z != null) {
                z.setHealth(z.getMaxHealth());
            }
        }
    }

    /** A zombie's hit on the player; true when it left the armour break effect. */
    private static boolean hitBy(ServerPlayer player, ServerLevel level, Zombie zombie) {
        player.removeEffect(ArmorBreak.EFFECT);
        player.removeEffect(MobEffects.RESISTANCE);
        player.invulnerableTime = 0;
        float hp = player.getHealth();
        player.hurtServer(level, level.damageSources().mobAttack(zombie), 4f);
        float after = player.getHealth();
        boolean broken = player.hasEffect(ArmorBreak.EFFECT);
        player.setHealth(player.getMaxHealth());
        log("  hit by " + (zombie.entityTags().contains(MobFactory.TAG) ? "night zombie" : "everyday zombie")
                + ": hp " + hp + " -> " + after + " armorBreak=" + broken + " defending=" + SiegeManager.get().isDefending(player));
        return broken;
    }

    private static String seat(ServerPlayer player, SiegeManager manager, ServerLevel level, BlockPos tower) {
        TrapService traps = manager.kits().traps();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        boolean handled = traps.tryMount(level, tower, player, ItemStack.EMPTY);
        boolean seated = traps.isSeated(player);
        if (seated) {
            player.stopRiding();   // out again (the seat goes on the next tick)
        }
        return "handled=" + handled + " seated=" + seated;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        EventLookTest.ensureCore(context, game);
        int[] c = context.computeOnClient(client -> {
            com.mki.nightsiege.game.CoreRecord core = SiegeManager.get().mainCore();
            return new int[]{core.x, core.y, core.z};
        });
        int cx = c[0];
        int cy = c[1];
        int cz = c[2];
        boolean anyTimeBefore = TrapService.mortarsAnyTime;
        TrapService.mortarsAnyTime = false;   // the real rule: traps and towers only in the night
        game.getServer().runCommand("gamerule advance_time false");
        game.getServer().runCommand("gamerule doDaylightCycle false");
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("difficulty normal");
        int x0 = cx + 20;
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(x0 - 4, cz - 20, x0 + 20, cz + 20));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(x0, cy - 1, cz - 18, x0 + 16, cy - 1, cz + 18));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(x0, cy, cz - 18, x0 + 16, cy + 6, cz + 18));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(cx + 2, cy - 1, cz + 2, cx + 8, cy - 1, cz + 8));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(cx + 2, cy, cz + 2, cx + 8, cy + 3, cz + 8));
        context.waitTicks(40);

        BlockPos tesla = new BlockPos(x0 + 6, cy, cz - 14);
        BlockPos spike = new BlockPos(x0 + 6, cy, cz);
        BlockPos turret = new BlockPos(x0 + 6, cy, cz + 14);
        BlockPos foreign = new BlockPos(x0 + 12, cy, cz + 8);   // another engineer's arrow tower
        BlockPos kitSpot = new BlockPos(cx + 3, cy - 1, cz + 3);    // floor: a kit cobblestone set and mined
        BlockPos standSpot = new BlockPos(cx + 6, cy - 1, cz + 6);  // floor: a kit cobblestone left standing, a kit torch on it
        BlockPos ownSpot = new BlockPos(cx + 3, cy, cz + 6);        // a plain cobblestone, mined (the drops work)
        final UUID[] trapZombies = new UUID[3];
        final UUID[] hitters = new UUID[2];

        // --- by day, no night anywhere
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            own.unlockClass(SkillClass.ENGINEER);
            own.switchTo(SkillClass.ENGINEER);
            own.levels.put(TrapKind.TURRET.id(), 1);
            own.levels.put(TrapKind.TESLA.id(), 1);
            own.levels.put(TrapKind.SPIKE.id(), 1);
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.teleportTo(level, cx + 2.5, cy, cz + 2.5, java.util.Set.of(), 0f, 0f, true);
            EventLookTest.place(level, manager, player, tesla, TrapKind.TESLA);
            EventLookTest.place(level, manager, player, spike, TrapKind.SPIKE);
            EventLookTest.place(level, manager, player, turret, TrapKind.TURRET);
            EventLookTest.place(level, manager, player, foreign, TrapKind.TURRET);
            String stranger = UUID.randomUUID().toString();
            for (TrapRecord r : manager.save().traps) {
                if (r.x == foreign.getX() && r.y == foreign.getY() && r.z == foreign.getZ()) {
                    r.owner = stranger;
                }
            }
            Zombie a = zombie(level, tesla.getX() + 3.5, cy, tesla.getZ() + 0.5, false);
            Zombie b = zombie(level, spike.getX() + 0.5, cy + 1, spike.getZ() + 0.5, false);
            Zombie t = zombie(level, turret.getX() + 6.5, cy, turret.getZ() + 0.5, false);
            trapZombies[0] = a == null ? null : a.getUUID();
            trapZombies[1] = b == null ? null : b.getUUID();
            trapZombies[2] = t == null ? null : t.getUUID();
            Zombie everyday = zombie(level, cx + 4.5, cy, cz + 1.5, false);
            hitters[0] = everyday == null ? null : everyday.getUUID();
            log("setup: core=" + cx + " " + cy + " " + cz + " active=" + manager.isActive() + " mode=" + player.gameMode()
                    + " traps=" + manager.save().traps.size() + " zombies=" + health(level, trapZombies));
            if (everyday != null) {
                log("DAY armor: everyday zombie hit -> armorBreak=" + hitBy(player, level, everyday) + " (expected false)");
            }
        });
        context.waitTicks(20);
        context.waitTicks(100);   // five seconds of day beside the traps
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            log("DAY traps: zombies after 5 s by day " + health(level, trapZombies) + " (expected all full: tesla spike turret)");
            log("DAY tower own: " + seat(player, manager, level, turret) + " (expected seated=false, only in the night)");
            log("DAY tower foreign: " + seat(player, manager, level, foreign) + " (expected seated=false)");
            log("DAY eventArea at core=" + SiegeManager.inEventArea(level, cx + 0.5, cz + 0.5) + " (expected false)");
        });
        context.waitTicks(20);
        // The ownership rule alone (the test flag lifts the time rule for a moment): not his tower, not a defender.
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            TrapService.mortarsAnyTime = true;
            log("FLAG tower foreign, no defender: " + seat(player, manager, level, foreign) + " (expected seated=false: not yours)");
            log("FLAG tower own: " + seat(player, manager, level, turret) + " (expected seated=true)");
            TrapService.mortarsAnyTime = false;
            heal(level, trapZombies);
        });
        context.waitTicks(20);

        // --- a test night (an eclipse) at the core
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            heal(level, trapZombies);
            boolean ok = manager.startTest(level.getServer(), level, new BlockPos(cx, cy, cz), 3, SiegeEvent.ECLIPSE);
            log("test night started=" + ok + " active=" + manager.isActive());
        });
        context.waitTicks(40);
        EventLookTest.closeScreen(context);
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            Inventory inv = player.getInventory();
            // The eclipse at full strength now (it fades in over 30 s otherwise).
            com.mki.nightsiege.game.EventAtmosphere.setServer(SiegeManager.dimensionName(level),
                    com.mki.nightsiege.game.EventAtmosphere.ECLIPSE, 1);
            log("NIGHT kit=" + manager.kits().hasStash(player) + " defending=" + manager.isDefending(player)
                    + " eventArea core=" + SiegeManager.inEventArea(level, cx + 0.5, cz + 0.5)
                    + " far=" + SiegeManager.inEventArea(level, cx + 400.5, cz + 0.5));
            // The free traps and the remote of the kit are kit items.
            StringBuilder handouts = new StringBuilder();
            for (TrapKind kind : TrapKind.values()) {
                Item item = ModContent.TRAP_ITEMS.get(kind);
                if (item != null && count(inv, item, null) > 0) {
                    handouts.append(kind.id()).append(" kit=").append(count(inv, item, true)).append(" plain=").append(count(inv, item, false)).append("; ");
                }
            }
            if (ModContent.REMOTE != null) {
                handouts.append("remote kit=").append(count(inv, ModContent.REMOTE, true)).append(" plain=").append(count(inv, ModContent.REMOTE, false));
            }
            log("NIGHT handouts: " + handouts + " (expected plain=0)");
            // Armour break: only Night Siege's own zombies, only for a defender.
            Zombie everyday = find(level, hitters[0]);
            if (everyday != null) {
                log("NIGHT armor: everyday zombie -> armorBreak=" + hitBy(player, level, everyday) + " (expected false)");
            }
            Zombie night = zombie(level, cx + 5.5, cy, cz + 1.5, true);
            if (night != null) {
                log("NIGHT armor: night zombie -> armorBreak=" + hitBy(player, level, night) + " (expected true)");
                night.discard();
            }
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 120, 4, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 120, 2, false, false));
            log("NIGHT tower own: " + seat(player, manager, level, turret) + " (expected seated=true)");
            log("NIGHT tower foreign as defender: " + seat(player, manager, level, foreign) + " (expected seated=true)");
            log("NIGHT traps start: zombies " + health(level, trapZombies));
        });
        context.waitTicks(20);
        // A kit cobblestone set down and mined; another left standing with a kit torch on it.
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            Inventory inv = player.getInventory();
            int slot = kitSlot(inv, Items.COBBLESTONE);
            log("NIGHT kit cobblestone slot=" + slot + " kitCobble=" + count(inv, Items.COBBLESTONE, true)
                    + " plainCobble=" + count(inv, Items.COBBLESTONE, false));
            if (slot < 0) {
                return;
            }
            toHand(player, slot);
            BlockPos at = kitSpot.above();
            boolean placed = placeOn(player, level, kitSpot);
            log("NIGHT kit block set=" + placed + " block=" + level.getBlockState(at).getBlock() + " temp=" + TempBlocks.isTemp(level, at)
                    + " listed=" + KitBlocks.listed(player.getUUID()));
            int pick = kitSlot(inv, Items.WOODEN_PICKAXE);
            if (pick < 0) {
                inv.setItem(inv.getSelectedSlot(), new ItemStack(Items.IRON_PICKAXE));
            } else {
                toHand(player, pick);
            }
            int before = count(inv, Items.COBBLESTONE, null);
            clearDrops(level, at, 4);
            boolean mined = player.gameMode.destroyBlock(at);
            log("NIGHT kit block mined: call=" + mined + " block now=" + level.getBlockState(at).getBlock()
                    + " temp=" + TempBlocks.isTemp(level, at) + " drops=" + drops(level, at, 3)
                    + " cobble " + before + " -> " + count(inv, Items.COBBLESTONE, null) + " (expected air, keine, same)");
            // A plain cobblestone (set by the server, not the kit) gives its stone: the drops work.
            level.setBlock(ownSpot, Blocks.COBBLESTONE.defaultBlockState(), 3);
            clearDrops(level, ownSpot, 3);
            player.gameMode.destroyBlock(ownSpot);
            log("NIGHT plain block mined: block now=" + level.getBlockState(ownSpot).getBlock() + " drops=" + drops(level, ownSpot, 3)
                    + " (expected a cobblestone)");
            clearDrops(level, ownSpot, 3);
            // Left standing: a kit cobblestone with a kit torch on it.
            int again = kitSlot(inv, Items.COBBLESTONE);
            if (again >= 0) {
                toHand(player, again);
                placeOn(player, level, standSpot);
            }
            inv.setItem(inv.getSelectedSlot(), KitService.mark(new ItemStack(Items.TORCH, 4)));
            placeOn(player, level, standSpot.above());
            log("NIGHT kit blocks standing: " + level.getBlockState(standSpot.above()).getBlock() + " temp="
                    + TempBlocks.isTemp(level, standSpot.above()) + ", " + level.getBlockState(standSpot.above(2)).getBlock()
                    + " temp=" + TempBlocks.isTemp(level, standSpot.above(2)) + " listed=" + KitBlocks.listed(player.getUUID()));
            // Kit potion: drunk, no glass bottle; a plain one leaves its bottle (the control).
            ItemStack kitPotion = KitService.mark(PotionContents.createItemStack(Items.POTION, Potions.HEALING));
            player.setItemInHand(InteractionHand.MAIN_HAND, kitPotion);
            ItemStack left = player.getMainHandItem().finishUsingItem(level, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, left);
            log("NIGHT kit potion drunk: in hand=" + player.getMainHandItem().getItem() + " x" + player.getMainHandItem().getCount()
                    + " bottles=" + count(inv, Items.GLASS_BOTTLE, null) + " (expected 0)");
            ItemStack plain = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
            ItemStack plainLeft = plain.finishUsingItem(level, player);
            log("NIGHT plain potion drunk (control): leaves " + plainLeft.getItem() + " (expected glass_bottle; not kept)");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        context.waitTicks(120);
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            log("NIGHT traps after 6 s: zombies " + health(level, trapZombies) + " (expected each below full: tesla spike turret)"
                    + " active=" + manager.isActive());
            // The eclipse's gameplay values near the core and far away.
            Vec3 near = new Vec3(cx + 4.5, cy + 1, cz + 4.5);
            Vec3 far = new Vec3(cx + 400.5, cy + 1, cz + 0.5);
            Object burnNear = level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, near);
            Object burnFar = level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, far);
            Object sky = level.environmentAttributes().getDimensionValue(EnvironmentAttributes.SKY_LIGHT_LEVEL);
            Zombie z = find(level, hitters[0]);
            log("ECLIPSE strength=" + com.mki.nightsiege.game.EventAtmosphere.serverStrength(SiegeManager.dimensionName(level),
                    com.mki.nightsiege.game.EventAtmosphere.ECLIPSE) + " monstersBurn near=" + burnNear + " far=" + burnFar
                    + " serverSkyLight=" + sky + " zombieLight near=" + (z == null ? "-" : z.getLightLevelDependentMagicValue())
                    + " (expected near=false far=true, sky light not darkened, zombie light < 0.5)");
            manager.abort(level.getServer());
            log("night aborted: active=" + manager.isActive());
        });
        context.waitTicks(60);
        EventLookTest.closeScreen(context);

        // --- the morning after
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            Inventory inv = player.getInventory();
            log("AFTER kit=" + manager.kits().hasStash(player) + " kit blocks: " + level.getBlockState(standSpot.above()).getBlock()
                    + " / " + level.getBlockState(standSpot.above(2)).getBlock() + " temp=" + TempBlocks.isTemp(level, standSpot.above())
                    + " drops=" + drops(level, standSpot.above(), 4) + " listed=" + KitBlocks.listed(player.getUUID())
                    + " (expected air / air, keine)");
            int kitLeft = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                kitLeft += KitService.isKit(inv.getItem(i)) ? 1 : 0;
            }
            log("AFTER inventory: bottles=" + count(inv, Items.GLASS_BOTTLE, null) + " cobble=" + count(inv, Items.COBBLESTONE, null)
                    + " torches=" + count(inv, Items.TORCH, null) + " kitItems=" + kitLeft + " (expected 0 0 0 0)");
            log("AFTER eventArea core=" + SiegeManager.inEventArea(level, cx + 0.5, cz + 0.5) + " (expected false)");
            heal(level, trapZombies);
        });
        context.waitTicks(100);
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            log("AFTER traps by day again: zombies " + health(level, trapZombies) + " (expected all full)");
            for (UUID id : trapZombies) {
                Zombie z = find(level, id);
                if (z != null) {
                    z.discard();
                }
            }
            Zombie z = find(level, hitters[0]);
            if (z != null) {
                z.discard();
            }
            player.removeEffect(MobEffects.RESISTANCE);
            player.removeEffect(MobEffects.REGENERATION);
        });
        context.waitTicks(20);
        TrapService.mortarsAnyTime = anyTimeBefore;
        game.getServer().runCommand("gamerule advance_time true");
        game.getServer().runCommand("gamerule doDaylightCycle true");
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(20);
    }
}
