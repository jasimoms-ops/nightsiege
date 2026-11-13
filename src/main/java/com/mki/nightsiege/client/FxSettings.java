package com.mki.nightsiege.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Z4 (Simon 12.11.): the client's own effect options, in {@code config/nightsiege-client.json}.
 * "Effekte: voll / reduziert" (reduziert = the old look before Z4) and the camera shake of one's own
 * ultimate, on or off. Set in the game with {@code /effekte voll|reduziert} and
 * {@code /effekte wackeln an|aus} (English: {@code /effects full|reduced}, {@code /effects shake on|off}).
 */
public final class FxSettings {

    /** What is stored. */
    public static final class Data {
        public boolean full = true;    // Chef 12.11. 22:20: "voll" as default after the second review passed (pruefer.md, Runde 2)
        public boolean shake = true;
        public int cross = 1;          // Tor 10: the war leader's cross, 0 small, 1 normal, 2 large (Esc menu of the view)
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data;

    private FxSettings() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("nightsiege-client.json");
    }

    private static Data data() {
        if (data == null) {
            data = new Data();
            try {
                Path f = file();
                if (Files.exists(f)) {
                    Data read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
                    if (read != null) {
                        data = read;
                    }
                }
            } catch (Exception e) {
                data = new Data();   // a broken file: the defaults, it is written anew on the next change
            }
        }
        return data;
    }

    /** True: the new full effects; false: the old look. */
    public static boolean full() {
        return data().full;
    }

    /** Camera shake on one's own ultimate. */
    public static boolean shake() {
        return data().shake;
    }

    /** The war leader's cross size: 0 small, 1 normal, 2 large. */
    public static int crossSize() {
        return Math.max(0, Math.min(2, data().cross));
    }

    public static void setCrossSize(int size) {
        data().cross = Math.max(0, Math.min(2, size));
        save();
    }

    public static void setFull(boolean full) {
        data().full = full;
        save();
    }

    public static void setShake(boolean shake) {
        data().shake = shake;
        save();
    }

    private static void save() {
        try {
            Files.writeString(file(), GSON.toJson(data()), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // the option still holds for this session
        }
    }

    /** The client commands /effekte and /effects. */
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> {
            command(dispatcher, "effekte", "voll", "reduziert", "wackeln", "an", "aus");
            command(dispatcher, "effects", "full", "reduced", "shake", "on", "off");
        });
    }

    private static void command(CommandDispatcher<FabricClientCommandSource> dispatcher, String name, String fullWord,
                                String reducedWord, String shakeWord, String on, String off) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal(name)
                .executes(c -> tell(c.getSource()))
                .then(ClientCommands.literal(fullWord).executes(c -> {
                    setFull(true);
                    return tell(c.getSource());
                }))
                .then(ClientCommands.literal(reducedWord).executes(c -> {
                    setFull(false);
                    return tell(c.getSource());
                }))
                .then(ClientCommands.literal(shakeWord)
                        .then(ClientCommands.literal(on).executes(c -> {
                            setShake(true);
                            return tell(c.getSource());
                        }))
                        .then(ClientCommands.literal(off).executes(c -> {
                            setShake(false);
                            return tell(c.getSource());
                        })));
        dispatcher.register(root);
    }

    private static int tell(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("nightsiege.fx.state",
                Component.translatable(full() ? "nightsiege.fx.full" : "nightsiege.fx.reduced"),
                Component.translatable(shake() ? "nightsiege.fx.on" : "nightsiege.fx.off")));
        return 1;
    }
}
