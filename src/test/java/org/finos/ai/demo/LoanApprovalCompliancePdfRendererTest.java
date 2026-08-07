package org.finos.ai.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LoanApprovalCompliancePdfRendererTest {

  @Test
  void render_producesPdfBytes() {
    ProcessInstanceStatus status =
        new ProcessInstanceStatus(
            "pi-pdf-1",
            "loan-pdf-1",
            "COMPLETED",
            new Date(),
            new Date(),
            List.of(),
            List.of(),
            new ProcessInstanceStatus.ControlPosture(true, true, true, true, true));

    LoanApprovalComplianceReport report =
        LoanApprovalComplianceEvidence.build(
            status,
            Map.of(
                "requestedAmount",
                25_000,
                "loanPurpose",
                "Car",
                "approvalDecision",
                "MANUAL_REVIEW",
                "loanOfficerDecision",
                "APPROVE",
                "humanFeedbackAgreesWithAi",
                true,
                "isInvalid",
                false,
                "isFraudulent",
                false,
                "creditScore",
                720,
                "creditRiskBand",
                "MEDIUM"),
            new LoanApprovalComplianceReport.ControlPosture(
                true, true, true, true, true, "2026-08-02T12:00:00Z", true),
            new LoanApprovalComplianceReport.SatSnapshot(
                true, true, 1.0, "2026-08-02T00:00:00Z", "Last SAT accepted"));

    byte[] pdf = LoanApprovalCompliancePdfRenderer.render(report);

    assertThat(pdf.length).isGreaterThan(200);
    assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
  }
}
