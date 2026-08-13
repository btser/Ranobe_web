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
    List<Chapter> findByIsDeletedFalse();
    
    // Lấy danh sách Chapter thuộc về một Volume cụ thể
    List<Chapter> findByVolumeVolumeIdAndIsDeletedFalse(int volumeId);

    @Query("SELECT c FROM Chapter c JOIN FETCH c.volume v JOIN FETCH v.product p JOIN FETCH p.uploader WHERE c.chapterId = :chapterId")
    Optional<Chapter> findByIdWithVolumeProductUploader(@Param("chapterId") int chapterId);
}
