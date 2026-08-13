package main.repository;

import main.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Integer> {
    List<Account> findByIsDeletedFalse();
    
    // Tìm account theo username để xử lý Đăng nhập
    Optional<Account> findByUsername(String username);
}