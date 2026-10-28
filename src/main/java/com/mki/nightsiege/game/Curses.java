package com.mki.nightsiege.game;

import com.mki.nightsiege.core.CurseKind;
import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The curse altar (update 2 "Fluch & Rache", Simon 28 October 2026: "Altar im Core-Fenster"): before
 * a night the defenders lay up to {@code curseMax} curse stones on the altar. Each makes the night
 * harder; a won night pays for each one (better crate odds, a chance of a second crate, skill points).
 * The stones lie for the next real night only and are taken off when it is over.
 */
public final class Curses {

    private static final EnumSet<CurseKind> ACTIVE = EnumSet.noneOf(CurseKind.class);
    private static boolean night;

    private Curses() {
    }

    /** The stones on the altar for the next night. */
    public static List<CurseKind> chosen(SiegeSave save) {
        List<CurseKind> out = new ArrayList<>();
        if (save == null || save.curses == null) {
            return out;
        }
        for (String id : save.curses) {
            CurseKind k = CurseKind.byId(id);
            if (k != null && !out.contains(k)) {
                out.add(k);
            }
        }
        return out;
    }

    /**
     * A defender lays a stone on the altar or takes it off. Returns false (with a message) when
     * the altar is sealed (a night runs) or full.
     */
    public static boolean toggle(SiegeManager m, ServerPlayer player, String id) {
        SiegeConfig cfg = m.config();
        CurseKind kind = CurseKind.byId(id);
        if (kind == null || !cfg.cursesEnabled) {
            return false;
        }
        if (m.isActive()) {
            player.sendSystemMessage(Component.translatable("nightsiege.curse.sealed").withStyle(ChatFormatting.RED));
            return false;
        }
        SiegeSave save = m.save();
        if (save.curses == null) {
            save.curses = new ArrayList<>();
        }
        List<CurseKind> now = chosen(save);
        boolean on;
        if (now.contains(kind)) {
            now.remove(kind);
            on = false;
        } else {
            if (now.size() >= cfg.curseMax) {
                player.sendSystemMessage(Component.translatable("nightsiege.curse.full", cfg.curseMax).withStyle(ChatFormatting.RED));
                return false;
            }
            now.add(kind);
            on = true;
        }
        save.curses.clear();
        for (CurseKind k : now) {
            save.curses.add(k.id());
        }
        m.persist();
        MinecraftServer server = player.level().getServer();
        Component msg = Component.translatable(on ? "nightsiege.curse.laid" : "nightsiege.curse.taken",
                player.getScoreboardName(), Component.translatable(kind.translationKey()), now.size(), cfg.curseMax)
                .withStyle(on ? ChatFormatting.DARK_PURPLE : ChatFormatting.GRAY);
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (m.isRegistered(p)) {
                    p.sendSystemMessage(msg);
                }
            }
        }
        player.level().playSound(null, player.blockPosition(), on ? SoundEvents.RESPAWN_ANCHOR_CHARGE : SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                SoundSource.BLOCKS, 0.7f, on ? 0.7f : 1.2f);
        return true;
    }

    // ------------------------------------------------------------------ the night

    /** A night begins: the stones on the altar act from now on (also in a test night). */
    public static void begin(List<CurseKind> curses) {
        ACTIVE.clear();
        if (curses != null) {
            ACTIVE.addAll(curses);
        }
        night = true;
    }

    public static void end() {
        ACTIVE.clear();
        night = false;
    }

    public static boolean has(CurseKind kind) {
        return night && ACTIVE.contains(kind);
    }

    /** Curses on the running night. */
    public static int count() {
        return night ? ACTIVE.size() : 0;
    }

    public static List<CurseKind> active() {
        return night ? new ArrayList<>(ACTIVE) : List.of();
    }

    private static SiegeConfig cfg() {
        SiegeManager m = SiegeManager.get();
        return m == null ? null : m.config();
    }

    private static double when(CurseKind kind, java.util.function.ToDoubleFunction<SiegeConfig> value) {
        SiegeConfig cfg = cfg();
        return cfg == null || !has(kind) ? 1.0 : value.applyAsDouble(cfg);
    }

    /** Monster life (Zähe Brut). */
    public static double healthFactor() {
        return when(CurseKind.TOUGH, c -> c.curseHealth);
    }

    /** Monster damage (Blutdurst). */
    public static double damageFactor() {
        return when(CurseKind.BLOODTHIRST, c -> c.curseDamage);
    }

    /** Monsters per wave (Übermacht). */
    public static double mobFactor() {
        return when(CurseKind.HORDE, c -> c.curseMobs);
    }

    /** Monster speed (Eile). */
    public static double speedFactor() {
        return when(CurseKind.HASTE, c -> c.curseSpeed);
    }

    /** Healing of the defenders (Siechtum). */
    public static double healFactor() {
        return when(CurseKind.BLIGHT, c -> c.curseHeal);
    }

    /** The time between two waves (Sturmlauf). */
    public static double waveFactor() {
        return when(CurseKind.ONSLAUGHT, c -> c.curseWaves);
    }

    /** Towers and traps (Stumpfe Fallen). */
    public static double trapFactor() {
        return when(CurseKind.BLUNT, c -> c.curseTraps);
    }

    /** Champions every wave brings (Champions). */
    public static int championsPerWave() {
        return has(CurseKind.CHAMPIONS) ? 1 : 0;
    }

    // ------------------------------------------------------------------ the reward

    /** The extra chance of a second crate for a night won under these curses. */
    public static double extraCrateChance(SiegeConfig cfg, int curses) {
        return cfg == null ? 0.0 : Math.max(0, curses) * cfg.curseCrateChance;
    }

    /** Skill points for every defender still standing. */
    public static int points(SiegeConfig cfg, int curses) {
        return cfg == null ? 0 : Math.max(0, curses) * cfg.cursePoints;
    }

    /** The bar's mark of a cursed night: "☠ 2". */
    public static MutableComponent barMark() {
        return Component.literal(" ☠" + count()).withStyle(ChatFormatting.DARK_PURPLE);
    }

    /** "Zähe Brut, Übermacht" for the messages. */
    public static MutableComponent names(List<CurseKind> curses) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < curses.size(); i++) {
            if (i > 0) {
                out.append(Component.literal(", "));
            }
            out.append(Component.translatable(curses.get(i).translationKey()));
        }
        return out;
    }
}
