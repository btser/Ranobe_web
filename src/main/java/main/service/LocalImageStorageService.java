package main.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalImageStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    @Value("${upload.local-dir:uploads}")
    private String uploadDir;

    public String saveImage(MultipartFile file, String folder) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IOException("File ảnh không hợp lệ. Chỉ hỗ trợ JPG, PNG, WEBP hoặc GIF.");
        }

        Path targetDir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(folder).normalize();
        Files.createDirectories(targetDir);

        String fileName = UUID.randomUUID() + "." + extension;
        Path targetFile = targetDir.resolve(fileName).normalize();
        if (!targetFile.startsWith(targetDir)) {
            throw new IOException("Tên file không hợp lệ.");
        }

        file.transferTo(targetFile);
        return "/uploads/" + folder + "/" + fileName;
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
