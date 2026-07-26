package main.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import main.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    // Spring Boot tự hiểu và lo hết code ngầm bên dưới
}
