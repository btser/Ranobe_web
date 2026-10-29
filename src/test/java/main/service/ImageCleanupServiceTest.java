package main.service;

import main.config.ImageCleanupConfig.Settings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageCleanupServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final String KEY = "chapters/p1/12345678-1234-1234-1234-123456789abc.jpg";
    private final R2StorageService storage = mock(R2StorageService.class);
    private final ImageCleanupStore store = mock(ImageCleanupStore.class);
    private final Settings settings = new Settings();
    private ImageCleanupService cleanup;

    @BeforeEach
    void setup() {
        cleanup = new ImageCleanupService(storage, store, settings, Clock.fixed(NOW, ZoneOffset.UTC), List.of());
        doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; }).when(store).withWorkerLock(any());
        doAnswer(call -> call.getArgument(0, Supplier.class).get()).when(store).duringDeletion(any());
        when(store.cursor()).thenReturn(new ImageCleanupStore.Cursor(0, null, NOW.plusSeconds(60)));
        when(store.due(any(), anyInt())).thenReturn(List.of());
    }

    private ImageCleanupStore.Candidate candidate() {
        return new ImageCleanupStore.Candidate(KEY, "etag", NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(1)));
    }

    private void due() {
        when(store.due(any(), anyInt())).thenReturn(List.of(candidate()));
        when(storage.findImage(KEY)).thenReturn(new R2StorageService.StoredImage(KEY, candidate().etag(), candidate().modifiedAt()));
    }

    @Test
    void scanOnlyMarksUnreferencedImagesAndPersistsPagination() {
        when(store.cursor()).thenReturn(new ImageCleanupStore.Cursor(0, "previous-page", NOW.minusSeconds(60)));
        var orphan = new R2StorageService.StoredImage(KEY, "etag", NOW);
        var referenced = new R2StorageService.StoredImage(KEY.replace("p1/", "p2/"), "etag2", NOW);
        when(storage.listImages("chapters/", "previous-page", 100))
                .thenReturn(new R2StorageService.ImagePage(List.of(orphan, referenced,
                        new R2StorageService.StoredImage("chapters/manual-logo.png", "etag3", NOW)), "next-page"));
        when(store.referenced(referenced.key())).thenReturn(true);

        cleanup.tick();

        verify(store).mark(orphan, NOW);
        verify(store).forget(referenced.key());
        verify(store).advance(new ImageCleanupStore.Cursor(0, "next-page", NOW));
        verify(storage, never()).deleteUnusedImage(anyString());
        verify(store).due(NOW.minus(Duration.ofHours(24)), 10);
    }

    @Test
    void wakingAfterMissedScheduleResumesSavedCursor() {
        when(store.cursor()).thenReturn(new ImageCleanupStore.Cursor(3, "saved-token", NOW.minus(Duration.ofDays(3))));
        when(storage.listImages("user-covers/", "saved-token", 100))
                .thenReturn(new R2StorageService.ImagePage(List.of(), null));
        cleanup.tick();
        verify(store).advance(new ImageCleanupStore.Cursor(0, null, NOW.plus(Duration.ofDays(1))));
    }

    @Test
    void expiredOrphanIsDeletedOnlyInsideProtectedTransaction() {
        due();
        cleanup.tick();
        var order = inOrder(store, storage);
        order.verify(store).referenced(KEY);
        order.verify(storage).findImage(KEY);
        order.verify(store).duringDeletion(any());
        order.verify(store).referenced(KEY);
        order.verify(storage).deleteUnusedImage(KEY);
        order.verify(store).forget(KEY);
    }

    @Test
    void imageReferencedAfterFirstScanIsKept() {
        due();
        when(store.referenced(KEY)).thenReturn(true);
        cleanup.tick();
        verify(storage, never()).deleteUnusedImage(anyString());
        verify(store).forget(KEY);
    }

    @Test
    void chapterSavedBetweenCloudCheckAndDatabaseLockIsKept() {
        due();
        when(store.referenced(KEY)).thenReturn(false, true);
        cleanup.tick();
        verify(storage).findImage(KEY);
        verify(store).duringDeletion(any());
        verify(storage, never()).deleteUnusedImage(anyString());
        verify(store).forget(KEY);
    }

    @Test
    void objectReplacedSinceScanStartsNewGracePeriod() {
        due();
        var replacement = new R2StorageService.StoredImage(KEY, "new-etag", NOW);
        when(storage.findImage(KEY)).thenReturn(replacement);
        cleanup.tick();
        verify(store).mark(replacement, NOW);
        verify(storage, never()).deleteUnusedImage(anyString());
    }

    @Test
    void failedDeleteKeepsCandidateForRetry() {
        due();
        doThrow(new IllegalStateException("R2 unavailable")).when(storage).deleteUnusedImage(KEY);
        cleanup.tick();
        verify(store, never()).forget(KEY);
        verify(store).defer(KEY, NOW.plusSeconds(300));
    }

    @Test
    void missingObjectClearsCandidateButCloudPermissionFailureDoesNot() {
        due();
        when(storage.findImage(KEY)).thenReturn(null);
        cleanup.tick();
        verify(store).forget(KEY);
        verify(storage, never()).deleteUnusedImage(anyString());
        clearInvocations(store, storage);
        when(storage.findImage(KEY)).thenThrow(new IllegalStateException("Access denied"));
        cleanup.tick();
        verify(store, never()).forget(KEY);
        verify(storage, never()).deleteUnusedImage(anyString());
    }

    @Test
    void failedListingDoesNotLoseScanPosition() {
        when(store.cursor()).thenReturn(new ImageCleanupStore.Cursor(1, "resume-here", NOW));
        when(storage.listImages("covers/", "resume-here", 100)).thenThrow(new IllegalStateException("Cloud unavailable"));
        cleanup.tick();
        verify(store, never()).advance(any());
        verify(storage, never()).deleteUnusedImage(anyString());
    }

    @Test
    void databaseFailureNeverDeletesImage() {
        due();
        when(store.referenced(KEY)).thenThrow(new IllegalStateException("DB unavailable"));
        cleanup.tick();
        verify(storage, never()).deleteUnusedImage(anyString());
        verify(store, never()).forget(KEY);
    }

    @Test
    void dryRunAndDisabledModesDoNotDeleteAnything() {
        due();
        settings.setDryRun(true);
        cleanup.tick();
        verify(storage, never()).deleteUnusedImage(anyString());
        clearInvocations(store, storage);
        settings.setEnabled(false);
        cleanup.tick();
        verifyNoInteractions(store, storage);
    }

    @Test
    void configuredSiteArtworkAndUnknownKeysAreProtected() {
        due();
        cleanup = new ImageCleanupService(storage, store, settings, Clock.fixed(NOW, ZoneOffset.UTC),
                List.of("https://images.example/" + KEY));
        cleanup.tick();
        verify(store, never()).referenced(anyString());
        verify(storage, never()).deleteUnusedImage(anyString());
        assertFalse(ImageCleanupService.managed("favicon-ln.png"));
        assertFalse(ImageCleanupService.managed("covers/manual-image.jpg"));
        assertFalse(ImageCleanupService.managed("other/" + KEY));
        assertFalse(ImageCleanupService.managed("chapters/../" + KEY));
        assertTrue(ImageCleanupService.managed(KEY));
        assertTrue(ImageCleanupService.managed(KEY.replace("chapters/p1", "covers")));
    }

    @Test
    void secondWorkerDoesNothingIfDatabaseLockIsHeld() {
        doNothing().when(store).withWorkerLock(any());
        cleanup.tick();
        verifyNoInteractions(storage);
        verify(store, never()).cursor();
    }

    @Test
    void unsafeLimitsAreRejectedBeforeAnyCloudAccess() {
        settings.setGracePeriod(Duration.ZERO);
        cleanup.tick();
        verifyNoInteractions(storage, store);
    }
}
