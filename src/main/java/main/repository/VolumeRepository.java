package main.repository;

import main.entity.Volume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
public interface VolumeRepository extends JpaRepository<Volume, Integer> {

    @Query("SELECT DISTINCT v FROM Volume v LEFT JOIN FETCH v.chapters c WHERE v.product.productId = :productId AND v.isDeleted = false")
    List<Volume> findByProductIdWithChapters(@Param("productId") Integer productId);

    @Query("SELECT v FROM Volume v JOIN FETCH v.product p JOIN FETCH p.uploader WHERE v.volumeId = :volumeId")
    Optional<Volume> findByIdWithProductUploader(@Param("volumeId") int volumeId);

    Optional<Volume> findById(int volumeId);
}
