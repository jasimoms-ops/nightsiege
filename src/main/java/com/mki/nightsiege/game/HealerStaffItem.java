package com.mki.nightsiege.game;

import com.mki.nightsiege.core.NightKit;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The healers' staff (Tor 4.7, Simon: "Geschoss auf Distanz"): the medic and the warden carry it instead of
 * the sword. The left click (held: again and again) shoots a homing magic bolt at the monster looked at,
 * up to 16 blocks; the right click stays the shield's in the off hand. The bolt deals what a full hit of
 * the kit sword of the level would (written on the staff by the kit), 1.33 bolts a second
 * ({@link NightKit#healerStaffShare}). Like the summoner's staff it neither hits nor mines.
 */
public class HealerStaffItem extends Item {

    /** Custom data key: the damage of one bolt, set by the kit. */
    public static final String BOLT = "ns_bolt";

    /** A bolt in flight. */
    private static final class Bolt {
        final UUID owner;
        final ServerLevel level;
        final float damage;
        Vec3 pos;
        UUID target;
        int life = 60;

        Bolt(UUID owner, ServerLevel level, Vec3 pos, UUID target, float damage) {
            this.owner = owner;
            this.level = level;
            this.pos = pos;
            this.target = target;
            this.damage = damage;
        }
    }

    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static final Map<UUID, Long> READY = new HashMap<>();
    /** For the scene: hits landed since the start (damage dealt). */
    private static int hits;
    private static double dealt;

    public HealerStaffItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;   // the right click is the shield's (off hand)
    }

    /** The damage of one bolt of this staff (a staff without the kit's number: the level-1 sword's). */
    public static float boltDamage(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        double bolt = data == null ? 0 : data.copyTag().getDoubleOr(BOLT, 0);
        return (float) (bolt > 0 ? bolt : NightKit.healerBolt(1, NightKit.sharpness(1)));
    }

    /** Writes the bolt's damage on a kit staff. */
    public static void setBoltDamage(ItemStack stack, double damage) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble(BOLT, damage));
    }

    /** The left click with the staff in hand (the "staffhit" key): a bolt at the monster looked at. */
    public static boolean attack(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof HealerStaffItem) || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        if (now < READY.getOrDefault(player.getUUID(), 0L)) {
            return false;
        }
        // Prüfer-Fund 4: only a monster in sight, never one behind a wall or the floor.
        LivingEntity prey = CombatEffects.lookTarget(level, player, NightKit.HEALER_BOLT_RANGE, true);
        if (prey == null) {
            return false;
        }
        READY.put(player.getUUID(), now + NightKit.HEALER_BOLT_PAUSE);
        BOLTS.add(new Bolt(player.getUUID(), level, player.getEyePosition().add(player.getLookAngle().scale(0.8)),
                prey.getUUID(), boltDamage(held)));
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
        return true;
    }

    /** Every tick: the bolts fly (white light, seen with shaders too); a lost target gives way to the next monster. */
    public static void tick(MinecraftServer server) {
        if (BOLTS.isEmpty()) {
            return;
        }
        for (Bolt b : new ArrayList<>(BOLTS)) {
            if (--b.life <= 0) {
                BOLTS.remove(b);
                continue;
            }
            Entity e = b.level.getEntity(b.target);
            LivingEntity target = e instanceof LivingEntity l && l.isAlive() ? l : null;
            if (target == null) {
                double best = Double.MAX_VALUE;
                for (LivingEntity near : CombatEffects.enemies(b.level, b.pos, 6.0, 3.0)) {
                    double d = near.distanceToSqr(b.pos);
                    if (d < best && !blocked(b.level, b.pos, near.position().add(0, near.getBbHeight() * 0.6, 0))) {
                        best = d;
                        target = near;
                    }
                }
                if (target == null) {
                    b.level.sendParticles(ParticleTypes.END_ROD, b.pos.x, b.pos.y, b.pos.z, 4, 0.1, 0.1, 0.1, 0.02);
                    BOLTS.remove(b);
                    continue;
                }
                b.target = target.getUUID();
            }
            Vec3 aim = target.position().add(0, target.getBbHeight() * 0.6, 0);
            Vec3 to = aim.subtract(b.pos);
            double dist = to.length();
            Vec3 next = dist <= NightKit.HEALER_BOLT_SPEED + 0.6 ? aim : b.pos.add(to.scale(NightKit.HEALER_BOLT_SPEED / dist));
            if (blocked(b.level, b.pos, next)) {   // the bolt stops at blocks (Prüfer-Fund 4)
                b.level.sendParticles(ParticleTypes.END_ROD, b.pos.x, b.pos.y, b.pos.z, 4, 0.1, 0.1, 0.1, 0.02);
                BOLTS.remove(b);
                continue;
            }
            if (dist <= NightKit.HEALER_BOLT_SPEED + 0.6) {
                BOLTS.remove(b);
                ServerPlayer owner = server.getPlayerList().getPlayer(b.owner);
                if (owner != null) {
                    float before = target.getHealth();
                    target.invulnerableTime = 0;
                    target.hurtServer(b.level, b.level.damageSources().playerAttack(owner), b.damage);
                    hits++;
                    dealt += Math.max(0f, before - Math.max(0f, target.getHealth()));
                    b.level.sendParticles(ParticleTypes.END_ROD, aim.x, aim.y, aim.z, 8, 0.2, 0.2, 0.2, 0.04);
                    b.level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, 0.8f, 1.5f);
                }
                continue;
            }
            b.pos = next;
            b.level.sendParticles(ParticleTypes.END_ROD, b.pos.x, b.pos.y, b.pos.z, 2, 0.04, 0.04, 0.04, 0.0);
            b.level.sendParticles(ParticleTypes.WAX_OFF, b.pos.x, b.pos.y, b.pos.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /** True if a block stands between the two points. */
    static boolean blocked(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType() != HitResult.Type.MISS;
    }

    /** For the scene: the staff may shoot again at once; the hit count from zero. */
    public static void resetForTest() {
        READY.clear();
        BOLTS.clear();
        hits = 0;
        dealt = 0;
    }

    public static int hits() {
        return hits;
    }

    public static double dealt() {
        return dealt;
    }
}
