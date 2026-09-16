package main.repository;

import main.entity.ChapterImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterImageRepository extends JpaRepository<ChapterImage, Integer> {

    List<ChapterImage> findByChapterChapterIdOrderBySortOrderAsc(int chapterId);

    @Query("SELECT COALESCE(MAX(ci.sortOrder), -1) FROM ChapterImage ci WHERE ci.chapter.chapterId = :chapterId")
    Integer findMaxSortOrderByChapterId(@Param("chapterId") int chapterId);

    @Query("SELECT ci FROM ChapterImage ci JOIN FETCH ci.chapter c JOIN FETCH c.volume v JOIN FETCH v.product p JOIN FETCH p.uploader WHERE ci.imageId = :imageId")
    Optional<ChapterImage> findByIdWithChapterVolumeProductUploader(@Param("imageId") int imageId);
}