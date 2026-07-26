package main;

import main.entity.Product;
import main.entity.Status;
import main.repository.ProductRepository; // Giả sử ông đã tạo ProductRepository
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public CommandLineRunner testDatabase(ProductRepository productRepository) {
        return args -> {
            System.out.println("====== BẮT ĐẦU TEST DATABASE ======");

            // 1. Tạo thử 1 object Product
            Product testProduct = new Product();
            testProduct.setTitle("Truyện Test Đêm Khuya");
            testProduct.setAuthor("Tác giả A");
            testProduct.setStatus(Status.PUBLIC);

            // 2. Lưu vào PostgreSQL
            Product savedProduct = productRepository.save(testProduct);
            System.out.println("-> Đã lưu thành công Product ID: " + savedProduct.getProductId());

            // 3. Đọc lại từ PostgreSQL ra Console
            System.out.println("-> Danh sách truyện hiện có trong DB:");
            productRepository.findAll().forEach(p -> 
                System.out.println("   + " + p.getProductId() + " | " + p.getTitle())
            );

            System.out.println("====== TEST THÀNH CÔNG ======");
        };
    }
}