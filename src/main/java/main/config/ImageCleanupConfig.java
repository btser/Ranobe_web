package main.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;

@Configuration
@EnableScheduling
public class ImageCleanupConfig {
    @Bean
    @ConfigurationProperties(prefix = "image-cleanup")
    public Settings imageCleanupSettings() { return new Settings(); }

    @Bean
    public ThreadPoolTaskScheduler imageCleanupScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("image-cleanup-");
        return scheduler;
    }

    public static class Settings {
        private boolean enabled = true;
        private boolean dryRun = false;
        private Duration gracePeriod = Duration.ofHours(24);
        private Duration scanInterval = Duration.ofHours(24);
        private int scanPageSize = 100;
        private int deleteBatchSize = 10;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public boolean isDryRun() { return dryRun; }
        public void setDryRun(boolean value) { dryRun = value; }
        public Duration getGracePeriod() { return gracePeriod; }
        public void setGracePeriod(Duration value) { gracePeriod = value; }
        public Duration getScanInterval() { return scanInterval; }
        public void setScanInterval(Duration value) { scanInterval = value; }
        public int getScanPageSize() { return scanPageSize; }
        public void setScanPageSize(int value) { scanPageSize = value; }
        public int getDeleteBatchSize() { return deleteBatchSize; }
        public void setDeleteBatchSize(int value) { deleteBatchSize = value; }

        public void validate() {
            if (gracePeriod == null || gracePeriod.compareTo(Duration.ofHours(1)) < 0
                    || scanInterval == null || scanInterval.compareTo(Duration.ofMinutes(1)) < 0
                    || scanPageSize < 1 || scanPageSize > 100 || deleteBatchSize < 1 || deleteBatchSize > 10) {
                throw new IllegalArgumentException("Lịch dọn ảnh không hợp lệ: chờ ít nhất 1 giờ, quét 1-100 ảnh, xóa 1-10 ảnh mỗi đợt.");
            }
        }
    }
}
