package main.controller;

import main.repository.*;
import main.service.EngagementService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HomeControllerTest {
    @Test
    @SuppressWarnings("unchecked")
    void recommendationsOnlyLoadForUnfilteredHomeAndUseRollingDay() {
        var products=mock(ProductRepository.class);
        var activity=mock(EngagementService.class);
        when(products.findAll(any(Specification.class),any(Pageable.class))).thenReturn(Page.empty());
        var controller=new HomeController(products,mock(AccountRepository.class),mock(ChapterRepository.class),mock(CategoryRepository.class),activity);
        var model=new ExtendedModelMap();
        Instant before=Instant.now().minusSeconds(24*3600);
        assertEquals("index",controller.homePage(model,new MockHttpSession(),"",0));
        verify(activity).ranking(argThat(cutoff -> !cutoff.isBefore(before) && cutoff.isBefore(Instant.now().minusSeconds(23*3600))),eq(6),eq(true));
        clearInvocations(activity);
        controller.homePage(new ExtendedModelMap(),new MockHttpSession(),"Book",0);
        verifyNoInteractions(activity);
    }
}
