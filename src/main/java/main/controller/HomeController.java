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

import java.util.List;

@Controller
public class HomeController {

    private final ProductRepository productRepository;
    private final AccountRepository accountRepository;

    public HomeController(ProductRepository productRepository, AccountRepository accountRepository) {
        this.productRepository = productRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping("/")
    public String homePage(Model model, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        Account account = getAccount(loggedInUser);
        boolean isStaff = account != null && (account.getRole() == AccountRole.ADMIN || account.getRole() == AccountRole.MOD);
        List<Product> products = isStaff
                ? productRepository.findByIsDeletedFalse()
                : productRepository.findByIsDeletedFalseAndStatus(Status.PUBLIC);

        model.addAttribute("products", products);
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
