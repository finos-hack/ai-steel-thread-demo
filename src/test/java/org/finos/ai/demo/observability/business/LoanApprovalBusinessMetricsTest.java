package org.finos.ai.demo.observability.business;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class LoanApprovalBusinessMetricsTest {

  private SimpleMeterRegistry registry;
  private LoanApprovalBusinessMetrics metrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    LoanApprovalBusinessMetricsProperties properties = new LoanApprovalBusinessMetricsProperties();
    properties.setWorkflowNamePrefix("LoanApproval");
    metrics = new LoanApprovalBusinessMetrics(registry, properties);
  }

  @Test
  void recordsProcessLifecycleAndOutcome() {
    metrics.recordProcessStarted("LoanApprovalProcess_AI:1:abc");
    metrics.recordProcessCompleted(
        "LoanApprovalProcess_AI:1:abc",
        Map.of(
            LoanApprovalProcessVariables.APPROVAL_DECISION,
            "AUTO_APPROVE"));

    assertThat(registry.find("fluxnova.loan.processes.started").counter().count()).isEqualTo(1);
    assertThat(registry.find("fluxnova.loan.processes.completed").counter().count()).isEqualTo(1);
    assertThat(
            registry.find("fluxnova.loan.process.outcomes")
                .tag("outcome", LoanApprovalBusinessMetrics.OUTCOME_AUTO_APPROVE)
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry.find("fluxnova.loan.process.outcomes")
                .tag("outcome", LoanApprovalBusinessMetrics.OUTCOME_SUCCESSFUL)
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry.find("fluxnova.loan.approval.decisions")
                .tag("decision", "AUTO_APPROVE")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void recordsInvalidDocumentOutcome() {
    metrics.recordProcessCompleted(
        "LoanApprovalProcess_AI:1:abc",
        Map.of(LoanApprovalProcessVariables.IS_INVALID, true));

    assertThat(
            registry.find("fluxnova.loan.invalid.documents").counter().count())
        .isEqualTo(1);
    assertThat(
            registry.find("fluxnova.loan.process.outcomes")
                .tag("outcome", LoanApprovalBusinessMetrics.OUTCOME_INVALID_DOCUMENT)
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void recordsFraudAndRejectOutcomes() {
    metrics.recordProcessCompleted(
        "LoanApprovalProcess_AI:1:abc",
        Map.of(
            LoanApprovalProcessVariables.IS_FRAUDULENT,
            true,
            LoanApprovalProcessVariables.APPROVAL_DECISION,
            "REJECT"));

    assertThat(registry.find("fluxnova.loan.fraud.detected").counter().count()).isEqualTo(1);
    assertThat(
            registry.find("fluxnova.loan.process.outcomes")
                .tag("outcome", LoanApprovalBusinessMetrics.OUTCOME_FRAUD_DETECTED)
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void recordsLoanOfficerDecision() {
    metrics.recordProcessCompleted(
        "LoanApprovalProcess_AI:1:abc",
        Map.of(
            LoanApprovalProcessVariables.APPROVAL_DECISION,
            "MANUAL_REVIEW",
            LoanApprovalProcessVariables.LOAN_OFFICER_DECISION,
            "APPROVE"));

    assertThat(
            registry.find("fluxnova.loan.loan_officer.decisions")
                .tag("decision", "APPROVE")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void recordsCreditScoresWhenPresentAtProcessCompletion() {
    metrics.recordProcessCompleted(
        "LoanApprovalProcess_AI:1:abc",
        Map.of(
            LoanApprovalProcessVariables.CREDIT_SCORE,
            710,
            LoanApprovalProcessVariables.AI_RISK_SCORE,
            0.25,
            LoanApprovalProcessVariables.APPROVAL_DECISION,
            "AUTO_APPROVE"));

    assertThat(registry.find("fluxnova.loan.credit.score").summary().totalAmount()).isEqualTo(710);
    assertThat(registry.find("fluxnova.loan.ai.risk.score").summary().totalAmount())
        .isEqualTo(0.25);
  }

  @Test
  void classifyOutcome_mapsBusinessStates() {
    assertThat(LoanApprovalBusinessMetrics.classifyOutcome(true, false, null))
        .isEqualTo(LoanApprovalBusinessMetrics.OUTCOME_INVALID_DOCUMENT);
    assertThat(LoanApprovalBusinessMetrics.classifyOutcome(false, true, "REJECT"))
        .isEqualTo(LoanApprovalBusinessMetrics.OUTCOME_FRAUD_DETECTED);
    assertThat(LoanApprovalBusinessMetrics.classifyOutcome(false, false, null))
        .isEqualTo(LoanApprovalBusinessMetrics.OUTCOME_INCOMPLETE);
    assertThat(LoanApprovalBusinessMetrics.classifyOutcome(false, false, "MANUAL_REVIEW"))
        .isEqualTo(LoanApprovalBusinessMetrics.OUTCOME_MANUAL_REVIEW);
  }

}
