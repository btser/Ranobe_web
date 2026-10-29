package main.controller;

import jakarta.servlet.http.HttpSession;
import main.entity.User;
import main.repository.ProductRepository;
import main.service.EngagementService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@Controller
public class EngagementController {
    private final EngagementService engagement;
    private final ProductRepository products;
    public EngagementController(EngagementService engagement, ProductRepository products) {
        this.engagement=engagement; this.products=products;
    }
    @GetMapping("/xep-hang")
    public String ranking(Model model) {
        model.addAttribute("rankedProducts", engagement.ranking(Instant.EPOCH,100,false));
        return "ranking";
    }
    @PostMapping("/truyen/{id}/vote")
    public String vote(@PathVariable int id, @RequestParam boolean selected, @RequestParam String interactionToken,
                       HttpSession session, RedirectAttributes redirects) {
        return submit(id,null,selected,interactionToken,session,redirects);
    }
    @PostMapping("/truyen/{id}/comment")
    public String comment(@PathVariable int id, @RequestParam String body, @RequestParam String interactionToken,
                          HttpSession session, RedirectAttributes redirects) {
        return submit(id,body,false,interactionToken,session,redirects);
    }
    private String submit(int id,String body,boolean selected,String token,HttpSession session,RedirectAttributes redirects) {
        User viewer=(User) session.getAttribute("loggedInUser");
        if(viewer==null) return "redirect:/login";
        Object expected=session.getAttribute("interactionToken");
        if(!(expected instanceof String value) || !MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Yêu cầu không hợp lệ. Hãy tải lại trang.");
        }
        var product=products.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            if(body!=null) engagement.comment(id,viewer.getUserId(),body);
            else engagement.vote(id,viewer.getUserId(),selected);
        } catch(ResponseStatusException e) {
            redirects.addFlashAttribute("engagementError",e.getReason());
            if(body!=null) redirects.addFlashAttribute("commentBody",body.substring(0,Math.min(body.length(),2000)));
        }
        return "redirect:"+product.getPath()+"#discussion";
    }
}
