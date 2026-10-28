package main.service;

import main.entity.*;
import main.repository.AccountRepository;
import main.repository.UserRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    @Test
    void migratesOnlySuccessfulLegacyLogin() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuthService auth = new AuthService(accounts, mock(UserRepository.class));
        Account account = new Account();
        account.setPassword("admin123");
        account.setUser(new User());
        when(accounts.findByUsername("admin")).thenReturn(Optional.of(account));

        assertTrue(auth.login("admin", "wrong").isEmpty());
        verify(accounts, never()).save(any(Account.class));
        assertTrue(auth.login("admin", "admin123").isPresent());
        assertTrue(Passwords.isEncoded(account.getPassword()));
        assertTrue(auth.login("admin", "admin123").isPresent());
        verify(accounts, times(1)).save(account);
    }

    @Test
    void rejectsLockedAccountAtLogin() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuthService auth = new AuthService(accounts, mock(UserRepository.class));
        Account account = new Account();
        account.setPassword("admin123");
        account.setUser(new User());
        account.setDeleted(true);
        when(accounts.findByUsername("admin")).thenReturn(Optional.of(account));
        assertTrue(auth.login("admin", "admin123").isEmpty());
        verify(accounts, never()).save(any(Account.class));
    }

    @Test
    void duplicateEmailDoesNotCreateProfile() {
        AccountRepository accounts = mock(AccountRepository.class);
        UserRepository users = mock(UserRepository.class);
        AuthService auth = new AuthService(accounts, users);
        when(accounts.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> auth.register("reader", " Test@Example.com ", "password123", "Reader"));
        verify(users, never()).save(any(User.class));
        verify(accounts, never()).saveAndFlush(any(Account.class));
    }
}
