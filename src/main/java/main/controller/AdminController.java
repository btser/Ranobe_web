package main.controller;

import main.entity.Account;
import main.entity.AccountRole;
import main.entity.Product;
import main.entity.User;
import main.repository.AccountRepository;
import main.repository.ProductRepository;
import main.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AccountRepository accountRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public AdminController(AccountRepository accountRepository, ProductRepository productRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String dashboard(Model model, HttpSession session) {
        Account currentAccount = getCurrentAccount(session);
        if (!isStaff(currentAccount)) {
            return "redirect:/";
        }

        List<Account> accounts = accountRepository.findAll();
        List<Product> products = productRepository.findAllWithDetails();

        model.addAttribute("accounts", accounts);
        model.addAttribute("products", products);
        model.addAttribute("currentAccount", currentAccount);
        model.addAttribute("isAdmin", currentAccount.getRole() == AccountRole.ADMIN);
        return "admin";
    }

    @PostMapping("/accounts/{accountId}/make-mod")
    public String makeMod(@PathVariable int accountId, HttpSession session) {
        Account currentAccount = getCurrentAccount(session);
        if (isAdmin(currentAccount)) {
            accountRepository.findById(accountId).ifPresent(account -> {
                if (account.getRole() == AccountRole.USER) {
                    account.setRole(AccountRole.MOD);
                    accountRepository.save(account);
                }
            });
        }
        return "redirect:/admin";
    }

    @PostMapping("/accounts/{accountId}/remove-mod")
    public String removeMod(@PathVariable int accountId, HttpSession session) {
        Account currentAccount = getCurrentAccount(session);
        if (isAdmin(currentAccount)) {
            accountRepository.findById(accountId).ifPresent(account -> {
                if (account.getRole() == AccountRole.MOD) {
                    account.setRole(AccountRole.USER);
                    accountRepository.save(account);
                }
            });
        }
        return "redirect:/admin";
    }

    @PostMapping("/accounts/{accountId}/ban")
    public String banAccount(@PathVariable int accountId, HttpSession session) {
        Account currentAccount = getCurrentAccount(session);
        if (isStaff(currentAccount) && currentAccount.getAccountId() != accountId) {
            accountRepository.findById(accountId).ifPresent(account -> {
                if (currentAccount.getRole() == AccountRole.ADMIN || account.getRole() == AccountRole.USER) {
                    account.setDeleted(true);
                    if (account.getUser() != null) {
                        account.getUser().setDeleted(true);
                        userRepository.save(account.getUser());
                    }
                    accountRepository.save(account);
                }
            });
        }
        return "redirect:/admin";
    }

    @PostMapping("/accounts/{accountId}/unban")
    public String unbanAccount(@PathVariable int accountId, HttpSession session) {
        Account currentAccount = getCurrentAccount(session);
        if (isStaff(currentAccount)) {
            accountRepository.findById(accountId).ifPresent(account -> {
                if (currentAccount.getRole() == AccountRole.ADMIN || account.getRole() == AccountRole.USER) {
                    account.setDeleted(false);
                    if (account.getUser() != null) {
                        account.getUser().setDeleted(false);
                        userRepository.save(account.getUser());
                    }
                    accountRepository.save(account);
                }
            });
        }
        return "redirect:/admin";
    }

    private Account getCurrentAccount(HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return null;
        }
        return accountRepository.findByUserUserId(user.getUserId()).orElse(null);
    }

    private boolean isStaff(Account account) {
        return account != null && (account.getRole() == AccountRole.ADMIN || account.getRole() == AccountRole.MOD);
    }

    private boolean isAdmin(Account account) {
        return account != null && account.getRole() == AccountRole.ADMIN;
    }
}
