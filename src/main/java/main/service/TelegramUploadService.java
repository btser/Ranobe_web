package main.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@Service
public class TelegramUploadService {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.chat.id}")
    private String chatId;

    private final RestTemplate restTemplate = new RestTemplate();

    public String uploadImage(MultipartFile file) {
        try {
            // 1. Gửi ảnh vào Telegram Channel
            String sendPhotoUrl = "https://api.telegram.org/bot" + botToken + "/sendPhoto";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("chat_id", chatId);

            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("photo", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            Map response = restTemplate.postForObject(sendPhotoUrl, requestEntity, Map.class);

            // 2. Lấy file_id của kích thước lớn nhất
            Map result = (Map) response.get("result");
            List photos = (List) result.get("photo");
            Map largestPhoto = (Map) photos.get(photos.size() - 1);
            String fileId = (String) largestPhoto.get("file_id");

            // 3. Lấy đường dẫn file từ Telegram
            String getFileUrl = "https://api.telegram.org/bot" + botToken + "/getFile?file_id=" + fileId;
            Map fileResponse = restTemplate.getForObject(getFileUrl, Map.class);
            Map fileResult = (Map) fileResponse.get("result");
            String filePath = (String) fileResult.get("file_path");

            // 4. Trả về URL trực tiếp của ảnh
            return "https://api.telegram.org/file/bot" + botToken + "/" + filePath;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}