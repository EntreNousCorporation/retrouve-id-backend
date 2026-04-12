package com.retrouvid.modules.messaging.controller;

import com.retrouvid.modules.messaging.entity.Conversation;
import com.retrouvid.modules.messaging.entity.Message;
import com.retrouvid.modules.messaging.service.MessageService;
import com.retrouvid.security.CurrentUser;
import com.retrouvid.shared.dto.ApiResponse;
import com.retrouvid.shared.dto.PagedResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService service;

    public record ConversationDto(UUID id, UUID matchId, UUID user1Id, UUID user2Id,
                                  Instant createdAt, Instant lastMessageAt) {
        static ConversationDto from(Conversation c) {
            return new ConversationDto(c.getId(),
                    c.getMatch() == null ? null : c.getMatch().getId(),
                    c.getUser1().getId(), c.getUser2().getId(),
                    c.getCreatedAt(), c.getLastMessageAt());
        }
    }

    public record MessageDto(UUID id, UUID conversationId, UUID senderId, String content,
                             boolean read, Instant createdAt) {
        static MessageDto from(Message m) {
            return new MessageDto(m.getId(), m.getConversation().getId(), m.getSender().getId(),
                    m.getContent(), m.isRead(), m.getCreatedAt());
        }
    }

    public record SendRequest(@NotBlank String content) {}

    public record OpenFromMatchRequest(UUID matchId) {}

    @GetMapping("/conversations")
    public ApiResponse<PagedResponse<ConversationDto>> list(@PageableDefault(size = 20) Pageable pageable) {
        var page = service.listForUser(CurrentUser.id(), pageable).map(ConversationDto::from);
        return ApiResponse.ok(PagedResponse.of(page));
    }

    @PostMapping("/conversations/from-match")
    public ApiResponse<ConversationDto> openFromMatch(@RequestBody OpenFromMatchRequest req) {
        return ApiResponse.ok(ConversationDto.from(service.openFromMatch(req.matchId(), CurrentUser.id())));
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<PagedResponse<MessageDto>> messages(@PathVariable UUID id,
                                                           @PageableDefault(size = 50) Pageable pageable) {
        var page = service.messagesOf(id, CurrentUser.id(), pageable).map(MessageDto::from);
        return ApiResponse.ok(PagedResponse.of(page));
    }

    @PostMapping("/conversations/{id}/messages")
    public ApiResponse<MessageDto> send(@PathVariable UUID id, @RequestBody SendRequest req) {
        return ApiResponse.ok(MessageDto.from(service.send(id, CurrentUser.id(), req.content())));
    }
}
