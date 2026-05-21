package com.campusforum.search.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索索引同步事件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchIndexEvent {

    public enum Operation { CREATE, UPDATE, DELETE }
    public enum EntityType { POST, RESOURCE }

    private EntityType entityType;
    private Operation operation;
    private Long entityId;
    private String title;
    private String content;
}
