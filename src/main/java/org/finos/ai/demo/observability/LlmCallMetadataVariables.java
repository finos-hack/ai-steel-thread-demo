package org.finos.ai.demo.observability;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Process variables for sanitized LLM call metadata (mi-4). */
public final class LlmCallMetadataVariables {

  public static final String MODEL = "llmLastModel";
  public static final String PROVIDER = "llmLastProvider";
  public static final String VERSION = "llmLastModelVersion";
  public static final String DURATION_MS = "llmLastDurationMs";
  public static final String STATUS = "llmLastStatus";
  public static final String CONTEXT_KEYS = "llmLastContextKeys";
  public static final String ERROR_TYPE = "llmLastErrorType";

  private static final String ACTIVITY_SEPARATOR = "__";

  private LlmCallMetadataVariables() {}

  public static boolean isMetadataVariable(String name) {
    if (name == null || name.isBlank()) {
      return false;
    }
    return isBase(name, MODEL)
        || isBase(name, PROVIDER)
        || isBase(name, VERSION)
        || isBase(name, DURATION_MS)
        || isBase(name, STATUS)
        || isBase(name, CONTEXT_KEYS)
        || isBase(name, ERROR_TYPE);
  }

  public static String scopedName(String baseName, String activityId) {
    return baseName + ACTIVITY_SEPARATOR + activityId;
  }

  public static Map<String, Object> toScopedVariables(
      String activityId,
      String model,
      String provider,
      String version,
      long durationMs,
      String status,
      List<String> contextKeys,
      String errorType) {
    Map<String, Object> variables = new HashMap<>();
    if (activityId == null || activityId.isBlank()) {
      return variables;
    }
    variables.put(scopedName(MODEL, activityId), model);
    variables.put(scopedName(PROVIDER, activityId), provider);
    variables.put(scopedName(VERSION, activityId), version);
    variables.put(scopedName(DURATION_MS, activityId), durationMs);
    variables.put(scopedName(STATUS, activityId), status);
    if (contextKeys != null && !contextKeys.isEmpty()) {
      variables.put(
          scopedName(CONTEXT_KEYS, activityId),
          contextKeys.stream().sorted().collect(Collectors.joining(",")));
    }
    if (errorType != null && !errorType.isBlank()) {
      variables.put(scopedName(ERROR_TYPE, activityId), errorType);
    }
    return variables;
  }

  private static boolean isBase(String name, String base) {
    return name.equals(base) || name.startsWith(base + ACTIVITY_SEPARATOR);
  }
}
