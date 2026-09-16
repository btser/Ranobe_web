package main.config;

import main.entity.Account;
import main.entity.AccountRole;
import main.entity.User;
import main.repository.AccountRepository;
import main.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public DataInitializer(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        accountRepository.findByUsername("admin").ifPresentOrElse(account -> {
            account.setRole(AccountRole.ADMIN);
            account.setDeleted(false);
            accountRepository.save(account);
        }, () -> {
            User adminUser = new User();
            adminUser.setUserName("Admin");
            adminUser.setBirth(new Date());
            adminUser = userRepository.save(adminUser);

            Account admin = new Account();
            admin.setUsername("admin");
            admin.setEmail("admin@ranobe.local");
            admin.setPassword("admin123");
            admin.setRole(AccountRole.ADMIN);
            admin.setUser(adminUser);
            accountRepository.save(admin);
        });
    }
}
