package com.campusforum.search.event;

import com.campusforum.search.service.MeiliSearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 监听搜索索引事件，异步同步到 Meilisearch。
 * 使用手动重试逻辑（最多 3 次，指数退避）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchSyncListener {

    private final MeiliSearchClient meiliSearchClient;

    @EventListener
    public void onSearchIndexEvent(SearchIndexEvent event) {
        String index = event.getEntityType() == SearchIndexEvent.EntityType.POST ? "posts" : "resources";

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                switch (event.getOperation()) {
                    case CREATE, UPDATE -> {
                        Map<String, Object> doc = new HashMap<>();
                        doc.put("id", event.getEntityId());
                        if (event.getTitle() != null) doc.put("title", event.getTitle());
                        if (event.getContent() != null) doc.put("content", event.getContent());
                        meiliSearchClient.indexDocument(index, doc);
                    }
                    case DELETE -> meiliSearchClient.deleteDocument(index, event.getEntityId());
                }
                log.debug("Search index synced: {} {} id={}", event.getOperation(), event.getEntityType(), event.getEntityId());
                return; // 成功，退出重试循环
            } catch (Exception e) {
                if (attempt == 3) {
                    log.error("Search sync failed after 3 retries: entityType={}, entityId={}, operation={}, error={}",
                            event.getEntityType(), event.getEntityId(), event.getOperation(), e.getMessage());
                } else {
                    try {
                        Thread.sleep((long) Math.pow(2, attempt - 1) * 1000); // 1s, 2s 退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}
