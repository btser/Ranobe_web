package main.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Đưa link logo (lưu trên Cloudflare R2) vào mọi trang: ${siteLogoSmall}, ${siteLogoBig}.
 * Mặc định: {r2.public-url}/site/favicon-ln.png và /site/logo-ln.png. Muốn dùng link khác thì đặt
 * site.logo-small-url / site.logo-big-url trong application.properties.
 */
@ControllerAdvice
public class SiteAssetsAdvice {

    private final String small;
    private final String big;

    public SiteAssetsAdvice(@Value("${r2.public-url}") String r2PublicUrl,
                            @Value("${site.logo-small-url:}") String smallOverride,
                            @Value("${site.logo-big-url:}") String bigOverride) {
        String base = r2PublicUrl.endsWith("/") ? r2PublicUrl.substring(0, r2PublicUrl.length() - 1) : r2PublicUrl;
        this.small = smallOverride.isBlank() ? base + "/site/favicon-ln.png" : smallOverride;
        this.big = bigOverride.isBlank() ? base + "/site/logo-ln.png" : bigOverride;
    }

    @ModelAttribute("siteLogoSmall")
    public String siteLogoSmall() {
        return small;
    }

    @ModelAttribute("siteLogoBig")
    public String siteLogoBig() {
        return big;
    }
}