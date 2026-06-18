package com.campusforum.ai.workspace;

import com.campusforum.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiWorkspaceController {

    private final AiWorkspaceService workspaceService;

    @GetMapping("/agents")
    public R<Map<String, Object>> agents(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(defaultValue = "recommend") String sort,
                                         @RequestParam(required = false) Boolean mine,
                                         @RequestParam(required = false) Boolean favorite,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(workspaceService.listAgents(keyword, category, sort, mine, favorite, page, pageSize));
    }

    @PostMapping("/agents")
    public R<Map<String, Object>> createAgent(@RequestBody Map<String, Object> body) {
        return R.ok(workspaceService.createAgent(body));
    }

    @GetMapping("/agents/{agentId}")
    public R<Map<String, Object>> agent(@PathVariable String agentId) {
        return R.ok(workspaceService.getAgent(agentId));
    }

    @PatchMapping("/agents/{agentId}")
    public R<Map<String, Object>> updateAgent(@PathVariable String agentId,
                                             @RequestBody Map<String, Object> body) {
        return R.ok(workspaceService.updateAgent(agentId, body));
    }

    @PostMapping("/agents/{agentId}/favorite")
    public R<Map<String, Object>> favoriteAgent(@PathVariable String agentId) {
        return R.ok(workspaceService.favoriteAgent(agentId, true));
    }

    @DeleteMapping("/agents/{agentId}/favorite")
    public R<Map<String, Object>> unfavoriteAgent(@PathVariable String agentId) {
        return R.ok(workspaceService.favoriteAgent(agentId, false));
    }

    @PostMapping("/agents/{agentId}/use")
    public R<Map<String, Object>> useAgent(@PathVariable String agentId) {
        return R.ok(workspaceService.useAgent(agentId));
    }

    @GetMapping("/plugins")
    public R<Map<String, Object>> plugins(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(defaultValue = "all") String tab,
                                          @RequestParam(defaultValue = "comprehensive") String sort,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(workspaceService.listPlugins(keyword, category, tab, sort, page, pageSize));
    }

    @GetMapping("/plugins/rankings")
    public R<List<Map<String, Object>>> pluginRankings() {
        return R.ok(workspaceService.pluginRankings());
    }

    @GetMapping("/plugins/latest")
    public R<List<Map<String, Object>>> latestPlugins() {
        return R.ok(workspaceService.latestPlugins());
    }

    @PostMapping("/plugins/{pluginId}/install")
    public R<Map<String, Object>> installPlugin(@PathVariable String pluginId) {
        return R.ok(workspaceService.installPlugin(pluginId, true));
    }

    @DeleteMapping("/plugins/{pluginId}/install")
    public R<Map<String, Object>> uninstallPlugin(@PathVariable String pluginId) {
        return R.ok(workspaceService.installPlugin(pluginId, false));
    }

    @PostMapping("/plugins/{pluginId}/invoke")
    public R<Map<String, Object>> invokePlugin(@PathVariable String pluginId,
                                               @RequestBody Map<String, Object> body) {
        return R.ok(workspaceService.invokePlugin(pluginId, body));
    }

    @PostMapping("/plugin-developer/applications")
    public R<Map<String, Object>> applyDeveloper(@RequestBody Map<String, Object> body) {
        return R.ok(workspaceService.applyPluginDeveloper(body));
    }

    @PostMapping("/plugins")
    public R<Map<String, Object>> publishPlugin(@RequestBody Map<String, Object> body) {
        return R.ok(workspaceService.publishPlugin(body));
    }

    @GetMapping("/knowledge-bases")
    public R<Map<String, Object>> knowledgeBases(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(defaultValue = "all") String tab,
                                                 @RequestParam(required = false) String type,
                                                 @RequestParam(defaultValue = "recent") String sort,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(workspaceService.listKnowledgeBases(keyword, category, tab, type, sort, page, pageSize));
    }

    @GetMapping("/knowledge-bases/stats")
    public R<Map<String, Object>> knowledgeStats() {
        return R.ok(workspaceService.knowledgeStats());
    }

    @PostMapping("/knowledge-bases")
    public R<Map<String, Object>> createKnowledgeBase(@RequestBody Map<String, Object> body) {
        return R.ok(workspaceService.createKnowledgeBase(body));
    }

    @PatchMapping("/knowledge-bases/{knowledgeBaseId}")
    public R<Map<String, Object>> updateKnowledgeBase(@PathVariable String knowledgeBaseId,
                                                      @RequestBody Map<String, Object> body) {
        return R.ok(workspaceService.updateKnowledgeBase(knowledgeBaseId, body));
    }

    @DeleteMapping("/knowledge-bases/{knowledgeBaseId}")
    public R<Map<String, Object>> deleteKnowledgeBase(@PathVariable String knowledgeBaseId) {
        return R.ok(workspaceService.deleteKnowledgeBase(knowledgeBaseId));
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/favorite")
    public R<Map<String, Object>> favoriteKnowledgeBase(@PathVariable String knowledgeBaseId) {
        return R.ok(workspaceService.favoriteKnowledgeBase(knowledgeBaseId, true));
    }

    @DeleteMapping("/knowledge-bases/{knowledgeBaseId}/favorite")
    public R<Map<String, Object>> unfavoriteKnowledgeBase(@PathVariable String knowledgeBaseId) {
        return R.ok(workspaceService.favoriteKnowledgeBase(knowledgeBaseId, false));
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/share")
    public R<Map<String, Object>> shareKnowledgeBase(@PathVariable String knowledgeBaseId,
                                                     @RequestBody Map<String, Object> body) {
        return R.ok(workspaceService.shareKnowledgeBase(knowledgeBaseId, body));
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public R<Map<String, Object>> uploadDocuments(@PathVariable String knowledgeBaseId,
                                                  @RequestParam("files") MultipartFile[] files,
                                                  @RequestParam(required = false) String tags,
                                                  @RequestParam(required = false) String parseMode) {
        return R.ok(workspaceService.uploadDocuments(knowledgeBaseId, files, tags, parseMode));
    }

    @GetMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public R<List<Map<String, Object>>> documents(@PathVariable String knowledgeBaseId) {
        return R.ok(workspaceService.listDocuments(knowledgeBaseId));
    }

    @DeleteMapping("/knowledge-bases/{knowledgeBaseId}/documents/{documentId}")
    public R<Map<String, Object>> deleteDocument(@PathVariable String knowledgeBaseId,
                                                 @PathVariable String documentId) {
        return R.ok(workspaceService.deleteDocument(knowledgeBaseId, documentId));
    }

    @GetMapping("/knowledge-ingest-tasks/{taskId}")
    public R<Map<String, Object>> ingestTask(@PathVariable String taskId) {
        return R.ok(workspaceService.ingestTask(taskId));
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/qa-pairs")
    public R<Map<String, Object>> createQaPair(@PathVariable String knowledgeBaseId,
                                               @RequestBody Map<String, Object> body) {
        return R.ok(workspaceService.createQaPair(knowledgeBaseId, body));
    }

    @GetMapping("/knowledge-bases/{knowledgeBaseId}/usage")
    public R<Map<String, Object>> knowledgeUsage(@PathVariable String knowledgeBaseId) {
        return R.ok(workspaceService.knowledgeUsage(knowledgeBaseId));
    }

    @PostMapping("/conversations")
    public R<Map<String, Object>> createConversation(@RequestBody Map<String, Object> body) {
        return R.ok(workspaceService.createConversation(body));
    }

    @GetMapping("/conversations")
    public R<Map<String, Object>> conversations(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(workspaceService.listConversations(page, pageSize));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public R<List<Map<String, Object>>> conversationMessages(@PathVariable String conversationId) {
        return R.ok(workspaceService.conversationMessages(conversationId));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public R<Map<String, Object>> sendMessage(@PathVariable String conversationId,
                                              @RequestBody Map<String, Object> body) {
        return R.ok(workspaceService.sendMessage(conversationId, body));
    }

    @PostMapping("/messages/{messageId}/feedback")
    public R<Map<String, Object>> feedback(@PathVariable String messageId,
                                           @RequestBody Map<String, Object> body) {
        return R.ok(workspaceService.feedback(messageId, body));
    }
}
