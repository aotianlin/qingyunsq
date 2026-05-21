package com.campusforum.search.event;

import com.campusforum.search.service.MeiliSearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 监听搜索索引事件，异步同步到 Meilisearch
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchSyncListener {

    private final MeiliSearchClient meiliSearchClient;

    @Async
    @EventListener
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void onSearchIndexEvent(SearchIndexEvent event) {
        String index = event.getEntityType() == SearchIndexEvent.EntityType.POST ? "posts" : "resources";

        switch (event.getOperation()) {
            case CREATE, UPDATE -> {
                Map<String, Object> doc = new HashMap<>();
                doc.put("id", event.getEntityId());
                if (event.getTitle() != null) doc.put("title", event.getTitle());
                if (event.getContent() != null) doc.put("content", event.getContent());
                meiliSearchClient.indexDocument(index, doc);
                log.debug("Search index synced: {} {} id={}", event.getOperation(), event.getEntityType(), event.getEntityId());
            }
            case DELETE -> {
                meiliSearchClient.deleteDocument(index, event.getEntityId());
                log.debug("Search index deleted: {} id={}", event.getEntityType(), event.getEntityId());
            }
        }
    }

    @Recover
    public void recover(Exception e, SearchIndexEvent event) {
        log.error("Search sync failed after 3 retries: entityType={}, entityId={}, operation={}, error={}",
                event.getEntityType(), event.getEntityId(), event.getOperation(), e.getMessage());
        // 不阻塞业务操作
    }
}
