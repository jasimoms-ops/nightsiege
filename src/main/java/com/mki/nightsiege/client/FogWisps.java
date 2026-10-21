package com.mki.nightsiege.client;

import com.mki.nightsiege.game.EventAtmosphere;
import com.mki.nightsiege.game.ModContent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Real mist in the world: big, faint, slowly drifting wisps near the ground.
 * They are particles, so every shader pack draws them, hidden behind walls
 * and hills like everything else.
 */
public final class FogWisps {

    private FogWisps() {
    }

    /** Spawns new wisps around the camera while a misty event is shown. */
    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        EventAtmosphere.State state = EventAtmosphere.client();
        float s = state.strength();
        String event = state.shown();
        int perTick;
        float r;
        float g;
        float b;
        float alpha;
        switch (event) {
            case EventAtmosphere.FOG -> {
                perTick = 3;
                r = 0.72f;
                g = 0.74f;
                b = 0.76f;
                alpha = 0.34f;
            }
            case EventAtmosphere.FROST -> {
                perTick = 1;
                r = 0.86f;
                g = 0.92f;
                b = 1.0f;
                alpha = 0.18f;
            }
            case EventAtmosphere.NETHER_RIFT -> {
                perTick = 1;
                r = 0.38f;
                g = 0.16f;
                b = 0.14f;
                alpha = 0.22f;
            }
            default -> {
                return;
            }
        }
        if (s < 0.2f) {
            return;
        }
        RandomSource random = level.getRandom();
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        for (int i = 0; i < perTick; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 3.0 + random.nextDouble() * 34.0;
            double x = eye.x + Math.cos(angle) * distance;
            double z = eye.z + Math.sin(angle) * distance;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            // Under a roof the wisps stay outside; in a cave there are none.
            if (ground > eye.y + 12 || !level.isLoaded(BlockPos.containing(x, ground, z))) {
                continue;
            }
            double y = Math.max(ground, eye.y - 6) + 0.4 + random.nextDouble() * 2.6;
            Particle particle = mc.particleEngine.createParticle(ModContent.FOG_WISP, x, y, z,
                    0.012 + random.nextDouble() * 0.01, 0.0, 0.004 - random.nextDouble() * 0.008);
            if (particle instanceof Wisp wisp) {
                wisp.look(r, g, b, alpha * Math.min(1f, s));
            }
        }
    }

    /** One wisp: fades in, drifts with the wind, fades out. */
    public static final class Wisp extends SingleQuadParticle {

        private float peak = 0.3f;

        Wisp(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite,
             RandomSource random) {
            super(level, x, y, z, sprite);
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            this.friction = 1.0f;
            this.gravity = 0.0f;
            this.hasPhysics = false;
            this.lifetime = 220 + random.nextInt(140);
            this.scale(28.0f + random.nextFloat() * 22.0f);
            this.roll = random.nextFloat() * (float) Math.PI * 2f;
            this.oRoll = this.roll;
            this.alpha = 0f;
        }

        void look(float r, float g, float b, float alpha) {
            setColor(r, g, b);
            this.peak = alpha;
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            this.oRoll = this.roll;
            if (this.age++ >= this.lifetime) {
                this.remove();
                return;
            }
            float t = this.age / (float) this.lifetime;
            float fade = Math.min(1f, Math.min(t / 0.25f, (1f - t) / 0.3f));
            this.alpha = this.peak * Math.max(0f, fade);
            this.roll += 0.0015f;
            this.move(this.xd, this.yd, this.zd);
        }

        @Override
        protected SingleQuadParticle.Layer getLayer() {
            return SingleQuadParticle.Layer.TRANSLUCENT;
        }
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
            return new Wisp(level, x, y, z, xd, yd, zd, sprites.get(random), random);
        }
    }
}
