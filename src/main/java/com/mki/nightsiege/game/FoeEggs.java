package com.mki.nightsiege.game;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The spawn eggs of the new attackers (update 1, 27 October 2026): one per kind and one for the camel troop. */
public final class FoeEggs {

    private static final List<Item> EGGS = new ArrayList<>();

    private FoeEggs() {
    }

    public static void register() {
        for (Foes.Role role : Foes.Role.values()) {
            add("foe_" + role.id(), role.entryId());
        }
        add("foe_camel_troop", com.mki.nightsiege.core.SiegeConfig.CAMEL_TROOP);
    }

    private static void add(String path, String entryId) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ModContent.id(path));
        Item egg = new FoeEggItem(entryId, new Item.Properties().setId(key).rarity(net.minecraft.world.item.Rarity.UNCOMMON));
        Registry.register(BuiltInRegistries.ITEM, key, egg);
        EGGS.add(egg);
    }

    public static List<Item> all() {
        return Collections.unmodifiableList(EGGS);
    }
}
