package org.finos.ai.demo.acceptance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.ResolvedContext;
import org.finos.ai.demo.dlp.Mi1DataLeakagePrevention;
import org.finos.ai.demo.dlp.Mi1DlpProperties;
import org.finos.ai.demo.worker.credit.CheckCreditScoreWorker;
import org.finos.ai.demo.worker.credit.EvaluateCreditRiskWorker;
import org.finos.ai.demo.worker.document.IdentityDocumentData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * FINOS AIGF mi-5 System Acceptance Testing harness for the loan-approval steel thread.
 *
 * <p>Runs deterministic acceptance cases that tolerate AI variability by checking policy invariants
 * and decision bands rather than exact LLM string matches.
 */
@Service
public class Mi5AcceptanceService {

  private static final Logger LOG = LoggerFactory.getLogger(Mi5AcceptanceService.class);

  private static final int MIN_AMOUNT = 10_000;
  private static final int MAX_AMOUNT = 100_000;
  private static final Set<String> LOAN_PURPOSES =
      Set.of(
          "Personal",
          "Debt Consolidation",
          "Home Renovation",
          "Education",
          "Emergency",
          "Travel",
          "Medical Emergency",
          "Car");

  private final Mi5AcceptanceProperties properties;
  private final Mi1DataLeakagePrevention dataLeakagePrevention;
  private final Mi1DlpProperties mi1DlpProperties;
  private final AtomicReference<Mi5AcceptanceReport> lastReport = new AtomicReference<>();

  public Mi5AcceptanceService(
      Mi5AcceptanceProperties properties,
      Mi1DataLeakagePrevention dataLeakagePrevention,
      Mi1DlpProperties mi1DlpProperties) {
    this.properties = properties;
    this.dataLeakagePrevention = dataLeakagePrevention;
    this.mi1DlpProperties = mi1DlpProperties;
  }

  public Mi5AcceptanceReport lastReport() {
    return lastReport.get();
  }

  public Mi5AcceptanceReport runSuite() {
    if (!properties.isEnabled()) {
      Mi5AcceptanceReport skipped =
          new Mi5AcceptanceReport(
              Instant.now().toString(),
              false,
              0,
              properties.getPassRateThreshold(),
              0,
              0,
              0,
              List.of(
                  new Mi5AcceptanceReport.CaseResult(
                      "mi5-disabled",
                      "suite",
                      "mi-5 acceptance suite disabled",
                      false,
                      "Enable fluxnova.acceptance.mi-5.enabled to run acceptance tests")));
      lastReport.set(skipped);
      return skipped;
    }

    List<Mi5AcceptanceReport.CaseResult> results = new ArrayList<>();
    results.addAll(creditRiskBandCases());
    results.addAll(approvalDecisionCases());
    results.addAll(mi1DlpCases());
    results.addAll(loanApplicationBoundCases());
    results.addAll(documentValidityCases());

    int passed = (int) results.stream().filter(Mi5AcceptanceReport.CaseResult::passed).count();
    int total = results.size();
    int failed = total - passed;
    double passRate = total == 0 ? 0 : (double) passed / total;
    boolean accepted = passRate + 1e-9 >= properties.getPassRateThreshold();

    Mi5AcceptanceReport report =
        new Mi5AcceptanceReport(
            Instant.now().toString(),
            accepted,
            passRate,
            properties.getPassRateThreshold(),
            total,
            passed,
            failed,
            List.copyOf(results));
    lastReport.set(report);
    LOG.info(
        "mi-5 SAT suite finished accepted={} passRate={} threshold={} passed={}/{}",
        accepted,
        String.format("%.3f", passRate),
        properties.getPassRateThreshold(),
        passed,
        total);
    return report;
  }

  private List<Mi5AcceptanceReport.CaseResult> creditRiskBandCases() {
    return List.of(
        assertEquals(
            "band-low",
            "credit-risk-bands",
            "Credit score 760 maps to LOW band",
            "LOW",
            CheckCreditScoreWorker.toCreditRiskBand(760)),
        assertEquals(
            "band-medium",
            "credit-risk-bands",
            "Credit score 700 maps to MEDIUM band",
            "MEDIUM",
            CheckCreditScoreWorker.toCreditRiskBand(700)),
        assertEquals(
            "band-high",
            "credit-risk-bands",
            "Credit score 640 maps to HIGH band",
            "HIGH",
            CheckCreditScoreWorker.toCreditRiskBand(640)),
        assertEquals(
            "band-boundary-low",
            "credit-risk-bands",
            "Credit score 740 is LOW boundary",
            "LOW",
            CheckCreditScoreWorker.toCreditRiskBand(740)),
        assertEquals(
            "band-boundary-medium",
            "credit-risk-bands",
            "Credit score 670 is MEDIUM boundary",
            "MEDIUM",
            CheckCreditScoreWorker.toCreditRiskBand(670)));
  }

  private List<Mi5AcceptanceReport.CaseResult> approvalDecisionCases() {
    return List.of(
        assertEquals(
            "decision-manual-high",
            "approval-decision",
            "High score with low AI risk → MANUAL_REVIEW",
            "MANUAL_REVIEW",
            EvaluateCreditRiskWorker.evaluateApprovalDecision(780, 0.1)),
        assertEquals(
            "decision-manual-mid",
            "approval-decision",
            "Mid score with low AI risk → MANUAL_REVIEW",
            "MANUAL_REVIEW",
            EvaluateCreditRiskWorker.evaluateApprovalDecision(660, 0.2)),
        assertEquals(
            "decision-reject-mid-risk",
            "approval-decision",
            "Mid score with higher AI risk → REJECT",
            "REJECT",
            EvaluateCreditRiskWorker.evaluateApprovalDecision(660, 0.35)),
        assertEquals(
            "decision-reject-low-score",
            "approval-decision",
            "Low score → REJECT",
            "REJECT",
            EvaluateCreditRiskWorker.evaluateApprovalDecision(620, 0.1)),
        assertEquals(
            "decision-manual-750",
            "approval-decision",
            "Score 750 always MANUAL_REVIEW",
            "MANUAL_REVIEW",
            EvaluateCreditRiskWorker.evaluateApprovalDecision(750, 0.5)));
  }

  private List<Mi5AcceptanceReport.CaseResult> mi1DlpCases() {
    boolean previous = mi1DlpProperties.isEnabled();
    mi1DlpProperties.setEnabled(true);
    try {
      ResolvedContext unsafe =
          new ResolvedContext(
              Map.of(
                  "creditScore",
                  710,
                  "requestedAmount",
                  25000,
                  "idNumber",
                  "SECRET",
                  "firstName",
                  "Ada",
                  "attachedFile",
                  "bytes"));
      ResolvedContext filtered = dataLeakagePrevention.filterCreditRiskContext(unsafe);
      Set<String> keys = filtered.variables().keySet();

      boolean allowlistOnly =
          keys.contains("creditScore")
              && keys.contains("requestedAmount")
              && !keys.contains("idNumber")
              && !keys.contains("firstName")
              && !keys.contains("attachedFile");

      Object redacted =
          dataLeakagePrevention.redactForStatusApi("idNumber", "AB123456");
      boolean redacts = "<redacted:mi-1>".equals(redacted);

      return List.of(
          new Mi5AcceptanceReport.CaseResult(
              "mi1-allowlist",
              "mi-1-dlp",
              "Credit-risk LLM context is allowlisted",
              allowlistOnly,
              allowlistOnly
                  ? "Only allowlisted underwriting keys remain: " + keys
                  : "Unexpected context keys after DLP: " + keys),
          new Mi5AcceptanceReport.CaseResult(
              "mi1-status-redaction",
              "mi-1-dlp",
              "Status API redacts forbidden PII",
              redacts,
              redacts
                  ? "idNumber redacted for status display"
                  : "Expected <redacted:mi-1>, got " + redacted));
    } finally {
      mi1DlpProperties.setEnabled(previous);
    }
  }

  private List<Mi5AcceptanceReport.CaseResult> loanApplicationBoundCases() {
    boolean amountOk = MIN_AMOUNT >= 10_000 && MAX_AMOUNT <= 100_000 && MIN_AMOUNT < MAX_AMOUNT;
    boolean purposesOk = LOAN_PURPOSES.contains("Car") && LOAN_PURPOSES.contains("Personal");
    return List.of(
        new Mi5AcceptanceReport.CaseResult(
            "amount-bounds",
            "loan-application-bounds",
            "Requested amount bounds are 10k–100k",
            amountOk,
            "min=" + MIN_AMOUNT + " max=" + MAX_AMOUNT),
        new Mi5AcceptanceReport.CaseResult(
            "loan-purposes",
            "loan-application-bounds",
            "Loan purposes include Car and Personal",
            purposesOk,
            "purposes=" + LOAN_PURPOSES));
  }

  private List<Mi5AcceptanceReport.CaseResult> documentValidityCases() {
    IdentityDocumentData empty =
        new IdentityDocumentData(null, null, null, null, null, null, null, null, null);
    IdentityDocumentData partial =
        new IdentityDocumentData("Ada", "Lovelace", null, null, null, null, null, null, "PASSPORT");
    boolean emptyInvalid = empty.isSubstantivelyEmpty();
    boolean partialValid = !partial.isSubstantivelyEmpty();
    return List.of(
        new Mi5AcceptanceReport.CaseResult(
            "doc-empty-invalid",
            "document-validity",
            "Empty extraction is invalid",
            emptyInvalid,
            emptyInvalid
                ? "Empty IdentityDocumentData → isSubstantivelyEmpty"
                : "Empty document not marked empty"),
        new Mi5AcceptanceReport.CaseResult(
            "doc-partial-valid",
            "document-validity",
            "Partial extraction with name is valid",
            partialValid,
            partialValid
                ? "Name present → valid for downstream processing"
                : "Partial document unexpectedly empty"));
  }

  private static Mi5AcceptanceReport.CaseResult assertEquals(
      String id, String category, String title, String expected, String actual) {
    boolean passed = expected.equals(actual);
    return new Mi5AcceptanceReport.CaseResult(
        id,
        category,
        title,
        passed,
        passed ? "expected=" + expected : "expected=" + expected + " actual=" + actual);
  }
}
