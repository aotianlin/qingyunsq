package com.campusforum.ai.workspace;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.ai.service.AiService;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class AiWorkspaceService {

    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path storePath = Path.of("data", "ai-workspace.json");

    private final Map<String, Map<String, Object>> agents = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> plugins = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> knowledgeBases = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> documents = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> qaPairs = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> ingestTasks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> conversations = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> conversationMessages = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> favoriteAgents = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> installedPlugins = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> favoriteKnowledgeBases = new ConcurrentHashMap<>();

    private final AtomicLong agentSeq = new AtomicLong(100);
    private final AtomicLong pluginSeq = new AtomicLong(100);
    private final AtomicLong kbSeq = new AtomicLong(100);
    private final AtomicLong docSeq = new AtomicLong(100);
    private final AtomicLong taskSeq = new AtomicLong(100);
    private final AtomicLong conversationSeq = new AtomicLong(100);
    private final AtomicLong messageSeq = new AtomicLong(100);

    public AiWorkspaceService(AiService aiService) {
        this.aiService = aiService;
        if (!load()) {
            seed();
            save();
        }
    }

    public Map<String, Object> listAgents(String keyword, String category, String sort,
                                          Boolean mine, Boolean favorite, int page, int pageSize) {
        long userId = userIdOrGuest();
        List<Map<String, Object>> list = agents.values().stream()
                .filter(item -> !bool(item.get("deleted")))
                .filter(item -> matches(item, keyword, "name", "description", "category"))
                .filter(item -> blank(category) || Objects.equals(item.get("category"), category))
                .filter(item -> !Boolean.TRUE.equals(mine) || Objects.equals(item.get("ownerId"), userId))
                .filter(item -> !Boolean.TRUE.equals(favorite) || favorites(favoriteAgents, userId).contains(id(item)))
                .map(item -> agentView(item, userId))
                .collect(Collectors.toCollection(ArrayList::new));
        sortAgents(list, sort);
        return page(list, page, pageSize);
    }

    public Map<String, Object> createAgent(Map<String, Object> body) {
        long userId = requireUser();
        String now = now();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "agent_" + agentSeq.incrementAndGet());
        item.put("name", str(body.get("name"), "Untitled Agent"));
        item.put("description", str(body.get("description"), ""));
        item.put("category", str(body.get("category"), "General"));
        item.put("model", str(body.get("model"), "deepseek-v4-flash"));
        item.put("prompt", str(body.get("prompt"), ""));
        item.put("abilities", list(body.get("abilities")));
        item.put("knowledgeBaseIds", list(body.get("knowledgeBaseIds")));
        item.put("pluginIds", list(body.get("pluginIds")));
        item.put("tags", list(body.get("tags")));
        item.put("avatar", str(body.get("avatar"), ""));
        item.put("color", str(body.get("color"), "#18c7a7"));
        item.put("userCount", 0L);
        item.put("rating", 5.0);
        item.put("ownerId", userId);
        item.put("createdAt", now);
        item.put("updatedAt", now);
        item.put("deleted", false);
        agents.put(id(item), item);
        save();
        return agentView(item, userId);
    }

    public Map<String, Object> getAgent(String agentId) {
        return agentView(require(agents, agentId), userIdOrGuest());
    }

    public Map<String, Object> updateAgent(String agentId, Map<String, Object> body) {
        long userId = requireUser();
        Map<String, Object> item = require(agents, agentId);
        requireOwner(item, userId);
        patch(item, body, "name", "description", "category", "model", "prompt", "abilities",
                "knowledgeBaseIds", "pluginIds", "tags", "avatar", "color");
        item.put("updatedAt", now());
        save();
        return agentView(item, userId);
    }

    public Map<String, Object> favoriteAgent(String agentId, boolean favorite) {
        long userId = requireUser();
        require(agents, agentId);
        setFlag(favorites(favoriteAgents, userId), agentId, favorite);
        save();
        return Map.of("id", agentId, "isFavorite", favorite);
    }

    public Map<String, Object> useAgent(String agentId) {
        long userId = requireUser();
        Map<String, Object> item = require(agents, agentId);
        item.put("userCount", num(item.get("userCount")) + 1);
        item.put("updatedAt", now());
        save();
        return Map.of(
                "agent", agentView(item, userId),
                "context", Map.of(
                        "model", str(item.get("model"), "deepseek-v4-flash"),
                        "prompt", str(item.get("prompt"), ""),
                        "abilities", list(item.get("abilities")),
                        "knowledgeBaseIds", list(item.get("knowledgeBaseIds")),
                        "pluginIds", list(item.get("pluginIds"))
                )
        );
    }

    public Map<String, Object> listPlugins(String keyword, String category, String tab,
                                           String sort, int page, int pageSize) {
        long userId = userIdOrGuest();
        List<Map<String, Object>> list = plugins.values().stream()
                .filter(item -> !bool(item.get("deleted")))
                .filter(item -> matches(item, keyword, "name", "description", "category"))
                .filter(item -> blank(category) || Objects.equals(item.get("category"), category))
                .filter(item -> pluginTab(item, tab))
                .map(item -> pluginView(item, userId))
                .collect(Collectors.toCollection(ArrayList::new));
        sortPlugins(list, sort);
        return page(list, page, pageSize);
    }

    public List<Map<String, Object>> pluginRankings() {
        return plugins.values().stream()
                .filter(item -> !bool(item.get("deleted")))
                .map(item -> pluginView(item, userIdOrGuest()))
                .sorted(Comparator.comparingLong(item -> -num(item.get("usageCount"))))
                .limit(10)
                .toList();
    }

    public List<Map<String, Object>> latestPlugins() {
        return plugins.values().stream()
                .filter(item -> !bool(item.get("deleted")))
                .map(item -> pluginView(item, userIdOrGuest()))
                .sorted(Comparator.comparing(item -> str(item.get("createdAt"), ""), Comparator.reverseOrder()))
                .limit(10)
                .toList();
    }

    public Map<String, Object> installPlugin(String pluginId, boolean installed) {
        long userId = requireUser();
        require(plugins, pluginId);
        setFlag(installs(userId), pluginId, installed);
        save();
        return Map.of("id", pluginId, "isInstalled", installed);
    }

    public Map<String, Object> invokePlugin(String pluginId, Map<String, Object> body) {
        long userId = requireUser();
        Map<String, Object> plugin = require(plugins, pluginId);
        if (!installs(userId).contains(pluginId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        plugin.put("usageCount", num(plugin.get("usageCount")) + 1);
        save();
        Map<String, Object> input = objectMap(body.get("input"));
        return Map.of(
                "pluginId", pluginId,
                "conversationId", str(body.get("conversationId"), ""),
                "agentId", str(body.get("agentId"), ""),
                "output", pluginOutput(pluginId, input),
                "invokedAt", now()
        );
    }

    public Map<String, Object> applyPluginDeveloper(Map<String, Object> body) {
        return Map.of(
                "applicationId", "dev_app_" + shortId(),
                "status", "pending",
                "developerName", str(body.get("developerName"), ""),
                "submittedAt", now()
        );
    }

    public Map<String, Object> publishPlugin(Map<String, Object> body) {
        long userId = requireUser();
        String now = now();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "plugin_" + pluginSeq.incrementAndGet());
        item.put("name", str(body.get("name"), "Untitled Plugin"));
        item.put("description", str(body.get("description"), ""));
        item.put("category", str(body.get("category"), "Productivity"));
        item.put("icon", str(body.get("icon"), ""));
        item.put("color", str(body.get("color"), "#38bdf8"));
        item.put("usageCount", 0L);
        item.put("installCount", 0L);
        item.put("rating", 5.0);
        item.put("isOfficial", false);
        item.put("isFeatured", false);
        item.put("permissions", list(body.get("permissions")));
        item.put("inputSchema", objectMap(body.get("inputSchema")));
        item.put("outputSchema", objectMap(body.get("outputSchema")));
        item.put("endpoint", str(body.get("endpoint"), ""));
        item.put("ownerId", userId);
        item.put("reviewStatus", "pending");
        item.put("createdAt", now);
        item.put("deleted", false);
        plugins.put(id(item), item);
        save();
        return pluginView(item, userId);
    }

    public Map<String, Object> listKnowledgeBases(String keyword, String category, String tab,
                                                  String type, String sort, int page, int pageSize) {
        long userId = userIdOrGuest();
        List<Map<String, Object>> list = knowledgeBases.values().stream()
                .filter(item -> !bool(item.get("deleted")))
                .filter(item -> matches(item, keyword, "name", "description", "category", "type"))
                .filter(item -> blank(category) || Objects.equals(item.get("category"), category))
                .filter(item -> blank(type) || Objects.equals(item.get("type"), type))
                .filter(item -> kbTab(item, tab, userId))
                .map(item -> kbView(item, userId))
                .collect(Collectors.toCollection(ArrayList::new));
        sortKnowledgeBases(list, sort);
        return page(list, page, pageSize);
    }

    public Map<String, Object> knowledgeStats() {
        List<Map<String, Object>> active = knowledgeBases.values().stream()
                .filter(item -> !bool(item.get("deleted")))
                .toList();
        long docs = active.stream().mapToLong(item -> num(item.get("documentCount"))).sum();
        long vectors = active.stream().mapToLong(item -> num(item.get("vectorCount"))).sum();
        long storage = active.stream().mapToLong(item -> num(item.get("storageBytes"))).sum();
        return Map.of(
                "knowledgeBaseCount", active.size(),
                "documentCount", docs,
                "vectorCount", vectors,
                "storageUsedBytes", storage,
                "storageLimitBytes", 53687091200L
        );
    }

    public Map<String, Object> createKnowledgeBase(Map<String, Object> body) {
        long userId = requireUser();
        String now = now();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "kb_" + kbSeq.incrementAndGet());
        item.put("name", str(body.get("name"), "Untitled Knowledge Base"));
        item.put("description", str(body.get("description"), ""));
        item.put("category", str(body.get("category"), "General"));
        item.put("type", str(body.get("type"), str(body.get("category"), "General")));
        item.put("visibility", str(body.get("visibility"), "private"));
        item.put("documentCount", 0L);
        item.put("vectorCount", 0L);
        item.put("storageBytes", 0L);
        item.put("owner", "Mine");
        item.put("ownerId", userId);
        item.put("updatedAt", now);
        item.put("createdAt", now);
        item.put("deleted", false);
        knowledgeBases.put(id(item), item);
        documents.put(id(item), new ArrayList<>());
        qaPairs.put(id(item), new ArrayList<>());
        save();
        return kbView(item, userId);
    }

    public Map<String, Object> updateKnowledgeBase(String knowledgeBaseId, Map<String, Object> body) {
        long userId = requireUser();
        Map<String, Object> item = require(knowledgeBases, knowledgeBaseId);
        requireOwner(item, userId);
        patch(item, body, "name", "description", "category", "type", "visibility");
        item.put("updatedAt", now());
        save();
        return kbView(item, userId);
    }

    public Map<String, Object> deleteKnowledgeBase(String knowledgeBaseId) {
        long userId = requireUser();
        Map<String, Object> item = require(knowledgeBases, knowledgeBaseId);
        requireOwner(item, userId);
        item.put("deleted", true);
        item.put("updatedAt", now());
        save();
        return Map.of("id", knowledgeBaseId, "deleted", true);
    }

    public Map<String, Object> favoriteKnowledgeBase(String knowledgeBaseId, boolean favorite) {
        long userId = requireUser();
        require(knowledgeBases, knowledgeBaseId);
        setFlag(favorites(favoriteKnowledgeBases, userId), knowledgeBaseId, favorite);
        save();
        return Map.of("id", knowledgeBaseId, "isFavorite", favorite);
    }

    public Map<String, Object> shareKnowledgeBase(String knowledgeBaseId, Map<String, Object> body) {
        requireUser();
        require(knowledgeBases, knowledgeBaseId);
        return Map.of(
                "shareId", "share_" + shortId(),
                "knowledgeBaseId", knowledgeBaseId,
                "targetUserIds", list(body.get("targetUserIds")),
                "permission", str(body.get("permission"), "read"),
                "url", "/ai/knowledge-bases/" + knowledgeBaseId,
                "expiresAt", OffsetDateTime.now().plusDays(7).toString()
        );
    }

    public Map<String, Object> uploadDocuments(String knowledgeBaseId, MultipartFile[] files,
                                               String tags, String parseMode) {
        long userId = requireUser();
        Map<String, Object> kb = require(knowledgeBases, knowledgeBaseId);
        requireOwner(kb, userId);
        List<Map<String, Object>> docs = documents.computeIfAbsent(knowledgeBaseId, key -> new ArrayList<>());
        int uploaded = 0;
        long bytes = 0;
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("id", "doc_" + docSeq.incrementAndGet());
                doc.put("fileName", file.getOriginalFilename());
                doc.put("fileSize", file.getSize());
                doc.put("tags", tags == null ? List.of() : List.of(tags.split(",")));
                doc.put("parseMode", blank(parseMode) ? "auto" : parseMode);
                doc.put("status", "ready");
                doc.put("createdAt", now());
                docs.add(doc);
                uploaded++;
                bytes += file.getSize();
            }
        }
        kb.put("documentCount", num(kb.get("documentCount")) + uploaded);
        kb.put("vectorCount", num(kb.get("vectorCount")) + uploaded * 512L);
        kb.put("storageBytes", num(kb.get("storageBytes")) + bytes);
        kb.put("updatedAt", now());
        String taskId = "task_" + taskSeq.incrementAndGet();
        ingestTasks.put(taskId, new LinkedHashMap<>(Map.of(
                "taskId", taskId,
                "knowledgeBaseId", knowledgeBaseId,
                "status", "completed",
                "progress", 100,
                "message", "Documents parsed"
        )));
        save();
        return Map.of("taskId", taskId, "uploaded", uploaded);
    }

    public List<Map<String, Object>> listDocuments(String knowledgeBaseId) {
        require(knowledgeBases, knowledgeBaseId);
        return new ArrayList<>(documents.getOrDefault(knowledgeBaseId, List.of()));
    }

    public Map<String, Object> deleteDocument(String knowledgeBaseId, String documentId) {
        long userId = requireUser();
        Map<String, Object> kb = require(knowledgeBases, knowledgeBaseId);
        requireOwner(kb, userId);
        List<Map<String, Object>> docs = documents.getOrDefault(knowledgeBaseId, new ArrayList<>());
        boolean removed = docs.removeIf(doc -> Objects.equals(doc.get("id"), documentId));
        if (removed) {
            kb.put("documentCount", Math.max(0, num(kb.get("documentCount")) - 1));
            kb.put("updatedAt", now());
            save();
        }
        return Map.of("id", documentId, "deleted", removed);
    }

    public Map<String, Object> ingestTask(String taskId) {
        return require(ingestTasks, taskId);
    }

    public Map<String, Object> createQaPair(String knowledgeBaseId, Map<String, Object> body) {
        long userId = requireUser();
        Map<String, Object> kb = require(knowledgeBases, knowledgeBaseId);
        requireOwner(kb, userId);
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("id", "qa_" + shortId());
        pair.put("question", str(body.get("question"), ""));
        pair.put("answer", str(body.get("answer"), ""));
        pair.put("tags", list(body.get("tags")));
        pair.put("createdAt", now());
        qaPairs.computeIfAbsent(knowledgeBaseId, key -> new ArrayList<>()).add(pair);
        kb.put("updatedAt", now());
        save();
        return pair;
    }

    public Map<String, Object> knowledgeUsage(String knowledgeBaseId) {
        require(knowledgeBases, knowledgeBaseId);
        return Map.of(
                "knowledgeBaseId", knowledgeBaseId,
                "callCount", 0,
                "hitRate", 0.0,
                "noAnswerRate", 0.0,
                "hotQuestions", qaPairs.getOrDefault(knowledgeBaseId, List.of()).stream()
                        .map(item -> item.get("question"))
                        .limit(10)
                        .toList()
        );
    }

    public Map<String, Object> createConversation(Map<String, Object> body) {
        long userId = requireUser();
        String id = "chat_" + conversationSeq.incrementAndGet();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("title", str(body.get("title"), "New conversation"));
        item.put("model", str(body.get("model"), "deepseek-v4-flash"));
        item.put("agentId", str(body.get("agentId"), ""));
        item.put("pluginIds", list(body.get("pluginIds")));
        item.put("knowledgeBaseIds", list(body.get("knowledgeBaseIds")));
        item.put("ownerId", userId);
        item.put("createdAt", now());
        item.put("updatedAt", now());
        conversations.put(id, item);
        conversationMessages.put(id, new ArrayList<>());
        save();
        return conversationView(item);
    }

    public Map<String, Object> listConversations(int page, int pageSize) {
        long userId = requireUser();
        List<Map<String, Object>> list = conversations.values().stream()
                .filter(item -> Objects.equals(item.get("ownerId"), userId))
                .map(this::conversationView)
                .sorted(Comparator.comparing(item -> str(item.get("updatedAt"), ""), Comparator.reverseOrder()))
                .collect(Collectors.toList());
        return page(list, page, pageSize);
    }

    public List<Map<String, Object>> conversationMessages(String conversationId) {
        long userId = requireUser();
        requireConversationOwner(conversationId, userId);
        return new ArrayList<>(conversationMessages.getOrDefault(conversationId, List.of()));
    }

    public Map<String, Object> sendMessage(String conversationId, Map<String, Object> body) {
        long userId = requireUser();
        Map<String, Object> conversation = requireConversationOwner(conversationId, userId);
        String content = str(body.get("content"), "");
        String model = str(body.get("model"), str(conversation.get("model"), "deepseek-v4-flash"));
        String agentId = str(body.get("agentId"), str(conversation.get("agentId"), ""));
        List<String> pluginIds = list(body.get("pluginIds")).isEmpty()
                ? list(conversation.get("pluginIds"))
                : list(body.get("pluginIds"));
        List<String> kbIds = list(body.get("knowledgeBaseIds")).isEmpty()
                ? list(conversation.get("knowledgeBaseIds"))
                : list(body.get("knowledgeBaseIds"));

        Map<String, Object> userMessage = message("user", content, model);
        userMessage.put("attachments", list(body.get("attachments")));
        List<Map<String, Object>> messages = conversationMessages.computeIfAbsent(conversationId, key -> new ArrayList<>());
        messages.add(userMessage);

        String context = buildConversationContext(agentId, pluginIds, kbIds);
        String reply = aiService.chat(List.of(new AiService.ChatMessage("user", content)), context, model);
        Map<String, Object> assistant = message("assistant", reply, model);
        assistant.put("agentId", agentId);
        assistant.put("pluginIds", pluginIds);
        assistant.put("knowledgeBaseIds", kbIds);
        messages.add(assistant);

        conversation.put("title", titleFrom(content, str(conversation.get("title"), "New conversation")));
        conversation.put("model", model);
        conversation.put("agentId", agentId);
        conversation.put("pluginIds", pluginIds);
        conversation.put("knowledgeBaseIds", kbIds);
        conversation.put("updatedAt", now());
        save();

        return Map.of("conversation", conversationView(conversation), "userMessage", userMessage, "assistantMessage", assistant);
    }

    public Map<String, Object> feedback(String messageId, Map<String, Object> body) {
        requireUser();
        for (List<Map<String, Object>> messages : conversationMessages.values()) {
            for (Map<String, Object> message : messages) {
                if (Objects.equals(message.get("id"), messageId)) {
                    message.put("feedback", Map.of(
                            "helpful", Boolean.TRUE.equals(body.get("helpful")),
                            "reason", str(body.get("reason"), ""),
                            "createdAt", now()
                    ));
                    save();
                    return message;
                }
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    private void seed() {
        if (!agents.isEmpty()) {
            return;
        }
        addAgent("agent_001", "Writing Assistant", "Drafts reports, summaries and polished copy.", "General", List.of("Writing"), "#18c7a7", "deepseek-v4-flash", "You are a concise writing assistant.");
        addAgent("agent_002", "Study Planner", "Turns goals into weekly learning plans.", "Learning", List.of("Planning"), "#3b82f6", "mimo-v2.5", "You help students make practical study plans.");
        addPlugin("plugin_weather", "Weather Lookup", "Gets weather-style structured information.", "Life Services", "#38bdf8", true, true, List.of("network"));
        addPlugin("plugin_translate", "Translator", "Translates short text between Chinese and English.", "AI Capability", "#a855f7", true, true, List.of());
        addPlugin("plugin_pdf", "PDF Helper", "Extracts and summarizes PDF-like document content.", "Productivity", "#f59e0b", false, true, List.of("file"));
        addKnowledgeBase("kb_001", "Product Help Docs", "Product usage guides, feature notes and FAQ.", "Product Docs", "Product Docs", "shared");
        addKnowledgeBase("kb_002", "Course Notes", "Reusable course notes and review materials.", "Learning", "Course Material", "private");
    }

    @SuppressWarnings("unchecked")
    private boolean load() {
        if (!Files.exists(storePath)) {
            return false;
        }
        try {
            Map<String, Object> state = objectMapper.readValue(storePath.toFile(), new TypeReference<>() {});
            putAll(agents, state.get("agents"));
            putAll(plugins, state.get("plugins"));
            putAll(knowledgeBases, state.get("knowledgeBases"));
            putAllList(documents, state.get("documents"));
            putAllList(qaPairs, state.get("qaPairs"));
            putAll(ingestTasks, state.get("ingestTasks"));
            putAll(conversations, state.get("conversations"));
            putAllList(conversationMessages, state.get("conversationMessages"));
            putAllSet(favoriteAgents, state.get("favoriteAgents"));
            putAllSet(installedPlugins, state.get("installedPlugins"));
            putAllSet(favoriteKnowledgeBases, state.get("favoriteKnowledgeBases"));
            agentSeq.set(num(state.get("agentSeq")));
            pluginSeq.set(num(state.get("pluginSeq")));
            kbSeq.set(num(state.get("kbSeq")));
            docSeq.set(num(state.get("docSeq")));
            taskSeq.set(num(state.get("taskSeq")));
            conversationSeq.set(num(state.get("conversationSeq")));
            messageSeq.set(num(state.get("messageSeq")));
            return !agents.isEmpty() || !plugins.isEmpty() || !knowledgeBases.isEmpty();
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(storePath.getParent());
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("agents", agents);
            state.put("plugins", plugins);
            state.put("knowledgeBases", knowledgeBases);
            state.put("documents", documents);
            state.put("qaPairs", qaPairs);
            state.put("ingestTasks", ingestTasks);
            state.put("conversations", conversations);
            state.put("conversationMessages", conversationMessages);
            state.put("favoriteAgents", setMap(favoriteAgents));
            state.put("installedPlugins", setMap(installedPlugins));
            state.put("favoriteKnowledgeBases", setMap(favoriteKnowledgeBases));
            state.put("agentSeq", agentSeq.get());
            state.put("pluginSeq", pluginSeq.get());
            state.put("kbSeq", kbSeq.get());
            state.put("docSeq", docSeq.get());
            state.put("taskSeq", taskSeq.get());
            state.put("conversationSeq", conversationSeq.get());
            state.put("messageSeq", messageSeq.get());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), state);
        } catch (IOException ignored) {
            // Workspace data is useful for local continuity, but should not break AI calls.
        }
    }

    @SuppressWarnings("unchecked")
    private void putAll(Map<String, Map<String, Object>> target, Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return;
        }
        source.forEach((key, val) -> {
            if (key != null && val instanceof Map<?, ?> map) {
                target.put(String.valueOf(key), new ConcurrentHashMap<>((Map<String, Object>) map));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void putAllList(Map<String, List<Map<String, Object>>> target, Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return;
        }
        source.forEach((key, val) -> {
            if (key != null && val instanceof List<?> list) {
                List<Map<String, Object>> items = list.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> new LinkedHashMap<>((Map<String, Object>) item))
                        .collect(Collectors.toCollection(ArrayList::new));
                target.put(String.valueOf(key), items);
            }
        });
    }

    private void putAllSet(Map<Long, Set<String>> target, Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return;
        }
        source.forEach((key, val) -> {
            if (key != null && val instanceof List<?> list) {
                Set<String> items = ConcurrentHashMap.newKeySet();
                list.stream().filter(Objects::nonNull).map(String::valueOf).forEach(items::add);
                target.put(Long.parseLong(String.valueOf(key)), items);
            }
        });
    }

    private Map<String, List<String>> setMap(Map<Long, Set<String>> source) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), new ArrayList<>(value)));
        return result;
    }

    private void addAgent(String id, String name, String description, String category, List<String> tags,
                          String color, String model, String prompt) {
        String now = now();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("description", description);
        item.put("category", category);
        item.put("tags", tags);
        item.put("avatar", "");
        item.put("color", color);
        item.put("model", model);
        item.put("prompt", prompt);
        item.put("abilities", tags);
        item.put("knowledgeBaseIds", List.of());
        item.put("pluginIds", List.of());
        item.put("userCount", 12500L / Math.max(1, agents.size() + 1));
        item.put("rating", 4.8);
        item.put("ownerId", 0L);
        item.put("createdAt", now);
        item.put("updatedAt", now);
        item.put("deleted", false);
        agents.put(id, item);
    }

    private void addPlugin(String id, String name, String description, String category, String color,
                           boolean official, boolean featured, List<String> permissions) {
        String now = now();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("description", description);
        item.put("category", category);
        item.put("icon", "");
        item.put("color", color);
        item.put("usageCount", 12400L / Math.max(1, plugins.size() + 1));
        item.put("installCount", 6500L / Math.max(1, plugins.size() + 1));
        item.put("rating", 4.7 + plugins.size() * 0.1);
        item.put("isOfficial", official);
        item.put("isFeatured", featured);
        item.put("permissions", permissions);
        item.put("inputSchema", Map.of());
        item.put("outputSchema", Map.of());
        item.put("reviewStatus", "approved");
        item.put("createdAt", now);
        item.put("deleted", false);
        plugins.put(id, item);
    }

    private void addKnowledgeBase(String id, String name, String description, String category, String type, String visibility) {
        String now = now();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("description", description);
        item.put("category", category);
        item.put("type", type);
        item.put("visibility", visibility);
        item.put("documentCount", 24L + knowledgeBases.size() * 8L);
        item.put("vectorCount", 12000L + knowledgeBases.size() * 5000L);
        item.put("storageBytes", 123456789L + knowledgeBases.size() * 2048L);
        item.put("owner", visibility.equals("private") ? "Mine" : "Shared");
        item.put("ownerId", visibility.equals("private") ? 1L : 0L);
        item.put("createdAt", now);
        item.put("updatedAt", now);
        item.put("deleted", false);
        knowledgeBases.put(id, item);
        documents.put(id, new ArrayList<>());
        qaPairs.put(id, new ArrayList<>());
    }

    private Map<String, Object> agentView(Map<String, Object> item, long userId) {
        Map<String, Object> view = new LinkedHashMap<>(item);
        view.remove("ownerId");
        view.remove("deleted");
        view.put("isMine", Objects.equals(item.get("ownerId"), userId));
        view.put("isFavorite", favorites(favoriteAgents, userId).contains(id(item)));
        return view;
    }

    private Map<String, Object> pluginView(Map<String, Object> item, long userId) {
        Map<String, Object> view = new LinkedHashMap<>(item);
        view.remove("ownerId");
        view.remove("deleted");
        view.put("isInstalled", installs(userId).contains(id(item)));
        return view;
    }

    private Map<String, Object> kbView(Map<String, Object> item, long userId) {
        Map<String, Object> view = new LinkedHashMap<>(item);
        view.remove("ownerId");
        view.remove("deleted");
        view.put("isFavorite", favorites(favoriteKnowledgeBases, userId).contains(id(item)));
        return view;
    }

    private Map<String, Object> conversationView(Map<String, Object> item) {
        Map<String, Object> view = new LinkedHashMap<>(item);
        view.remove("ownerId");
        view.put("messageCount", conversationMessages.getOrDefault(id(item), List.of()).size());
        return view;
    }

    private Map<String, Object> message(String role, String content, String model) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "msg_" + messageSeq.incrementAndGet());
        item.put("role", role);
        item.put("content", content);
        item.put("model", model);
        item.put("createdAt", now());
        return item;
    }

    private String buildConversationContext(String agentId, List<String> pluginIds, List<String> kbIds) {
        List<String> parts = new ArrayList<>();
        if (!blank(agentId) && agents.containsKey(agentId)) {
            parts.add("Agent prompt: " + str(agents.get(agentId).get("prompt"), ""));
        }
        if (!pluginIds.isEmpty()) {
            parts.add("Available plugins: " + pluginIds.stream()
                    .filter(plugins::containsKey)
                    .map(id -> str(plugins.get(id).get("name"), id))
                    .collect(Collectors.joining(", ")));
        }
        if (!kbIds.isEmpty()) {
            parts.add("Knowledge bases: " + kbIds.stream()
                    .filter(knowledgeBases::containsKey)
                    .map(id -> str(knowledgeBases.get(id).get("name"), id))
                    .collect(Collectors.joining(", ")));
        }
        return String.join("\n", parts);
    }

    private Map<String, Object> pluginOutput(String pluginId, Map<String, Object> input) {
        if ("plugin_weather".equals(pluginId)) {
            return Map.of("city", str(input.get("city"), "Unknown"), "summary", "Weather lookup completed");
        }
        if ("plugin_translate".equals(pluginId)) {
            return Map.of("text", str(input.get("text"), ""), "result", "Translation queued");
        }
        return Map.of("result", "Plugin invoked", "input", input);
    }

    private Map<String, Object> page(List<Map<String, Object>> list, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize <= 0 ? 10 : pageSize));
        int from = Math.min(list.size(), (safePage - 1) * safeSize);
        int to = Math.min(list.size(), from + safeSize);
        return Map.of("items", list.subList(from, to), "total", list.size());
    }

    private boolean matches(Map<String, Object> item, String keyword, String... fields) {
        if (blank(keyword)) {
            return true;
        }
        String q = keyword.toLowerCase(Locale.ROOT);
        for (String field : fields) {
            if (str(item.get(field), "").toLowerCase(Locale.ROOT).contains(q)) {
                return true;
            }
        }
        return false;
    }

    private void sortAgents(List<Map<String, Object>> list, String sort) {
        if ("latest".equals(sort)) {
            list.sort(Comparator.comparing(item -> str(item.get("createdAt"), ""), Comparator.reverseOrder()));
        } else if ("popular".equals(sort)) {
            list.sort(Comparator.comparingLong(item -> -num(item.get("userCount"))));
        } else if ("rating".equals(sort)) {
            list.sort(Comparator.comparingDouble(item -> -dbl(item.get("rating"))));
        } else {
            list.sort(Comparator.comparingDouble(item -> -dbl(item.get("rating"))));
        }
    }

    private void sortPlugins(List<Map<String, Object>> list, String sort) {
        if ("rating".equals(sort)) {
            list.sort(Comparator.comparingDouble(item -> -dbl(item.get("rating"))));
        } else if ("usage".equals(sort)) {
            list.sort(Comparator.comparingLong(item -> -num(item.get("usageCount"))));
        } else {
            list.sort(Comparator.comparingLong(item -> -num(item.get("installCount"))));
        }
    }

    private void sortKnowledgeBases(List<Map<String, Object>> list, String sort) {
        if ("docs".equals(sort)) {
            list.sort(Comparator.comparingLong(item -> -num(item.get("documentCount"))));
        } else if ("vectors".equals(sort)) {
            list.sort(Comparator.comparingLong(item -> -num(item.get("vectorCount"))));
        } else {
            list.sort(Comparator.comparing(item -> str(item.get("updatedAt"), ""), Comparator.reverseOrder()));
        }
    }

    private boolean pluginTab(Map<String, Object> item, String tab) {
        if ("official".equals(tab)) {
            return bool(item.get("isOfficial"));
        }
        if ("featured".equals(tab)) {
            return bool(item.get("isFeatured"));
        }
        if ("latest".equals(tab)) {
            return true;
        }
        return true;
    }

    private boolean kbTab(Map<String, Object> item, String tab, long userId) {
        if ("mine".equals(tab)) {
            return Objects.equals(item.get("ownerId"), userId);
        }
        if ("shared".equals(tab)) {
            return "shared".equals(item.get("visibility"));
        }
        if ("favorite".equals(tab)) {
            return favorites(favoriteKnowledgeBases, userId).contains(id(item));
        }
        return true;
    }

    private Map<String, Object> require(Map<String, Map<String, Object>> source, String id) {
        Map<String, Object> item = source.get(id);
        if (item == null || bool(item.get("deleted"))) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return item;
    }

    private Map<String, Object> requireConversationOwner(String conversationId, long userId) {
        Map<String, Object> item = require(conversations, conversationId);
        requireOwner(item, userId);
        return item;
    }

    private void requireOwner(Map<String, Object> item, long userId) {
        Object owner = item.get("ownerId");
        if (owner instanceof Number n && n.longValue() == 0L) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!Objects.equals(owner, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private long requireUser() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return StpUtil.getLoginIdAsLong();
    }

    private long userIdOrGuest() {
        return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : 0L;
    }

    private Set<String> favorites(Map<Long, Set<String>> source, long userId) {
        return source.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet());
    }

    private Set<String> installs(long userId) {
        return installedPlugins.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet());
    }

    private void setFlag(Set<String> set, String id, boolean enabled) {
        if (enabled) {
            set.add(id);
        } else {
            set.remove(id);
        }
    }

    private void patch(Map<String, Object> item, Map<String, Object> body, String... fields) {
        for (String field : fields) {
            if (body.containsKey(field)) {
                item.put(field, body.get(field));
            }
        }
    }

    private String titleFrom(String content, String fallback) {
        if (blank(content)) {
            return fallback;
        }
        String value = content.strip();
        return value.length() > 24 ? value.substring(0, 24) : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Object value) {
        if (value instanceof List<?> raw) {
            return raw.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        }
        if (value instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return List.of();
    }

    private String id(Map<String, Object> item) {
        return str(item.get("id"), "");
    }

    private String str(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private long num(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private double dbl(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    private boolean bool(Object value) {
        return Boolean.TRUE.equals(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String now() {
        return OffsetDateTime.now().toString();
    }
}
