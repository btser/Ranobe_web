package main.service;

import main.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReadingNavigationTest {
    @Test
    void skipsEmptyHiddenVolumesAndNavigatesAcrossVolumes() {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "productId", 1);
        product.setTitle("Truyen");
        Volume empty = volume(1, 0, product);
        Volume first = volume(2, 1, product);
        Volume second = volume(3, 2, product);
        Volume hidden = volume(4, 3, product);
        hidden.setDeleted(true);
        Chapter a = chapter(11, 0, first);
        Chapter b = chapter(12, 1, first);
        Chapter c = chapter(13, 0, second);
        Chapter removed = chapter(14, 2, first);
        removed.setDeleted(true);
        first.setChapters(List.of(b, removed, a));
        second.setChapters(List.of(c));
        hidden.setChapters(List.of(chapter(15, 0, hidden)));
        var result = ReadingNavigation.build(product.getPath(), List.of(second, hidden, empty, first), 12);
        assertEquals(List.of(11, 12, 13), result.chapters().stream().map(ReadingNavigation.Link::chapterId).toList());
        assertEquals(11, result.previous().chapterId());
        assertEquals(13, result.next().chapterId());
        assertNull(ReadingNavigation.build(product.getPath(), List.of(first, second), 11).previous());
        assertNull(ReadingNavigation.build(product.getPath(), List.of(first, second), 13).next());
    }

    @Test
    void breaksOrderingTiesByIdAndHandlesNoChapters() {
        Product product = new Product();
        Volume volume = volume(1, 0, product);
        assertTrue(ReadingNavigation.build(product.getPath(), List.of(volume), null).chapters().isEmpty());
        volume.setChapters(List.of(chapter(3, 0, volume), chapter(2, 0, volume)));
        assertEquals(2, ReadingNavigation.build(product.getPath(), List.of(volume), null).chapters().get(0).chapterId());
    }

    @Test
    void doesNotLoadParentProductFromDetachedVolume() {
        Volume volume = new Volume() {
            @Override public Product getProduct() {
                throw new IllegalStateException("Parent relation is not loaded");
            }
        };
        volume.setChapters(List.of(chapter(8, 0, volume)));
        assertTrue(ReadingNavigation.build("/truyen/1/truyen", List.of(volume), 8)
                .chapters().get(0).path().startsWith("/truyen/1/truyen/chuong/8/"));
    }

    private static Volume volume(int id, int order, Product product) {
        Volume volume = new Volume(id, product);
        volume.setSortOrder(order);
        return volume;
    }

    private static Chapter chapter(int id, int order, Volume volume) {
        Chapter chapter = new Chapter(id, volume, "Text");
        chapter.setTitle("Chuong " + id);
        chapter.setSortOrder(order);
        return chapter;
    }
}
