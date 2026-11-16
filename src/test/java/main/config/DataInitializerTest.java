package main.config;

import main.entity.Account;
import main.entity.AccountRole;
import main.entity.User;
import main.repository.AccountRepository;
import main.repository.UserRepository;
import main.service.Passwords;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataInitializerTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final UserRepository users = mock(UserRepository.class);

    @Test
    void startupDoesNotUnlockPromoteOrResetExistingAdminName() {
        var existing = new Account();
        existing.setDeleted(true);
        existing.setRole(AccountRole.USER);
        existing.setPassword("existing-hash");
        when(accounts.findAll()).thenReturn(List.of(existing));
        when(accounts.findByUsername("admin")).thenReturn(Optional.of(existing));
        new DataInitializer(accounts, users, "explicit-bootstrap-password").run();
        assertTrue(existing.isDeleted());
        assertEquals(AccountRole.USER, existing.getRole());
        assertEquals("existing-hash", existing.getPassword());
        verify(accounts, never()).save(any());
        verifyNoInteractions(users);
    }

    @Test
    void noAdminIsCreatedWithoutAnExplicitBootstrapPassword() {
        when(accounts.findByUsername("admin")).thenReturn(Optional.empty());
        new DataInitializer(accounts, users, "").run();
        verify(accounts, never()).save(any());
        verifyNoInteractions(users);
    }

    @Test
    void explicitBootstrapHashesPasswordAndRejectsShortValues() {
        when(accounts.findByUsername("admin")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> new DataInitializer(accounts, users, "short").run());
        verifyNoInteractions(users);
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        new DataInitializer(accounts, users, "explicit-bootstrap-password").run();
        var saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        assertEquals(AccountRole.ADMIN, saved.getValue().getRole());
        assertTrue(Passwords.matches("explicit-bootstrap-password", saved.getValue().getPassword()));
        assertNotEquals("explicit-bootstrap-password", saved.getValue().getPassword());
    }
}
