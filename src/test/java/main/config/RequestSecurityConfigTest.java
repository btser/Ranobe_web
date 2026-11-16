package main.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RequestSecurityConfigTest {
    private final MockMvc mvc = standaloneSetup(new Endpoint())
            .addFilters(new RequestSecurityConfig().csrfFilter(new HttpSessionCsrfTokenRepository()).getFilter()).build();

    @RestController
    static class Endpoint {
        @GetMapping("/form")
        String token(HttpServletRequest request) {
            return ((CsrfToken) request.getAttribute("_csrf")).getToken();
        }
        @RequestMapping("/write")
        String write() { return "ok"; }
    }

    @Test
    void allUnsafeMethodsRejectMissingTokens() throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                post("/write"), put("/write"), patch("/write"), delete("/write")}) {
            mvc.perform(request).andExpect(status().isForbidden());
        }
        mvc.perform(get("/write")).andExpect(status().isOk());
        mvc.perform(head("/write")).andExpect(status().isOk());
        mvc.perform(options("/write")).andExpect(status().isOk());
    }

    @Test
    void tokensAreBoundToTheSessionAndAcceptFormOrHeader() throws Exception {
        var session = new MockHttpSession();
        String token = mvc.perform(get("/form").session(session)).andReturn().getResponse().getContentAsString();
        assertFalse(token.isBlank());
        mvc.perform(post("/write").session(session).param("_csrf", token)).andExpect(status().isOk());
        mvc.perform(put("/write").session(session).header("X-CSRF-TOKEN", token)).andExpect(status().isOk());
        mvc.perform(post("/write").session(session).param("_csrf", "invalid")).andExpect(status().isForbidden());
        mvc.perform(post("/write").session(new MockHttpSession()).param("_csrf", token))
                .andExpect(status().isForbidden());
    }
}
