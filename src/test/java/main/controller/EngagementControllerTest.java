package main.controller;

import main.entity.*;
import main.repository.ProductRepository;
import main.service.EngagementService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;

class EngagementControllerTest {
    @Test
    void guestsAndForgedFormCannotVote() {
        var service=mock(EngagementService.class);
        var products=mock(ProductRepository.class);
        var controller=new EngagementController(service,products);
        var session=new MockHttpSession();
        assertEquals("redirect:/login",controller.vote(1,true,"fake",session,new RedirectAttributesModelMap()));
        User user=new User();user.setUserId(7);session.setAttribute("loggedInUser",user);
        session.setAttribute("interactionToken","correct");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.vote(1,true,"fake",session,new RedirectAttributesModelMap()));
        verifyNoInteractions(service,products);
    }
    @Test
    void validatedFormCallsUserScopedVoteAndRedirectsToDiscussion() {
        var service=mock(EngagementService.class);
        var products=mock(ProductRepository.class);
        Product product=new Product();product.setTitle("Book");
        when(products.findById(1)).thenReturn(Optional.of(product));
        var session=new MockHttpSession(); User user=new User();user.setUserId(7);
        session.setAttribute("loggedInUser",user);session.setAttribute("interactionToken","correct");
        String redirect=new EngagementController(service,products).vote(1,true,"correct",session,new RedirectAttributesModelMap());
        verify(service).vote(1,7,true);
        assertTrue(redirect.endsWith("#discussion"));
    }
}
