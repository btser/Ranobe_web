package main.service;

import main.entity.Product;
import main.entity.Status;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import java.util.ArrayList;
import java.util.List;

public final class CatalogSearch {
    private CatalogSearch() {}

    public static String sortKey(String sort) {
        return "oldest".equals(sort) || "title".equals(sort) ? sort : "updated";
    }

    public static Specification<Product> matching(String keyword, Integer categoryId,
                                                  boolean staff, String sort) {
        return matching(keyword, "", "", "", categoryId, staff, sort);
    }

    public static Specification<Product> matching(String title, String author, String artist,
                                                  String uploader, Integer categoryId, boolean staff, String sort) {
        return matchingCategories(title, author, artist, uploader,
                categoryId == null ? List.of() : List.of(categoryId), staff, sort);
    }

    public static List<Integer> categoryIds(List<Integer> ids) {
        return ids == null ? List.of() : ids.stream().filter(java.util.Objects::nonNull)
                .filter(id -> id > 0).distinct().limit(20).toList();
    }

    public static Specification<Product> matchingCategories(String title, String author, String artist,
                                                  String uploader, List<Integer> categoryIds, boolean staff, String sort) {
        String order = sortKey(sort);
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.isFalse(root.get("isDeleted")));
            if (!staff) conditions.add(cb.equal(root.get("status"), Status.PUBLIC));
            contains(conditions, cb, root.get("title"), title);
            contains(conditions, cb, root.get("author"), author);
            contains(conditions, cb, root.get("artist"), artist);
            if (uploader != null && !uploader.isBlank()) {
                contains(conditions, cb, root.join("uploader").get("userName"), uploader);
            }
            for (Integer categoryId : categoryIds(categoryIds)) {
                var categoryProducts = query.subquery(Integer.class);
                var product = categoryProducts.from(Product.class);
                categoryProducts.select(product.get("productId"))
                        .where(cb.equal(product.join("categories").get("categoryId"), categoryId));
                conditions.add(cb.or(cb.equal(root.get("category").get("categoryId"), categoryId),
                        root.get("productId").in(categoryProducts)));
            }
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                if ("title".equals(order)) {
                    query.orderBy(cb.asc(cb.lower(root.get("title"))), cb.asc(root.get("productId")));
                } else {
                    var unknownDate = cb.<Integer>selectCase().when(cb.isNull(root.get("updatedAt")), 1)
                            .otherwise(0);
                    query.orderBy(cb.asc(unknownDate), "oldest".equals(order)
                            ? cb.asc(root.get("updatedAt")) : cb.desc(root.get("updatedAt")),
                            cb.desc(root.get("productId")));
                }
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }

    private static void contains(List<Predicate> conditions, CriteriaBuilder cb,
                                 Expression<String> field, String value) {
        if (value == null || value.isBlank()) return;
        String text = value.trim().toLowerCase(java.util.Locale.ROOT);
        String pattern = "%" + text.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
        conditions.add(cb.like(cb.lower(field), pattern, '!'));
    }
}
