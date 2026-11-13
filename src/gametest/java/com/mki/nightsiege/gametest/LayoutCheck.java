package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.PurpleUi;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Paket 13.11b: the open window in English and German at GUI 2 and 3 (window 1280x720), one picture each
 * ("name_en_us_gui2" ...), and the footer row checked: no button over another, every label whole (at most shrunk).
 */
final class LayoutCheck {

    private LayoutCheck() {
    }

    /**
     * Runs the four looks of the open screen. {@code footer}: check the buttons in the row of "Done".
     * {@code each} runs after every re-layout (before the picture), e.g. for a screen's own check. Returns pictures.
     */
    static int matrix(ClientGameTestContext context, String name, boolean footer, BiConsumer<Boolean, String> check,
                      Consumer<String> each) {
        int[] window = context.computeOnClient(client -> new int[] {client.getWindow().getWidth(), client.getWindow().getHeight()});
        int scaleBefore = context.computeOnClient(client -> client.options.guiScale().get());
        String langBefore = context.computeOnClient(client -> client.getLanguageManager().getSelected());
        context.getInput().resizeWindow(1280, 720);
        context.waitTicks(5);
        int pics = 0;
        try {
            for (String lang : new String[] {"en_us", "de_de"}) {
                language(context, lang);
                for (int scale : new int[] {2, 3}) {
                    context.runOnClient(client -> {
                        client.options.guiScale().set(scale);
                        client.resizeGui();
                    });
                    context.waitTicks(5);
                    int real = context.computeOnClient(client -> client.getWindow().getGuiScale());
                    String tag = name + " " + lang + " gui " + scale;
                    check.accept(real == scale, tag + ": real GUI scale " + real);
                    boolean open = context.computeOnClient(client -> client.gui.screen() != null);
                    check.accept(open, tag + ": the window is open");
                    if (footer && open) {
                        List<String> problems = footer(context);
                        check.accept(problems.isEmpty(), tag + ": footer without overlap or cut text " + problems);
                    }
                    if (each != null) {
                        each.accept(tag);
                    }
                    int windowH = context.computeOnClient(client -> client.getWindow().getScreenHeight());
                    context.getInput().setCursorPos(2, Math.max(2, windowH - 2));   // no tooltip in the picture
                    context.waitTicks(3);
                    Path p = context.takeScreenshot(name + "_" + lang + "_gui" + scale);
                    pics += p != null && Files.exists(p) ? 1 : 0;
                }
            }
        } finally {
            // Language, scale and window back even when a step throws (Pruefer 13.11b).
            language(context, langBefore);
            context.runOnClient(client -> {
                client.options.guiScale().set(scaleBefore);
                client.resizeGui();
            });
            context.getInput().resizeWindow(window[0], window[1]);
            context.waitTicks(5);
        }
        check.accept(pics == 4, name + ": " + pics + " of 4 pictures written");
        return pics;
    }

    /** The buttons in the row of "Done": pairs that overlap, labels that do not fit, buttons off the screen. */
    static List<String> footer(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            List<String> out = new ArrayList<>();
            var screen = client.gui.screen();
            if (screen == null) {
                out.add("no window");
                return out;
            }
            String done = Component.translatable("gui.done").getString();
            List<Button> all = new ArrayList<>();
            int doneY = Integer.MIN_VALUE;
            for (GuiEventListener child : screen.children()) {
                if (child instanceof Button b && b.visible && !b.getMessage().getString().isEmpty()) {
                    all.add(b);
                    if (b.getMessage().getString().equals(done)) {
                        doneY = b.getY();
                    }
                }
            }
            if (doneY == Integer.MIN_VALUE) {
                out.add("no Done button");
                return out;
            }
            List<Button> row = new ArrayList<>();
            for (Button b : all) {
                if (b.getY() == doneY) {
                    row.add(b);
                }
            }
            for (int i = 0; i < row.size(); i++) {
                Button a = row.get(i);
                String ta = a.getMessage().getString();
                if (!PurpleUi.fitsButton(client.font, ta, a.getWidth())) {
                    out.add("'" + ta + "' cut (" + a.getWidth() + " px for " + client.font.width(ta) + ")");
                }
                if (a.getX() < 0 || a.getX() + a.getWidth() > screen.width) {
                    out.add("'" + ta + "' off the screen");
                }
                for (int j = i + 1; j < row.size(); j++) {
                    Button b = row.get(j);
                    if (a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth()) {
                        out.add("'" + ta + "' over '" + b.getMessage().getString() + "'");
                    }
                }
            }
            // 13.11b: the core window's hint text must not lie under a button (a letter peeked out between them).
            int[] t = screen instanceof com.mki.nightsiege.client.CoreScreen ? com.mki.nightsiege.client.CoreScreen.testFooterText : null;
            if (t != null) {
                for (Button b : all) {
                    if (t[0] < b.getX() + b.getWidth() && b.getX() < t[2] && t[1] < b.getY() + b.getHeight() && b.getY() < t[3]) {
                        out.add("hint text " + t[0] + ".." + t[2] + " under '" + b.getMessage().getString() + "'");
                    }
                }
            }
            System.out.println("[layout] footer " + row.size() + " buttons: " + describe(row)
                    + (t == null ? " hint: none" : " hint: " + t[0] + ".." + t[2]));
            return out;
        });
    }

    private static String describe(List<Button> row) {
        StringBuilder sb = new StringBuilder();
        for (Button b : row) {
            sb.append('[').append(b.getMessage().getString()).append(' ').append(b.getX()).append('+').append(b.getWidth()).append(']');
        }
        return sb.toString();
    }

    /** Switches the client's language without a full resource reload (only the texts), as in the scene steckbriefe. */
    static void language(ClientGameTestContext context, String code) {
        context.runOnClient(client -> {
            client.getLanguageManager().setSelected(code);
            client.options.languageCode = code;
            client.getLanguageManager().onResourceManagerReload(client.getResourceManager());
        });
        context.waitTicks(5);
    }
}
