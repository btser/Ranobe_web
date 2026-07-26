package main.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import main.entity.Account;

import java.util.List;

@Repository
public interface AccountRepository extends JpaRepository<Account, Integer> {
    // Làm thêm 1 hàm tiện ích để sau này làm tính năng Đăng Nhập (Tìm tài khoản bằng username)
    List<Account> findbyIsDeletedFalse();
}