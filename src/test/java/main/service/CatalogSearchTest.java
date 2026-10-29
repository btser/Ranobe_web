package main.service;

import main.entity.Product;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogSearchTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void adminProductPageLoadsUploaderButCountQueryDoesNotFetch() {
        Root<Product> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder builder = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        when(query.getResultType()).thenReturn(Product.class);
        AdministrationSearch.products("", "all").toPredicate(root, query, builder);
        verify(root).fetch("uploader", JoinType.LEFT);
        clearInvocations(root);
        when(query.getResultType()).thenReturn(Long.class);
        AdministrationSearch.products("", "all").toPredicate(root, query, builder);
        verify(root, never()).fetch(anyString(), any(JoinType.class));
    }
    @Test
    void genreIdsAreBoundedDistinctAndPositive() {
        assertEquals(List.of(2, 1), CatalogSearch.categoryIds(Arrays.asList(2, null, -1, 2, 0, 1)));
        assertEquals(List.of(), CatalogSearch.categoryIds(null));
        assertEquals(20, CatalogSearch.categoryIds(java.util.stream.IntStream.range(1, 50).boxed().toList()).size());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void eachSelectedGenreAddsItsOwnMembershipCondition() {
        Root<Product> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder builder = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        when(query.getResultType()).thenReturn(Long.class);
        CatalogSearch.matchingCategories("", "", "", "", List.of(1, 2, 1), false, "updated")
                .toPredicate(root, query, builder);
        verify(query, times(2)).subquery(Integer.class);
        verify(builder, times(2)).equal(any(Expression.class), eq((Object)1));
        verify(builder, times(2)).equal(any(Expression.class), eq((Object)2));
    }
}
