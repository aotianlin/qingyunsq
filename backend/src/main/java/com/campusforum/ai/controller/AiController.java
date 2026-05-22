package com.campusforum.ai.controller;

import com.campusforum.ai.dto.AiRequest;
import com.campusforum.ai.dto.AiResponse;
import com.campusforum.ai.service.AiService;
import com.campusforum.ai.service.RagChatService;
import com.campusforum.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final RagChatService ragChatService;

    @PostMapping("/summarize")
    public R<AiResponse> summarize(@RequestBody AiRequest req) {
        String result = aiService.summarize(req.getContent());
        return R.ok(AiResponse.builder().summary(result).build());
    }

    @PostMapping("/moderate")
    public R<AiResponse> moderate(@RequestBody AiRequest req) {
        AiService.RiskResult result = aiService.moderate(req.getContent());
        return R.ok(AiResponse.builder()
                .riskLevel(result.level())
                .riskReason(result.reason())
                .build());
    }

    @PostMapping("/tags")
    public R<AiResponse> recommendTags(@RequestBody AiRequest req) {
        var tags = aiService.recommendTags(req.getTitle(), req.getContent());
        return R.ok(AiResponse.builder().tags(tags).build());
    }

    @PostMapping("/chat")
    public R<AiResponse> chat(@RequestBody AiRequest req) {
        String reply = aiService.chat(req.getMessages(), req.getContext());
        return R.ok(AiResponse.builder().reply(reply).build());
    }

    @PostMapping("/rag-chat")
    public R<AiResponse> ragChat(@RequestBody AiRequest req) {
        return R.ok(ragChatService.chat(req.getMessages(), req.getContext()));
    }
}
