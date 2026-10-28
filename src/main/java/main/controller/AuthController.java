package main.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import main.entity.Account;
import main.service.AuthService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@Controller
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String handleLogin(@RequestParam("username") String username,
                              @RequestParam("password") String password,
                              HttpServletRequest request, Model model) {
        Optional<Account> found = auth.login(username, password);
        if (found.isEmpty()) {
            model.addAttribute("error", "Ten dang nhap hoac mat khau khong dung.");
            model.addAttribute("formUsername", username);
            return "login";
        }
        // Doi ma phien sau khi dang nhap thanh cong.
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        Account account = found.get();
        session.setAttribute("loggedInUser", account.getUser());
        session.setAttribute("loggedInAccount", account);
        return "redirect:/";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/register")
    public String showRegisterForm() {
        return "register";
    }

    @PostMapping("/register")
    public String handleRegister(@RequestParam("username") String username,
                                 @RequestParam("email") String email,
                                 @RequestParam("password") String password,
                                 @RequestParam("userName") String displayName, Model model) {
        try {
            auth.register(username, email, password, displayName);
            return "redirect:/login?registered=1";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("error",
                    "Khong the tao tai khoan. Hay kiem tra ten dang nhap va dia chi thu dien tu.");
        }
        model.addAttribute("formUsername", username);
        model.addAttribute("formEmail", email);
        model.addAttribute("formDisplayName", displayName);
        return "register";
    }
}
