package com.retrouvid.modules.messaging.service;

import com.retrouvid.modules.matching.entity.Match;
import com.retrouvid.modules.matching.repository.MatchRepository;
import com.retrouvid.modules.messaging.entity.Conversation;
import com.retrouvid.modules.messaging.entity.Message;
import com.retrouvid.modules.messaging.repository.ConversationRepository;
import com.retrouvid.modules.messaging.repository.MessageRepository;
import com.retrouvid.modules.notification.entity.NotificationType;
import com.retrouvid.modules.notification.service.NotificationService;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import com.retrouvid.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate wsTemplate;

    @Transactional
    public Conversation openFromMatch(UUID matchId, UUID userId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Match introuvable"));
        User p = match.getDeclarationPerte().getUser();
        User d = match.getDeclarationDecouverte().getUser();
        if (!p.getId().equals(userId) && !d.getId().equals(userId)) {
            throw ApiException.forbidden("Action non autorisée");
        }
        return conversationRepository.findByMatchId(matchId).orElseGet(() ->
                conversationRepository.save(Conversation.builder()
                        .match(match).user1(p).user2(d).build()));
    }

    @Transactional(readOnly = true)
    public Page<Conversation> listForUser(UUID userId, Pageable pageable) {
        return conversationRepository.findForUser(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Message> messagesOf(UUID conversationId, UUID userId, Pageable pageable) {
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound("Conversation introuvable"));
        ensureParticipant(c, userId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId, pageable);
    }

    @Transactional
    public Message send(UUID conversationId, UUID senderId, String content) {
        if (content == null || content.isBlank()) {
            throw ApiException.badRequest("Message vide");
        }
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound("Conversation introuvable"));
        ensureParticipant(c, senderId);
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> ApiException.unauthorized("Utilisateur introuvable"));
        Message m = messageRepository.save(Message.builder()
                .conversation(c).sender(sender).content(content).read(false).build());
        c.setLastMessageAt(Instant.now());

        UUID recipientId = c.getUser1().getId().equals(senderId) ? c.getUser2().getId() : c.getUser1().getId();
        wsTemplate.convertAndSendToUser(recipientId.toString(), "/queue/messages",
                Map.of("conversationId", conversationId.toString(),
                        "messageId", m.getId().toString(),
                        "senderId", senderId.toString(),
                        "content", content,
                        "createdAt", m.getCreatedAt().toString()));
        notificationService.notify(recipientId, NotificationType.MESSAGE,
                "Nouveau message",
                content.length() > 120 ? content.substring(0, 117) + "..." : content,
                Map.of("conversationId", conversationId.toString()));
        return m;
    }

    private void ensureParticipant(Conversation c, UUID userId) {
        if (!c.getUser1().getId().equals(userId) && !c.getUser2().getId().equals(userId)) {
            throw ApiException.forbidden("Action non autorisée");
        }
    }
}
