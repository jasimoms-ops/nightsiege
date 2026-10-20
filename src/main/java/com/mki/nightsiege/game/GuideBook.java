package com.mki.nightsiege.game;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/** The written guide every player receives the first time they join. */
public final class GuideBook {

    /** How many pages the book has. Each one has its own translation key. */
    private static final int PAGES = 14;

    private GuideBook() {
    }

    public static ItemStack create() {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int i = 1; i <= PAGES; i++) {
            pages.add(Filterable.passThrough(Component.translatable("nightsiege.book.page" + i)));
        }
        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough(Component.translatable("nightsiege.book.title").getString()),
                "MKI96",
                0,
                pages,
                true);
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return book;
    }
}
