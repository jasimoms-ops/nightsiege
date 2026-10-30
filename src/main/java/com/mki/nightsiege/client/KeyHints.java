package com.mki.nightsiege.client;

import com.mki.nightsiege.core.KeyHint;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Turns a {@link KeyHint} into the line shown under a description ("Key: R"), with the
 * player's own key bindings, so a rebound key shows as it really is.
 */
public final class KeyHints {

    private KeyHints() {
    }

    /** The whole line ("Taste: ..."), or null when the thing needs no key. */
    public static Component line(KeyHint hint) {
        Component keys = keys(hint);
        if (keys == null) {
            return null;
        }
        return Component.translatable("nightsiege.keyhint.prefix", keys).withStyle(ChatFormatting.AQUA);
    }

    /**
     * The line for a skill or crate gain: always there (Simon, 26 October 2026), the key
     * in aqua, or a gray "no key of its own" when it works by itself. Starts with its
     * colour code, for the screens that build their text from § strings.
     */
    public static String always(KeyHint hint) {
        Component keys = line(hint);
        if (keys != null) {
            return "§b" + keys.getString();
        }
        return "§8" + Component.translatable("nightsiege.keyhint.none_line").getString();
    }

    /** Only the key part ("R", "Sneak + Right Button"), or null. */
    public static Component keys(KeyHint hint) {
        if (hint == null || hint == KeyHint.NONE) {
            return null;
        }
        Options o = Minecraft.getInstance().options;
        KeyMapping[] used = switch (hint) {
            case ULT, ULT_LOOK, ULT_UPGRADE -> new KeyMapping[]{NightSiegeClient.ultimateKey()};
            case ULT_SHOT -> new KeyMapping[]{NightSiegeClient.ultimateKey(), o.keyUse};
            case ULT2, ULT2_REMOTE, ULT2_STAFF -> new KeyMapping[]{GainClient.secondUltKey()};
            case STAFF_SNEAK_X -> new KeyMapping[]{GainClient.orderKey(), o.keyShift};
            case QUIVER -> new KeyMapping[]{GainClient.quiverKey()};
            case GAIN_KEY -> new KeyMapping[]{GainClient.gainKey(0), GainClient.gainKey(1), GainClient.gainKey(2)};
            case QUIVER_SHOT -> new KeyMapping[]{GainClient.quiverKey(), o.keyUse};
            case AIR_JUMP -> new KeyMapping[]{o.keyJump};
            case JUMP_ATTACK -> new KeyMapping[]{o.keyJump, o.keyAttack};
            case USE, USE_BLOCK, PLACE_TRAP, USE_MATE, DRAW, BLOCK, PERFECT_BLOCK -> new KeyMapping[]{o.keyUse};
            // the remote (27 October 2026): overcharge and mark on Fähigkeit 2, the right click is the tower menu
            case REMOTE_BOOST, REMOTE_MARK -> new KeyMapping[]{GainClient.orderKey()};
            // the staff's own attack is the left click (27 October 2026)
            case STAFF_ATTACK -> new KeyMapping[]{o.keyAttack};
            // the summoner's staff (26 October 2026): the call, the spells
            case STAFF_CALL -> new KeyMapping[]{o.keyUse, o.keyShift, GainClient.quiverKey()};
            case STAFF_SPELL -> new KeyMapping[]{GainClient.quiverKey(), o.keyShift, o.keyUse};
            case USE_MATE_OR_SHIELD, SHIELD_ACTION -> new KeyMapping[]{o.keyUse, o.keyAttack};
            case SNEAK, SNEAK_SHOOT -> new KeyMapping[]{o.keyShift};
            case SNEAK_BLOCK -> new KeyMapping[]{o.keyShift, o.keyUse};
            case SNEAK_USE, REMOTE_DETONATE, STAFF_X_MINION -> new KeyMapping[]{GainClient.orderKey()};
            case REMOTE_RELOAD -> new KeyMapping[]{o.keyUse};
            // the X key does what sneak + right click did with the remote and the staff (25 October 2026)
            case REMOTE -> new KeyMapping[]{GainClient.orderKey(), o.keyUse, GainClient.secondUltKey()};
            case STAFF -> new KeyMapping[]{GainClient.orderKey(), o.keyUse, o.keyShift, GainClient.quiverKey(), GainClient.secondUltKey(),
                    o.keyAttack};
            // the ability bar (28 October 2026): the alchemist's potions and acid flask, the engineer's escape
            case BAR -> new KeyMapping[]{GainClient.slotKey(0), GainClient.slotKey(GainClient.SLOTS - 1)};
            case BAR_FIRST -> new KeyMapping[]{GainClient.slotKey(0)};
            default -> new KeyMapping[0];
        };
        Object[] names = new Object[used.length];
        for (int i = 0; i < used.length; i++) {
            names[i] = used[i] == null ? Component.literal("?")
                    : used[i].getTranslatedKeyMessage().copy().withStyle(ChatFormatting.WHITE);
        }
        MutableComponent text = Component.translatable("nightsiege.keyhint." + hint.name().toLowerCase(java.util.Locale.ROOT), names);
        return text;
    }
}
