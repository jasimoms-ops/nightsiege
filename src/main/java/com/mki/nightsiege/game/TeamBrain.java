package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.TrapKind;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One team bot of the balance tool (29 October 2026). It plays its subclass like the single bot
 * of the class simulation ({@link MeleeSim}), but at a post of the base instead of next to the core:
 *
 * <ul>
 *   <li><b>Tanks</b> stand in front of a gate and take the monsters; <b>melee</b> beside them;</li>
 *   <li><b>healers</b> just in front of the gate, healing the most wounded team-mate in reach;</li>
 *   <li><b>archers</b> on the wall near the gate; <b>engineers</b> set their towers and traps around
 *       their post in the preparation and overcharge them; <b>summoners</b> beside the tanks.</li>
 * </ul>
 *
 * Melee bots step towards a monster up to {@link #LEASH} blocks from their post (in small
 * teleports, as a bot has no client to walk with) and go back when it is gone.
 */
final class TeamBrain {

    enum Role { TANK, HEALER, MELEE, RANGED, ENGINEER, SUMMONER }

    /** How far a melee bot leaves its post for a monster. */
    static final double LEASH = 7.0;
    /** Blocks a bot steps per tick (about a player's walking pace). */
    private static final double STEP = 0.22;

    final UUID id;
    final String subclass;
    final String subNode;
    final SkillClass clazz;
    final Role role;
    BlockPos post;
    /** The gate this bot holds (index into the base's gates, -1 = around the core). */
    int gate = -1;
    private long nextUse;
    private long nextStaff;
    private long nextRemote;
    private long nextSlot;
    private long nextHeal;
    private int staffTurn;
    private int slotTurn;
    private boolean trapsSet;
    private final List<BlockPos> towers = new ArrayList<>();

    TeamBrain(UUID id, String[] sub) {
        this.id = id;
        this.clazz = SkillClass.valueOf(sub[0]);
        this.subNode = sub[1];
        this.subclass = sub[2];
        this.role = roleOf(sub[2]);
    }

    static Role roleOf(String subclass) {
        return switch (subclass) {
            case "shieldfighter", "bulwark" -> Role.TANK;
            case "medic", "warden" -> Role.HEALER;
            case "blademaster", "berserker", "axe", "paladin" -> Role.MELEE;
            case "swift", "heavy", "bolter", "trapper" -> Role.RANGED;
            case "gunner", "electrician", "demolition", "alchemist" -> Role.ENGINEER;
            default -> Role.SUMMONER;
        };
    }

    /** Back to the start of a run: timers reset, no traps set yet. */
    void reset(long now) {
        nextUse = now;
        nextStaff = now;
        nextRemote = now + 100;
        nextSlot = now + 40;
        nextHeal = now;
        staffTurn = 0;
        slotTurn = 0;
        trapsSet = false;
        towers.clear();
    }

    /** In the preparation: the engineer sets every trap of his kit around his post. */
    void prepare(SiegeManager m, ServerPlayer player, ServerLevel level) {
        if (trapsSet) {
            return;
        }
        trapsSet = true;
        stand(player, level);
        if (clazz != SkillClass.ENGINEER) {
            return;
        }
        Inventory inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            TrapKind kind = ModContent.trapOf(stack);
            while (kind != null && !stack.isEmpty()) {
                if (m.trapService().placeBlocked(player, kind) != null) {
                    break;
                }
                BlockPos pos = trapSpot(level, kind, n++);
                if (pos == null) {
                    break;
                }
                level.setBlock(pos, ModContent.TRAP_BLOCKS.get(kind).defaultBlockState(), 3);
                m.onTrapPlaced(level, pos, player, kind);
                stack.shrink(1);
                if (isTower(kind)) {
                    towers.add(pos);
                }
            }
        }
    }

    private static boolean isTower(TrapKind kind) {
        return kind == TrapKind.TURRET || kind == TrapKind.TESLA || kind == TrapKind.MORTAR || kind == TrapKind.BREWER;
    }

    /** Towers close to the post, floor traps a little further out (golden angle, so they spread). */
    private BlockPos trapSpot(ServerLevel level, TrapKind kind, int n) {
        boolean tower = isTower(kind);
        for (int tries = 0; tries < 48; tries++) {
            int k = n + tries * 7;
            double radius = tower ? 2.5 + (k % 2) : 4 + (k % 3) * 1.5;
            double angle = k * 2.3999632;
            BlockPos at = post.offset((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius));
            BlockPos spot = TeamSim.standable(level, at, 2);
            if (spot != null && !spot.equals(post) && spot.distManhattan(post) > 1
                    && level.getBlockState(spot).isAir()) {
                return spot;
            }
        }
        return null;
    }

    private void stand(ServerPlayer player, ServerLevel level) {
        if (post != null) {
            player.teleportTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5);
        }
    }

    /** One tick in the night. {@code team}: the other bots still standing. */
    void tick(SiegeManager m, ServerPlayer player, ServerLevel level, long now, List<ServerPlayer> team) {
        if (post == null) {
            return;
        }
        PlayerSkills skills = m.save().skillsOf(player.getUUID().toString());
        boolean guardian = clazz == SkillClass.GUARDIAN;
        boolean bulwark = SkillTree.SUB_BULWARK.equals(m.skillTree().subclassOf(skills));
        Vec3 home = Vec3.atBottomCenterOf(post);
        boolean melee = role == Role.TANK || role == Role.MELEE || role == Role.HEALER || role == Role.SUMMONER;
        if (player.position().distanceToSqr(home) > (melee ? (LEASH + 3) * (LEASH + 3) : 9.0)) {
            stand(player, level);
        }
        Mob close = MeleeSim.nearest(level, player, melee ? LEASH + 4 : 10.0);
        Mob far = MeleeSim.nearest(level, player, 32.0);
        double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        boolean inReach = close != null && player.distanceTo(close) <= reach + 0.3;

        // Melee: a few steps towards the monster, never further than the leash from the post.
        if (melee && close != null && !inReach && close.position().distanceTo(home) <= LEASH + 2) {
            stepTowards(player, level, close.position(), home);
        } else if (melee && close == null && player.position().distanceToSqr(home) > 2.0) {
            stepTowards(player, level, home, home);
        }

        if (clazz == SkillClass.SUMMONER && now >= nextStaff) {
            nextStaff = now + 20;
            staff(m, player, level, skills, close != null ? close : far);
        }
        if (clazz == SkillClass.ENGINEER && now >= nextRemote && !towers.isEmpty()) {
            nextRemote = now + 200;
            m.kits().traps().useRemote(player, towers.get((int) ((now / 200) % towers.size())), true);   // overcharge
        }
        if (role == Role.HEALER && now >= nextHeal) {
            nextHeal = now + 20;
            heal(m, player, team);
        }

        boolean ranged = clazz == SkillClass.ARCHER || clazz == SkillClass.ENGINEER;
        if (inReach || !ranged) {
            if (close != null && (inReach || role != Role.HEALER || now < nextHeal - 10)) {
                player.lookAt(EntityAnchorArgument.Anchor.EYES, close.getEyePosition());
            }
            if (inReach && MeleeSim.hold(player, MeleeSim::isMelee) && player.getAttackStrengthScale(0.5f) >= 0.95f) {
                if (player.isUsingItem()) {
                    player.stopUsingItem();
                }
                player.attack(close);
                player.swing(InteractionHand.MAIN_HAND, true);
            } else if (inReach && clazz == SkillClass.ARCHER) {
                MeleeSim.shoot(player, level, close);
            } else if (inReach && clazz == SkillClass.SUMMONER && now % 10 == 0
                    && MeleeSim.hold(player, s -> s.is(ModContent.STAFF))) {
                m.onCoreAction(player, "staffhit");
            } else if (guardian && !player.isUsingItem() && !player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
                player.startUsingItem(InteractionHand.OFF_HAND);
            }
        } else if (far != null) {
            MeleeSim.shoot(player, level, far);
        } else if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        player.setShiftKeyDown(bulwark);
        if (now % 60 == 0) {
            m.onCoreAction(player, "ult");
        }
        if (now % 100 == 50 && clazz != SkillClass.ENGINEER && clazz != SkillClass.SUMMONER) {
            m.onCoreAction(player, "ult2");   // Fähigkeit 2
        }
        if (guardian && player.isBlocking() && now % 40 == 20) {
            m.onCoreAction(player, "shield");
        }
        // The ability bar (spells, potions, salvos): the next one that is ready, every two seconds.
        // Archers' keys only pick the arrow kind, healers fire theirs at a team-mate in heal().
        if (now >= nextSlot && clazz != SkillClass.ARCHER && role != Role.HEALER && clazz != SkillClass.SUMMONER) {
            nextSlot = now + 40;
            fireReadySlot(m, player);
        }
        if (now >= nextUse) {
            boolean low = player.getHealth() < player.getMaxHealth() * 0.45f;
            if (low && MeleeSim.use(player, level, true)) {
                nextUse = now + 40;
            } else if (player.getFoodData().getFoodLevel() < 14 && MeleeSim.use(player, level, false)) {
                nextUse = now + 40;
            }
        }
    }

    /** The staff like the class simulation: attack, every few turns the order, call or a spell. */
    private void staff(SiegeManager m, ServerPlayer player, ServerLevel level, PlayerSkills skills, Mob aim) {
        boolean dead = SkillTree.WAY_DEAD.equals(skills.pathTaken(m.skillTree()));
        if (!MeleeSim.hold(player, s -> s.is(ModContent.STAFF))) {
            return;
        }
        if (aim != null) {
            player.lookAt(EntityAnchorArgument.Anchor.EYES, aim.position().add(0, aim.getBbHeight() * 0.5, 0));
        }
        staffTurn++;
        boolean sneak = dead ? staffTurn % 3 == 0 : staffTurn % 5 == 3 && aim != null;
        if (!dead && staffTurn % 5 == 1) {
            m.onCoreAction(player, "quiver");
        }
        if (dead && sneak) {
            int keys = Math.max(1, m.abilitySlots(player).size());
            m.onCoreAction(player, "slot:" + (staffTurn / 3) % keys);
        } else if (sneak) {
            player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
        } else {
            m.onCoreAction(player, "staffhit");
        }
    }

    /** The healer looks at the most wounded team-mate in reach (or keeps himself) and fires a ready heal. */
    private void heal(SiegeManager m, ServerPlayer player, List<ServerPlayer> team) {
        ServerPlayer worst = null;
        float worstShare = 0.85f;
        for (ServerPlayer mate : team) {
            if (mate == player || mate.distanceTo(player) > 12.0) {
                continue;
            }
            float share = mate.getHealth() / Math.max(1f, mate.getMaxHealth());
            if (share < worstShare) {
                worstShare = share;
                worst = mate;
            }
        }
        if (worst != null) {
            player.lookAt(EntityAnchorArgument.Anchor.EYES, worst.getEyePosition());
        } else if (player.getHealth() >= player.getMaxHealth() * 0.85f) {
            return;   // nobody to heal
        }
        fireReadySlot(m, player);
    }

    private void fireReadySlot(SiegeManager m, ServerPlayer player) {
        List<com.mki.nightsiege.core.UltState.Slot> slots = m.abilitySlots(player);
        for (int k = 0; k < slots.size(); k++) {
            int i = (slotTurn + k) % slots.size();
            if (slots.get(i).cooldownTicks <= 0) {
                m.onCoreAction(player, "slot:" + i);
                slotTurn = i + 1;
                return;
            }
        }
    }

    /** One small step towards {@code target}, on standable ground, never beyond the leash. */
    private static void stepTowards(ServerPlayer player, ServerLevel level, Vec3 target, Vec3 home) {
        Vec3 here = player.position();
        Vec3 d = new Vec3(target.x - here.x, 0, target.z - here.z);
        double len = d.length();
        if (len < 0.4) {
            return;
        }
        Vec3 next = here.add(d.scale(Math.min(STEP, len) / len));
        if (next.distanceTo(home) > LEASH) {
            return;
        }
        BlockPos spot = TeamSim.standable(level, BlockPos.containing(next), 1);
        if (spot == null) {
            return;
        }
        double y = spot.getY();
        player.teleportTo(next.x, y, next.z);
    }

    /** For the log: a short line about this bot. */
    String describe() {
        return subclass + "@" + (post == null ? "-" : post.toShortString()) + (gate >= 0 ? " Tor " + gate : "");
    }

    static void log(String line) {
        NightSiegeMod.LOGGER.info(line);
    }

    /** True if the block is one the bot can walk through (air, plants, open doors and the like). */
    static boolean passable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }
}
