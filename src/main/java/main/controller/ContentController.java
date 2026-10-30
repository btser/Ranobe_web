package main.controller;

import jakarta.servlet.http.HttpSession;
import main.entity.*;
import main.repository.*;
import main.service.AccessPolicy;
import main.service.ChapterContent;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manage")
public class ContentController {
    private final ProductRepository products;
    private final VolumeRepository volumes;
    private final ChapterRepository chapters;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final AccessPolicy access;

    public ContentController(ProductRepository products, VolumeRepository volumes,
                             ChapterRepository chapters, CategoryRepository categories,
                             UserRepository users, AccessPolicy access) {
        this.products = products;
        this.volumes = volumes;
        this.chapters = chapters;
        this.categories = categories;
        this.users = users;
        this.access = access;
    }

    public record CategoryRef(int categoryId) {}
    public record ProductRef(int productId) {}
    public record VolumeRef(int volumeId) {}
    public record ProductInput(String title, String author, String artist,
                               Status status, CategoryRef category) {}
    public record VolumeInput(String title, ProductRef product) {}
    public record ChapterInput(String title, @com.fasterxml.jackson.annotation.JsonAlias("context") String content, VolumeRef volume) {}
    public record ProductView(int productId, String title, Status status, String path) {}

    @GetMapping("/products")
    public List<ProductView> getAllActiveProducts(HttpSession session) {
        requireStaff(session);
        return products.findByIsDeletedFalse().stream()
                .map(p -> new ProductView(p.getProductId(), p.getTitle(), p.getStatus(), p.getPath()))
                .toList();
    }

    @PostMapping("/products")
    @Transactional
    public Map<String, Object> createProduct(@RequestBody ProductInput input, HttpSession session) {
        User viewer = requireUser(session);
        if (input.category() == null) throw badRequest("Can chon the loai.");
        Category category = categories.findById(input.category().categoryId())
                .orElseThrow(() -> badRequest("The loai khong ton tai."));
        if (category.isDeleted()) throw badRequest("The loai da bi an.");

        Product product = new Product();
        product.setTitle(requiredTitle(input.title()));
        product.setAuthor(optionalText(input.author(), 150));
        product.setArtist(optionalText(input.artist(), 150));
        product.setStatus(input.status() == null ? Status.PRIVATE : input.status());
        product.setCategory(category);
        product.getCategories().add(category);
        product.setUploader(users.findById(viewer.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
        product.setDeleted(false);
        product.setUpdatedAt(LocalDateTime.now());
        products.save(product);
        return Map.of("productId", product.getProductId(), "path", product.getPath());
    }

    @PutMapping("/products/{id}/toggle-hide")
    @Transactional
    public Map<String, Object> toggleHideProduct(@PathVariable("id") int id, HttpSession session) {
        requireStaff(session);
        Product product = products.findById(id).orElseThrow(ContentController::notFound);
        product.setDeleted(!product.isDeleted());
        products.save(product);
        return Map.of("productId", id, "deleted", product.isDeleted());
    }

    @PostMapping("/volumes")
    @Transactional
    public Map<String, Object> createVolume(@RequestBody VolumeInput input, HttpSession session) {
        User viewer = requireUser(session);
        if (input.product() == null) throw badRequest("Can chon truyen.");
        Product product = products.findByIdWithDetails(input.product().productId())
                .orElseThrow(ContentController::notFound);
        if (!access.canEdit(viewer, product)) throw forbidden();
        Volume volume = new Volume();
        volume.setProduct(product);
        volume.setTitle(optionalText(input.title(), 255));
        volume.setSortOrder(volumes.findMaxSortOrderByProductId(product.getProductId()) + 1);
        volume.setDeleted(false);
        volumes.save(volume);
        return Map.of("volumeId", volume.getVolumeId());
    }

    @PutMapping("/volumes/{id}/toggle-hide")
    @Transactional
    public Map<String, Object> toggleHideVolume(@PathVariable("id") int id, HttpSession session) {
        requireStaff(session);
        Volume volume = volumes.findById(Integer.valueOf(id)).orElseThrow(ContentController::notFound);
        volume.setDeleted(!volume.isDeleted());
        volumes.save(volume);
        return Map.of("volumeId", id, "deleted", volume.isDeleted());
    }

    @PostMapping("/chapters")
    @Transactional
    public Map<String, Object> createChapter(@RequestBody ChapterInput input, HttpSession session) {
        User viewer = requireUser(session);
        if (input.volume() == null) throw badRequest("Can chon tap.");
        Volume volume = volumes.findByIdWithProductUploader(input.volume().volumeId())
                .orElseThrow(ContentController::notFound);
        if (!access.canEdit(viewer, volume.getProduct())) throw forbidden();
        if (input.content() == null || input.content().isBlank()
                || input.content().length() > 1_000_000) {
            throw badRequest("Noi dung can 1-1000000 ky tu.");
        }
        Chapter chapter = new Chapter();
        chapter.setVolume(volume);
        chapter.setTitle(requiredTitle(input.title()));
        // Noi dung gui len o dang ma danh dau trang; loc truoc khi luu.
        chapter.setContent(ChapterContent.sanitize(input.content()));
        chapter.setSortOrder(chapters.findMaxSortOrderByVolumeId(volume.getVolumeId()) + 1);
        chapter.setDeleted(false);
        chapters.save(chapter);
        products.touch(volume.getProduct().getProductId(), LocalDateTime.now());
        return Map.of("chapterId", chapter.getChapterId());
    }

    // Truy van truc tiep de khoi phuc ca chuong bi bo loc an.
    @PutMapping("/chapters/{id}/toggle-hide")
    @Transactional
    public Map<String, Object> toggleHideChapter(@PathVariable("id") int id, HttpSession session) {
        requireStaff(session);
        if (chapters.toggleDeletedById(id) == 0) throw notFound();
        return Map.of("chapterId", id, "deleted", chapters.findDeletedFlagById(id));
    }

    private User requireUser(HttpSession session) {
        User viewer = (User) session.getAttribute("loggedInUser");
        if (viewer == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return viewer;
    }

    private void requireStaff(HttpSession session) {
        if (!access.canModerate(requireUser(session))) throw forbidden();
    }

    private static String requiredTitle(String value) {
        String clean = optionalText(value, 255);
        if (clean == null) throw badRequest("Tieu de khong duoc de trong.");
        return clean;
    }

    private static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() > max) throw badRequest("Noi dung vuot do dai cho phep.");
        return clean;
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
