package main.controller;

import main.entity.*;
import main.repository.*;
import main.service.R2StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.ui.ExtendedModelMap;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PdfImportControllerTest {
    @TempDir Path dir;
    final VolumeRepository volumes = mock(VolumeRepository.class);
    final ChapterRepository chapters = mock(ChapterRepository.class);
    final ChapterImageRepository images = mock(ChapterImageRepository.class);
    final ProductRepository products = mock(ProductRepository.class);
    final R2StorageService r2 = mock(R2StorageService.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    final TransactionStatus tx = mock(TransactionStatus.class);
    final MockHttpSession session = new MockHttpSession();
    final Map<String,String> fields = new HashMap<>();
    final AtomicBoolean completed = new AtomicBoolean();
    PdfImportController controller;
    String token;

    @BeforeEach
    void setup() throws Exception {
        controller = new PdfImportController(volumes, chapters, images, products, r2, jdbc, manager);
        ReflectionTestUtils.setField(controller, "uploadDir", dir.toString());
        User user = new User(); user.setUserId(7);
        session.setAttribute("loggedInUser", user);
        Product product = new Product(); product.setUploader(user); product.setTitle("Truyện thử");
        ReflectionTestUtils.setField(product, "productId", 1);
        Volume volume = new Volume(2, product);
        when(volumes.findByIdWithProductUploader(2)).thenReturn(Optional.of(volume));
        when(chapters.findMaxSortOrderByVolumeId(2)).thenReturn(0);
        when(chapters.save(any())).thenAnswer(c -> c.getArgument(0));
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(tx);
        when(jdbc.queryForObject(startsWith("SELECT EXISTS"), eq(Boolean.class), any(Object[].class)))
                .thenAnswer(c -> completed.get());
        token = UUID.randomUUID().toString();
        ReflectionTestUtils.invokeMethod(PdfImportController.class, "registerJob", token, session, 2);
        Files.createDirectories(dir.resolve("tmp").resolve(token));
        fields.put("token", token);
        fields.put("chapterCount", "2"); fields.put("imageCount", "0");
        fields.put("title_0", "Một"); fields.put("text_0", "Nội dung một");
        fields.put("title_1", "Hai"); fields.put("text_1", "Nội dung hai");
        fields.put("summary", "Tóm tắt mới");
    }

    @Test
    void failedImagePreservesPreviewAndRetriesOnlyFailedUpload() throws Exception {
        fields.put("imageCount", "2");
        for(int i=0;i<2;i++) {
            String file = "p00" + (i+1) + ".jpg";
            Files.write(dir.resolve("tmp").resolve(token).resolve(file), new byte[]{(byte)i});
            fields.put("img_"+i, file); fields.put("imgOn_"+i, "on");
        }
        when(r2.uploadBytes(argThat(b -> b != null && b[0]==0), eq("image/jpeg"), eq("chapters/v2"))).thenReturn("https://img/a.jpg");
        when(r2.uploadBytes(argThat(b -> b != null && b[0]==1), eq("image/jpeg"), eq("chapters/v2"))).thenReturn(null, "https://img/b.jpg");
        var model = new ExtendedModelMap();
        assertEquals("volume-import-preview", controller.commit(2, fields, session, model));
        assertTrue(model.get("importFailure").toString().contains("p002.jpg"));
        verifyNoInteractions(manager);
        verify(chapters, never()).save(any());
        assertTrue(Files.exists(dir.resolve("tmp").resolve(token)));
        assertTrue(controller.commit(2, fields, session, new ExtendedModelMap()).contains("imported=2"));
        verify(r2, times(1)).uploadBytes(argThat(b -> b != null && b[0]==0), anyString(), anyString());
        verify(r2, times(2)).uploadBytes(argThat(b -> b != null && b[0]==1), anyString(), anyString());
        verify(manager).commit(tx);
    }

    @Test
    void chapterFailureRollsBackSummaryAndEarlierChaptersThenAllowsRetry() {
        when(chapters.save(any())).thenAnswer(c -> c.getArgument(0))
                .thenThrow(new IllegalStateException("Database failed"));
        var model = new ExtendedModelMap();
        assertEquals("volume-import-preview", controller.commit(2, fields, session, model));
        verify(manager).rollback(tx);
        verify(manager, never()).commit(any());
        assertEquals(fields, model.get("retryValues"));
        assertTrue(Files.exists(dir.resolve("tmp").resolve(token)));
        doAnswer(c -> c.getArgument(0)).when(chapters).save(any());
        assertTrue(controller.commit(2, fields, session, new ExtendedModelMap()).contains("imported=2"));
        verify(manager).commit(tx);
    }

    @Test
    void completedReceiptBlocksRepeatedSaveEvenWithoutPreviewFiles() {
        completed.set(true);
        assertTrue(controller.commit(2, fields, session, new ExtendedModelMap()).endsWith("importedAlready=1"));
        verifyNoInteractions(r2, manager);
        verify(chapters, never()).save(any());
    }

    @Test
    void successfulSaveThenBrowserResubmitDoesNotCreateExtraChapters() {
        doAnswer(c -> { completed.set(true); return 1; }).when(jdbc)
                .update(startsWith("INSERT INTO pdf_import_receipts"), any(Object[].class));
        assertTrue(controller.commit(2, fields, session, new ExtendedModelMap()).contains("imported=2"));
        assertFalse(Files.exists(dir.resolve("tmp").resolve(token)));
        assertTrue(controller.commit(2, fields, session, new ExtendedModelMap()).endsWith("importedAlready=1"));
        verify(chapters, times(2)).save(any());
        verify(manager, times(1)).commit(tx);
    }

    @Test
    void missingInlineImageDoesNotSilentlyLoseIllustration() {
        fields.put("text_0", "Ảnh\n[[IMG:p001.jpg]]");
        var model = new ExtendedModelMap();
        assertEquals("volume-import-preview", controller.commit(2, fields, session, model));
        assertTrue(model.get("importFailure").toString().contains("p001.jpg"));
        verify(chapters, never()).save(any());
        verifyNoInteractions(manager);
    }

    @Test
    void wrongUserCannotConsumeAnotherUsersPreview() {
        User stranger = new User(); stranger.setUserId(8);
        MockHttpSession other = new MockHttpSession(); other.setAttribute("loggedInUser", stranger);
        assertEquals("redirect:/", controller.commit(2, fields, other, new ExtendedModelMap()));
        assertTrue(controller.commit(2, fields, session, new ExtendedModelMap()).contains("imported=2"));
    }

    @Test
    void emptySelectionIsReportedWithoutWritingSummary() {
        fields.put("skip_0", "on"); fields.put("skip_1", "on");
        assertEquals("volume-import-preview", controller.commit(2, fields, session, new ExtendedModelMap()));
        verify(volumes, never()).save(any());
        verifyNoInteractions(manager);
    }
}
