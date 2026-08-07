package org.finos.ai.demo.observability;

import java.util.HashMap;
import java.util.Map;

import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;

/** Process-variable names used to surface LLM token usage in the demo execution log. */
public final class LlmTokenUsageVariables {

  public static final String PROMPT_TOKENS = "llmPromptTokens";
  public static final String COMPLETION_TOKENS = "llmCompletionTokens";
  public static final String TOTAL_TOKENS = "llmTotalTokens";
  public static final String ESTIMATED_COST_USD = "llmEstimatedCostUsd";
  private static final String ACTIVITY_SEPARATOR = "__";

  private LlmTokenUsageVariables() {}

  public static boolean isTokenUsageVariable(String name) {
    if (name == null || name.isBlank()) {
      return false;
    }
    return name.equals(PROMPT_TOKENS)
        || name.equals(COMPLETION_TOKENS)
        || name.equals(TOTAL_TOKENS)
        || name.equals(ESTIMATED_COST_USD)
        || name.startsWith(PROMPT_TOKENS + ACTIVITY_SEPARATOR)
        || name.startsWith(COMPLETION_TOKENS + ACTIVITY_SEPARATOR)
        || name.startsWith(TOTAL_TOKENS + ACTIVITY_SEPARATOR)
        || name.startsWith(ESTIMATED_COST_USD + ACTIVITY_SEPARATOR);
  }

  public static String scopedName(String baseName, String activityId) {
    return baseName + ACTIVITY_SEPARATOR + activityId;
  }

  public static Map<String, Object> toVariables(TokenUsage usage) {
    Map<String, Object> variables = new HashMap<>();
    if (usage == null) {
      return variables;
    }
    variables.put(PROMPT_TOKENS, usage.promptTokens());
    variables.put(COMPLETION_TOKENS, usage.completionTokens());
    variables.put(TOTAL_TOKENS, usage.totalTokens());
    return variables;
  }

  public static Map<String, Object> toScopedVariables(String activityId, TokenUsage usage) {
    Map<String, Object> variables = new HashMap<>();
    if (usage == null || activityId == null || activityId.isBlank()) {
      return variables;
    }
    variables.put(scopedName(PROMPT_TOKENS, activityId), usage.promptTokens());
    variables.put(scopedName(COMPLETION_TOKENS, activityId), usage.completionTokens());
    variables.put(scopedName(TOTAL_TOKENS, activityId), usage.totalTokens());
    return variables;
  }

  public static Map<String, Object> toScopedCostVariables(String activityId, double estimatedCostUsd) {
    Map<String, Object> variables = new HashMap<>();
    if (activityId == null || activityId.isBlank()) {
      return variables;
    }
    variables.put(scopedName(ESTIMATED_COST_USD, activityId), estimatedCostUsd);
    return variables;
  }

  public static Double costFromVariable(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Double.parseDouble(text.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  public static double mergeCost(Double existing, double delta) {
    return (existing == null ? 0.0 : existing) + delta;
  }

  public static TokenUsage merge(TokenUsage existing, TokenUsage delta) {
    if (delta == null) {
      return existing;
    }
    if (existing == null) {
      return delta;
    }
    return new TokenUsage(
        existing.promptTokens() + delta.promptTokens(),
        existing.completionTokens() + delta.completionTokens(),
        existing.totalTokens() + delta.totalTokens());
  }

  public static TokenUsage fromVariables(Object prompt, Object completion, Object total) {
    Integer promptTokens = asInteger(prompt);
    Integer completionTokens = asInteger(completion);
    Integer totalTokens = asInteger(total);
    if (promptTokens == null && completionTokens == null && totalTokens == null) {
      return null;
    }
    int promptValue = promptTokens == null ? 0 : promptTokens;
    int completionValue = completionTokens == null ? 0 : completionTokens;
    int totalValue = totalTokens == null ? promptValue + completionValue : totalTokens;
    return new TokenUsage(promptValue, completionValue, totalValue);
  }

  private static Integer asInteger(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Integer.parseInt(text.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
