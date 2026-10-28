package main.service;

import main.entity.*;
import main.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessPolicyTest {
    @Test
    void ownerViewsPrivateButCannotModerate() {
        AccountRepository accounts = mock(AccountRepository.class);
        User owner = user(1);
        Account account = new Account();
        account.setRole(AccountRole.USER);
        when(accounts.findByUserUserId(1)).thenReturn(Optional.of(account));
        Product product = new Product();
        product.setUploader(owner);
        product.setStatus(Status.PRIVATE);
        AccessPolicy policy = new AccessPolicy(accounts);

        assertTrue(policy.canView(owner, product));
        assertTrue(policy.canEdit(owner, product));
        assertFalse(policy.canManage(owner, product));
        assertFalse(policy.canView(null, product));
        product.setDeleted(true);
        assertFalse(policy.canView(owner, product));
    }

    @Test
    void staffModeratesWithoutEditingSomeoneElsesContent() {
        for (AccountRole role : new AccountRole[]{AccountRole.MOD, AccountRole.ADMIN}) {
            AccountRepository accounts = mock(AccountRepository.class);
            User staff = user(2);
            Account account = new Account();
            account.setRole(role);
            when(accounts.findByUserUserId(2)).thenReturn(Optional.of(account));
            Product product = new Product();
            product.setUploader(user(1));
            product.setStatus(Status.PRIVATE);
            product.setDeleted(true);
            AccessPolicy policy = new AccessPolicy(accounts);
            assertTrue(policy.canView(staff, product));
            assertTrue(policy.canManage(staff, product));
            assertFalse(policy.canEdit(staff, product));
        }
    }

    @Test
    void publicVisibleButUnknownStatusDeniedToAnonymous() {
        AccessPolicy policy = new AccessPolicy(mock(AccountRepository.class));
        Product product = new Product();
        product.setStatus(Status.PUBLIC);
        assertTrue(policy.canView(null, product));
        product.setStatus(null);
        assertFalse(policy.canView(null, product));
        assertFalse(policy.canView(null, null));
    }

    private static User user(int id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }
}
