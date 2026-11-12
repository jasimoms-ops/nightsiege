package com.mki.nightsiege.client;

import com.mki.nightsiege.core.FxBudget;
import com.mki.nightsiege.game.CoreNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Z4 (Simon 12.11.: "alle Animationen aussagekräftiger und auffälliger, größer"): draws the abilities'
 * new effects the server announces ({@link CoreNetworking.FxPayload}). With "Effekte: reduziert"
 * ({@link FxSettings}) nothing of this is drawn - the old look stays as it was.
 *
 * <ul>
 *   <li>Cast: the arm swings (seen on every player), a burst in the class colour with the subclass accent.</li>
 *   <li>Ring: the real radius of an area ability on the ground, three pulses (longer to see).</li>
 *   <li>Ultimate: a column of light, a flash, a shock wave ring running outwards, its own sound, and a
 *       short camera shake for the caster alone (can be switched off).</li>
 *   <li>Hits: the monster flashes white for a moment (overlay, like the creeper) with a spark of colour.</li>
 * </ul>
 * Plain particles and the entity overlay: both show with shaders. All particles go through the
 * {@link FxBudget} per player and respect Minecraft's particle setting.
 */
public final class AbilityFxClient {

    /** Dust as the old effects used it was about 1.0-1.2 big; the new one is {@link FxBudget#SIZE}× that. */
    private static final float DUST = (float) (1.2 * FxBudget.SIZE);
    private static final double SHOCK_RADIUS = 8.0;
    private static final int SHOCK_TICKS = 10;
    private static final int FLASH_TICKS = 5;
    private static final int SHAKE_TICKS = 10;

    private static final FxBudget BUDGET = new FxBudget();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<Integer, Integer> FLASH = new HashMap<>();
    private static final int[] RECEIVED = new int[6];
    /** Between OPEN and CLOSE: the source whose key runs, its place; -1 for none. */
    private static int openSource = -1;
    private static Vec3 openAt;
    private static int boosted;
    private static int drawn;
    private static int shake;
    private static long now;
    private static ClientLevel lastLevel;

    private record Pending(long at, Runnable run) {
    }

    private AbilityFxClient() {
    }

    /** One payload from the server (client thread). */
    public static void receive(CoreNetworking.FxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || p.kind() < 0 || p.kind() > 5) {
            return;
        }
        RECEIVED[p.kind()]++;
        if (p.kind() == CoreNetworking.FxPayload.OPEN || p.kind() == CoreNetworking.FxPayload.CLOSE) {
            openSource = p.kind() == CoreNetworking.FxPayload.OPEN ? p.source() : -1;
            openAt = p.pos();
            return;
        }
        if (!FxSettings.full()) {
            return;   // reduziert: the old look
        }
        now = level.getGameTime();
        Entity src = level.getEntity(p.source());
        switch (p.kind()) {
            case CoreNetworking.FxPayload.CAST -> cast(level, src, p, false);
            case CoreNetworking.FxPayload.ULT -> cast(level, src, p, true);
            case CoreNetworking.FxPayload.RING -> {
                for (int pulse = 0; pulse < 3; pulse++) {
                    later(pulse * 6L, () -> ring(level, p.source(), p.pos(), p.radius(), p.color(), p.accent(), DUST * 0.85f, 1.0));
                }
            }
            case CoreNetworking.FxPayload.HITS -> hits(level, p);
            default -> {
            }
        }
    }

    private static void cast(ClientLevel level, Entity src, CoreNetworking.FxPayload p, boolean ult) {
        Vec3 at = src != null ? src.position() : p.pos();
        if (src instanceof LivingEntity living) {
            living.swing(InteractionHand.MAIN_HAND, false);   // the arm, seen from every side (no packet back)
        }
        burst(level, p.source(), at, p.color(), p.accent(), ult ? 48 : 30);
        if (!ult) {
            return;
        }
        // the column of light: three pulses
        for (int pulse = 0; pulse < 3; pulse++) {
            later(pulse * 4L, () -> column(level, p.source(), at, p.accent()));
        }
        if (take(p.source(), 1) > 0) {
            level.addParticle(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | p.accent()), true, false,
                    at.x, at.y + 1.0, at.z, 0, 0, 0);
        }
        // the shock wave: a ring running outwards to the ultimate's real radius (8 blocks for one without an area)
        double reach = p.radius() > 0 ? Math.max(4.0, p.radius()) : SHOCK_RADIUS;
        for (int t = 0; t <= SHOCK_TICKS; t++) {
            double r = 1.0 + (reach - 1.0) * t / SHOCK_TICKS;
            later(t, () -> ring(level, p.source(), at, r, p.color(), p.accent(), DUST, 0.5));
        }
        // its own sound: thunder over a deep beacon chord, loud enough over a fight
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.1f, 1.35f, false);
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4f, 0.6f, false);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && p.source() == mc.player.getId() && FxSettings.shake()) {
            shake = SHAKE_TICKS;
        }
    }

    private static void burst(ClientLevel level, int source, Vec3 at, int color, int accent, int count) {
        int n = take(source, count);
        RandomSource rnd = level.getRandom();
        DustParticleOptions main = new DustParticleOptions(color, DUST);
        DustParticleOptions acc = new DustParticleOptions(accent, DUST * 0.8f);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = 0.35 + rnd.nextDouble() * 0.5;
            add(level, i % 3 == 0 ? acc : main, at.x + Math.cos(a) * d, at.y + 0.2 + rnd.nextDouble() * 1.8,
                    at.z + Math.sin(a) * d, Math.cos(a) * 0.05, 0.06 + rnd.nextDouble() * 0.06, Math.sin(a) * 0.05);
        }
    }

    private static void column(ClientLevel level, int source, Vec3 at, int accent) {
        int n = take(source, 40);
        DustParticleOptions dust = new DustParticleOptions(accent, Math.min(4.0f, DUST * 1.4f));
        for (int i = 0; i < n; i++) {
            double y = at.y + i * 0.35;
            add(level, i % 2 == 0 ? dust : ParticleTypes.END_ROD, at.x, y, at.z, 0, 0.02, 0);
        }
    }

    /** A ring on the ground: n points, as many as the budget gives, spread evenly. */
    private static void ring(ClientLevel level, int source, Vec3 c, double r, int color, int accent, float size, double density) {
        int want = (int) Math.max(16, Math.min(140, Math.round(Math.PI * 2 * r * 2.2 * density)));
        int n = take(source, want);
        if (n <= 0) {
            return;
        }
        DustParticleOptions main = new DustParticleOptions(color, Math.min(4.0f, size));
        DustParticleOptions acc = new DustParticleOptions(accent, Math.min(4.0f, size));
        double phase = level.getRandom().nextDouble();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * (i + phase) / n;
            add(level, i % 4 == 0 ? acc : main, c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r, 0, 0.01, 0);
        }
    }

    private static void hits(ClientLevel level, CoreNetworking.FxPayload p) {
        DustParticleOptions dust = new DustParticleOptions(p.color(), DUST);
        for (int id : p.ids()) {
            Entity e = level.getEntity(id);
            if (!(e instanceof LivingEntity)) {
                continue;
            }
            FLASH.put(id, FLASH_TICKS);
            int n = take(p.source(), 6);
            for (int i = 0; i < n; i++) {
                add(level, dust, e.getX(), e.getY(0.5), e.getZ(), 0, 0, 0);
            }
        }
    }

    private static void add(ClientLevel level, ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz) {
        // force: seen up to the payload's range, not only 32 blocks; Minecraft's particle setting still holds
        level.addParticle(particle, true, false, x, y, z, dx, dy, dz);
        drawn++;
    }

    private static int take(int source, int wanted) {
        return BUDGET.take(source, wanted, now);
    }

    private static void later(long ticks, Runnable run) {
        if (ticks <= 0) {
            run.run();
        } else {
            PENDING.add(new Pending(now + ticks, run));
        }
    }

    /**
     * A particle packet from the server (client thread, ClientPacketListenerMixin): while a key's code runs
     * (OPEN ... CLOSE) and the effects are full, its own particles come {@link FxBudget#SIZE}× bigger,
     * {@link FxBudget#LIFE}× longer and up to {@link FxBudget#COUNT}× as many (the extra ones from the budget).
     * Returns false to leave the packet to Minecraft (reduced, other particles, minimal particle setting).
     */
    public static boolean boost(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (openSource < 0 || mc.level == null || !FxSettings.full()
                || mc.options.particles().get() == net.minecraft.server.level.ParticleStatus.MINIMAL) {
            return false;
        }
        if (openAt != null && openAt.distanceToSqr(p.getX(), p.getY(), p.getZ()) > 48 * 48) {
            return false;
        }
        now = mc.level.getGameTime();
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        RandomSource rnd = mc.level.getRandom();
        int count = p.getCount();
        float speed = p.getMaxSpeed();
        if (count == 0) {
            spawnBig(mc, p, eye, p.getX(), p.getY(), p.getZ(), speed * p.getXDist(), speed * p.getYDist(), speed * p.getZDist());
            return true;
        }
        int n = count + take(openSource, (int) Math.ceil(count * (FxBudget.COUNT - 1.0)));
        for (int i = 0; i < n; i++) {
            double x = p.getX() + rnd.nextGaussian() * p.getXDist();
            double y = p.getY() + rnd.nextGaussian() * p.getYDist();
            double z = p.getZ() + rnd.nextGaussian() * p.getZDist();
            spawnBig(mc, p, eye, x, y, z, rnd.nextGaussian() * speed, rnd.nextGaussian() * speed, rnd.nextGaussian() * speed);
        }
        return true;
    }

    private static void spawnBig(Minecraft mc, net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket p, Vec3 eye,
                                 double x, double y, double z, double dx, double dy, double dz) {
        if (!p.isOverrideLimiter() && eye.distanceToSqr(x, y, z) > 1024.0) {
            return;   // as Minecraft: unforced particles only within 32 blocks
        }
        net.minecraft.client.particle.Particle made = mc.particleEngine.createParticle(p.getParticle(), x, y, z, dx, dy, dz);
        if (made != null) {
            made.scale((float) FxBudget.SIZE);
            made.setLifetime((int) Math.round(made.getLifetime() * FxBudget.LIFE));
            boosted++;
        }
    }

    /** Tests: old particles drawn bigger so far. */
    public static int boostedForTests() {
        return boosted;
    }

    /** Every client tick: the pulses due, the flashes and the shake running down. */
    public static void tick(Minecraft mc) {
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            PENDING.clear();
            FLASH.clear();
            BUDGET.clear();
            shake = 0;
            openSource = -1;
        }
        if (mc.level == null) {
            return;
        }
        now = mc.level.getGameTime();
        if (!PENDING.isEmpty()) {
            List<Pending> due = new ArrayList<>();
            for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
                Pending p = it.next();
                if (p.at() <= now) {
                    due.add(p);
                    it.remove();
                }
            }
            for (Pending p : due) {
                p.run().run();
            }
        }
        if (!FLASH.isEmpty()) {
            FLASH.replaceAll((id, left) -> left - 1);
            FLASH.values().removeIf(left -> left <= 0);
        }
        if (shake > 0) {
            shake--;
        }
    }

    /** The white flash of a monster hit by an ability, 0 to 1 (the overlay's strength). */
    public static float flash(int entityId) {
        Integer left = FLASH.get(entityId);
        return left == null ? 0f : 0.85f * left / FLASH_TICKS;
    }

    /** Camera shake now in degrees: {yaw, pitch}; zero when none runs. */
    public static float[] shake(float partialTick) {
        if (shake <= 0) {
            return null;
        }
        float t = SHAKE_TICKS - shake + partialTick;
        float amp = 1.6f * shake / SHAKE_TICKS;
        return new float[]{amp * (float) Math.sin(t * 2.9), amp * 0.7f * (float) Math.cos(t * 3.7)};
    }

    /** Tests: payloads received by kind, particles drawn, shake running. */
    public static int receivedForTests(int kind) {
        return RECEIVED[kind];
    }

    public static int drawnForTests() {
        return drawn;
    }

    public static boolean shakingForTests() {
        return shake > 0;
    }
}
