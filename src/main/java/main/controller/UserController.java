package main.controller;

import jakarta.servlet.http.HttpSession;
import main.entity.Account;
import main.entity.AccountRole;
import main.entity.Product;
import main.entity.Status;
import main.entity.User;
import main.repository.AccountRepository;
import main.repository.ProductRepository;
import main.repository.UserRepository;
import main.service.LocalImageStorageService;
import main.service.R2StorageService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;

@Controller
public class UserController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final AccountRepository accountRepository;
    private final R2StorageService r2StorageService;
    private final LocalImageStorageService localImageStorageService;

    public UserController(UserRepository userRepository,
                          ProductRepository productRepository,
                          AccountRepository accountRepository,
                          R2StorageService r2StorageService,
                          LocalImageStorageService localImageStorageService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.accountRepository = accountRepository;
        this.r2StorageService = r2StorageService;
        this.localImageStorageService = localImageStorageService;
    }

    // Trang hồ sơ công khai
    @GetMapping("/users/{id}")
    public String profile(@PathVariable("id") int id, Model model, HttpSession session) {
        User viewer = (User) session.getAttribute("loggedInUser");
        User profileUser = userRepository.findById(id).orElse(null);

        boolean isOwner = viewer != null && viewer.getUserId() == profileUser.getUserId();
        boolean isStaff = isStaff(viewer);

        if (profileUser == null || (profileUser.isDeleted() && !isStaff)) {
            return "redirect:/";
        }

        List<Product> products = productRepository.findByUploaderLatestFirst(id);
        if (!isOwner && !isStaff) {
            products = products.stream().filter(p -> p.getStatus() == Status.PUBLIC).toList();
        }

        Account profileAccount = accountRepository.findByUserUserId(profileUser.getUserId()).orElse(null);

        model.addAttribute("profileAccount", profileAccount);
        model.addAttribute("canManageAccount", canManageAccount(viewer, profileAccount));
        model.addAttribute("profileUser", profileUser);
        model.addAttribute("products", products);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("user", viewer);
        model.addAttribute("isStaff", isStaff);
        return "profile";
    }

    // Vào hồ sơ của chính mình
    @GetMapping("/profile")
    public String myProfile(HttpSession session) {
        User viewer = (User) session.getAttribute("loggedInUser");
        if (viewer == null) {
            return "redirect:/login";
        }
        return "redirect:/users/" + viewer.getUserId();
    }

    @GetMapping("/profile/edit")
    public String editForm(Model model, HttpSession session) {
        User viewer = (User) session.getAttribute("loggedInUser");
        if (viewer == null) {
            return "redirect:/login";
        }
        User profileUser = userRepository.findById(viewer.getUserId()).orElse(null);
        if (profileUser == null || profileUser.isDeleted()) {
            return "redirect:/";
        }
        model.addAttribute("profileUser", profileUser);
        return "profile-edit";
    }

    @PostMapping("/profile/edit")
    public String saveProfile(@RequestParam("userName") String userName,
                              @RequestParam(value = "introduce", required = false) String introduce,
                              @RequestParam(value = "hobby", required = false) String hobby,
                              @RequestParam(value = "birth", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date birth,
                              @RequestParam(value = "avatarFile", required = false) MultipartFile avatarFile,
                              @RequestParam(value = "coverFile", required = false) MultipartFile coverFile,
                              @RequestParam(value = "removeAvatar", defaultValue = "false") boolean removeAvatar,
                              @RequestParam(value = "removeCover", defaultValue = "false") boolean removeCover,
                              HttpSession session) {

        User viewer = (User) session.getAttribute("loggedInUser");
        if (viewer == null) {
            return "redirect:/login";
        }
        User user = userRepository.findById(viewer.getUserId()).orElse(null);
        if (user == null || user.isDeleted()) {
            return "redirect:/";
        }

        String cleanName = userName == null ? "" : userName.trim();
        if (!cleanName.isEmpty()) {
            user.setUserName(cleanName.length() > 100 ? cleanName.substring(0, 100) : cleanName);
        }
        user.setIntroduce(blankToNull(introduce));
        user.setHobby(blankToNull(hobby));
        user.setBirth(birth);

        // Avatar
        if (removeAvatar) {
            deleteImage(user.getAvatarUrl());
            user.setAvatarUrl(null);
        } else if (isImage(avatarFile)) {
            String url = uploadImage(avatarFile, "avatars");
            if (url != null) {
                deleteImage(user.getAvatarUrl());
                user.setAvatarUrl(url);
            }
        }

        // Ảnh bìa
        if (removeCover) {
            deleteImage(user.getCoverUrl());
            user.setCoverUrl(null);
        } else if (isImage(coverFile)) {
            String url = uploadImage(coverFile, "user-covers");
            if (url != null) {
                deleteImage(user.getCoverUrl());
                user.setCoverUrl(url);
            }
        }

        userRepository.save(user);
        session.setAttribute("loggedInUser", user); // cập nhật lại session để navbar hiện tên mới

        return "redirect:/users/" + user.getUserId();
    }

    @PostMapping("/users/{id}/ban")
    public String banUserFromProfile(@PathVariable("id") int userId, HttpSession session) {
        User viewer = (User) session.getAttribute("loggedInUser");
        Account targetAccount = accountRepository.findByUserUserId(userId).orElse(null);

        if (canManageAccount(viewer, targetAccount)) {
            targetAccount.setDeleted(true);
            if (targetAccount.getUser() != null) {
                targetAccount.getUser().setDeleted(true);
                userRepository.save(targetAccount.getUser());
            }
            accountRepository.save(targetAccount);
        }

        return "redirect:/users/" + userId;
    }

    @PostMapping("/users/{id}/unban")
    public String unbanUserFromProfile(@PathVariable("id") int userId, HttpSession session) {
        User viewer = (User) session.getAttribute("loggedInUser");
        Account targetAccount = accountRepository.findByUserUserId(userId).orElse(null);

        if (canManageAccount(viewer, targetAccount)) {
            targetAccount.setDeleted(false);
            if (targetAccount.getUser() != null) {
                targetAccount.getUser().setDeleted(false);
                userRepository.save(targetAccount.getUser());
            }
            accountRepository.save(targetAccount);
        }

        return "redirect:/users/" + userId;
    }

    // ---------- helpers ----------

    private boolean isStaff(User user) {
        if (user == null) {
            return false;
        }
        Account account = accountRepository.findByUserUserId(user.getUserId()).orElse(null);
        return account != null && (account.getRole() == AccountRole.ADMIN || account.getRole() == AccountRole.MOD);
    }

    private boolean canManageAccount(User viewer, Account targetAccount) {
        if (viewer == null || targetAccount == null || targetAccount.getUser() == null) {
            return false;
        }

        Account currentAccount = accountRepository.findByUserUserId(viewer.getUserId()).orElse(null);
        if (currentAccount == null || currentAccount.getAccountId() == targetAccount.getAccountId()) {
            return false;
        }

        if (currentAccount.getRole() == AccountRole.ADMIN) {
            return true;
        }

        return currentAccount.getRole() == AccountRole.MOD
                && targetAccount.getRole() == AccountRole.USER;
    }

    private boolean isImage(MultipartFile file) {
        return file != null && !file.isEmpty()
                && file.getContentType() != null && file.getContentType().startsWith("image/");
    }

    private String uploadImage(MultipartFile imageFile, String folder) {
        return r2StorageService.uploadImage(imageFile, folder);
    }

    private void deleteImage(String url) {
        r2StorageService.deleteByUrl(url);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}