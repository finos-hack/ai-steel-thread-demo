package org.finos.ai.demo;

import java.util.List;

/** Per-instance compliance evidence pack (not a re-run of the mi-5 SAT suite). */
public record LoanApprovalComplianceReport(
    String processInstanceId,
    String businessKey,
    String state,
    String generatedAt,
    String overallStatus,
    ControlPosture controls,
    DecisionPath decisionPath,
    List<CheckFinding> checks,
    List<LlmCallEvidence> llmCalls,
    SatSnapshot sat) {

  public record ControlPosture(
      boolean mi1Enabled,
      boolean mi1RedactStatusApi,
      boolean mi4Enabled,
      boolean mi5Enabled,
      boolean mi11Enabled,
      /** ISO-8601 timestamp when posture was frozen on the instance; null if live fallback. */
      String snapshottedAt,
      boolean instanceSnapshot) {}

  public record DecisionPath(
      Integer requestedAmount,
      String loanPurpose,
      Integer creditScore,
      String creditRiskBand,
      Double aiRiskScore,
      String approvalDecision,
      String loanOfficerDecision,
      Boolean agreesWithAi,
      Boolean invalidDocument,
      Boolean fraudulent) {}

  public record CheckFinding(
      String id, String control, String title, String status, String detail) {}

  public record LlmCallEvidence(
      String activityId,
      String activityName,
      String model,
      String provider,
      String modelVersion,
      Long durationMs,
      String status,
      String contextKeys,
      Integer totalTokens,
      Double estimatedCostUsd) {}

  public record SatSnapshot(
      boolean suiteEnabled,
      Boolean lastAccepted,
      Double lastPassRate,
      String lastRanAt,
      String note) {}
}
