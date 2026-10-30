package main.controller;

import main.entity.User;
import main.service.AccessPolicy;
import main.service.DiscussionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscussionControllerTest {
    @Test
    void postedReplyRedirectsToLastPageAndFailedReplyKeepsContent() {
        var service=mock(DiscussionService.class);var controller=new DiscussionController(service,mock(AccessPolicy.class));
        var session=new MockHttpSession();User viewer=new User();viewer.setUserId(7);
        session.setAttribute("loggedInUser",viewer);session.setAttribute("discussionToken","real");
        when(service.postReply(1,7,"reply")).thenReturn(45L);
        when(service.replies(1,false,0)).thenReturn(new org.springframework.data.domain.PageImpl<>(
                java.util.List.of(),org.springframework.data.domain.PageRequest.of(0,20),41));
        assertEquals("redirect:/thao-luan/1?page=2#comment-45",controller.reply(1,"reply","real",session,new RedirectAttributesModelMap()));
        when(service.postReply(1,7,"again")).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"Hãy chờ"));
        var redirects=new RedirectAttributesModelMap();
        assertEquals("redirect:/thao-luan/1#reply-form",controller.reply(1,"again","real",session,redirects));
        assertEquals("again",redirects.getFlashAttributes().get("replyContent"));
        assertEquals("Hãy chờ",redirects.getFlashAttributes().get("discussionError"));
    }
    @Test
    void guestsAndInvalidTokenCannotWrite() {
        var service=mock(DiscussionService.class);var access=mock(AccessPolicy.class);
        var controller=new DiscussionController(service,access);var session=new MockHttpSession();
        assertEquals("redirect:/login",controller.createForm(new ExtendedModelMap(),session));
        assertEquals("redirect:/login",controller.create("title","body",false,"fake",new ExtendedModelMap(),session));
        User viewer=new User();viewer.setUserId(7);session.setAttribute("loggedInUser",viewer);session.setAttribute("discussionToken","real");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->controller.create("title","body",false,"fake",new ExtendedModelMap(),session));
        verifyNoInteractions(service);
    }
    @Test
    void invalidContentKeepsFormValues() {
        var service=mock(DiscussionService.class);var controller=new DiscussionController(service,mock(AccessPolicy.class));
        var session=new MockHttpSession();User viewer=new User();viewer.setUserId(7);
        session.setAttribute("loggedInUser",viewer);session.setAttribute("discussionToken","real");
        when(service.create(7,"title","",false)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Nội dung trống"));
        var model=new ExtendedModelMap();
        assertEquals("discussion-form",controller.create("title","",false,"real",model,session));
        assertEquals("title",model.get("formTitle"));assertEquals("Nội dung trống",model.get("formError"));
    }
    @Test
    void viewingTwiceInSameSessionCountsOnce() {
        var service=mock(DiscussionService.class);var controller=new DiscussionController(service,mock(AccessPolicy.class));
        var now=Instant.now();
        when(service.topic(1,false)).thenReturn(new DiscussionService.Topic(1,7,"A","Title","Text",false,0,now,now,false,false,0,now,7,"A"));
        var session=new MockHttpSession();
        controller.detail(1,0,new ExtendedModelMap(),session);controller.detail(1,0,new ExtendedModelMap(),session);
        verify(service,times(1)).view(1);
    }
}
