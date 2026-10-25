package main.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nội dung chương có 2 dạng:
 * - chữ thường (chương nhập từ PDF, chương cũ): đoạn cách nhau bằng dòng trống, ảnh là dòng [img]https://...[/img];
 * - HTML đã lọc an toàn (chương soạn bằng khung soạn thảo): bắt đầu bằng HTML_MARK.
 */
public final class ChapterContent {

    /** Đánh dấu nội dung đã lưu dạng HTML (comment, không hiện ra). */
    public static final String HTML_MARK = "<!--h-->";

    private static final Pattern IMG = Pattern.compile("\\[img\\](https?://[^\\s\\[\\]]+)\\[/img\\]");
    private static final Pattern TAG = Pattern.compile("<!--.*?-->|<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>", Pattern.DOTALL);
    private static final Pattern SRC = Pattern.compile("\\bsrc\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE);
    private static final Pattern SAFE_URL = Pattern.compile("^https?://[^\\s\"'<>]+$", Pattern.CASE_INSENSITIVE);

    private static final Set<String> KEEP = new HashSet<>(Arrays.asList("p", "b", "i", "u", "s", "blockquote"));
    private static final Set<String> RENAME_B = new HashSet<>(Arrays.asList("strong"));
    private static final Set<String> RENAME_I = new HashSet<>(Arrays.asList("em"));
    private static final Set<String> RENAME_S = new HashSet<>(Arrays.asList("strike", "del"));
    private static final Set<String> RENAME_P = new HashSet<>(Arrays.asList("div", "h1", "h2", "h3", "h4", "li"));
    private static final Set<String> DROP_WITH_CONTENT = new HashSet<>(Arrays.asList(
            "script", "style", "iframe", "object", "embed", "svg", "math", "form", "textarea", "noscript", "template", "head", "title"));

    private ChapterContent() {}

    // ---------- Khối hiển thị cho chương chữ thường ----------

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

    public static boolean isHtml(String content) {
        return content != null && content.startsWith(HTML_MARK);
    }

    public static List<Block> parse(String content) {
        List<Block> out = new ArrayList<>();
        if (content == null || isHtml(content)) {
            return out;
        }
        // Chữ tiếng Việt dạng tổ hợp (dấu rời) hiển thị lỗi font -> đưa về dạng dựng sẵn (NFC)
        content = Normalizer.normalize(content, Normalizer.Form.NFC);
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

    /** Mỗi đoạn (cách nhau bằng dòng trống) là một khối chữ riêng. */
    private static void addText(List<Block> out, String text) {
        for (String para : text.split("\\n\\s*\\n")) {
            String t = para.strip();
            if (!t.isEmpty()) {
                out.add(new Block(false, t));
            }
        }
    }

    // ---------- Số từ ----------

    /** Số từ (bỏ thẻ ảnh và thẻ HTML), để hiện "Độ dài: N từ". */
    public static int wordCount(String content) {
        if (content == null) {
            return 0;
        }
        String t = content;
        if (isHtml(t)) {
            t = t.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ");
        }
        t = IMG.matcher(t).replaceAll(" ").strip();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    // ---------- Khung soạn thảo ----------

    /** HTML đưa vào khung soạn thảo (đã an toàn). extraImageUrls: ảnh gắn riêng của chương cũ, đặt lên đầu. */
    public static String toEditorHtml(String content, List<String> extraImageUrls) {
        StringBuilder sb = new StringBuilder();
        if (extraImageUrls != null) {
            for (String u : extraImageUrls) {
                if (u != null && SAFE_URL.matcher(u).matches()) {
                    sb.append("<p><img src=\"").append(escAttr(u)).append("\"></p>");
                }
            }
        }
        if (content == null || content.isBlank()) {
            return sb.toString();
        }
        if (isHtml(content)) {
            sb.append(sanitizeBody(content.substring(HTML_MARK.length())));
            return sb.toString();
        }
        for (Block b : parse(content)) {
            if (b.isImage()) {
                sb.append("<p><img src=\"").append(escAttr(b.getValue())).append("\"></p>");
            } else {
                sb.append("<p>").append(escText(b.getValue()).replace("\n", "<br>")).append("</p>");
            }
        }
        return sb.toString();
    }

    /** Lọc HTML từ khung soạn thảo rồi gắn dấu HTML_MARK để lưu. */
    public static String sanitize(String html) {
        return HTML_MARK + sanitizeBody(html == null ? "" : html);
    }

    /** HTML để hiển thị cho người đọc (không có dấu HTML_MARK); lọc lại một lần nữa cho chắc. */
    public static String htmlForReader(String content) {
        if (!isHtml(content)) {
            return null;
        }
        return sanitizeBody(content.substring(HTML_MARK.length()));
    }

    static String sanitizeBody(String html) {
        String in = Normalizer.normalize(html, Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder();
        List<String> open = new ArrayList<>();
        Matcher m = TAG.matcher(in);
        int last = 0;
        while (m.find()) {
            out.append(cleanText(in.substring(last, m.start())));
            last = m.end();
            if (m.group(2) == null) {
                continue; // comment
            }
            boolean closing = !m.group(1).isEmpty();
            String name = m.group(2).toLowerCase();
            String attrs = m.group(3) == null ? "" : m.group(3);

            if (DROP_WITH_CONTENT.contains(name)) {
                if (!closing && !attrs.endsWith("/")) {
                    int end = indexOfClose(in, name, m.end());
                    if (end >= 0) {
                        last = end;
                        m.region(end, in.length());
                    }
                }
                continue;
            }
            if (name.equals("br")) {
                if (!closing) {
                    out.append("<br>");
                }
                continue;
            }
            if (name.equals("img")) {
                if (!closing) {
                    Matcher sm = SRC.matcher(attrs);
                    if (sm.find()) {
                        String src = sm.group(1) != null ? sm.group(1) : sm.group(2);
                        src = src.replace("&amp;", "&").strip();
                        if (SAFE_URL.matcher(src).matches()) {
                            out.append("<img src=\"").append(escAttr(src)).append("\">");
                        }
                    }
                }
                continue;
            }
            String tag = null;
            if (KEEP.contains(name)) tag = name;
            else if (RENAME_B.contains(name)) tag = "b";
            else if (RENAME_I.contains(name)) tag = "i";
            else if (RENAME_S.contains(name)) tag = "s";
            else if (RENAME_P.contains(name)) tag = "p";
            if (tag == null) {
                continue; // thẻ lạ: bỏ thẻ, giữ chữ bên trong
            }
            if (!closing) {
                open.add(tag);
                out.append('<').append(tag).append('>');
            } else {
                int idx = open.lastIndexOf(tag);
                if (idx >= 0) {
                    for (int k = open.size() - 1; k >= idx; k--) {
                        out.append("</").append(open.get(k)).append('>');
                    }
                    while (open.size() > idx) {
                        open.remove(open.size() - 1);
                    }
                }
            }
        }
        out.append(cleanText(in.substring(last)));
        for (int k = open.size() - 1; k >= 0; k--) {
            out.append("</").append(open.get(k)).append('>');
        }
        return out.toString().replace("\u00a0", " ");
    }

    private static int indexOfClose(String in, String name, int from) {
        Matcher cm = Pattern.compile("</" + name + "\\s*>", Pattern.CASE_INSENSITIVE).matcher(in);
        return cm.find(from) ? cm.end() : -1;
    }

    /** Chữ nằm giữa các thẻ: giữ nguyên các entity có sẵn (&amp; &lt;...), chỉ chặn < > còn sót. */
    private static String cleanText(String s) {
        return s.replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escText(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escAttr(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}