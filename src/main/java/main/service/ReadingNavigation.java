package main.service;

import main.entity.Chapter;
import main.entity.Volume;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Danh sach chuong hien thi, sap xep on dinh xuyen qua cac tap. */
public final class ReadingNavigation {
    private ReadingNavigation() {}

    public record Link(int chapterId, String title, String volumeTitle, String path) {}
    public record Result(List<Link> chapters, Link previous, Link next) {}

    public static Result build(String productPath, List<Volume> volumes, Integer currentChapterId) {
        List<Link> links = new ArrayList<>();
        List<Volume> orderedVolumes = volumes.stream().filter(v -> !v.isDeleted())
                .sorted(Comparator.comparingInt(Volume::getSortOrder).thenComparingInt(Volume::getVolumeId))
                .toList();
        int volumeNumber = 0;
        for (Volume volume : orderedVolumes) {
            volumeNumber++;
            String volumeTitle = volume.getTitle() == null || volume.getTitle().isBlank()
                    ? "Tập " + volumeNumber : volume.getTitle();
            int chapterNumber = 0;
            for (Chapter chapter : volume.getChapters().stream().filter(c -> !c.isDeleted())
                    .sorted(Comparator.comparingInt(Chapter::getSortOrder).thenComparingInt(Chapter::getChapterId))
                    .toList()) {
                chapterNumber++;
                String title = chapter.getTitle() == null || chapter.getTitle().isBlank()
                        ? "Chương " + chapterNumber : chapter.getTitle();
                links.add(new Link(chapter.getChapterId(), title, volumeTitle,
                        productPath + "/chuong/" + chapter.getChapterId() + "/" + chapter.getSlug()));
            }
        }
        Link previous = null;
        Link next = null;
        for (int i = 0; i < links.size(); i++) {
            if (Integer.valueOf(links.get(i).chapterId()).equals(currentChapterId)) {
                previous = i == 0 ? null : links.get(i - 1);
                next = i + 1 == links.size() ? null : links.get(i + 1);
                break;
            }
        }
        return new Result(List.copyOf(links), previous, next);
    }
}
