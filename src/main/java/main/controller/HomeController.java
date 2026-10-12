package main.controller;

import main.entity.Account;
import main.entity.AccountRole;
import main.entity.Product;
import main.entity.Status;
import main.entity.User;
import main.repository.AccountRepository;
import main.repository.ProductRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import main.entity.Chapter;
import main.repository.ChapterRepository;
import java.util.HashMap;
import java.util.Map;

import java.util.List;

@Controller
public class HomeController {

    private final ProductRepository productRepository;
    private final AccountRepository accountRepository;
    private final ChapterRepository chapterRepository;


    public HomeController(ProductRepository productRepository, AccountRepository accountRepository, ChapterRepository chapterRepository) {
        this.productRepository = productRepository;
        this.accountRepository = accountRepository;
        this.chapterRepository = chapterRepository;
    }

    @GetMapping("/")
    public String homePage(Model model, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        Account account = getAccount(loggedInUser);
        boolean isStaff = account != null && (account.getRole() == AccountRole.ADMIN || account.getRole() == AccountRole.MOD);

        List<Product> products = isStaff
                ? productRepository.findAllActiveLatestFirst()
                : productRepository.findPublicLatestFirst(Status.PUBLIC);

        Map<Integer, Chapter> latestChapters = new HashMap<>();
        for (Chapter c : chapterRepository.findLatestChapterOfEachProduct()) {
            latestChapters.put(c.getVolume().getProduct().getProductId(), c);
        }

        model.addAttribute("products", products);
        model.addAttribute("latestChapters", latestChapters);
        model.addAttribute("user", loggedInUser);
        model.addAttribute("account", account);
        model.addAttribute("isStaff", isStaff);
    
        return "index";
    }

    private Account getAccount(User user) {
        if (user == null) {
            return null;
        }
        return accountRepository.findByUserUserId(user.getUserId()).orElse(null);
    }
}
