package main.config;

import main.entity.Account;
import main.entity.AccountRole;
import main.entity.User;
import main.repository.AccountRepository;
import main.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final String bootstrapPassword;

    public DataInitializer(AccountRepository accountRepository, UserRepository userRepository,
                           @Value("${bootstrap.admin-password:}") String bootstrapPassword) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {

        for (Account acc : accountRepository.findAll()) {
            if (acc.getRole() == null) {
                acc.setRole(AccountRole.USER);
                accountRepository.save(acc);
            }
        }
        if (accountRepository.findByUsername("admin").isEmpty() && !bootstrapPassword.isBlank()) {
            if (bootstrapPassword.length() < 12 || bootstrapPassword.length() > 256) {
                throw new IllegalArgumentException("Mật khẩu khởi tạo quản trị cần từ 12 đến 256 ký tự.");
            }
            User adminUser = new User();
            adminUser.setUserName("Admin");
            adminUser.setBirth(new Date());
            adminUser = userRepository.save(adminUser);

            Account admin = new Account();
            admin.setUsername("admin");
            admin.setEmail("admin@ranobe.local");
            admin.setPassword(main.service.Passwords.hash(bootstrapPassword));
            admin.setRole(AccountRole.ADMIN);
            admin.setUser(adminUser);
            accountRepository.save(admin);
        }
    }
}
