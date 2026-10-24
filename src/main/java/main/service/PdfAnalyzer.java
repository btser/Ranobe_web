package main.service;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc một file PDF (có chữ chọn được): tách chương theo dòng tiêu đề, và render các trang minh họa
 * (ít chữ, có ảnh to) thành file JPG trong thư mục tạm.
 * Lớp thuần Java, không phụ thuộc Spring.
 */
public class PdfAnalyzer {

    public static final int MAX_PAGES = 800;
    public static final int MAX_ILLUS = 120;

    /** Trang có ít hơn số ký tự này (không tính khoảng trắng) và có ảnh to thì coi là trang minh họa. */
    private static final int ILLUS_MAX_CHARS = 60;
    private static final int MIN_IMAGE_PIXELS = 120_000;
    private static final float RENDER_DPI = 130f;

    /**
     * Mặc định chỉ coi là đề mục chương những dòng bắt đầu bằng "Chương"/"Chapter" + số (01, 5, 5.1, IV) hoặc
     * "kết/cuối". "Phần 1", "Mở đầu", "Prologue"... KHÔNG tách chương, chúng nằm trong chương chứa chúng.
     */
    public static final String DEFAULT_PATTERN =
            "^(?:chương|chapter)\\s*(?:[0-9]+(?:\\.[0-9]+)*|[ivxlc]+|kết|cuối|đặc biệt|phụ)\\b.{0,90}$";

    private static final Pattern SUMMARY_HEAD = Pattern.compile(
            "^(?:tóm tắt|giới thiệu|nội dung chính|nội dung|synopsis|summary|blurb|introduction)"
                    + "(?:\\s+(?:nội dung|truyện|tập))?\\s*(?:[:：]\\s*(.*))?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern STOP_HEAD = Pattern.compile(
            "^(?:mục lục|table of contents|contents|minh họa|illustrations?|mở đầu|lời mở đầu|prologue|lời nói đầu)\\b.*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Tóm tắt chỉ được tìm ở vài trang đầu, trước đề mục chương đầu tiên. */
    private static final int SUMMARY_MAX_PAGE = 15;
    /** Trang có từ chừng này ký tự (không tính khoảng trắng) mới coi là đã vào phần nội dung. */
    private static final int BODY_PAGE_CHARS = 300;

    /** Dòng đánh dấu vị trí ảnh trong nội dung chương, ví dụ [[IMG:p014.jpg]]. Khi lưu sẽ thay bằng link ảnh. */
    public static final String IMG_OPEN = "[[IMG:";
    public static final String IMG_CLOSE = "]]";

    public enum Kind { COLOR, TOC, BW }

    public static class Draft {
        private final String title;
        private final String text;
        private final int startPage;

        public Draft(String title, String text, int startPage) {
            this.title = title;
            this.text = text;
            this.startPage = startPage;
        }

        public String getTitle() { return title; }
        public String getText() { return text; }
        public int getStartPage() { return startPage; }
        public int getLength() { return text == null ? 0 : text.length(); }

        /** Chương quá ngắn (mục lục, trang tên sách...) thì mặc định đánh dấu bỏ. */
        public boolean isSkipDefault() { return text == null || text.strip().length() < 80; }
    }

    public static class Illus {
        private final String file;
        private final int page;
        private final int chapterIndex;
        private final Kind kind;

        public Illus(String file, int page, int chapterIndex, Kind kind) {
            this.file = file;
            this.page = page;
            this.chapterIndex = chapterIndex;
            this.kind = kind;
        }

        public String getFile() { return file; }
        public int getPage() { return page; }
        /** -1: ảnh ở phần đầu sách (trước nội dung) -> chương Minh họa. Ngược lại là chỉ số chương chứa trang đó. */
        public int getChapterIndex() { return chapterIndex; }
        public boolean isFront() { return chapterIndex < 0; }
        public Kind getKind() { return kind; }
        public String getKindName() { return kind.name(); }
    }

    public static class Result {
        private final List<Draft> chapters;
        private final List<Illus> images;
        private final int pageCount;
        private final boolean noText;
        private final String summary;

        public Result(List<Draft> chapters, List<Illus> images, int pageCount, boolean noText, String summary) {
            this.chapters = chapters;
            this.images = images;
            this.pageCount = pageCount;
            this.noText = noText;
            this.summary = summary;
        }

        public List<Draft> getChapters() { return chapters; }
        public List<Illus> getImages() { return images; }
        public int getPageCount() { return pageCount; }
        /** true khi PDF gần như không có chữ (thường là bản scan). */
        public boolean isNoText() { return noText; }
        /** Tóm tắt tìm thấy trong PDF, null nếu không có. */
        public String getSummary() { return summary; }
    }

    public Result analyze(Path pdfFile, String headingRegex, Path outDir) throws IOException {
        String regex = (headingRegex == null || headingRegex.isBlank()) ? DEFAULT_PATTERN : headingRegex;
        Pattern heading = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        try (PDDocument doc = PDDocument.load(pdfFile.toFile(), MemoryUsageSetting.setupTempFileOnly())) {
            int pages = doc.getNumberOfPages();
            if (pages > MAX_PAGES) {
                throw new IOException("PDF có " + pages + " trang, vượt giới hạn " + MAX_PAGES + " trang.");
            }

            // Lượt 1: lấy chữ từng trang
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<List<String>> pageLines = new ArrayList<>();
            long totalChars = 0;
            for (int p = 0; p < pages; p++) {
                stripper.setStartPage(p + 1);
                stripper.setEndPage(p + 1);
                String t = stripper.getText(doc);
                List<String> lines = new ArrayList<>();
                for (String raw : (t == null ? "" : t).split("\\R")) {
                    String line = clean(raw);
                    if (!line.isEmpty() && !line.matches("\\d{1,4}")) {
                        lines.add(line); // bỏ dòng trống và số trang
                    }
                }
                pageLines.add(lines);
            }
            // Bỏ tiêu đề chạy (dòng lặp lại ở đầu/cuối nhiều trang, ví dụ tên bộ truyện)
            List<String> headers = findRunningHeaders(pageLines);
            for (List<String> lines : pageLines) {
                List<String> kept = new ArrayList<>();
                for (String line : lines) {
                    String l = line;
                    for (String h : headers) {
                        l = l.replace(h, " ");
                    }
                    l = clean(l);
                    if (!l.isEmpty()) {
                        kept.add(l);
                    }
                }
                lines.clear();
                lines.addAll(kept);
                totalChars += compactLength(String.join("", lines));
            }
            if (totalChars < (long) pages * 15) {
                return new Result(new ArrayList<>(), new ArrayList<>(), pages, true, null);
            }

            // Lượt 2: tách chương, tóm tắt, render trang minh họa
            Files.createDirectories(outDir);
            PDFRenderer renderer = new PDFRenderer(doc);

            List<Draft> drafts = new ArrayList<>();
            List<Illus> images = new ArrayList<>();
            List<String> preface = new ArrayList<>();   // chữ trước đề mục chương đầu tiên
            int prefaceStart = 0;
            List<String> curLines = new ArrayList<>();
            String curTitle = null;
            int curStart = 1;
            boolean titleOpen = false;                   // vừa gặp đề mục, dòng kế có thể là phần tiếp của tiêu đề
            boolean bodyStarted = false;                 // đã qua phần đầu sách (ảnh từ đây trở đi là ảnh trong chương)
            StringBuilder summary = null;

            for (int p = 0; p < pages; p++) {
                List<String> lines = pageLines.get(p);
                int chars = compactLength(String.join("", lines));
                boolean illus = chars < ILLUS_MAX_CHARS && hasBigImage(doc.getPage(p));
                if (illus) {
                    if (images.size() < MAX_ILLUS) {
                        String name = String.format("p%03d.jpg", p + 1);
                        BufferedImage img = trimMargins(renderer.renderImageWithDPI(p, RENDER_DPI, ImageType.RGB));
                        writeJpeg(img, outDir.resolve(name), 0.85f);
                        boolean front = !bodyStarted;
                        Kind kind = isColorful(img) ? Kind.COLOR
                                : (front && looksLikeToc(img) ? Kind.TOC : Kind.BW);
                        images.add(new Illus(name, p + 1, front ? -1 : drafts.size(), kind));
                        if (!front) {
                            curLines.add(IMG_OPEN + name + IMG_CLOSE); // chèn đúng vị trí giữa các đoạn văn
                        }
                    }
                    continue; // trang minh họa: bỏ phần chữ (thường là nhiễu hoặc chú thích)
                }

                // Trang mục lục bằng chữ: nhiều dòng giống đề mục, ít chữ khác -> bỏ qua cả trang
                int headingLines = 0;
                for (String l : lines) {
                    if (l.length() <= 100 && heading.matcher(l).matches()) {
                        headingLines++;
                    }
                }
                boolean tocPage = (headingLines >= 3 && chars < 1500)
                        || (!lines.isEmpty() && chars < 1500
                        && lines.get(0).toLowerCase().matches("^(mục lục|table of contents|contents)\\b.*"));
                if (tocPage) {
                    continue;
                }
                if (chars >= BODY_PAGE_CHARS) {
                    bodyStarted = true;
                }

                boolean inSummary = false;
                for (String line : lines) {
                    // tóm tắt (chỉ tìm ở các trang đầu, trước chương đầu tiên)
                    if (summary == null && curTitle == null && p < SUMMARY_MAX_PAGE && line.length() <= 400) {
                        Matcher sm = SUMMARY_HEAD.matcher(line);
                        if (sm.matches() && (line.contains(":") || line.contains("：") || line.length() <= 40)) {
                            summary = new StringBuilder();
                            String rest = sm.group(1);
                            if (rest != null && !rest.isBlank()) {
                                summary.append(rest.strip());
                            }
                            inSummary = true;
                            continue;
                        }
                    }
                    if (inSummary) {
                        boolean stop = (line.length() <= 100 && heading.matcher(line).matches())
                                || (line.length() <= 60 && STOP_HEAD.matcher(line).matches());
                        if (!stop) {
                            if (summary.length() > 0) {
                                summary.append('\n');
                            }
                            summary.append(line);
                            continue;
                        }
                        inSummary = false;
                    }

                    if (line.length() <= 100 && heading.matcher(line).matches()) {
                        if (curTitle != null && !curLines.isEmpty()) {
                            drafts.add(new Draft(curTitle, reflow(curLines), curStart));
                            curLines = new ArrayList<>();
                        } else if (curTitle == null) {
                            preface = curLines;          // chữ trước chương đầu
                            curLines = new ArrayList<>();
                        }
                        // Chương hiện tại chưa có chữ thì chỉ đổi tên, không tạo chương rỗng
                        curTitle = cleanTitle(line);
                        curStart = p + 1;
                        titleOpen = true;
                        bodyStarted = true;
                        continue;
                    }
                    if (titleOpen) {
                        titleOpen = false;
                        // Tiêu đề bị xuống dòng: "Chương 01: Chào mừng ..." / "đường trong mơ của tôi."
                        if (line.length() <= 80 && Character.isLowerCase(line.charAt(0))
                                && !endsSentence(curTitle)) {
                            curTitle = curTitle + " " + line.replaceAll("[.。]+$", "").strip();
                            continue;
                        }
                    }
                    if (curTitle == null && curLines.isEmpty()) {
                        prefaceStart = p + 1;
                    }
                    curLines.add(line);
                }
            }

            // Kết thúc
            if (curTitle == null) {
                // Không có đề mục chương nào -> gộp tất cả thành MỘT chương
                drafts.add(new Draft("Nội dung", reflow(curLines), Math.max(1, prefaceStart)));
            } else {
                drafts.add(new Draft(curTitle, reflow(curLines), curStart));
                // Chữ nằm trước chương đầu (mở đầu, prologue...) gộp vào chương đầu thay vì tách riêng;
                // đoạn quá ngắn (trang tên sách, mục lục) thì bỏ.
                if (compactLength(String.join("", preface)) >= 300) {
                    Draft first = drafts.get(0);
                    drafts.set(0, new Draft(first.getTitle(),
                            reflow(preface) + "\n\n" + first.getText(), Math.max(1, prefaceStart)));
                }
            }

            String sum = summary == null ? null : reflow(Arrays.asList(summary.toString().split("\n")));
            if (sum != null && sum.isBlank()) {
                sum = null;
            }
            return new Result(drafts, images, pages, false, sum);
        }
    }

    /** Dòng xuất hiện (sau khi bỏ chữ số) ở 2 dòng đầu/cuối của >= 30% số trang (tối thiểu 3 trang). */
    static List<String> findRunningHeaders(List<List<String>> pageLines) {
        Map<String, Integer> count = new HashMap<>();
        for (List<String> lines : pageLines) {
            Set<String> seen = new HashSet<>();
            int n = lines.size();
            for (int i = 0; i < n; i++) {
                if (i < 2 || i >= n - 2) {
                    String l = lines.get(i);
                    if (l.length() >= 4 && l.length() <= 100) {
                        seen.add(l);
                    }
                }
            }
            for (String s : seen) {
                count.merge(s, 1, Integer::sum);
            }
        }
        int min = Math.max(3, (int) Math.ceil(pageLines.size() * 0.3));
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            if (e.getValue() >= min) {
                out.add(e.getKey());
            }
        }
        out.sort((a, b) -> b.length() - a.length());
        return out;
    }

    /**
     * Cắt sát viền: bỏ phần trắng quanh ảnh. Các vệt nhỏ ở rìa (số trang, dòng tiêu đề chạy) bị bỏ qua
     * vì khối nội dung chính có lượng "mực" lớn hơn rất nhiều.
     */
    static BufferedImage trimMargins(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        boolean[][] ink = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                ink[y][x] = r < 238 || g < 238 || b < 238;
            }
        }
        int[] rows = new int[h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (ink[y][x]) {
                    rows[y]++;
                }
            }
        }
        int[] yr = mainSpan(rows, Math.max(2, (int) (w * 0.003)), (int) (h * 0.02));
        if (yr == null) {
            return img;
        }
        int[] cols = new int[w];
        for (int y = yr[0]; y <= yr[1]; y++) {
            for (int x = 0; x < w; x++) {
                if (ink[y][x]) {
                    cols[x]++;
                }
            }
        }
        int[] xr = mainSpan(cols, Math.max(2, (int) ((yr[1] - yr[0] + 1) * 0.003)), (int) (w * 0.02));
        if (xr == null) {
            return img;
        }
        int pad = 6;
        int top = Math.max(0, yr[0] - pad), bottom = Math.min(h - 1, yr[1] + pad);
        int left = Math.max(0, xr[0] - pad), right = Math.min(w - 1, xr[1] + pad);
        int nw = right - left + 1, nh = bottom - top + 1;
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        out.getGraphics().drawImage(img.getSubimage(left, top, nw, nh), 0, 0, null);
        return out;
    }

    /**
     * Từ profile (số điểm "mực" mỗi hàng/cột) tìm đoạn nội dung chính: gom các hàng/cột có mực thành cụm
     * (khoảng trống nhỏ hơn maxGap thì nối liền), giữ cụm có tổng mực >= 15% cụm lớn nhất.
     * Trả về {đầu, cuối} hoặc null nếu trang trắng.
     */
    private static int[] mainSpan(int[] profile, int minInk, int maxGap) {
        List<long[]> clusters = new ArrayList<>(); // {start, end, mass}
        int n = profile.length;
        int i = 0;
        while (i < n) {
            if (profile[i] < minInk) {
                i++;
                continue;
            }
            long start = i, end = i, mass = 0;
            int gap = 0;
            int j = i;
            while (j < n && gap <= maxGap) {
                if (profile[j] >= minInk) {
                    end = j;
                    mass += profile[j];
                    gap = 0;
                } else {
                    gap++;
                }
                j++;
            }
            clusters.add(new long[]{start, end, mass});
            i = j;
        }
        if (clusters.isEmpty()) {
            return null;
        }
        long max = 0;
        for (long[] c : clusters) {
            max = Math.max(max, c[2]);
        }
        int lo = Integer.MAX_VALUE, hi = -1;
        for (long[] c : clusters) {
            if (c[2] * 100 >= max * 15) {
                lo = (int) Math.min(lo, c[0]);
                hi = (int) Math.max(hi, c[1]);
            }
        }
        // Gọt các hàng/cột ở mép chỉ có ít mực (dính dòng chữ nhỏ như số trang sát ảnh)
        List<Integer> active = new ArrayList<>();
        for (int k = lo; k <= hi; k++) {
            if (profile[k] >= minInk) {
                active.add(profile[k]);
            }
        }
        java.util.Collections.sort(active);
        int median = active.isEmpty() ? 0 : active.get(active.size() / 2);
        while (lo < hi && profile[lo] < median * 0.5) {
            lo++;
        }
        while (hi > lo && profile[hi] < median * 0.5) {
            hi--;
        }
        return new int[]{lo, hi};
    }

    /** Ảnh có màu: tỉ lệ điểm ảnh lệch kênh màu đáng kể. Trang đen trắng/xám thì gần như 0. */
    static boolean isColorful(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        long colored = 0, total = 0;
        for (int y = 0; y < h; y += 4) {
            for (int x = 0; x < w; x += 4) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
                if (max - min > 40) {
                    colored++;
                }
                total++;
            }
        }
        return total > 0 && colored * 100.0 / total >= 3.0;
    }

    /** Ảnh mục lục: nền trắng, ít mực, nhiều dải chữ nằm ngang tách rời nhau. Chỉ là đoán, xem trước có thể đổi lại. */
    static boolean looksLikeToc(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        long ink = 0;
        int bands = 0;
        boolean inBand = false;
        int bandH = 0;
        for (int y = 0; y < h; y += 2) {
            int rowInk = 0, cols = 0;
            for (int x = 0; x < w; x += 2) {
                int rgb = img.getRGB(x, y);
                int gray = (((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255)) / 3;
                if (gray < 110) {
                    rowInk++;
                }
                cols++;
            }
            ink += rowInk;
            boolean inkRow = rowInk * 100.0 / cols >= 1.5;
            if (inkRow) {
                inBand = true;
                bandH++;
            } else if (inBand) {
                if (bandH >= 3 && bandH <= 30) {
                    bands++;
                }
                inBand = false;
                bandH = 0;
            }
        }
        double frac = ink * 4.0 / ((double) w * h); // ink đếm theo lưới 2x2 -> nhân 4
        return bands >= 8 && frac > 0.005 && frac < 0.12;
    }

    // ---------- helpers ----------

    private static int compactLength(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                n++;
            }
        }
        return n;
    }

    private static boolean hasBigImage(PDPage page) {
        try {
            PDResources res = page.getResources();
            if (res == null) {
                return false;
            }
            for (COSName name : res.getXObjectNames()) {
                PDXObject x = res.getXObject(name);
                if (x instanceof PDImageXObject) {
                    PDImageXObject im = (PDImageXObject) x;
                    if ((long) im.getWidth() * im.getHeight() >= MIN_IMAGE_PIXELS) {
                        return true;
                    }
                }
            }
        } catch (IOException ignored) {
            // trang lỗi thì coi như không có ảnh
        }
        return false;
    }

    private static String clean(String s) {
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC); // gộp dấu rời thành chữ dựng sẵn
        return s.replace(' ', ' ').replace("­", "").replaceAll("\\s+", " ").strip();
    }

    /** Bỏ phần chấm dẫn và số trang ở cuối tiêu đề mục lục: "Chương 1: Mở đầu ...... 5" -> "Chương 1: Mở đầu". */
    private static String cleanTitle(String s) {
        String t = s.replaceAll("[.·…\\s]{3,}\\d*$", "").strip();
        return t.isEmpty() ? s : t;
    }

    /** Nối các dòng bị PDF ngắt giữa câu thành đoạn văn; giữ ngắt đoạn ở cuối câu, lời thoại, dòng ngăn cách. */
    static String reflow(List<String> lines) {
        List<String> work = new ArrayList<>(lines);
        while (!work.isEmpty() && isSeparator(work.get(work.size() - 1))) {
            work.remove(work.size() - 1); // bỏ dòng ngăn cách thừa ở cuối chương
        }
        StringBuilder sb = new StringBuilder();
        String prev = null;
        for (String line : work) {
            if (prev == null) {
                sb.append(line);
            } else if (isImg(prev) || isImg(line) || endsSentence(prev) || startsDialog(line) || isSeparator(line) || isSeparator(prev)) {
                sb.append("\n\n").append(line);
            } else {
                sb.append(' ').append(line);
            }
            prev = line;
        }
        return sb.toString();
    }

    private static boolean isImg(String s) {
        return s.startsWith(IMG_OPEN) && s.endsWith(IMG_CLOSE);
    }

    private static boolean endsSentence(String s) {
        char c = s.charAt(s.length() - 1);
        return ".!?…”\"»」』)]】".indexOf(c) >= 0;
    }

    private static boolean startsDialog(String s) {
        char c = s.charAt(0);
        return "“\"«「『-–—".indexOf(c) >= 0;
    }

    private static boolean isSeparator(String s) {
        return s.length() <= 12 && s.matches("[\\p{Punct}\\s*◇◆※＊~•・—–-]+");
    }

    private static void writeJpeg(BufferedImage img, Path target, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(Files.newOutputStream(target))) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}