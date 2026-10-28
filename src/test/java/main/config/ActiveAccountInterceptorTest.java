package main.config;

import main.entity.Account;
import main.entity.User;
import main.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ActiveAccountInterceptorTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final ActiveAccountInterceptor interceptor = new ActiveAccountInterceptor(accounts);

    @RestController
    static class WriteController {
        final AtomicInteger writes = new AtomicInteger();
        @RequestMapping({"/write", "/api/write"})
        String write() { writes.incrementAndGet(); return "saved"; }
    }

    private MockHttpSession session() {
        User staleUser = new User();
        staleUser.setUserId(7);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loggedInUser", staleUser);
        return session;
    }

    private Account account(boolean locked, boolean profileLocked) {
        User freshUser = new User();
        freshUser.setUserId(7);
        freshUser.setDeleted(profileLocked);
        Account account = new Account();
        account.setUser(freshUser);
        account.setDeleted(locked);
        return account;
    }

    @Test
    void newlyLockedAccountCannotReachWriteHandlerAndSessionEnds() throws Exception {
        WriteController controller = new WriteController();
        MockMvc mvc = standaloneSetup(controller).addInterceptors(interceptor).build();
        MockHttpSession session = session();
        when(accounts.findByUserUserId(7)).thenReturn(Optional.of(account(true, false)));
        mvc.perform(post("/write").session(session))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/login?accountUnavailable=1"));
        assertTrue(session.isInvalid());
        assertEquals(0, controller.writes.get());
    }

    @Test
    void apiReceivesForbiddenWithoutRedirectOrWriting() throws Exception {
        WriteController controller = new WriteController();
        MockMvc mvc = standaloneSetup(controller).addInterceptors(interceptor).build();
        MockHttpSession session = session();
        when(accounts.findByUserUserId(7)).thenReturn(Optional.of(account(false, true)));
        mvc.perform(put("/api/write").session(session))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").exists());
        assertTrue(session.isInvalid());
        assertEquals(0, controller.writes.get());
    }

    @Test
    void missingAccountAndProfileAlsoEndSessionForEveryWriteMethod() throws Exception {
        Account missingProfile = new Account();
        for (Optional<Account> state : java.util.List.of(Optional.<Account>empty(), Optional.of(missingProfile))) {
            when(accounts.findByUserUserId(7)).thenReturn(state);
            for (String method : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
                MockHttpServletRequest request = new MockHttpServletRequest(method, "/write");
                MockHttpSession session = session();
                request.setSession(session);
                assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
                assertTrue(session.isInvalid());
            }
        }
    }

    @Test
    void activeAccountCanWrite() throws Exception {
        WriteController controller = new WriteController();
        MockMvc mvc = standaloneSetup(controller).addInterceptors(interceptor).build();
        MockHttpSession session = session();
        when(accounts.findByUserUserId(7)).thenReturn(Optional.of(account(false, false)));
        mvc.perform(post("/write").session(session)).andExpect(status().isOk());
        assertFalse(session.isInvalid());
        assertEquals(1, controller.writes.get());
    }

    @Test
    void readingAndGuestsDoNotQueryAccountOrCreateSession() throws Exception {
        for (String method : new String[]{"GET", "HEAD", "OPTIONS"}) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/read");
            request.setSession(session());
            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        }
        MockHttpServletRequest guest = new MockHttpServletRequest("POST", "/login");
        assertTrue(interceptor.preHandle(guest, new MockHttpServletResponse(), new Object()));
        assertNull(guest.getSession(false));
        verifyNoInteractions(accounts);
    }
}
