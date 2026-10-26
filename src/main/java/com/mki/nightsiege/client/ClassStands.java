package com.mki.nightsiege.client;

import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The armour stands of the class windows: one picture per subclass outfit, drawn by
 * tools/armor/stands.py from the outfit textures and their 3D parts, and a shadow of each
 * for locked classes. All pictures share one frame, so stands line up on their base.
 */
public final class ClassStands {

    public static final int W = 192;
    public static final int H = 284;

    private ClassStands() {
    }

    /** True when there is a picture for this outfit. */
    public static boolean has(String look) {
        return look != null && ClassLooks.LOOKS.contains(look);
    }

    /** The outfit shown for a class without a subclass: its first one. */
    public static String firstOf(SkillClass clazz) {
        List<SkillTree.SubclassInfo> subs = SkillTree.subclassesOf(clazz);
        return subs.isEmpty() ? "" : subs.get(0).key();
    }

    /** Draws a stand into the box (aspect kept), centred and standing on the box's bottom edge. */
    public static void draw(GuiGraphicsExtractor gui, String look, boolean shadow, int x, int y, int w, int h) {
        if (!has(look) || w <= 0 || h <= 0) {
            return;
        }
        float scale = Math.min(w / (float) W, h / (float) H);
        int dw = Math.max(1, Math.round(W * scale));
        int dh = Math.max(1, Math.round(H * scale));
        Identifier texture = Identifier.fromNamespaceAndPath("nightsiege",
                "textures/gui/stands/" + look + (shadow ? "_shadow" : "") + ".png");
        gui.blit(RenderPipelines.GUI_TEXTURED, texture, x + (w - dw) / 2, y + h - dh, 0f, 0f, dw, dh, W, H, W, H);
    }
}
