package main.repository;

import main.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    List<Product> findByIsDeletedFalse();

    List<Product> findByIsDeletedFalseAndStatus(main.entity.Status status);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.uploader")
    List<Product> findAllWithDetails();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.uploader WHERE p.productId = :id")
    Optional<Product> findByIdWithDetails(@Param("id") Integer id);

    @Query("SELECT p FROM Product p WHERE p.isDeleted = false ORDER BY p.updatedAt DESC NULLS LAST, p.productId DESC")
    List<Product> findAllActiveLatestFirst();

    @Query("SELECT p FROM Product p WHERE p.isDeleted = false AND p.status = :status ORDER BY p.updatedAt DESC NULLS LAST, p.productId DESC")
    List<Product> findPublicLatestFirst(@Param("status") main.entity.Status status);

    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.updatedAt = :now WHERE p.productId = :id")
    void touch(@Param("id") int id, @Param("now") LocalDateTime now);
}
