package org.finos.ai.demo.observability;

import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

public final class SpringAiTokenUsageExtractor {

  private SpringAiTokenUsageExtractor() {}

  public static TokenUsage from(ChatResponse response) {
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
    int cacheRead = toInt(usage.getCacheReadInputTokens());
    int cacheWrite = toInt(usage.getCacheWriteInputTokens());
    if (prompt == null && completion == null && total == null && cacheRead == 0 && cacheWrite == 0) {
      return null;
    }
    int promptTokens = prompt != null ? prompt : 0;
    int completionTokens = completion != null ? completion : 0;
    int totalTokens = total != null ? total : promptTokens + completionTokens;
    return new TokenUsage(
        promptTokens, completionTokens, totalTokens, cacheRead, cacheWrite);
  }

  private static int toInt(Long value) {
    if (value == null || value <= 0L) {
      return 0;
    }
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
  }
}
