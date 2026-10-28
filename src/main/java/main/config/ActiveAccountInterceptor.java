package main.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import main.entity.Account;
import main.entity.User;
import main.repository.AccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

/** Kiểm tra trạng thái mới nhất trước khi xử lý yêu cầu có thể ghi dữ liệu. */
@Component
public class ActiveAccountInterceptor implements HandlerInterceptor {
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final AccountRepository accounts;

    public ActiveAccountInterceptor(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (READ_METHODS.contains(request.getMethod())) return true;
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("loggedInUser") instanceof User viewer)) {
            return true; // Quyền truy cập của khách vẫn do từng đường dẫn kiểm tra.
        }
        Account account = accounts.findByUserUserId(viewer.getUserId()).orElse(null);
        if (account != null && !account.isDeleted() && account.getUser() != null
                && !account.getUser().isDeleted()) return true;

        session.invalidate();
        response.setHeader("Cache-Control", "no-store");
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String accept = request.getHeader("Accept");
        if (path.startsWith("/api/") || (accept != null && accept.contains("application/json"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Tài khoản đã bị khóa hoặc không còn hợp lệ. Phiên đăng nhập đã kết thúc.\"}");
        } else {
            response.setStatus(HttpServletResponse.SC_SEE_OTHER);
            response.setHeader("Location", request.getContextPath() + "/login?accountUnavailable=1");
        }
        return false;
    }
}
