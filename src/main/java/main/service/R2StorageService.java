package main.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@Service
public class R2StorageService {

    private static final int MAX_WIDTH = 1600;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(R2StorageService.class);

    private final S3Client s3;
    private final String bucket;
    private final String publicUrl;
    public R2StorageService(@Value("${r2.account-id}") String accountId,
                            @Value("${r2.access-key}") String accessKey,
                            @Value("${r2.secret-key}") String secretKey,
                            @Value("${r2.bucket}") String bucket,
                            @Value("${r2.public-url}") String publicUrl) {
        this.bucket = bucket;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(30)))
                .build();
    }

    /** Upload ảnh (đã nén nếu được), trả về URL công khai cố định hoặc null nếu lỗi. */
    public String uploadImage(MultipartFile file, String folder) {
        try {
            if (file == null || file.isEmpty()) {
                return null;
            }
            String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            return uploadBytes(file.getBytes(), contentType, folder);
        } catch (Exception e) {
            log.error("Upload R2 lỗi: {}", e.toString(), e);
            return null;
        }
    }

    /** Upload ảnh từ mảng byte (dùng cho ảnh trang PDF). Trả về URL công khai hoặc null nếu lỗi. */
    public String uploadBytes(byte[] bytes, String contentType, String folder) {
        try {
            String ext = extensionOf(contentType);

            byte[] compressed = compress(bytes, contentType);
            if (compressed != null) {
                bytes = compressed;
                contentType = "image/jpeg";
                ext = "jpg";
            }

            String key = folder + "/" + UUID.randomUUID() + "." + ext;
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .cacheControl("public, max-age=31536000, immutable")
                            .build(),
                    RequestBody.fromBytes(bytes));
            return publicUrl + "/" + key;
        } catch (Exception e) {
            log.error("Upload R2 lỗi: {}", e.toString(), e);
            return null;
        }
    }

    /** Xóa ảnh trên R2 nếu url thuộc bucket của mình. Lỗi thì bỏ qua. */
    public void deleteByUrl(String url) {
        try {
            if (url == null || !url.startsWith(publicUrl + "/")) {
                return;
            }
            String key = url.substring(publicUrl.length() + 1);
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Resize về tối đa 1600px và nén JPEG 85%. Trả null nếu không nén được (GIF/PNG trong suốt/WebP...). */
    private byte[] compress(byte[] bytes, String contentType) {
        try {
            if (!contentType.equals("image/jpeg") && !contentType.equals("image/png")) {
                return null;
            }
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
            if (src == null || src.getColorModel().hasAlpha()) {
                return null; // giữ nguyên bản gốc để không mất nền trong suốt
            }
            int w = src.getWidth(), h = src.getHeight();
            if (w > MAX_WIDTH) {
                h = (int) ((long) h * MAX_WIDTH / w);
                w = MAX_WIDTH;
            }
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();

            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(out, null, null), param);
            } finally {
                writer.dispose();
            }
            byte[] result = bos.toByteArray();
            return result.length < bytes.length ? result : null; // chỉ dùng nếu thật sự nhỏ hơn
        } catch (Exception e) {
            return null;
        }
    }

    private String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}