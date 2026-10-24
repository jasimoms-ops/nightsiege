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

    /** Only the key part ("R", "Sneak + Right Button"), or null. */
    public static Component keys(KeyHint hint) {
        if (hint == null || hint == KeyHint.NONE) {
            return null;
        }
        Options o = Minecraft.getInstance().options;
        KeyMapping[] used = switch (hint) {
            case ULT -> new KeyMapping[]{NightSiegeClient.ultimateKey()};
            case ULT2 -> new KeyMapping[]{GainClient.secondUltKey()};
            case QUIVER -> new KeyMapping[]{GainClient.quiverKey()};
            case AIR_JUMP -> new KeyMapping[]{o.keyJump};
            case USE, USE_BLOCK, USE_MATE, DRAW -> new KeyMapping[]{o.keyUse};
            case USE_MATE_OR_SHIELD, SHIELD_ACTION -> new KeyMapping[]{o.keyUse, o.keyAttack};
            case SNEAK -> new KeyMapping[]{o.keyShift};
            case SNEAK_BLOCK, SNEAK_USE, REMOTE -> new KeyMapping[]{o.keyShift, o.keyUse};
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
