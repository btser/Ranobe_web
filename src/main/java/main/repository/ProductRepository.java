package main.repository;

import main.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    List<Product> findByIsDeletedFalse();

    List<Product> findByIsDeletedFalseAndStatus(main.entity.Status status);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.uploader")
    List<Product> findAllWithDetails();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.uploader WHERE p.productId = :id")
    Optional<Product> findByIdWithDetails(@Param("id") Integer id);
}
