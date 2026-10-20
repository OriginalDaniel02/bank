package danbank.notification.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * A small, dedicated worker pool for delivering notifications. Deliberately
 * separate from the request-handling thread pool and the database connection
 * pool's own concurrency: notification persistence must never compete with
 * (and potentially starve) the connections that money-movement requests are
 * holding, especially under the pessimistic row locking used for transfers.
 */
@Configuration
public class NotificationAsyncConfig {

    @Bean
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notif-");
        executor.initialize();
        return executor;
    }
}
