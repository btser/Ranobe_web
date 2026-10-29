package main.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageCleanupStoreTest {
    @Test
    void referenceQueryIncludesHiddenContentAndEveryImageLocation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new ImageCleanupStore(jdbc, mock(PlatformTransactionManager.class));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(true);
        assertTrue(store.referenced("chapters/p1/example.jpg"));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Boolean.class), any(Object[].class));
        String query = sql.getValue();
        for (String table : new String[]{"products", "volumes", "users", "chapters", "chapter_images"}) {
            assertTrue(query.contains("FROM " + table), table);
        }
        assertFalse(query.contains("is_deleted"), "Nội dung ẩn vẫn phải giữ ảnh");
        assertFalse(query.contains("LIKE"), "Đường dẫn không phải mẫu tìm kiếm");
    }
}
