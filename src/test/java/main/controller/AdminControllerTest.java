package main.controller;

import main.entity.*;
import main.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminControllerTest {
    @Test
    void guestCannotReadAccountDirectory() {
        var accounts = mock(AccountRepository.class);
        var products = mock(ProductRepository.class);
        var controller = new AdminController(accounts, products, mock(UserRepository.class));
        assertEquals("redirect:/", controller.dashboard(new ExtendedModelMap(), new MockHttpSession(), "", "all", 0, "", "all", 0));
        verifyNoInteractions(accounts, products);
    }

    @Test
    @SuppressWarnings("unchecked")
    void staffFiltersAndPagesEachListIndependently() {
        var accounts = mock(AccountRepository.class);
        var products = mock(ProductRepository.class);
        var controller = new AdminController(accounts, products, mock(UserRepository.class));
        User viewer = new User(); viewer.setUserId(1);
        Account account = new Account(); account.setRole(AccountRole.MOD);
        when(accounts.findByUserUserId(1)).thenReturn(Optional.of(account));
        when(accounts.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        when(products.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        var session = new MockHttpSession(); session.setAttribute("loggedInUser", viewer);
        var model = new ExtendedModelMap();
        assertEquals("admin", controller.dashboard(model, session, " test ", "hidden", -2, " book ", "bad", 2));
        verify(accounts).findAll(any(Specification.class), eq(PageRequest.of(0,24,Sort.by("accountId").descending())));
        verify(products).findAll(any(Specification.class), eq(PageRequest.of(2,24,Sort.by("productId").descending())));
        assertEquals("test", model.get("accountQ"));
        assertEquals("hidden", model.get("accountState"));
        assertEquals("all", model.get("productState"));
        assertEquals(false, model.get("isAdmin"));
    }
}
