package main.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Đưa link logo (lưu trên Cloudflare R2) vào mọi trang: ${siteLogoSmall}, ${siteLogoBig}.
 * Mặc định: {r2.public-url}/favicon-ln.png và /logo-ln.png. Muốn dùng link khác thì đặt
 * site.logo-small-url / site.logo-big-url trong application.properties.
 */
@ControllerAdvice
public class SiteAssetsAdvice {

    private final String small;
    private final String big;
    private final String cover;

    public SiteAssetsAdvice(@Value("${r2.public-url}") String r2PublicUrl,
                            @Value("${site.logo-small-url:}") String smallOverride,
                            @Value("${site.logo-big-url:}") String bigOverride,
                            @Value("${site.default-cover-url:}") String coverOverride) {
        String base = r2PublicUrl.endsWith("/") ? r2PublicUrl.substring(0, r2PublicUrl.length() - 1) : r2PublicUrl;
        this.small = smallOverride.isBlank() ? base + "/favicon-ln.png" : smallOverride;
        this.cover = coverOverride.isBlank() ? base + "/default-cover.webp" : coverOverride;
        this.big = bigOverride.isBlank() ? base + "/logo-ln.png" : bigOverride;
    }

    @ModelAttribute("siteLogoSmall")
    public String siteLogoSmall() {
        return small;
    }

    @ModelAttribute("siteLogoBig")
    public String siteLogoBig() {
        return big;
    }

    @ModelAttribute("siteDefaultCover")
    public String siteDefaultCover() {
        return cover;
    }
}