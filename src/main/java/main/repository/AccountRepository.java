package main.repository;

import main.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Integer>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Account> {
    List<Account> findByIsDeletedFalse();
    Optional<Account> findByUsername(String username);
    Optional<Account> findByUserUserId(int userId);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
}
