package org.finos.ai.demo.observability.business;

import java.util.Map;

import org.finos.ai.demo.observability.LlmTokenUsageMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;

public final class LoanApprovalBusinessMetrics {

  static final String OUTCOME_SUCCESSFUL = "successful";
  static final String OUTCOME_INVALID_DOCUMENT = "invalid_document";
  static final String OUTCOME_FRAUD_DETECTED = "fraud_detected";
  static final String OUTCOME_INCOMPLETE = "incomplete";
  static final String OUTCOME_AUTO_APPROVE = "auto_approve";
  static final String OUTCOME_REJECT = "reject";
  static final String OUTCOME_MANUAL_REVIEW = "manual_review";

  private static final Logger LOG = LoggerFactory.getLogger(LoanApprovalBusinessMetrics.class);

  private static final String PROCESSES_STARTED = "fluxnova.loan.processes.started";
  private static final String PROCESSES_COMPLETED = "fluxnova.loan.processes.completed";
  private static final String PROCESS_OUTCOMES = "fluxnova.loan.process.outcomes";
  private static final String INVALID_DOCUMENTS = "fluxnova.loan.invalid.documents";
  private static final String FRAUD_DETECTED = "fluxnova.loan.fraud.detected";
  private static final String APPROVAL_DECISIONS = "fluxnova.loan.approval.decisions";
  private static final String LOAN_OFFICER_DECISIONS = "fluxnova.loan.loan_officer.decisions";
  private static final String CREDIT_SCORE = "fluxnova.loan.credit.score";
  private static final String AI_RISK_SCORE = "fluxnova.loan.ai.risk.score";

  private final MeterRegistry registry;
  private final String workflowNamePrefix;

  public LoanApprovalBusinessMetrics(
      MeterRegistry registry, LoanApprovalBusinessMetricsProperties properties) {
    this.registry = registry;
    this.workflowNamePrefix = properties.getWorkflowNamePrefix();
  }

  public void recordProcessStarted(String processDefinitionId) {
    String workflow = workflowKey(processDefinitionId);
    if (!tracksWorkflow(workflow)) {
      return;
    }
    registry.counter(PROCESSES_STARTED, "workflow", workflow).increment();
    LOG.debug("Loan application started workflow={}", workflow);
  }

  public void recordProcessCompleted(String processDefinitionId, Map<String, Object> variables) {
    String workflow = workflowKey(processDefinitionId);
    if (!tracksWorkflow(workflow)) {
      return;
    }

    registry.counter(PROCESSES_COMPLETED, "workflow", workflow).increment();

    boolean invalidDocument = isTrue(variables.get(LoanApprovalProcessVariables.IS_INVALID));
    boolean fraudDetected = isTrue(variables.get(LoanApprovalProcessVariables.IS_FRAUDULENT));
    String approvalDecision = asString(variables.get(LoanApprovalProcessVariables.APPROVAL_DECISION));
    String loanOfficerDecision =
        asString(variables.get(LoanApprovalProcessVariables.LOAN_OFFICER_DECISION));

    if (invalidDocument) {
      registry.counter(INVALID_DOCUMENTS, "workflow", workflow).increment();
    }
    if (fraudDetected) {
      registry.counter(FRAUD_DETECTED, "workflow", workflow).increment();
    }
    if (approvalDecision != null) {
      registry.counter(APPROVAL_DECISIONS, "workflow", workflow, "decision", approvalDecision)
          .increment();
    }
    if (loanOfficerDecision != null) {
      registry.counter(LOAN_OFFICER_DECISIONS, "workflow", workflow, "decision", loanOfficerDecision)
          .increment();
    }

    recordCreditAssessment(workflow, variables);

    String outcome = classifyOutcome(invalidDocument, fraudDetected, approvalDecision);
    registry.counter(PROCESS_OUTCOMES, "workflow", workflow, "outcome", outcome).increment();

    if (classifySuccessfulOutcome(invalidDocument, fraudDetected, approvalDecision) != null) {
      registry.counter(PROCESS_OUTCOMES, "workflow", workflow, "outcome", OUTCOME_SUCCESSFUL)
          .increment();
    }

    LOG.info(
        "Loan application completed workflow={} outcome={} invalidDocument={} fraudDetected={} "
            + "approvalDecision={} loanOfficerDecision={} creditScore={} aiRiskScore={}",
        workflow,
        outcome,
        invalidDocument,
        fraudDetected,
        approvalDecision,
        loanOfficerDecision,
        variables.get(LoanApprovalProcessVariables.CREDIT_SCORE),
        variables.get(LoanApprovalProcessVariables.AI_RISK_SCORE));
  }

  static String classifyOutcome(
      boolean invalidDocument, boolean fraudDetected, String approvalDecision) {
    if (invalidDocument) {
      return OUTCOME_INVALID_DOCUMENT;
    }
    if (fraudDetected) {
      return OUTCOME_FRAUD_DETECTED;
    }
    if (approvalDecision == null) {
      return OUTCOME_INCOMPLETE;
    }
    return switch (approvalDecision) {
      case "AUTO_APPROVE" -> OUTCOME_AUTO_APPROVE;
      case "REJECT" -> OUTCOME_REJECT;
      case "MANUAL_REVIEW" -> OUTCOME_MANUAL_REVIEW;
      default -> OUTCOME_INCOMPLETE;
    };
  }

  static String classifySuccessfulOutcome(
      boolean invalidDocument, boolean fraudDetected, String approvalDecision) {
    if (invalidDocument || fraudDetected) {
      return null;
    }
    if (approvalDecision == null || "REJECT".equals(approvalDecision)) {
      return null;
    }
    return OUTCOME_SUCCESSFUL;
  }

  private void recordCreditAssessment(String workflow, Map<String, Object> variables) {
    Number creditScore = asNumber(variables.get(LoanApprovalProcessVariables.CREDIT_SCORE));
    if (creditScore != null) {
      registry.summary(CREDIT_SCORE, "workflow", workflow).record(creditScore.doubleValue());
      LOG.debug("Recorded credit score workflow={} creditScore={}", workflow, creditScore);
    }
    Number aiRiskScore = asNumber(variables.get(LoanApprovalProcessVariables.AI_RISK_SCORE));
    if (aiRiskScore != null) {
      registry.summary(AI_RISK_SCORE, "workflow", workflow).record(aiRiskScore.doubleValue());
      LOG.debug("Recorded AI risk score workflow={} aiRiskScore={}", workflow, aiRiskScore);
    }
  }

  private boolean tracksWorkflow(String workflow) {
    return workflowNamePrefix == null
        || workflowNamePrefix.isBlank()
        || workflow.contains(workflowNamePrefix);
  }

  private static String workflowKey(String processDefinitionId) {
    return LlmTokenUsageMetrics.workflowKey(processDefinitionId);
  }

  private static boolean isTrue(Object value) {
    return Boolean.TRUE.equals(value);
  }

  private static String asString(Object value) {
    if (value == null) {
      return null;
    }
    String text = value.toString();
    return text.isBlank() ? null : text;
  }

  private static Number asNumber(Object value) {
    return value instanceof Number number ? number : null;
  }

}
