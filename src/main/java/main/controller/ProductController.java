package main.controller;

import main.entity.Account;
import main.entity.AccountRole;
import main.entity.Category;
import main.entity.Chapter;
import main.entity.ChapterImage;
import main.entity.Product;
import main.entity.Status;
import main.entity.User;
import main.entity.Volume;
import main.repository.CategoryRepository;
import main.repository.ChapterRepository;
import main.repository.ChapterImageRepository;
import main.repository.ProductRepository;
import main.repository.AccountRepository;
import main.repository.VolumeRepository;
import main.service.LocalImageStorageService;
import main.service.R2StorageService;
import java.time.LocalDateTime;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final VolumeRepository volumeRepository;
    private final ChapterRepository chapterRepository;
    private final ChapterImageRepository chapterImageRepository;
    private final LocalImageStorageService localImageStorageService;
    private final R2StorageService r2StorageService;

    public ProductController(ProductRepository productRepository,
                             CategoryRepository categoryRepository,
                             AccountRepository accountRepository,
                             VolumeRepository volumeRepository,
                             ChapterRepository chapterRepository,
                             ChapterImageRepository chapterImageRepository,
                             LocalImageStorageService localImageStorageService,
                             R2StorageService r2StorageService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.volumeRepository = volumeRepository;
        this.chapterRepository = chapterRepository;
        this.chapterImageRepository = chapterImageRepository;
        this.localImageStorageService = localImageStorageService;
        this.r2StorageService = r2StorageService;
    }

    // ============== ADD PRODUCT ==============

    @GetMapping("/add")
    public String showAddForm(Model model) {
        List<Category> categories = categoryRepository.findByIsDeletedFalse();
        model.addAttribute("product", new Product());
        model.addAttribute("categories", categories);
        model.addAttribute("statuses", Status.values());
        return "product-add";
    }

    @PostMapping("/add")
    public String saveProduct(@ModelAttribute("product") Product product,
                            @RequestParam(value = "categoryId", required = false) Integer categoryId,
                            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                            HttpSession session) {

        User currentUser = (User) session.getAttribute("loggedInUser");
        if (currentUser == null) {
            return "redirect:/login";
        }

        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId).orElse(null);
            product.setCategory(category);
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            String imageUrl = uploadImage(imageFile, "covers");
            product.setCoverImage(imageUrl);
        }

        product.setUploader(currentUser);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        return "redirect:/";
    }

    // ============== PRODUCT DETAIL (READ-ONLY) ==============

    @GetMapping("/{id}")
    public String showProductDetail(@PathVariable("id") Integer productId, Model model, HttpSession session) {
        Product product = productRepository.findByIdWithDetails(productId).orElse(null);
        if (product == null) {
            return "redirect:/";
        }

        User currentUser = (User) session.getAttribute("loggedInUser");
        if ((product.isDeleted() || product.getStatus() == Status.PRIVATE) && !canManageProduct(currentUser, product)) {
            return "redirect:/";
        }

        List<Volume> volumes = volumeRepository.findByProductIdWithChapters(productId);

        model.addAttribute("product", product);
        model.addAttribute("volumes", volumes);
        model.addAttribute("user", currentUser);
        model.addAttribute("isOwner", isOwner(currentUser, product));
        model.addAttribute("isStaff", canModerate(currentUser));
        model.addAttribute("canEdit", canEditProduct(currentUser, product));
        model.addAttribute("canManageProduct", canManageProduct(currentUser, product));

        return "product-detail";
    }

    // ============== EDIT PAGE (OWNER ONLY) ==============

    @GetMapping("/{id}/edit")
    public String showEditProductForm(@PathVariable("id") Integer productId, Model model, HttpSession session) {
        Product product = productRepository.findByIdWithDetails(productId).orElse(null);
        if (product == null) {
            return "redirect:/";
        }

        User currentUser = (User) session.getAttribute("loggedInUser");
        if (!canEditProduct(currentUser, product)) {
            return "redirect:/products/" + productId;
        }

        List<Volume> volumes = volumeRepository.findByProductIdWithChapters(productId);
        List<Category> categories = categoryRepository.findByIsDeletedFalse();

        model.addAttribute("product", product);
        model.addAttribute("volumes", volumes);
        model.addAttribute("categories", categories);
        model.addAttribute("statuses", Status.values());

        return "product-edit";
    }

    // Sửa nhanh mỗi title (giữ lại - có thể dùng nơi khác)
    @PostMapping("/{id}/edit-title")
    public String editProductTitle(@PathVariable("id") Integer productId,
        @RequestParam("title") String title, HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Product product = productRepository.findByIdWithDetails(productId).orElse(null);

            if (canEditProduct(currentUser, product)) {
                String cleanTitle = cleanTitle(title);
                if (cleanTitle != null) {
                    product.setTitle(cleanTitle);
                    productRepository.save(product);
                }
            }

            return "redirect:/products/" + productId + "/edit";
    }

    // Sửa toàn bộ thông tin (tab "Thông tin")
    @PostMapping("/{id}/edit-info")
    public String editProductInfo(@PathVariable("id") Integer productId,
        @RequestParam("title") String title,
        @RequestParam(value = "author", required = false) String author,
        @RequestParam(value = "artist", required = false) String artist,
        @RequestParam(value = "categoryId", required = false) Integer categoryId,
        @RequestParam(value = "status", required = false) Status status,
        @RequestParam(value = "summary", required = false) String summary,
        HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Product product = productRepository.findByIdWithDetails(productId).orElse(null);

            if (canEditProduct(currentUser, product)) {
                String cleanTitle = cleanTitle(title);
                if (cleanTitle != null) {
                    product.setTitle(cleanTitle);
                }
                product.setAuthor(author);
                product.setArtist(artist);
                product.setSummary((summary == null || summary.isBlank()) ? null : summary.trim());
                if (status != null) {
                    product.setStatus(status);
                }
                if (categoryId != null) {
                    categoryRepository.findById(categoryId).ifPresent(product::setCategory);
                }
                productRepository.save(product);
            }

            return "redirect:/products/" + productId + "/edit";
    }

    // ============== VOLUME ==============

    @PostMapping("/{id}/add-volume")
    public String addVolume(@PathVariable("id") Integer productId,
        @RequestParam(value = "title", required = false) String title, HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Product product = productRepository.findByIdWithDetails(productId).orElse(null);

            if (canEditProduct(currentUser, product)) {
                Volume newVolume = new Volume();
                newVolume.setProduct(product);
                newVolume.setTitle(cleanTitle(title));
                newVolume.setDeleted(false);

                int maxOrder = volumeRepository.findMaxSortOrderByProductId(productId);
                newVolume.setSortOrder(maxOrder + 1);

                volumeRepository.save(newVolume);
            }

            return "redirect:/products/" + productId + "/edit";
    }

    @PostMapping("/volume/{volumeId}/edit-title")
    public String editVolumeTitle(@PathVariable("volumeId") int volumeId,
        @RequestParam("title") String title,
        @RequestParam(value = "summary", required = false) String summary,
        HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Volume volume = volumeRepository.findByIdWithProductUploader(volumeId).orElse(null);

            if (canManageVolume(currentUser, volume)) {
                volume.setTitle(cleanTitle(title));
                if (summary != null) { // chỉ cập nhật khi form có gửi ô tóm tắt
                    volume.setSummary(summary.isBlank() ? null : summary.trim());
                }
                volumeRepository.save(volume);
                return "redirect:/products/" + volume.getProduct().getProductId() + "/edit";
            }

            return "redirect:/";
    }

    @PostMapping("/volume/{volumeId}/delete")
    public String deleteVolume(@PathVariable("volumeId") int volumeId, HttpSession session) {
        User currentUser = (User) session.getAttribute("loggedInUser");
        Volume volume = volumeRepository.findByIdWithProductUploader(volumeId).orElse(null);

        if (canManageVolume(currentUser, volume)) {
            int productId = volume.getProduct().getProductId();
            volume.setDeleted(true);
            volumeRepository.save(volume);
            return "redirect:/products/" + productId + "/edit";
        }

        return "redirect:/";
    }

    // Kéo thả sắp xếp volume - AJAX
    @PostMapping("/{id}/volumes/reorder")
    @ResponseBody
    public Map<String, Object> reorderVolumes(@PathVariable("id") Integer productId,
        @RequestBody List<Integer> orderedVolumeIds, HttpSession session) {

            Map<String, Object> result = new HashMap<>();
            User currentUser = (User) session.getAttribute("loggedInUser");
            Product product = productRepository.findByIdWithDetails(productId).orElse(null);

            if (!canEditProduct(currentUser, product)) {
                result.put("success", false);
                return result;
            }

            List<Volume> volumes = volumeRepository.findActiveByProductIdOrderBySortOrder(productId);
            Map<Integer, Volume> volumeMap = new HashMap<>();
            for (Volume v : volumes) {
                volumeMap.put(v.getVolumeId(), v);
            }

            // Chỉ chấp nhận nếu danh sách id gửi lên thuộc đúng product này
            if (!volumeMap.keySet().containsAll(orderedVolumeIds)) {
                result.put("success", false);
                return result;
            }

            int order = 0;
            for (Integer vid : orderedVolumeIds) {
                Volume v = volumeMap.get(vid);
                v.setSortOrder(order++);
                volumeRepository.save(v);
            }

            result.put("success", true);
            return result;
    }

        // Upload / thay ảnh bìa cho volume
    @PostMapping("/volume/{volumeId}/cover")
    public String uploadVolumeCover(@PathVariable("volumeId") int volumeId,
        @RequestParam("coverFile") MultipartFile coverFile, HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Volume volume = volumeRepository.findByIdWithProductUploader(volumeId).orElse(null);

            if (canManageVolume(currentUser, volume) && coverFile != null && !coverFile.isEmpty()) {
                    String imageUrl = uploadImage(coverFile, "covers");
                if (imageUrl != null) {
                    deleteImage(volume.getCoverImage());
                    volume.setCoverImage(imageUrl);
                    volumeRepository.save(volume);
                }
                return "redirect:/products/" + volume.getProduct().getProductId() + "/edit";
            }

            return "redirect:/";
    }

        // Gỡ ảnh bìa volume
    @PostMapping("/volume/{volumeId}/cover/remove")
    public String removeVolumeCover(@PathVariable("volumeId") int volumeId, HttpSession session) {
        User currentUser = (User) session.getAttribute("loggedInUser");
        Volume volume = volumeRepository.findByIdWithProductUploader(volumeId).orElse(null);

        if (canManageVolume(currentUser, volume)) {
            deleteImage(volume.getCoverImage());
            volume.setCoverImage(null);
            volumeRepository.save(volume);
            return "redirect:/products/" + volume.getProduct().getProductId() + "/edit";
        }
        return "redirect:/";
    }

    // Đổi ảnh bìa truyện
    @PostMapping("/{id}/cover")
    public String uploadProductCover(@PathVariable("id") Integer productId,
        @RequestParam("coverFile") MultipartFile coverFile, HttpSession session) {

        User currentUser = (User) session.getAttribute("loggedInUser");
        Product product = productRepository.findByIdWithDetails(productId).orElse(null);

        if (canEditProduct(currentUser, product)) {
            if (coverFile != null && !coverFile.isEmpty()) {
                String imageUrl = uploadImage(coverFile, "covers");
                if (imageUrl != null) {
                    deleteImage(product.getCoverImage());
                    product.setCoverImage(imageUrl);
                    productRepository.save(product);
                }
            }
            return "redirect:/products/" + productId + "/edit";
        }
        return "redirect:/";
    }

    // Gỡ ảnh bìa truyện (quay về ảnh mặc định)
    @PostMapping("/{id}/cover/remove")
    public String removeProductCover(@PathVariable("id") Integer productId, HttpSession session) {
        User currentUser = (User) session.getAttribute("loggedInUser");
        Product product = productRepository.findByIdWithDetails(productId).orElse(null);

        if (canEditProduct(currentUser, product)) {
            deleteImage(product.getCoverImage());
            product.setCoverImage(null);
            productRepository.save(product);
            return "redirect:/products/" + productId + "/edit";
        }
        return "redirect:/";
    }

    // ============== CHAPTER ==============

    @GetMapping("/volume/{volumeId}/add-chapter")
    public String showAddChapterForm(@PathVariable("volumeId") int volumeId,
        Model model, HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Volume volume = volumeRepository.findByIdWithProductUploader(volumeId).orElse(null);

            if (!canManageVolume(currentUser, volume)) {
                return "redirect:/";
            }

            model.addAttribute("volume", volume);
            return "chapter-add";
        }

    @PostMapping("/volume/{volumeId}/add-chapter")
    public String saveChapter(@PathVariable("volumeId") int volumeId, @RequestParam("title") String title,
        @RequestParam("context") String context, HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Volume volume = volumeRepository.findByIdWithProductUploader(volumeId).orElse(null);

            if (canManageVolume(currentUser, volume)) {
                Chapter chapter = new Chapter();
                chapter.setVolume(volume);
                chapter.setTitle(title);
                chapter.setContext(context);
                chapter.setDeleted(false);

                int maxOrder = chapterRepository.findMaxSortOrderByVolumeId(volumeId);
                chapter.setSortOrder(maxOrder + 1);

                chapterRepository.save(chapter);
                touchProduct(volume.getProduct().getProductId());

                return "redirect:/products/" + volume.getProduct().getProductId() + "/edit";
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
                return "redirect:/products/" + chapter.getVolume().getProduct().getProductId() + "/edit";
            }

            return "redirect:/";
    }

    // Sửa nội dung chương
    @GetMapping("/chapter/{chapterId}/edit")
    public String showEditChapterForm(@PathVariable("chapterId") int chapterId,
        Model model, HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Chapter chapter = chapterRepository.findByIdWithVolumeProductUploader(chapterId).orElse(null);

            if (chapter == null || !canManageVolume(currentUser, chapter.getVolume())) {
                return "redirect:/";
            }

            List<ChapterImage> images = chapterImageRepository.findByChapterChapterIdOrderBySortOrderAsc(chapterId);

            model.addAttribute("chapter", chapter);
            model.addAttribute("images", images);
            return "chapter-edit";
    }

    @PostMapping("/chapter/{chapterId}/edit")
    public String editChapterContent(@PathVariable("chapterId") int chapterId,
        @RequestParam("title") String title,
        @RequestParam("context") String context, HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Chapter chapter = chapterRepository.findByIdWithVolumeProductUploader(chapterId).orElse(null);

            if (chapter == null || !canManageVolume(currentUser, chapter.getVolume())) {
                return "redirect:/";
            }

            String cleanTitle = cleanTitle(title);
            if (cleanTitle != null) {
                chapter.setTitle(cleanTitle);
            }
            chapter.setContext(context);
            chapterRepository.save(chapter);
            touchProduct(chapter.getVolume().getProduct().getProductId());

            return "redirect:/products/" + chapter.getVolume().getProduct().getProductId() + "/edit";
    }

    // Delete chapter

    @PostMapping("/chapter/{chapterId}/delete")
    public String deleteChapter(@PathVariable("chapterId") int chapterId, HttpSession session) {
        User currentUser = (User) session.getAttribute("loggedInUser");
        Chapter chapter = chapterRepository.findByIdWithVolumeProductUploader(chapterId).orElse(null);

        if (chapter != null && canManageVolume(currentUser, chapter.getVolume())) {
            int productId = chapter.getVolume().getProduct().getProductId();
            chapter.setDeleted(true);
            chapterRepository.save(chapter);
            return "redirect:/products/" + productId + "/edit";
        }

        return "redirect:/";
    }

    // Add illustrations into chapter
    @PostMapping("/chapter/{chapterId}/images")
    public String addChapterImages(@PathVariable("chapterId") int chapterId,
        @RequestParam("imageFiles") List<MultipartFile> imageFiles, HttpSession session) {

            User currentUser = (User) session.getAttribute("loggedInUser");
            Chapter chapter = chapterRepository.findByIdWithVolumeProductUploader(chapterId).orElse(null);

            if (chapter != null && canManageVolume(currentUser, chapter.getVolume())
                    && imageFiles != null && !imageFiles.isEmpty()) {

                int maxOrder = chapterImageRepository.findMaxSortOrderByChapterId(chapterId);
                int uploadedCount = 0;
                List<String> uploadErrors = new ArrayList<>();

                for (MultipartFile imageFile : imageFiles) {
                    if (imageFile == null || imageFile.isEmpty()) {
                        continue;
                    }

                    String imageUrl = uploadImage(imageFile, "chapters/" + chapterId);
                    if (imageUrl != null) {
                        ChapterImage image = new ChapterImage(chapter, imageUrl, ++maxOrder);
                        chapterImageRepository.save(image);
                        uploadedCount++;
                    } else {
                        uploadErrors.add(imageFile.getOriginalFilename());
                    }
                }

                String redirectUrl = "redirect:/products/chapter/" + chapterId + "/edit";
                if (uploadedCount > 0) {
                    redirectUrl += "?uploaded=" + uploadedCount;
                }
                if (!uploadErrors.isEmpty() || uploadedCount == 0) {
                    redirectUrl += (uploadedCount > 0 ? "&" : "?") + "uploadError=true";
                }
                return redirectUrl;
            }

            return chapter != null ? "redirect:/products/chapter/" + chapterId + "/edit?uploadError=true" : "redirect:/";
    }

    // Xóa 1 ảnh minh họa khỏi chương
    @PostMapping("/chapter/image/{imageId}/delete")
    public String deleteChapterImage(@PathVariable("imageId") int imageId, HttpSession session) {
        User currentUser = (User) session.getAttribute("loggedInUser");
        ChapterImage image = chapterImageRepository.findByIdWithChapterVolumeProductUploader(imageId).orElse(null);

        if (image != null && canManageVolume(currentUser, image.getChapter().getVolume())) {
            int chapterId = image.getChapter().getChapterId();
            chapterImageRepository.delete(image);
            return "redirect:/products/chapter/" + chapterId + "/edit";
        }

        return "redirect:/";
    }

    // Kéo thả sắp xếp ảnh trong 1 chương - AJAX
    @PostMapping("/chapter/{chapterId}/images/reorder")
    @ResponseBody
    public Map<String, Object> reorderChapterImages(@PathVariable("chapterId") int chapterId,
        @RequestBody List<Integer> orderedImageIds, HttpSession session) {

            Map<String, Object> result = new HashMap<>();
            User currentUser = (User) session.getAttribute("loggedInUser");
            Chapter chapter = chapterRepository.findByIdWithVolumeProductUploader(chapterId).orElse(null);

            if (!canManageVolume(currentUser, chapter != null ? chapter.getVolume() : null)) {
                result.put("success", false);
                return result;
            }

            List<ChapterImage> images = chapterImageRepository.findByChapterChapterIdOrderBySortOrderAsc(chapterId);
            Map<Integer, ChapterImage> imageMap = new HashMap<>();
            for (ChapterImage img : images) {
                imageMap.put(img.getImageId(), img);
            }

            if (!imageMap.keySet().containsAll(orderedImageIds)) {
                result.put("success", false);
                return result;
            }

            int order = 0;
            for (Integer iid : orderedImageIds) {
                ChapterImage img = imageMap.get(iid);
                img.setSortOrder(order++);
                chapterImageRepository.save(img);
            }

            result.put("success", true);
            return result;
    }

    // Kéo thả sắp xếp chương trong 1 volume - AJAX
    @PostMapping("/volume/{volumeId}/chapters/reorder")
    @ResponseBody
    public Map<String, Object> reorderChapters(@PathVariable("volumeId") int volumeId,
        @RequestBody List<Integer> orderedChapterIds, HttpSession session) {

            Map<String, Object> result = new HashMap<>();
            User currentUser = (User) session.getAttribute("loggedInUser");
            Volume volume = volumeRepository.findByIdWithProductUploader(volumeId).orElse(null);

            if (!canManageVolume(currentUser, volume)) {
                result.put("success", false);
                return result;
            }

            List<Chapter> chapters = chapterRepository.findByVolumeVolumeIdAndIsDeletedFalseOrderBySortOrderAsc(volumeId);
            Map<Integer, Chapter> chapterMap = new HashMap<>();
            for (Chapter c : chapters) {
                chapterMap.put(c.getChapterId(), c);
            }

            if (!chapterMap.keySet().containsAll(orderedChapterIds)) {
                result.put("success", false);
                return result;
            }

            int order = 0;
            for (Integer cid : orderedChapterIds) {
                Chapter c = chapterMap.get(cid);
                c.setSortOrder(order++);
                chapterRepository.save(c);
            }

            result.put("success", true);
            return result;
    }

    // 6. Enjoy (Chan bo may de)
    @GetMapping("/chapter/{chapterId}")
    public String readChapter(@PathVariable("chapterId") Integer chapterId, Model model, HttpSession session) {
        Chapter chapter = chapterRepository.findByIdWithVolumeProductUploader(chapterId).orElse(null);
        if (chapter == null) {
            return "redirect:/";
        }
        User currentUser = (User) session.getAttribute("loggedInUser");
        Product product = chapter.getVolume().getProduct();
        if ((product.isDeleted() || product.getStatus() == Status.PRIVATE) && !canManageProduct(currentUser, product)) {
            return "redirect:/";
        }

        List<ChapterImage> images = chapterImageRepository.findByChapterChapterIdOrderBySortOrderAsc(chapterId);

        model.addAttribute("chapter", chapter);
        model.addAttribute("images", images);
        model.addAttribute("blocks", main.service.ChapterContent.parse(chapter.getContext()));
        return "chapter-detail";
    }

    @PostMapping("/{id}/hide")
    public String hideProduct(@PathVariable("id") Integer productId, HttpSession session) {
        User currentUser = (User) session.getAttribute("loggedInUser");
        Product product = productRepository.findByIdWithDetails(productId).orElse(null);

        if (canModerate(currentUser) && product != null) {
            product.setDeleted(true);
            productRepository.save(product);
        }

        return "redirect:/admin";
    }

    @PostMapping("/{id}/restore")
    public String restoreProduct(@PathVariable("id") Integer productId, HttpSession session) {
        User currentUser = (User) session.getAttribute("loggedInUser");
        Product product = productRepository.findByIdWithDetails(productId).orElse(null);

        if (canModerate(currentUser) && product != null) {
            product.setDeleted(false);
            productRepository.save(product);
        }

        return "redirect:/admin";
    }

    // ============== HELPERS ==============

    private boolean canManageProduct(User currentUser, Product product) {       // Only mod or admin can manage product
        return canModerate(currentUser);
    }

    private boolean canEditProduct(User currentUser, Product product) {         // Only owner can edit product
        return isOwner(currentUser, product);
    }

    private boolean isOwner(User currentUser, Product product) {
        return currentUser != null && product != null && product.getUploader() != null
            && currentUser.getUserId() == product.getUploader().getUserId();
    }

    private boolean canManageVolume(User currentUser, Volume volume) {
        return volume != null && canEditProduct(currentUser, volume.getProduct());
    }

    private boolean canModerate(User currentUser) {
        Account account = getAccount(currentUser);
        return account != null && (account.getRole() == AccountRole.ADMIN || account.getRole() == AccountRole.MOD);
    }

    private Account getAccount(User currentUser) {
        if (currentUser == null) {
            return null;
        }
        return accountRepository.findByUserUserId(currentUser.getUserId()).orElse(null);
    }

    private String uploadImage(MultipartFile imageFile, String folder) {
        return r2StorageService.uploadImage(imageFile, folder);
    }

    private void deleteImage(String url) {
        r2StorageService.deleteByUrl(url);
    }

    private void touchProduct(int productId) {
        productRepository.touch(productId, LocalDateTime.now());
    }

    private String cleanTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return title.trim();
    }
}
