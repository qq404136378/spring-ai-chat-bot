package com.springai.chatbot.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("chat")
public class ChatBotController {

    private final ChatClient chatClient;

    private final InMemoryChatMemory inMemoryChatMemory;

    public ChatBotController(ChatClient chatClient, InMemoryChatMemory inMemoryChatMemory) {
        this.chatClient = chatClient;
        this.inMemoryChatMemory = inMemoryChatMemory;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatRequest request) {
        //用户id
        String userId = request.userId();

        return chatClient.prompt(request.message())
                .advisors(new MessageChatMemoryAdvisor(inMemoryChatMemory, userId, 10), new SimpleLoggerAdvisor())
                .stream().content().map(content -> ServerSentEvent.builder(content).event("message").build())
                //问题回答结速标识,以便前端消息展示处理
                .concatWithValues(ServerSentEvent.builder("[DONE]").build())
                .onErrorResume(e -> Flux.just(ServerSentEvent.builder("Error: " + e.getMessage()).event("error").build()));
    }

    @GetMapping("/advisor/chat/{id}")
    public Flux<String> advisorChat(
            HttpServletResponse response,
            @PathVariable String id,
            @RequestParam String query) {

        response.setCharacterEncoding("UTF-8");

        return this.chatClient.prompt(query)
                .advisors(
                        a -> a
                                .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, id)
                                .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100)
                ).stream().content();
    }

    record ChatRequest(String userId, String message) {

    }

}
