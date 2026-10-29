package main.service;

import main.entity.Account;
import main.entity.Product;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import java.util.Locale;

public final class AdministrationSearch {
    private AdministrationSearch() {}
    public static String state(String value) {
        return "hidden".equals(value) || "active".equals(value) ? value : "all";
    }
    public static String keyword(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.substring(0, Math.min(150, clean.length()));
    }
    private static String pattern(String value) {
        return "%" + keyword(value).toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
    public static Specification<Account> accounts(String q, String state) {
        return (root, query, cb) -> {
            var user = root.join("user", JoinType.LEFT);
            var match = cb.or(cb.like(cb.lower(root.get("username")), pattern(q), '!'),
                    cb.like(cb.lower(root.get("email")), pattern(q), '!'),
                    cb.like(cb.lower(user.get("userName")), pattern(q), '!'));
            var locked = cb.or(cb.isTrue(root.get("isDeleted")), cb.isTrue(user.get("isDeleted")));
            return cb.and(match, "hidden".equals(state) ? locked : "active".equals(state) ? cb.not(locked) : cb.conjunction());
        };
    }
    public static Specification<Product> products(String q, String state) {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("uploader", JoinType.LEFT);
            }
            return cb.and(cb.like(cb.lower(root.get("title")), pattern(q), '!'),
                "hidden".equals(state) ? cb.isTrue(root.get("isDeleted"))
                        : "active".equals(state) ? cb.isFalse(root.get("isDeleted")) : cb.conjunction());
        };
    }
}
