package org.finos.ai.demo.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class SpringAiTokenUsageExtractorTest {

  @Test
  void extractsTokenUsageFromChatResponse() {
    ChatResponse response =
        ChatResponse.builder()
            .metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(100, 25, 125)).build())
            .generations(List.of(new Generation(
                AssistantMessage.builder().content("{\"firstName\":\"Andrew\"}").build())))
            .build();

    TokenUsage usage = SpringAiTokenUsageExtractor.from(response);

    assertThat(usage).isEqualTo(new TokenUsage(100, 25, 125, 0, 0));
  }

  @Test
  void extractsCacheTokenCountsFromChatResponse() {
    ChatResponse response =
        ChatResponse.builder()
            .metadata(
                ChatResponseMetadata.builder()
                    .usage(new DefaultUsage(1000, 50, 1050, null, 400L, 200L))
                    .build())
            .generations(List.of(new Generation(
                AssistantMessage.builder().content("ok").build())))
            .build();

    TokenUsage usage = SpringAiTokenUsageExtractor.from(response);

    assertThat(usage).isEqualTo(new TokenUsage(1000, 50, 1050, 400, 200));
  }

  @Test
  void returnsNullWhenResponseMissing() {
    assertThat(SpringAiTokenUsageExtractor.from(null)).isNull();
  }

}
