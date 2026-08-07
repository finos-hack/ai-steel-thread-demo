package org.finos.ai.demo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.finos.ai.demo.acceptance.Mi5AcceptanceReport;
import org.finos.ai.demo.observability.LlmCallMetadataVariables;

import org.finos.ai.demo.feedback.Mi11FeedbackVariables;

/**
 * Assembles a read-only compliance evidence report from instance status + process variables.
 *
 * <p>Does not re-run the mi-5 SAT suite; it only references the last suite result when available.
 */
final class LoanApprovalComplianceEvidence {

  private static final int MIN_AMOUNT = 10_000;
  private static final int MAX_AMOUNT = 100_000;
  private static final String REDACTED = "<redacted:mi-1>";

  private LoanApprovalComplianceEvidence() {}

  static LoanApprovalComplianceReport build(
      ProcessInstanceStatus status,
      Map<String, Object> processVariables,
      LoanApprovalComplianceReport.ControlPosture controls,
      LoanApprovalComplianceReport.SatSnapshot sat) {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(controls, "controls");
    Objects.requireNonNull(sat, "sat");
    Map<String, Object> vars = processVariables == null ? Map.of() : processVariables;

    LoanApprovalComplianceReport.DecisionPath decisionPath = decisionPath(vars);
    List<LoanApprovalComplianceReport.LlmCallEvidence> llmCalls = llmCalls(status);
    Mi5AcceptanceReport lastSat = ComplianceControlSnapshot.toAcceptanceReportForChecks(sat);
    List<LoanApprovalComplianceReport.CheckFinding> checks =
        checks(status, vars, decisionPath, controls, llmCalls, lastSat);
    String overall = overallStatus(status.state(), checks);

    return new LoanApprovalComplianceReport(
        status.processInstanceId(),
        status.businessKey(),
        status.state(),
        Instant.now().toString(),
        overall,
        controls,
        decisionPath,
        List.copyOf(checks),
        List.copyOf(llmCalls),
        sat);
  }

  private static LoanApprovalComplianceReport.DecisionPath decisionPath(Map<String, Object> vars) {
    return new LoanApprovalComplianceReport.DecisionPath(
        asInteger(vars.get("requestedAmount")),
        asString(vars.get("loanPurpose")),
        asInteger(vars.get("creditScore")),
        asString(vars.get("creditRiskBand")),
        asDouble(vars.get("aiRiskScore")),
        asString(vars.get("approvalDecision")),
        asString(vars.get(Mi11FeedbackVariables.LOAN_OFFICER_DECISION)),
        asBoolean(vars.get(Mi11FeedbackVariables.AGREES_WITH_AI)),
        asBoolean(vars.get("isInvalid")),
        asBoolean(vars.get("isFraudulent")));
  }

  private static List<LoanApprovalComplianceReport.LlmCallEvidence> llmCalls(
      ProcessInstanceStatus status) {
    List<LoanApprovalComplianceReport.LlmCallEvidence> calls = new ArrayList<>();
    for (ProcessInstanceStatus.ActivityStatusEntry activity : status.activities()) {
      ProcessInstanceStatus.TokenUsageSummary usage = activity.tokenUsage();
      if (usage == null) {
        continue;
      }
      String contextKeys = findVariable(activity, LlmCallMetadataVariables.CONTEXT_KEYS);
      // Context keys may only exist as process-scoped metadata stripped from I/O lists; include
      // whatever survived on outputs if present under a scoped name.
      if (contextKeys == null) {
        contextKeys =
            findVariable(
                activity, LlmCallMetadataVariables.scopedName(
                    LlmCallMetadataVariables.CONTEXT_KEYS, activity.activityId()));
      }
      calls.add(
          new LoanApprovalComplianceReport.LlmCallEvidence(
              activity.activityId(),
              activity.activityName(),
              usage.model(),
              usage.provider(),
              usage.modelVersion(),
              usage.durationMs(),
              usage.status(),
              contextKeys,
              usage.totalTokens(),
              usage.estimatedCostUsd()));
    }
    return calls;
  }

  private static String findVariable(
      ProcessInstanceStatus.ActivityStatusEntry activity, String name) {
    for (ProcessInstanceStatus.VariableEntry entry : activity.outputs()) {
      if (name.equals(entry.name()) && entry.value() != null) {
        return String.valueOf(entry.value());
      }
    }
    for (ProcessInstanceStatus.VariableEntry entry : activity.inputs()) {
      if (name.equals(entry.name()) && entry.value() != null) {
        return String.valueOf(entry.value());
      }
    }
    return null;
  }

  private static List<LoanApprovalComplianceReport.CheckFinding> checks(
      ProcessInstanceStatus status,
      Map<String, Object> vars,
      LoanApprovalComplianceReport.DecisionPath decisionPath,
      LoanApprovalComplianceReport.ControlPosture controls,
      List<LoanApprovalComplianceReport.LlmCallEvidence> llmCalls,
      Mi5AcceptanceReport lastSat) {
    List<LoanApprovalComplianceReport.CheckFinding> findings = new ArrayList<>();

    findings.add(
        finding(
            "mi1-enforced",
            "mi-1",
            "AI data leakage prevention enforced",
            controls.mi1Enabled() ? "pass" : "warn",
            controls.mi1Enabled()
                ? "mi-1 runtime allowlisting is on for this environment"
                : "mi-1 is off — hosted-model egress is not allowlisted"));

    findings.add(
        finding(
            "mi1-status-redaction",
            "mi-1",
            "Status API redacts sensitive fields",
            controls.mi1Enabled() && controls.mi1RedactStatusApi() ? "pass" : "warn",
            controls.mi1Enabled() && controls.mi1RedactStatusApi()
                ? "Forbidden PII / conversation traces are redacted in the execution log"
                : "Status redaction is not active"));

    boolean leaked = containsUnredactedSensitive(status);
    findings.add(
        finding(
            "mi1-no-plaintext-pii",
            "mi-1",
            "No plaintext PII in execution evidence",
            !controls.mi1Enabled()
                ? "warn"
                : leaked ? "fail" : "pass",
            !controls.mi1Enabled()
                ? "Cannot assert redaction while mi-1 is off"
                : leaked
                    ? "Found unredacted sensitive field values in activity variables"
                    : "Sensitive fields are absent or redacted in the status view"));

    findings.add(
        finding(
            "mi4-enforced",
            "mi-4",
            "AI system observability enforced",
            controls.mi4Enabled() ? "pass" : "warn",
            controls.mi4Enabled()
                ? "mi-4 metrics and sanitized audit are enabled"
                : "mi-4 is off — LLM latency/error audit may be incomplete"));

    boolean processEnded = isTerminal(status.state());
    if (llmCalls.isEmpty()) {
      findings.add(
          finding(
              "mi4-llm-evidence",
              "mi-4",
              "LLM call evidence recorded",
              processEnded ? "warn" : "pending",
              processEnded
                  ? "No LLM token/call metadata found on completed activities"
                  : "Waiting for agent activities to emit call metadata"));
    } else {
      boolean missingModel =
          llmCalls.stream().anyMatch(call -> call.model() == null || call.model().isBlank());
      findings.add(
          finding(
              "mi4-llm-evidence",
              "mi-4",
              "LLM call evidence recorded",
              missingModel ? "warn" : "pass",
              missingModel
                  ? "Some LLM activities lack model/provider metadata"
                  : llmCalls.size() + " LLM call(s) recorded with token/latency metadata"));
    }

    Integer amount = decisionPath.requestedAmount();
    if (amount == null) {
      findings.add(
          finding(
              "amount-bounds",
              "policy",
              "Requested amount within demo bounds",
              processEnded ? "warn" : "pending",
              "requestedAmount not yet available"));
    } else {
      boolean inBounds = amount >= MIN_AMOUNT && amount <= MAX_AMOUNT;
      findings.add(
          finding(
              "amount-bounds",
              "policy",
              "Requested amount within demo bounds",
              inBounds ? "pass" : "fail",
              "requestedAmount=" + amount + " (allowed " + MIN_AMOUNT + "–" + MAX_AMOUNT + ")"));
    }

    Boolean invalid = decisionPath.invalidDocument();
    if (invalid == null) {
      findings.add(
          finding(
              "document-validity",
              "policy",
              "Identity document extraction validity",
              processEnded ? "warn" : "pending",
              "isInvalid not yet set"));
    } else if (invalid) {
      findings.add(
          finding(
              "document-validity",
              "policy",
              "Identity document extraction validity",
              "fail",
              "Document marked invalid (isInvalid=true)"));
    } else {
      findings.add(
          finding(
              "document-validity",
              "policy",
              "Identity document extraction validity",
              "pass",
              "Document extraction accepted (isInvalid=false)"));
    }

    String decision = decisionPath.approvalDecision();
    if (decision == null || decision.isBlank()) {
      findings.add(
          finding(
              "approval-decision",
              "policy",
              "Approval decision recorded",
              processEnded ? "warn" : "pending",
              "approvalDecision not yet available"));
    } else {
      findings.add(
          finding(
              "approval-decision",
              "policy",
              "Approval decision recorded",
              "pass",
              "approvalDecision="
                  + decision
                  + (decisionPath.creditRiskBand() != null
                      ? ", creditRiskBand=" + decisionPath.creditRiskBand()
                      : "")
                  + (decisionPath.creditScore() != null
                      ? ", creditScore=" + decisionPath.creditScore()
                      : "")));
    }

    if (Boolean.TRUE.equals(decisionPath.fraudulent())) {
      findings.add(
          finding(
              "fraud-flag",
              "policy",
              "Fraud screening outcome",
              "fail",
              "isFraudulent=true"));
    } else if (decisionPath.fraudulent() != null) {
      findings.add(
          finding(
              "fraud-flag",
              "policy",
              "Fraud screening outcome",
              "pass",
              "isFraudulent=false"));
    }

    if (!controls.mi5Enabled()) {
      findings.add(
          finding(
              "mi5-sat",
              "mi-5",
              "System acceptance suite posture",
              "warn",
              "mi-5 suite is disabled — system-level acceptance not asserted"));
    } else if (lastSat == null) {
      findings.add(
          finding(
              "mi5-sat",
              "mi-5",
              "System acceptance suite posture",
              "info",
              "No SAT run in this session yet (instance evidence still collected)"));
    } else if (lastSat.accepted()) {
      findings.add(
          finding(
              "mi5-sat",
              "mi-5",
              "System acceptance suite posture",
              "pass",
              "Last SAT accepted at "
                  + Math.round(lastSat.passRate() * 100)
                  + "% (threshold "
                  + Math.round(lastSat.passRateThreshold() * 100)
                  + "%)"));
    } else {
      findings.add(
          finding(
              "mi5-sat",
              "mi-5",
              "System acceptance suite posture",
              "warn",
              "Last SAT was not accepted — system criteria may be drifting"));
    }

    findings.add(
        finding(
            "mi11-enforced",
            "mi-11",
            "Human feedback loop enforced",
            controls.mi11Enabled() ? "pass" : "warn",
            controls.mi11Enabled()
                ? "mi-11 human feedback is on for this instance"
                : "mi-11 is off — structured loan-officer feedback is not asserted"));

    boolean manualReview =
        decision != null && "MANUAL_REVIEW".equalsIgnoreCase(decision.trim());
    String loanOfficerDecision = decisionPath.loanOfficerDecision();
    boolean hasLoanOfficerDecision =
        loanOfficerDecision != null && !loanOfficerDecision.isBlank();
    if (!controls.mi11Enabled()) {
      findings.add(
          finding(
              "mi11-feedback-recorded",
              "mi-11",
              "Loan officer feedback recorded",
              "info",
              "mi-11 off — feedback recording not asserted"));
    } else if (!manualReview) {
      findings.add(
          finding(
              "mi11-feedback-recorded",
              "mi-11",
              "Loan officer feedback recorded",
              "info",
              decision == null || decision.isBlank()
                  ? "approvalDecision not yet available"
                  : "BPMN does not require loan-officer review for approvalDecision=" + decision));
    } else if (hasLoanOfficerDecision) {
      findings.add(
          finding(
              "mi11-feedback-recorded",
              "mi-11",
              "Loan officer feedback recorded",
              "pass",
              "loanOfficerDecision="
                  + loanOfficerDecision
                  + (decisionPath.agreesWithAi() != null
                      ? ", agreesWithAi=" + decisionPath.agreesWithAi()
                      : "")));
    } else if (processEnded) {
      findings.add(
          finding(
              "mi11-feedback-recorded",
              "mi-11",
              "Loan officer feedback recorded",
              "fail",
              "MANUAL_REVIEW path ended without loanOfficerDecision"));
    } else {
      findings.add(
          finding(
              "mi11-feedback-recorded",
              "mi-11",
              "Loan officer feedback recorded",
              "pending",
              "Waiting on UserTask_LoanOfficerReview"));
    }

    // Keep vars referenced for future checks without unused warning if we add more.
    if (vars.containsKey("attachedFile") && controls.mi1Enabled()) {
      Object attached = vars.get("attachedFile");
      if (attached != null
          && !(attached instanceof byte[])
          && !REDACTED.equals(String.valueOf(attached))
          && !String.valueOf(attached).startsWith("<")) {
        // Binary file values are expected; plaintext attachment payloads are not.
        findings.add(
            finding(
                "mi1-attachment-shape",
                "mi-1",
                "Identity media not exposed as plaintext",
                "warn",
                "attachedFile present in a non-binary shape"));
      }
    }

    return findings;
  }

  private static boolean containsUnredactedSensitive(ProcessInstanceStatus status) {
    for (ProcessInstanceStatus.ActivityStatusEntry activity : status.activities()) {
      if (hasUnredactedSensitive(activity.inputs()) || hasUnredactedSensitive(activity.outputs())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasUnredactedSensitive(List<ProcessInstanceStatus.VariableEntry> entries) {
    for (ProcessInstanceStatus.VariableEntry entry : entries) {
      String name = entry.name() == null ? "" : entry.name().toLowerCase(Locale.ROOT);
      if (!(name.contains("idnumber")
          || name.contains("firstname")
          || name.contains("lastname")
          || name.contains("dateofbirth")
          || name.contains("address")
          || name.contains("agentconversation"))) {
        continue;
      }
      Object value = entry.value();
      if (value == null) {
        continue;
      }
      String text = String.valueOf(value);
      if (!REDACTED.equals(text) && !text.isBlank()) {
        return true;
      }
    }
    return false;
  }

  private static String overallStatus(
      String processState, List<LoanApprovalComplianceReport.CheckFinding> checks) {
    boolean fail = checks.stream().anyMatch(c -> "fail".equals(c.status()));
    if (fail) {
      return "FAIL";
    }
    boolean warn = checks.stream().anyMatch(c -> "warn".equals(c.status()));
    if (!isTerminal(processState)) {
      return warn ? "WARN" : "IN_PROGRESS";
    }
    if (warn) {
      return "WARN";
    }
    return "PASS";
  }

  private static boolean isTerminal(String state) {
    if (state == null) {
      return false;
    }
    return "COMPLETED".equals(state)
        || "EXTERNALLY_TERMINATED".equals(state)
        || "INTERNALLY_TERMINATED".equals(state);
  }

  private static LoanApprovalComplianceReport.CheckFinding finding(
      String id, String control, String title, String status, String detail) {
    return new LoanApprovalComplianceReport.CheckFinding(id, control, title, status, detail);
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

  private static Double asDouble(Object value) {
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

  private static Boolean asBoolean(Object value) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof String text && !text.isBlank()) {
      return Boolean.parseBoolean(text.trim());
    }
    return null;
  }

  private static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /** Helper for tests / callers that want a mutable map builder style. */
  static Map<String, Object> vars(Object... keyValues) {
    if (keyValues.length % 2 != 0) {
      throw new IllegalArgumentException("keyValues must be even");
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    return map;
  }
}
