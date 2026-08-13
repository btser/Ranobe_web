package main.controller;

import main.entity.Product;
import main.entity.Volume;
import main.entity.Chapter;
import main.repository.ProductRepository;
import main.repository.VolumeRepository;
import main.repository.ChapterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manage")
public class ContentController {
    @Autowired private ProductRepository productRepository;
    @Autowired private VolumeRepository volumeRepository;
    @Autowired private ChapterRepository chapterRepository;

    // ================= PRODUCT =================
    @GetMapping("/products")
    public List<Product> getAllActiveProducts() {
        return productRepository.findByIsDeletedFalse();
    }

    @PostMapping("/products")
    public Product createProduct(@RequestBody Product product) {
        return productRepository.save(product);
    }

    @PutMapping("/products/{id}/toggle-hide")
    public String toggleHideProduct(@PathVariable Integer id) {
        Product product = productRepository.findById(id).orElse(null);
        if (product != null) {
            product.setDeleted(!product.isDeleted());
            productRepository.save(product);
            return "Đã đổi trạng thái ẩn cho Product ID: " + id;
        }
        return "Không tìm thấy Product!";
    }

    // ================= VOLUME =================
    @PostMapping("/volumes")
    public Volume createVolume(@RequestBody Volume volume) {
        return volumeRepository.save(volume);
    }

    @PutMapping("/volumes/{id}/toggle-hide")
    public String toggleHideVolume(@PathVariable Integer id) {
        Volume volume = volumeRepository.findById(id).orElse(null);
        if (volume != null) {
            volume.setDeleted(!volume.isDeleted());
            volumeRepository.save(volume);
            return "Đã đổi trạng thái ẩn cho Volume ID: " + id;
        }
        return "Không tìm thấy Volume!";
    }

    // ================= CHAPTER =================
    @PostMapping("/chapters")
    public Chapter createChapter(@RequestBody Chapter chapter) {
        return chapterRepository.save(chapter);
    }

    @PutMapping("/chapters/{id}/toggle-hide")
    public String toggleHideChapter(@PathVariable Integer id) {
        Chapter chapter = chapterRepository.findById(id).orElse(null);
        if (chapter != null) {
            chapter.setDeleted(!chapter.isDeleted());
            chapterRepository.save(chapter);
            return "Đã đổi trạng thái ẩn cho chương: " + id;
        }
        return "Không tìm thấy chương!";
    }
}