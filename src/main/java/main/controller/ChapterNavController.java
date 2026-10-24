package main.controller;

import main.entity.Chapter;
import main.repository.ChapterRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Nút chương trước / chương sau: tìm chương kề trong cùng tập rồi chuyển hướng. Hết chương thì về trang truyện. */
@Controller
public class ChapterNavController {

    private final ChapterRepository chapterRepository;

    public ChapterNavController(ChapterRepository chapterRepository) {
        this.chapterRepository = chapterRepository;
    }

    @GetMapping("/products/chapter/{chapterId}/prev")
    public String prev(@PathVariable("chapterId") int chapterId) {
        return go(chapterId, -1);
    }

    @GetMapping("/products/chapter/{chapterId}/next")
    public String next(@PathVariable("chapterId") int chapterId) {
        return go(chapterId, 1);
    }

    private String go(int chapterId, int delta) {
        Chapter cur = chapterRepository.findByIdWithVolumeProductUploader(chapterId).orElse(null);
        if (cur == null) {
            return "redirect:/";
        }
        List<Chapter> list = new ArrayList<>(chapterRepository
                .findByVolumeVolumeIdAndIsDeletedFalseOrderBySortOrderAsc(cur.getVolume().getVolumeId()));
        list.sort(Comparator.comparingInt(Chapter::getSortOrder).thenComparingInt(Chapter::getChapterId));
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getChapterId() == chapterId) {
                int j = i + delta;
                if (j >= 0 && j < list.size()) {
                    return "redirect:/products/chapter/" + list.get(j).getChapterId();
                }
                break;
            }
        }
        return "redirect:/products/" + cur.getVolume().getProduct().getProductId();
    }
}