package org.finos.fluxnova.observability;

import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

final class SpringAiTokenUsageExtractor {

  private SpringAiTokenUsageExtractor() {}

  static TokenUsage from(ChatResponse response) {
    if (response == null || response.getMetadata() == null) {
      return null;
    }
    Usage usage = response.getMetadata().getUsage();
    if (usage == null) {
      return null;
    }
    Integer prompt = usage.getPromptTokens();
    Integer completion = usage.getCompletionTokens();
    Integer total = usage.getTotalTokens();
    if (prompt == null && completion == null && total == null) {
      return null;
    }
    int promptTokens = prompt != null ? prompt : 0;
    int completionTokens = completion != null ? completion : 0;
    int totalTokens = total != null ? total : promptTokens + completionTokens;
    return new TokenUsage(promptTokens, completionTokens, totalTokens);
  }

}
