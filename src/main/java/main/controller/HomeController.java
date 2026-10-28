package main.controller;

import jakarta.servlet.http.HttpSession;
import main.entity.Account;
import main.entity.AccountRole;
import main.entity.Chapter;
import main.entity.User;
import main.repository.AccountRepository;
import main.repository.CategoryRepository;
import main.repository.ChapterRepository;
import main.repository.ProductRepository;
import main.service.CatalogSearch;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.HashMap;
import java.util.Map;

@Controller
public class HomeController {
    private final ProductRepository productRepository;
    private final AccountRepository accountRepository;
    private final ChapterRepository chapterRepository;
    private final CategoryRepository categoryRepository;

    public HomeController(ProductRepository productRepository, AccountRepository accountRepository,
                          ChapterRepository chapterRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.accountRepository = accountRepository;
        this.chapterRepository = chapterRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/")
    public String homePage(Model model, HttpSession session,
                           @RequestParam(defaultValue = "") String q,
                           @RequestParam(defaultValue = "0") int page) {
        return catalog(model, session, q, "", "", "", null, "updated", page, false);
    }

    @GetMapping("/tim-kiem")
    public String advancedSearch(Model model, HttpSession session,
                                 @RequestParam(defaultValue = "") String q,
                                 @RequestParam(defaultValue = "") String author,
                                 @RequestParam(defaultValue = "") String artist,
                                 @RequestParam(defaultValue = "") String uploader,
                                 @RequestParam(required = false) Integer categoryId,
                                 @RequestParam(defaultValue = "updated") String sort,
                                 @RequestParam(defaultValue = "0") int page) {
        return catalog(model, session, q, author, artist, uploader, categoryId, sort, page, true);
    }

    private String catalog(Model model, HttpSession session, String q, String author, String artist,
                           String uploader, Integer categoryId, String sort, int page, boolean advanced) {
        User user = (User) session.getAttribute("loggedInUser");
        Account account = user == null ? null
                : accountRepository.findByUserUserId(user.getUserId()).orElse(null);
        boolean staff = account != null && (account.getRole() == AccountRole.ADMIN || account.getRole() == AccountRole.MOD);
        q = clean(q);
        author = clean(author);
        artist = clean(artist);
        uploader = clean(uploader);
        sort = CatalogSearch.sortKey(sort);
        var results = productRepository.findAll(CatalogSearch.matching(q, author, artist, uploader,
                categoryId, staff, sort), PageRequest.of(Math.max(0, page), 24));

        Map<Integer, Chapter> latestChapters = new HashMap<>();
        for (Chapter chapter : chapterRepository.findLatestChapterOfEachProduct()) {
            latestChapters.put(chapter.getVolume().getProduct().getProductId(), chapter);
        }

        model.addAttribute("products", results.getContent());
        model.addAttribute("latestChapters", latestChapters);
        model.addAttribute("user", user);
        model.addAttribute("account", account);
        model.addAttribute("isStaff", staff);
        if (advanced) model.addAttribute("categories", categoryRepository.findByIsDeletedFalse());
        model.addAttribute("q", q);
        model.addAttribute("author", author);
        model.addAttribute("artist", artist);
        model.addAttribute("uploader", uploader);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("sort", sort);
        model.addAttribute("advancedSearch", advanced);
        model.addAttribute("results", results);
        return advanced ? "search" : "index";
    }

    private static String clean(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() > 150 ? text.substring(0, 150) : text;
    }
}
