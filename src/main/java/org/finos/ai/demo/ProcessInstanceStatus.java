package org.finos.ai.demo;

import java.util.Date;
import java.util.List;

public record ProcessInstanceStatus(
    String processInstanceId,
    String businessKey,
    String state,
    Date startTime,
    Date endTime,
    List<String> currentActivityNames,
    List<ActivityStatusEntry> activities,
    /** mi-1 / mi-4 posture for this instance (snapshot when available). */
    ControlPosture controls) {

  public record ControlPosture(
      boolean mi1Enabled,
      boolean mi1RedactStatusApi,
      boolean mi4Enabled,
      boolean mi11Enabled,
      boolean instanceSnapshot) {}

  public record ActivityStatusEntry(
      String id,
      String activityId,
      String activityName,
      String activityType,
      String status,
      Date startTime,
      Date endTime,
      Long durationInMillis,
      TokenUsageSummary tokenUsage,
      List<VariableEntry> inputs,
      List<VariableEntry> outputs) {}

  public record TokenUsageSummary(
      int promptTokens,
      int completionTokens,
      int totalTokens,
      String model,
      String provider,
      String modelVersion,
      Long durationMs,
      String status,
      Double estimatedCostUsd) {

    public TokenUsageSummary(int promptTokens, int completionTokens, int totalTokens) {
      this(promptTokens, completionTokens, totalTokens, null, null, null, null, null, null);
    }

    public TokenUsageSummary(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        String model,
        String provider,
        String modelVersion,
        Long durationMs,
        String status) {
      this(
          promptTokens,
          completionTokens,
          totalTokens,
          model,
          provider,
          modelVersion,
          durationMs,
          status,
          null);
    }
  }

  public record VariableEntry(String name, String type, Object value) {}
}
