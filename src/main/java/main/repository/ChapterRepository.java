package main.repository;

import main.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Integer> {

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query(value = "UPDATE chapters SET is_deleted = NOT COALESCE(is_deleted, FALSE) WHERE chapter_id = :id",
           nativeQuery = true)
    int toggleDeletedById(@Param("id") int id);

    @Query(value = "SELECT COALESCE(is_deleted, FALSE) FROM chapters WHERE chapter_id = :id", nativeQuery = true)
    Boolean findDeletedFlagById(@Param("id") int id);

    List<Chapter> findByIsDeletedFalse();

    List<Chapter> findByVolumeVolumeIdAndIsDeletedFalseOrderBySortOrderAsc(int volumeId);

    @Query("SELECT c FROM Chapter c JOIN FETCH c.volume v JOIN FETCH v.product p JOIN FETCH p.uploader WHERE c.chapterId = :chapterId")
    Optional<Chapter> findByIdWithVolumeProductUploader(@Param("chapterId") int chapterId);

    @Query("SELECT COALESCE(MAX(c.sortOrder), -1) FROM Chapter c WHERE c.volume.volumeId = :volumeId")
    Integer findMaxSortOrderByVolumeId(@Param("volumeId") int volumeId);

    @Query("SELECT c FROM Chapter c JOIN FETCH c.volume v JOIN FETCH v.product p " +
           "WHERE c.chapterId IN (SELECT MAX(c2.chapterId) FROM Chapter c2 " +
           "WHERE c2.volume.isDeleted = false GROUP BY c2.volume.product.productId)")
    List<Chapter> findLatestChapterOfEachProduct();
}
