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
    /** The ring at the caster's feet on every key (blocks). */
    private static final double CAST_RING = 1.6;
    /** ... seen by the others and in F5 (CI 1d981282a: 0.8 % apart for single target keys). */
    private static final double CAST_RING_OUTSIDE = 2.2;
    /** Dot size of the rings from one's own eyes (CI 1d981282a: archers 28 %, paladin 20 % of the view). */
    private static final float OWN = 0.6f;
    /** Particles this close to the camera never grow (blocks). */
    private static final double NEAR = 3.0;
    /**
     * Chef 12.11. 20:10, from one's own eyes (CI 6a2f3ae4d: archers' rain 20-31 %, bombardment 54-68 % of the view):
     * old particles nearer than this are halved (bright ones) or left out (smoke, clouds), new ones left out.
     */
    private static final double OWN_CLEAR = 2.0;
    private static final double OWN_CLEAR_NEW = 2.5;
    /** From one's own eyes bright particles only grow and last longer beyond this (blocks). */
    private static final double OWN_NEAR = 6.0;
    /** From one's own eyes: old smoke, clouds and poofs this big, explosions this big (one instead of the huge eight). */
    private static final float OWN_SMOKE = 0.6f;
    private static final float OWN_BLAST = 0.3f;
    /**
     * Prüfer 12.11. (F5: walls of clouds, alchemist ~40 %, shield ~25 %): seen from outside (F5, other players) old smoke,
     * clouds and poofs half as many and this big, every left out one a small glowing dot in the class colour instead.
     */
    private static final float FAR_SMOKE = 0.8f;
    private static final float FAR_BLAST = 0.5f;
    /** A packet this full is a cloud over an area (toxic cloud: 150): it gets no extra particles and grows at most ×1.25. */
    private static final int MASS = 60;
    /** From one's own eyes the ult's column stands this far ahead (above the head it was out of sight). */
    private static final double OWN_COLUMN_AHEAD = 6.0;
    /** The coloured edge of the screen on every own key: a sign that covers nothing (ticks). */
    private static final int EDGE_TICKS = 12;

    private static final FxBudget BUDGET = new FxBudget();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<Integer, Integer> FLASH = new HashMap<>();
    /** The particles drawn here (weak: gone with the particle). */
    private static final java.util.Set<net.minecraft.client.particle.Particle> GLOWING =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final int[] RECEIVED = new int[6];
    /** Between OPEN and CLOSE: the source whose key runs, its place; -1 for none. */
    private static int openSource = -1;
    private static Vec3 openAt;
    private static int openColor;
    private static int openAccent;
    private static int boosted;
    private static int drawn;
    private static int shake;
    private static int edge;
    private static int edgeColor;
    private static int edgeAccent;
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
            openColor = p.color();
            openAccent = p.accent();
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
                int pulses = ownView(p.source()) ? 2 : 3;   // CI 1d981282a: archers' rain 28 % of the own view
                for (int pulse = 0; pulse < pulses; pulse++) {
                    later(pulse * 7L, () -> ring(level, p.source(), p.pos(), p.radius(), p.color(), p.accent(), DUST * 0.85f, 1.0, false));
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
        boolean own = ownView(p.source());
        if (!own) {   // CI 550877110 (berserker 18 %): from one's own eyes only the ring at the feet, no burst
            burst(level, p.source(), at, p.color(), p.accent(), ult ? 40 : 24, false);
        }
        // Chef 12.11. 18:10: every key visibly new - a ring in the class colour at the caster's feet, two pulses
        double feet = own ? CAST_RING : CAST_RING_OUTSIDE;
        for (int pulse = 0; pulse < 2; pulse++) {
            later(pulse * 5L, () -> ring(level, p.source(), at, feet, p.color(), p.accent(), own ? DUST * 0.8f : DUST, 1.4, true));
        }
        Minecraft mine = Minecraft.getInstance();
        if (mine.player != null && p.source() == mine.player.getId()) {
            // Chef 12.11. 20:10: one's own key also shows from one's own eyes without closing the view - the screen's edge
            // lights up in the class colour (CI 6a2f3ae4d: 0.4-0.9 % apart for six subclasses)
            edge = EDGE_TICKS;
            edgeColor = p.color();
            edgeAccent = p.accent();
        }
        if (!ult) {
            return;
        }
        // the column of light: three pulses; seen from one's own eyes it starts above the head (no wall in the face)
        // Prüfer 12.11. (shield fighter's charge: nothing new from one's own eyes): there a thin column stands ahead
        Vec3 ahead = at;
        if (own && src != null) {
            Vec3 look = src.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (flat.lengthSqr() > 1.0E-4) {
                ahead = at.add(flat.normalize().scale(OWN_COLUMN_AHEAD));
            }
        }
        final Vec3 columnAt = ahead;
        for (int pulse = 0; pulse < 3; pulse++) {
            later(pulse * 4L, () -> column(level, p.source(), columnAt, p.accent(), own));
        }
        if (!own && take(p.source(), 1) > 0) {
            level.addParticle(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | p.accent()), true, false,
                    at.x, at.y + 1.0, at.z, 0, 0, 0);
        }
        // the shock wave: a ring running outwards to the ultimate's real radius (8 blocks for one without an area)
        double reach = p.radius() > 0 ? Math.max(4.0, p.radius()) : SHOCK_RADIUS;
        for (int t = 0; t <= SHOCK_TICKS; t++) {
            double r = 1.0 + (reach - 1.0) * t / SHOCK_TICKS;
            if (own && r < 3.0) {
                continue;   // CI 1d981282a: paladin's ult 20 % of the own view - the wave starts beyond arm's length
            }
            // own view: around where the caster is now (a charge ran out of its own wave)
            later(t, () -> ring(level, p.source(), own && src != null && src.isAlive() ? src.position() : at, r,
                    p.color(), p.accent(), DUST, 0.5, false));
        }
        // its own sound: thunder over a deep beacon chord, loud enough over a fight
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.1f, 1.35f, false);
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4f, 0.6f, false);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && p.source() == mc.player.getId() && FxSettings.shake()) {
            shake = SHAKE_TICKS;
        }
    }

    /** True when the effects of this source are seen from its own eyes (the local player in first person). */
    private static boolean ownView(int source) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && source == mc.player.getId() && mc.options.getCameraType().isFirstPerson();
    }

    /**
     * The burst around the caster. From one's own eyes it stays low around the feet, fewer and smaller
     * (Chef 12.11. 18:10: nothing may close the view).
     */
    private static void burst(ClientLevel level, int source, Vec3 at, int color, int accent, int count, boolean own) {
        int n = take(source, own ? count / 2 : count);
        RandomSource rnd = level.getRandom();
        float size = own ? DUST * 0.6f : DUST;
        DustParticleOptions main = new DustParticleOptions(color, size);
        DustParticleOptions acc = new DustParticleOptions(accent, size * 0.8f);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = own ? 0.9 + rnd.nextDouble() * 0.6 : 0.35 + rnd.nextDouble() * 0.5;
            double h = own ? 0.1 + rnd.nextDouble() * 0.4 : 0.2 + rnd.nextDouble() * 1.8;
            add(level, i % 3 == 0 ? acc : main, at.x + Math.cos(a) * d, at.y + h,
                    at.z + Math.sin(a) * d, Math.cos(a) * 0.05, 0.06 + rnd.nextDouble() * 0.06, Math.sin(a) * 0.05);
        }
    }

    private static void column(ClientLevel level, int source, Vec3 at, int accent, boolean own) {
        int n = take(source, 40);
        // own view: ahead and thin (OWN) instead of above the head
        DustParticleOptions dust = new DustParticleOptions(accent, Math.min(4.0f, DUST * 1.4f) * (own ? OWN : 1.0f));
        double from = 0.0;
        for (int i = 0; i < n; i++) {
            double y = at.y + from + i * 0.35;
            add(level, i % 2 == 0 ? dust : ParticleTypes.END_ROD, at.x, y, at.z, 0, 0.02, 0);
        }
    }

    /** A ring on the ground: n points, as many as the budget gives, spread evenly. */
    private static void ring(ClientLevel level, int source, Vec3 c, double r, int color, int accent, float size, double density,
                             boolean feet) {
        int want = (int) Math.max(16, Math.min(140, Math.round(Math.PI * 2 * r * 2.2 * density)));
        int n = take(source, want);
        if (n <= 0) {
            return;
        }
        boolean own = ownView(source);
        Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
        float dot = own ? size * OWN : size;
        DustParticleOptions main = new DustParticleOptions(color, Math.min(4.0f, dot));
        DustParticleOptions acc = new DustParticleOptions(accent, Math.min(4.0f, dot));
        double phase = level.getRandom().nextDouble();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * (i + phase) / n;
            double x = c.x + Math.cos(a) * r;
            double z = c.z + Math.sin(a) * r;
            if (own && !feet && eye.distanceToSqr(x, c.y + 0.15, z) < OWN_CLEAR_NEW * OWN_CLEAR_NEW) {
                continue;   // CI 6a2f3ae4d: a ring running under one's own eyes filled a fifth of the view
            }
            add(level, i % 4 == 0 ? acc : main, x, c.y + 0.15, z, 0, 0.01, 0);
        }
    }

    /**
     * Struck monsters flash white and get a thin bolt of light in the class colour from their feet up
     * (Chef 12.11. 18:10: single target abilities like the electrician's chain must look new too).
     */
    private static void hits(ClientLevel level, CoreNetworking.FxPayload p) {
        boolean own = ownView(p.source());
        Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
        if (p.accent() == com.mki.nightsiege.core.FxColors.accent(com.mki.nightsiege.core.SkillClass.ENGINEER,
                com.mki.nightsiege.core.SkillTree.SUB_ELECTRICIAN)) {
            chain(level, p, eye, own);
        }
        DustParticleOptions dust = new DustParticleOptions(p.color(), own ? DUST * 0.7f : DUST);
        DustParticleOptions acc = new DustParticleOptions(p.accent(), DUST * 0.7f);
        for (int id : p.ids()) {
            Entity e = level.getEntity(id);
            if (!(e instanceof LivingEntity)) {
                continue;
            }
            FLASH.put(id, FLASH_TICKS);
            int n = take(p.source(), 16);
            double top = e.getBbHeight() + 1.2;
            for (int i = 0; i < n; i++) {
                double y = e.getY() + top * i / Math.max(1, n - 1);
                if (own && eye.distanceToSqr(e.getX(), y, e.getZ()) < OWN_CLEAR_NEW * OWN_CLEAR_NEW) {
                    continue;
                }
                add(level, i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : i % 3 == 1 ? acc : dust, e.getX(), y, e.getZ(), 0, 0.01, 0);
            }
        }
    }

    /**
     * Prüfer 12.11. (electrician's chain hardly seen from one's own eyes): a chain of sparks from the caster's chest
     * from target to target, in the order they were struck; nothing nearer than {@link #OWN_CLEAR_NEW} to one's eyes.
     */
    private static void chain(ClientLevel level, CoreNetworking.FxPayload p, Vec3 eye, boolean own) {
        Entity src = level.getEntity(p.source());
        Vec3 from = src != null ? src.position().add(0, src.getBbHeight() * 0.7, 0) : p.pos().add(0, 1.2, 0);
        DustParticleOptions acc = new DustParticleOptions(p.accent(), DUST * (own ? 0.6f : 0.8f));
        for (int id : p.ids()) {
            Entity e = level.getEntity(id);
            if (!(e instanceof LivingEntity)) {
                continue;
            }
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.6, 0);
            double len = from.distanceTo(to);
            int n = take(p.source(), (int) Math.min(48, Math.ceil(len / 0.3)));
            RandomSource rnd = level.getRandom();
            for (int i = 0; i < n; i++) {
                double f = (i + 0.5) / n;
                double x = from.x + (to.x - from.x) * f + (rnd.nextDouble() - 0.5) * 0.25;
                double y = from.y + (to.y - from.y) * f + (rnd.nextDouble() - 0.5) * 0.25;
                double z = from.z + (to.z - from.z) * f + (rnd.nextDouble() - 0.5) * 0.25;
                if (own && eye.distanceToSqr(x, y, z) < OWN_CLEAR_NEW * OWN_CLEAR_NEW) {
                    continue;
                }
                add(level, i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : acc, x, y, z, 0, 0, 0);
            }
            from = to;
        }
    }

    private static void add(ClientLevel level, ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz) {
        // CI 550877110: coloured dust took the night's light and was hardly seen; the new particles glow
        // (full light, also under shaders). Minecraft's particle setting still holds.
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.server.level.ParticleStatus status = mc.options.particles().get();
        if (status == net.minecraft.server.level.ParticleStatus.MINIMAL
                || status == net.minecraft.server.level.ParticleStatus.DECREASED && level.getRandom().nextInt(3) == 0) {
            return;
        }
        net.minecraft.client.particle.Particle made = mc.particleEngine.createParticle(particle, x, y, z, dx, dy, dz);
        if (made != null) {
            GLOWING.add(made);
            drawn++;
        }
    }

    /** True for the particles of these effects: drawn in full light (ParticleGlowMixin). */
    public static boolean glows(net.minecraft.client.particle.Particle particle) {
        return !GLOWING.isEmpty() && GLOWING.contains(particle);
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
        boolean bright = bright(p.getParticle());
        if (!bright) {
            // Chef 12.11. 20:10: from one's own eyes the old smoke, clouds and explosions may become smaller and fewer
            // (CI 6a2f3ae4d: the bombardment's eight huge blasts per hit covered half of the own view already before);
            // Prüfer 12.11.: in F5 and for other players too (walls of clouds), glowing dots instead
            ownDim(mc, p, eye, rnd, ownView(openSource));
            return true;
        }
        int count = p.getCount();
        boolean mass = count >= MASS;
        float speed = p.getMaxSpeed();
        if (count == 0) {
            spawnBig(mc, p, eye, bright, false, 0, p.getX(), p.getY(), p.getZ(), speed * p.getXDist(), speed * p.getYDist(), speed * p.getZDist());
            return true;
        }
        double more = mass ? 0.0 : (bright && !ownView(openSource) ? FxBudget.COUNT : FxBudget.DIM) - 1.0;
        int n = count + take(openSource, (int) Math.floor(count * more));
        for (int i = 0; i < n; i++) {
            double x = p.getX() + rnd.nextGaussian() * p.getXDist();
            double y = p.getY() + rnd.nextGaussian() * p.getYDist();
            double z = p.getZ() + rnd.nextGaussian() * p.getZDist();
            spawnBig(mc, p, eye, bright, mass, i, x, y, z, rnd.nextGaussian() * speed, rnd.nextGaussian() * speed, rnd.nextGaussian() * speed);
        }
        return true;
    }

    /**
     * Chef 12.11. 18:10: only glowing, coloured and thin particles grow by the full factors (dust, end rod,
     * sparks, crits, enchant, flames, souls, hearts); smoke, clouds, poofs and explosions at most ×1.25.
     */
    static boolean bright(ParticleOptions o) {
        if (o instanceof DustParticleOptions || o instanceof net.minecraft.core.particles.DustColorTransitionOptions) {
            return true;
        }
        net.minecraft.core.particles.ParticleType<?> t = o.getType();
        return t == ParticleTypes.END_ROD || t == ParticleTypes.ELECTRIC_SPARK || t == ParticleTypes.CRIT
                || t == ParticleTypes.ENCHANTED_HIT || t == ParticleTypes.ENCHANT || t == ParticleTypes.FLAME
                || t == ParticleTypes.SOUL_FIRE_FLAME || t == ParticleTypes.SOUL || t == ParticleTypes.HEART
                || t == ParticleTypes.HAPPY_VILLAGER || t == ParticleTypes.GLOW || t == ParticleTypes.WAX_ON
                || t == ParticleTypes.WAX_OFF || t == ParticleTypes.SCRAPE || t == ParticleTypes.TOTEM_OF_UNDYING
                || t == ParticleTypes.FIREWORK || t == ParticleTypes.WITCH || t == ParticleTypes.DRAGON_BREATH
                || t == ParticleTypes.SONIC_BOOM || t == ParticleTypes.NOTE || t == ParticleTypes.DAMAGE_INDICATOR;
    }

    private static void spawnBig(Minecraft mc, net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket p, Vec3 eye,
                                 boolean bright, boolean mass, int index, double x, double y, double z, double dx, double dy, double dz) {
        double dist2 = eye.distanceToSqr(x, y, z);
        if (!p.isOverrideLimiter() && dist2 > 1024.0) {
            return;   // as Minecraft: unforced particles only within 32 blocks
        }
        boolean own = ownView(openSource);
        boolean near = dist2 < (own ? OWN_NEAR * OWN_NEAR : NEAR * NEAR);
        if (near && !bright && index % 2 == 1) {
            return;   // smoke and clouds right before the eyes: every second one only (nothing closes the view)
        }
        if (own && dist2 < OWN_CLEAR * OWN_CLEAR && index % 2 == 1) {
            return;   // CI 6a2f3ae4d: the archers' rain circle right under one's own eyes - every second one only
        }
        net.minecraft.client.particle.Particle made = mc.particleEngine.createParticle(p.getParticle(), x, y, z, dx, dy, dz);
        if (made != null) {
            double size = near ? 1.0 : bright && !mass ? (own ? FxBudget.SIZE_OWN : FxBudget.SIZE) : FxBudget.DIM;
            double life = near ? 1.0 : bright ? FxBudget.LIFE : FxBudget.DIM;
            made.scale((float) size);
            made.setLifetime((int) Math.round(made.getLifetime() * life));
            boosted++;
        }
    }

    /**
     * From one's own eyes (full effects): a key's old smoke, clouds and poofs half as many and {@link #OWN_SMOKE} as big,
     * none nearer than {@link #OWN_CLEAR}; the huge explosion (eight big blasts around) one small blast.
     */
    private static void ownDim(Minecraft mc, net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket p, Vec3 eye,
                               RandomSource rnd, boolean own) {
        ParticleOptions o = p.getParticle();
        boolean force = p.isOverrideLimiter();
        float blast = own ? OWN_BLAST : FAR_BLAST;
        if (o.getType() == ParticleTypes.EXPLOSION_EMITTER) {
            spawnOwn(mc, ParticleTypes.EXPLOSION, eye, force, blast, own, p.getX(), p.getY(), p.getZ(), 0, 0, 0);
            if (!own) {
                glowFor(mc, p.getX(), p.getY(), p.getZ(), 6, 1.2, rnd);
            }
            return;
        }
        float size = o.getType() == ParticleTypes.EXPLOSION ? blast : own ? OWN_SMOKE : FAR_SMOKE;
        float speed = p.getMaxSpeed();
        if (p.getCount() == 0) {
            spawnOwn(mc, o, eye, force, size, own, p.getX(), p.getY(), p.getZ(), speed * p.getXDist(), speed * p.getYDist(), speed * p.getZDist());
            return;
        }
        int n = Math.max(1, p.getCount() / 2);
        for (int i = 0; i < n; i++) {
            spawnOwn(mc, o, eye, force, size, own, p.getX() + rnd.nextGaussian() * p.getXDist(), p.getY() + rnd.nextGaussian() * p.getYDist(),
                    p.getZ() + rnd.nextGaussian() * p.getZDist(), rnd.nextGaussian() * speed, rnd.nextGaussian() * speed, rnd.nextGaussian() * speed);
        }
        if (!own) {   // the left out half: small glowing dots in the class colour (they light, they close nothing)
            glowFor(mc, p.getX(), p.getY(), p.getZ(), p.getCount() - n, Math.max(0.3, Math.max(p.getXDist(), p.getZDist())), rnd);
        }
    }

    /** Small glowing dots in the colours of the running key around a spot, instead of smoke (budget of the source). */
    private static void glowFor(Minecraft mc, double x, double y, double z, int wanted, double spread, RandomSource rnd) {
        if (mc.level == null || wanted <= 0) {
            return;
        }
        int n = take(openSource, Math.min(24, wanted));
        DustParticleOptions main = new DustParticleOptions(openColor, DUST * 0.6f);
        DustParticleOptions acc = new DustParticleOptions(openAccent, DUST * 0.6f);
        for (int i = 0; i < n; i++) {
            add(mc.level, i % 3 == 0 ? acc : main, x + rnd.nextGaussian() * spread, y + rnd.nextGaussian() * 0.5,
                    z + rnd.nextGaussian() * spread, 0, 0.03, 0);
        }
    }

    private static void spawnOwn(Minecraft mc, ParticleOptions o, Vec3 eye, boolean force, float size, boolean own,
                                 double x, double y, double z, double dx, double dy, double dz) {
        double dist2 = eye.distanceToSqr(x, y, z);
        double clear = own ? OWN_CLEAR : NEAR;
        if (!force && dist2 > 1024.0 || dist2 < clear * clear) {
            return;
        }
        net.minecraft.client.particle.Particle made = mc.particleEngine.createParticle(o, x, y, z, dx, dy, dz);
        if (made != null) {
            made.scale(size);
            boosted++;
        }
    }

    /**
     * The edge of the screen after one's own key (full effects, every view): the class colour outside, the subclass
     * accent inside, one GUI pixel each, fading in the last ticks. Covers no part of the picture's middle.
     */
    public static void drawEdge(net.minecraft.client.gui.GuiGraphicsExtractor gui) {
        if (edge <= 0 || !FxSettings.full()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        float a = Math.min(1f, edge / 4f);
        frame(gui, 0, w, h, ((int) (0xE0 * a) << 24) | (edgeColor & 0xFFFFFF));
        frame(gui, 1, w, h, ((int) (0x90 * a) << 24) | (edgeAccent & 0xFFFFFF));
    }

    private static void frame(net.minecraft.client.gui.GuiGraphicsExtractor gui, int in, int w, int h, int color) {
        gui.fill(in, in, w - in, in + 1, color);
        gui.fill(in, h - in - 1, w - in, h - in, color);
        gui.fill(in, in + 1, in + 1, h - in - 1, color);
        gui.fill(w - in - 1, in + 1, w - in, h - in - 1, color);
    }

    /** Tests: the screen's edge lights up now. */
    public static boolean edgeForTests() {
        return edge > 0;
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
            edge = 0;
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
        if (edge > 0) {
            edge--;
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
