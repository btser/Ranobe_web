package main.util;

import java.text.Normalizer;
import java.util.Locale;

/** Tạo phần đuôi tiêu đề trong link: bỏ dấu, viết thường, khoảng trắng và ký tự lạ thành "-". */
public final class Slugs {

    private Slugs() {}

    public static String slug(String text, String fallback) {
        if (text == null) {
            return fallback;
        }
        String s = text.replace('đ', 'd').replace('Đ', 'D');
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.length() > 80) {
            s = s.substring(0, 80).replaceAll("-+$", "");
        }
        return s.isEmpty() ? fallback : s;
    }
}