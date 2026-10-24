package main.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tách nội dung chương thành các khối chữ / ảnh. Ảnh nằm trên dòng riêng dạng [img]https://...[/img]. */
public final class ChapterContent {

    private static final Pattern IMG = Pattern.compile("\\[img\\](https?://[^\\s\\[\\]]+)\\[/img\\]");

    private ChapterContent() {}

    public static class Block {
        private final boolean image;
        private final String value;

        Block(boolean image, String value) {
            this.image = image;
            this.value = value;
        }

        public boolean isImage() { return image; }
        public String getValue() { return value; }
    }

    public static List<Block> parse(String content) {
        List<Block> out = new ArrayList<>();
        if (content == null) {
            return out;
        }
        // Chữ tiếng Việt dạng tổ hợp (dấu rời) hiển thị lỗi font -> đưa về dạng dựng sẵn (NFC)
        content = java.text.Normalizer.normalize(content, java.text.Normalizer.Form.NFC);
        Matcher m = IMG.matcher(content);
        int last = 0;
        while (m.find()) {
            addText(out, content.substring(last, m.start()));
            out.add(new Block(true, m.group(1)));
            last = m.end();
        }
        addText(out, content.substring(last));
        return out;
    }

    /** Số từ (bỏ thẻ ảnh), để hiện "Độ dài: N từ". */
    public static int wordCount(String content) {
        if (content == null) {
            return 0;
        }
        String t = IMG.matcher(content).replaceAll(" ").strip();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    /** Mỗi đoạn (cách nhau bằng dòng trống) là một khối chữ riêng. */
    private static void addText(List<Block> out, String text) {
        for (String para : text.split("\\n\\s*\\n")) {
            String t = para.strip();
            if (!t.isEmpty()) {
                out.add(new Block(false, t));
            }
        }
    }
}