package main.controller;

import main.entity.Category;
import main.entity.Chapter;
import main.entity.Product;
import main.entity.Status;
import main.entity.User;
import main.entity.Volume;
import main.repository.CategoryRepository;
import main.repository.ProductRepository;
import main.repository.UserRepository;
import main.repository.VolumeRepository;
import main.repository.ChapterRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

import java.util.List;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final VolumeRepository volumeRepository;
    private final ChapterRepository chapterRepository;

    public ProductController(ProductRepository productRepository, 
                             CategoryRepository categoryRepository, 
                             UserRepository userRepository,
                             VolumeRepository volumeRepository,
                             ChapterRepository chapterRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.volumeRepository = volumeRepository;
        this.chapterRepository = chapterRepository;
    }

    // 1. Hiển thị trang Form tạo truyện
    @GetMapping("/add")
    public String showAddForm(Model model) {
        List<Category> categories = categoryRepository.findByIsDeletedFalse();
        model.addAttribute("product", new Product());
        model.addAttribute("categories", categories);
        model.addAttribute("statuses", Status.values());
        return "product-add";
    }

    // 2. Xử lý lưu truyện từ Form
    @PostMapping("/add")
    public String saveProduct(@ModelAttribute("product") Product product, 
                            @RequestParam(value = "categoryId", required = false) Integer categoryId,
                            jakarta.servlet.http.HttpSession session) {
        
        // 1. Kiểm tra xem đã đăng nhập chưa
        main.entity.User currentUser = (main.entity.User) session.getAttribute("loggedInUser");
        if (currentUser == null) {
            return "redirect:/login"; // Chưa đăng nhập thì bắt quay về login
        }

        // 2. Gán Category
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId).orElse(null);
            product.setCategory(category);
        }

        // 3. Gán CHÍNH USER ĐANG ĐĂNG NHẬP làm Uploader
        product.setUploader(currentUser);

        // 4. Lưu xuống Database
        productRepository.save(product);

        return "redirect:/";
    }

    // 3. product detail
    @GetMapping("/{id}")
    public String showProductDetail(@PathVariable("id") Integer productId, Model model, HttpSession session) {
        // Dùng findByIdWithDetails để JOIN FETCH sẵn Category & Uploader
        Product product = productRepository.findByIdWithDetails(productId).orElse(null);
        if (product == null) {
            return "redirect:/";
        }

        // Dùng findByProductIdWithChapters để JOIN FETCH sẵn Chapters
        List<Volume> volumes = volumeRepository.findByProductIdWithChapters(productId);

        User currentUser = (User) session.getAttribute("loggedInUser");

        boolean isOwner = false;
        if (currentUser != null && product.getUploader() != null)
        {
            if (currentUser.getUserId() == product.getUploader().getUserId())
            {
                isOwner = true;
            }
        }

        model.addAttribute("product", product);
        model.addAttribute("volumes", volumes);
        model.addAttribute("user", currentUser);
        model.addAttribute("isOwner", isOwner);

        return "product-detail";
    }

    // 4. Add volume into product
    @PostMapping("/{id}/add-volume")
    public String addVolume(@PathVariable("id") Integer productId, 
        @RequestParam(value = "title", required = false) String title, HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Product product = productRepository.findByIdWithDetails(productId).orElse(null);

            if (canManageProduct(currentUser, product)) {
                    Volume newVolume = new Volume();
                    newVolume.setProduct(product);
                    newVolume.setTitle(cleanTitle(title));
                    newVolume.setDeleted(false);
                    volumeRepository.save(newVolume);
            }

            return "redirect:/products/" + productId;
    }

    @PostMapping("/{id}/edit-title")
    public String editProductTitle(@PathVariable("id") Integer productId,
        @RequestParam("title") String title, HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Product product = productRepository.findByIdWithDetails(productId).orElse(null);

            if (canManageProduct(currentUser, product)) {
                String cleanTitle = cleanTitle(title);
                if (cleanTitle != null) {
                    product.setTitle(cleanTitle);
                    productRepository.save(product);
                }
            }

            return "redirect:/products/" + productId;
    }

    @PostMapping("/volume/{volumeId}/edit-title")
    public String editVolumeTitle(@PathVariable("volumeId") int volumeId,
        @RequestParam("title") String title, HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Volume volume = volumeRepository.findByIdWithProductUploader(volumeId).orElse(null);

            if (canManageVolume(currentUser, volume)) {
                volume.setTitle(cleanTitle(title));
                volumeRepository.save(volume);
                return "redirect:/products/" + volume.getProduct().getProductId();
            }

            return "redirect:/";
    }

    @PostMapping("/chapter/{chapterId}/edit-title")
    public String editChapterTitle(@PathVariable("chapterId") int chapterId,
        @RequestParam("title") String title, HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Chapter chapter = chapterRepository.findByIdWithVolumeProductUploader(chapterId).orElse(null);

            if (chapter != null && canManageVolume(currentUser, chapter.getVolume())) {
                String cleanTitle = cleanTitle(title);
                if (cleanTitle != null) {
                    chapter.setTitle(cleanTitle);
                    chapterRepository.save(chapter);
                }
                return "redirect:/products/" + chapter.getVolume().getProduct().getProductId();
            }

            return "redirect:/";
    }

    // Form add chapter
    @GetMapping("/volume/{volumeId}/add-chapter")
    public String showAddChapterForm(@PathVariable("volumeId") int volumeId,
        Model model, HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Volume volume = volumeRepository.findByIdWithProductUploader(volumeId).orElse(null);

            if (volume == null || currentUser == null || volume.getProduct().getUploader() == null ||
                !(currentUser.getUserId() == volume.getProduct().getUploader().getUserId()))
                    return "redirect:/";
                
            model.addAttribute("volume", volume);
            return "chapter-add";
        }


    // 5. Save chapter from form
    @PostMapping("/volume/{volumeId}/add-chapter")
    public String saveChapter(@PathVariable("volumeId") int volumeId, @RequestParam("title") String title,
        @RequestParam("context") String context, HttpSession session) {
            User currentUser = (User) session.getAttribute("loggedInUser");
            Volume volume = volumeRepository.findByIdWithProductUploader(volumeId).orElse(null);

            if (volume != null && currentUser != null && volume.getProduct().getUploader() != null &&
                currentUser.getUserId() == volume.getProduct().getUploader().getUserId()) {

                    Chapter chapter = new Chapter();
                    chapter.setVolume(volume);
                    chapter.setTitle(title);
                    chapter.setContext(context);
                    chapter.setDeleted(false);
                    chapterRepository.save(chapter);
                    
                    return "redirect:/products/" + volume.getProduct().getProductId();
                }

            return "redirect:/";
        }

    // 6. Enjoy
    @GetMapping("/chapter/{chapterId}")
    public String readChapter(@PathVariable("chapterId") Integer chapterId, Model model) {
        Chapter chapter = chapterRepository.findByIdWithVolumeProductUploader(chapterId).orElse(null);
        if (chapter == null) {
            return "redirect:/";
        }

        model.addAttribute("chapter", chapter);
        return "chapter-detail";
    }

    private boolean canManageProduct(User currentUser, Product product) {
        return currentUser != null && product != null && product.getUploader() != null
            && currentUser.getUserId() == product.getUploader().getUserId();
    }

    private boolean canManageVolume(User currentUser, Volume volume) {
        return volume != null && canManageProduct(currentUser, volume.getProduct());
    }

    private String cleanTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return title.trim();
    }
}
