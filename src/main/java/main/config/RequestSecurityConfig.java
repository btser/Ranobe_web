package main.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.servlet.support.csrf.CsrfRequestDataValueProcessor;
import org.springframework.web.multipart.support.MultipartFilter;
import org.springframework.web.servlet.support.RequestDataValueProcessor;

/** Bảo vệ yêu cầu ghi; đăng nhập và phân quyền vẫn do các bộ điều khiển hiện có quản lý. */
@Configuration
public class RequestSecurityConfig {
    @Bean
    public FilterRegistrationBean<MultipartFilter> securityMultipartFilter() {
        var filter = new MultipartFilter();
        filter.setMultipartResolverBeanName("multipartResolver");
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 90);
        return registration;
    }

    @Bean
    public HttpSessionCsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    public FilterRegistrationBean<CsrfFilter> csrfFilter(HttpSessionCsrfTokenRepository repository) {
        var filter = new CsrfFilter(repository);
        filter.setAccessDeniedHandler((request, response, error) -> {
            response.setStatus(403);
            response.setContentType("text/plain;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("Phiên biểu mẫu không còn hợp lệ. Hãy tải lại trang rồi thử lại.");
        });
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return registration;
    }

    @Bean(name = "requestDataValueProcessor")
    public RequestDataValueProcessor requestDataValueProcessor() {
        return new CsrfRequestDataValueProcessor();
    }
}
