package main.service;

import main.entity.Account;
import main.entity.AccountRole;
import main.entity.User;
import main.repository.AccountRepository;
import main.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {
    private final AccountRepository accounts;
    private final UserRepository users;

    public AuthService(AccountRepository accounts, UserRepository users) {
        this.accounts = accounts;
        this.users = users;
    }

    @Transactional
    public Optional<Account> login(String username, String password) {
        if (username == null || password == null || password.length() > 4096) {
            return Optional.empty();
        }
        Optional<Account> found = accounts.findByUsername(username.trim());
        if (found.isEmpty()) return Optional.empty();
        Account account = found.get();
        if (account.isDeleted() || account.getUser() == null || account.getUser().isDeleted()
                || !Passwords.matches(password, account.getPassword())) {
            return Optional.empty();
        }
        if (!Passwords.isEncoded(account.getPassword())) {
            account.setPassword(Passwords.hash(password));
            accounts.save(account);
        }
        return Optional.of(account);
    }

    @Transactional
    public void register(String username, String email, String password, String displayName) {
        String cleanUsername = username == null ? "" : username.trim();
        String cleanEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String cleanName = displayName == null ? "" : displayName.trim();

        if (!cleanUsername.matches("[A-Za-z0-9_]{3,50}")) {
            throw new IllegalArgumentException("Ten dang nhap can 3-50 ky tu: chu, so hoac dau gach duoi.");
        }
        if (cleanEmail.length() > 150
                || !cleanEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Dia chi thu dien tu khong hop le.");
        }
        if (cleanName.isEmpty() || cleanName.length() > 100) {
            throw new IllegalArgumentException("Ten hien thi can 1-100 ky tu.");
        }
        if (password == null || password.length() < 8 || password.length() > 256
                || password.isBlank()) {
            throw new IllegalArgumentException("Mat khau can 8-256 ky tu.");
        }
        if (accounts.existsByUsernameIgnoreCase(cleanUsername)) {
            throw new IllegalArgumentException("Ten dang nhap da duoc su dung.");
        }
        if (accounts.existsByEmailIgnoreCase(cleanEmail)) {
            throw new IllegalArgumentException("Dia chi thu dien tu da duoc su dung.");
        }

        User user = new User();
        user.setUserName(cleanName);
        user.setBirth(new Date());
        user = users.save(user);

        Account account = new Account();
        account.setUsername(cleanUsername);
        account.setEmail(cleanEmail);
        account.setPassword(Passwords.hash(password));
        account.setRole(AccountRole.USER);
        account.setUser(user);
        accounts.saveAndFlush(account);
    }
}
