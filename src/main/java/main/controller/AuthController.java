package main.controller;

import main.entity.Account;
import main.entity.AccountRole;
import main.entity.User;
import main.repository.AccountRepository;
import main.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.Date;

@Controller
public class AuthController {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AuthController(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    // 1. Hiển thị trang Login
    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }

    // 2. Xử lý submit Login
    @PostMapping("/login")
    public String handleLogin(@RequestParam("username") String username,
                              @RequestParam("password") String password,
                              HttpSession session,
                              Model model) {
        
        Optional<Account> accountOpt = accountRepository.findByUsername(username);

        // Kiểm tra username và password đơn giản (sau này nâng cấp Spring Security sau)
        if (accountOpt.isPresent()
                && !accountOpt.get().isDeleted()
                && accountOpt.get().getPassword().equals(password)
                && accountOpt.get().getUser() != null
                && !accountOpt.get().getUser().isDeleted()) {
            // Lưu User đã đăng nhập vào Session
            session.setAttribute("loggedInUser", accountOpt.get().getUser());
            session.setAttribute("loggedInAccount", accountOpt.get());
            return "redirect:/"; // Đăng nhập thành công về trang chủ
        }

        model.addAttribute("error", "Username or password is incorrect!");
        return "login";
    }

    // 3. Log out
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate(); // Xóa sạch session
        return "redirect:/login";
    }

    // 4. Register
    @GetMapping("/register")
    public String showRegisterForm() {
        return "register";
    }

    @PostMapping("/register")
    public String handleRegister (@RequestParam("username") String username, @RequestParam("email") String email, @RequestParam("password") String password, @RequestParam("userName") String displayName, Model model) {
        if (accountRepository.findByUsername(username).isPresent()) {
            model.addAttribute ("error", "Username is already exists");
            return "register";
        }

    // create user profile
    User newUser = new User();
    newUser.setUserName(displayName);
    newUser.setBirth(new java.util.Date());
    newUser = userRepository.save(newUser);

    // create account link to user

    Account newAccount = new Account();
    newAccount.setUsername(username);
    newAccount.setEmail(email);
    newAccount.setPassword(password);
    newAccount.setRole(AccountRole.USER);
    newAccount.setUser(newUser);
    accountRepository.save(newAccount);

    return "redirect:/login" ;
    }
}
