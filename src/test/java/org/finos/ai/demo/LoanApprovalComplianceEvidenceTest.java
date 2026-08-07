package org.finos.ai.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.finos.ai.demo.feedback.Mi11FeedbackVariables;
import org.junit.jupiter.api.Test;

class LoanApprovalComplianceEvidenceTest {

  @Test
  void build_passWhenControlsOnAndDecisionPresent() {
    ProcessInstanceStatus status =
        status(
            "COMPLETED",
            List.of(
                activity(
                    "AgentTask_CreditRisk",
                    "Credit risk agent",
                    new ProcessInstanceStatus.TokenUsageSummary(
                        10, 5, 15, "gpt-test", "openai", "v1", 120L, "ok"),
                    List.of(),
                    List.of(
                        new ProcessInstanceStatus.VariableEntry(
                            "creditScore", "Integer", 720)))));

    LoanApprovalComplianceReport report =
        LoanApprovalComplianceEvidence.build(
            status,
            LoanApprovalComplianceEvidence.vars(
                "requestedAmount",
                25_000,
                "loanPurpose",
                "Car",
                "creditScore",
                720,
                "creditRiskBand",
                "MEDIUM",
                "aiRiskScore",
                0.2,
                "approvalDecision",
                "MANUAL_REVIEW",
                Mi11FeedbackVariables.LOAN_OFFICER_DECISION,
                "APPROVE",
                Mi11FeedbackVariables.AGREES_WITH_AI,
                true,
                "isInvalid",
                false,
                "isFraudulent",
                false),
            new LoanApprovalComplianceReport.ControlPosture(
                true, true, true, true, true, "2026-08-02T12:00:00Z", true),
            new LoanApprovalComplianceReport.SatSnapshot(
                true, true, 1.0, "2026-08-01T00:00:00Z", "Last SAT accepted"));

    assertThat(report.overallStatus()).isEqualTo("PASS");
    assertThat(report.decisionPath().approvalDecision()).isEqualTo("MANUAL_REVIEW");
    assertThat(report.decisionPath().loanOfficerDecision()).isEqualTo("APPROVE");
    assertThat(report.checks())
        .extracting(LoanApprovalComplianceReport.CheckFinding::status)
        .doesNotContain("fail", "warn", "pending");
    assertThat(report.llmCalls()).hasSize(1);
    assertThat(report.sat().lastAccepted()).isTrue();
  }

  @Test
  void build_pendingMi11WhileManualReviewTaskOpen() {
    ProcessInstanceStatus status = status("ACTIVE", List.of());

    LoanApprovalComplianceReport report =
        LoanApprovalComplianceEvidence.build(
            status,
            LoanApprovalComplianceEvidence.vars(
                "requestedAmount",
                25_000,
                "approvalDecision",
                "MANUAL_REVIEW",
                "isInvalid",
                false,
                "isFraudulent",
                false),
            new LoanApprovalComplianceReport.ControlPosture(
                true, true, true, true, true, null, false),
            new LoanApprovalComplianceReport.SatSnapshot(
                true, null, null, null, "Suite enabled; no run captured yet"));

    assertThat(report.checks())
        .anyMatch(
            check ->
                "mi11-feedback-recorded".equals(check.id()) && "pending".equals(check.status()));
  }

  @Test
  void build_failsOnUnredactedPiiWhenMi1On() {
    ProcessInstanceStatus status =
        status(
            "COMPLETED",
            List.of(
                activity(
                    "ServiceTask_Extract",
                    "Extract document",
                    null,
                    List.of(),
                    List.of(
                        new ProcessInstanceStatus.VariableEntry(
                            "idNumber", "String", "AB123456")))));

    LoanApprovalComplianceReport report =
        LoanApprovalComplianceEvidence.build(
            status,
            Map.of(
                "requestedAmount",
                25_000,
                "isInvalid",
                false,
                "approvalDecision",
                "REJECT"),
            new LoanApprovalComplianceReport.ControlPosture(
                true, true, true, true, true, null, false),
            new LoanApprovalComplianceReport.SatSnapshot(
                true, null, null, null, "Suite enabled; no run captured yet"));

    assertThat(report.overallStatus()).isEqualTo("FAIL");
    assertThat(report.checks())
        .anyMatch(
            check ->
                "mi1-no-plaintext-pii".equals(check.id()) && "fail".equals(check.status()));
  }

  @Test
  void build_inProgressWhenActiveWithoutDecision() {
    ProcessInstanceStatus status = status("ACTIVE", List.of());

    LoanApprovalComplianceReport report =
        LoanApprovalComplianceEvidence.build(
            status,
            Map.of("requestedAmount", 40_000),
            new LoanApprovalComplianceReport.ControlPosture(
                true, true, true, true, true, null, false),
            new LoanApprovalComplianceReport.SatSnapshot(
                true, null, null, null, "Suite enabled; no run captured yet"));

    assertThat(report.overallStatus()).isEqualTo("IN_PROGRESS");
    assertThat(report.checks())
        .anyMatch(
            check -> "approval-decision".equals(check.id()) && "pending".equals(check.status()));
  }

  private static ProcessInstanceStatus status(
      String state, List<ProcessInstanceStatus.ActivityStatusEntry> activities) {
    return new ProcessInstanceStatus(
        "pi-1",
        "loan-1",
        state,
        new Date(),
        "COMPLETED".equals(state) ? new Date() : null,
        List.of(),
        activities,
        new ProcessInstanceStatus.ControlPosture(true, true, true, true, true));
  }

  private static ProcessInstanceStatus.ActivityStatusEntry activity(
      String activityId,
      String name,
      ProcessInstanceStatus.TokenUsageSummary tokenUsage,
      List<ProcessInstanceStatus.VariableEntry> inputs,
      List<ProcessInstanceStatus.VariableEntry> outputs) {
    return new ProcessInstanceStatus.ActivityStatusEntry(
        activityId + "-inst",
        activityId,
        name,
        "serviceTask",
        "completed",
        new Date(),
        new Date(),
        10L,
        tokenUsage,
        inputs,
        outputs);
  }
}
