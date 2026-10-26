package com.mki.nightsiege.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** The client side of the healers' life view: whom to show, and the line under the name. */
public final class HealthViewClient {

    private static final Set<UUID> SHOWN = new HashSet<>();

    private HealthViewClient() {
    }

    /** The server's list (comma separated ids, empty for nothing). */
    public static void set(String ids) {
        SHOWN.clear();
        if (ids == null || ids.isBlank()) {
            return;
        }
        for (String id : ids.split(",")) {
            try {
                SHOWN.add(UUID.fromString(id.trim()));
            } catch (IllegalArgumentException ignored) {
                // a broken id is skipped
            }
        }
    }

    public static void clear() {
        SHOWN.clear();
    }

    /** "♥ 14/20" in green, yellow or red for a shown defender, or null. */
    public static Component line(Player player) {
        if (SHOWN.isEmpty() || !SHOWN.contains(player.getUUID())) {
            return null;
        }
        float max = Math.max(1f, player.getMaxHealth());
        float now = Math.max(0f, player.getHealth());
        float share = now / max;
        ChatFormatting color = share > 0.6f ? ChatFormatting.GREEN : share > 0.3f ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return Component.literal("♥ " + (int) Math.ceil(now) + "/" + Math.round(max)).withStyle(color);
    }
}
