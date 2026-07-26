package main.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import main.entity.Product;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
    // Làm thêm hàm tìm kiếm truyện theo tên (giống tính năng thanh Search trên UI)
    List<Product> findByIsDeletedFalse();
}
