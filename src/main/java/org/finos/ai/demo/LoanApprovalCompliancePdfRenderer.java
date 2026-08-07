package org.finos.ai.demo;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/** Renders {@link LoanApprovalComplianceReport} as a downloadable PDF. */
final class LoanApprovalCompliancePdfRenderer {

  private static final Color INK = new Color(28, 34, 44);
  private static final Color MUTED = new Color(90, 100, 115);
  private static final Color RULE = new Color(210, 216, 224);
  private static final Color PASS = new Color(46, 125, 90);
  private static final Color WARN = new Color(180, 110, 40);
  private static final Color FAIL = new Color(170, 60, 60);

  private LoanApprovalCompliancePdfRenderer() {}

  static byte[] render(LoanApprovalComplianceReport report) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      Document document = new Document(PageSize.A4, 48, 48, 48, 48);
      PdfWriter.getInstance(document, out);
      document.open();

      Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, INK);
      Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, INK);
      Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, INK);
      Font mutedFont = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);
      Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, MUTED);

      Paragraph title = new Paragraph("Loan Approval — Compliance Evidence", titleFont);
      title.setSpacingAfter(4f);
      document.add(title);

      Paragraph subtitle =
          new Paragraph(
              "FINOS AI Steel Thread Demo · per-instance evidence (not a re-run of mi-5 SAT)",
              mutedFont);
      subtitle.setSpacingAfter(14f);
      document.add(subtitle);

      addKeyValue(document, labelFont, bodyFont, "Overall status", nvl(report.overallStatus()));
      addKeyValue(document, labelFont, bodyFont, "Process instance", nvl(report.processInstanceId()));
      addKeyValue(document, labelFont, bodyFont, "Business key", nvl(report.businessKey()));
      addKeyValue(document, labelFont, bodyFont, "Process state", nvl(report.state()));
      addKeyValue(document, labelFont, bodyFont, "Generated at", nvl(report.generatedAt()));

      document.add(section("Control posture", headingFont));
      LoanApprovalComplianceReport.ControlPosture controls = report.controls();
      addKeyValue(
          document,
          labelFont,
          bodyFont,
          "mi-1 DLP",
          onOff(controls.mi1Enabled())
              + (controls.mi1RedactStatusApi() ? " · status redaction on" : " · status redaction off"));
      addKeyValue(document, labelFont, bodyFont, "mi-4 observability", onOff(controls.mi4Enabled()));
      addKeyValue(document, labelFont, bodyFont, "mi-5 acceptance suite", onOff(controls.mi5Enabled()));
      addKeyValue(document, labelFont, bodyFont, "mi-11 human feedback", onOff(controls.mi11Enabled()));
      addKeyValue(
          document,
          labelFont,
          bodyFont,
          "Posture source",
          controls.instanceSnapshot()
              ? "Frozen on this instance at " + nvl(controls.snapshottedAt())
              : "Live environment fallback (pre-snapshot instance)");

      document.add(section("Decision path", headingFont));
      LoanApprovalComplianceReport.DecisionPath path = report.decisionPath();
      addKeyValue(document, labelFont, bodyFont, "Requested amount", value(path.requestedAmount()));
      addKeyValue(document, labelFont, bodyFont, "Loan purpose", nvl(path.loanPurpose()));
      addKeyValue(
          document,
          labelFont,
          bodyFont,
          "Credit",
          path.creditScore() == null
              ? "—"
              : path.creditScore()
                  + (path.creditRiskBand() != null ? " · " + path.creditRiskBand() : ""));
      addKeyValue(document, labelFont, bodyFont, "AI risk score", value(path.aiRiskScore()));
      addKeyValue(document, labelFont, bodyFont, "Approval decision", nvl(path.approvalDecision()));
      addKeyValue(document, labelFont, bodyFont, "Invalid document", value(path.invalidDocument()));
      addKeyValue(document, labelFont, bodyFont, "Fraudulent", value(path.fraudulent()));

      document.add(section("Human feedback (mi-11)", headingFont));
      addKeyValue(
          document, labelFont, bodyFont, "Loan officer decision", nvl(path.loanOfficerDecision()));
      addKeyValue(document, labelFont, bodyFont, "Agrees with AI", value(path.agreesWithAi()));

      document.add(section("Checks", headingFont));
      document.add(checksTable(report.checks(), bodyFont, mutedFont));

      if (report.llmCalls() != null && !report.llmCalls().isEmpty()) {
        document.add(section("LLM calls", headingFont));
        for (LoanApprovalComplianceReport.LlmCallEvidence call : report.llmCalls()) {
          Paragraph line =
              new Paragraph(
                  nvl(call.activityName())
                      + " — "
                      + join(" · ", call.provider(), call.model(), call.modelVersion())
                      + (call.totalTokens() != null ? " · " + call.totalTokens() + " tokens" : "")
                      + (call.estimatedCostUsd() != null
                          ? " · ~$" + String.format("%.6f", call.estimatedCostUsd())
                          : "")
                      + (call.durationMs() != null ? " · " + call.durationMs() + " ms" : ""),
                  bodyFont);
          line.setSpacingAfter(2f);
          document.add(line);
          if (call.contextKeys() != null && !call.contextKeys().isBlank()) {
            Paragraph keys = new Paragraph("context keys: " + call.contextKeys(), mutedFont);
            keys.setSpacingAfter(6f);
            document.add(keys);
          }
        }
      }

      document.add(section("System acceptance (mi-5)", headingFont));
      LoanApprovalComplianceReport.SatSnapshot sat = report.sat();
      addKeyValue(document, labelFont, bodyFont, "Suite enabled", onOff(sat.suiteEnabled()));
      addKeyValue(
          document,
          labelFont,
          bodyFont,
          "Last accepted",
          sat.lastAccepted() == null ? "—" : String.valueOf(sat.lastAccepted()));
      addKeyValue(
          document,
          labelFont,
          bodyFont,
          "Last pass rate",
          sat.lastPassRate() == null ? "—" : Math.round(sat.lastPassRate() * 100) + "%");
      addKeyValue(document, labelFont, bodyFont, "Last run at", nvl(sat.lastRanAt()));
      Paragraph note = new Paragraph(nvl(sat.note()), mutedFont);
      note.setSpacingBefore(4f);
      document.add(note);

      Paragraph footer =
          new Paragraph(
              "Sensitive identity fields are redacted under mi-1 when enforcement is active. "
                  + "This PDF is demo evidence only.",
              mutedFont);
      footer.setSpacingBefore(18f);
      document.add(footer);

      document.close();
      return out.toByteArray();
    } catch (DocumentException ex) {
      throw new UncheckedIOException(new IOException("Failed to render compliance PDF", ex));
    }
  }

  private static Paragraph section(String title, Font headingFont) {
    Paragraph paragraph = new Paragraph(title, headingFont);
    paragraph.setSpacingBefore(14f);
    paragraph.setSpacingAfter(6f);
    return paragraph;
  }

  private static void addKeyValue(
      Document document, Font labelFont, Font bodyFont, String label, String value)
      throws DocumentException {
    Paragraph paragraph = new Paragraph();
    paragraph.add(new Chunk(label + ": ", labelFont));
    paragraph.add(new Chunk(value == null || value.isBlank() ? "—" : value, bodyFont));
    paragraph.setSpacingAfter(2f);
    document.add(paragraph);
  }

  private static PdfPTable checksTable(
      List<LoanApprovalComplianceReport.CheckFinding> checks, Font bodyFont, Font mutedFont)
      throws DocumentException {
    PdfPTable table = new PdfPTable(new float[] {1.1f, 1.0f, 3.2f, 4.2f});
    table.setWidthPercentage(100f);
    table.setSpacingBefore(2f);
    addHeader(table, "Status", mutedFont);
    addHeader(table, "Control", mutedFont);
    addHeader(table, "Check", mutedFont);
    addHeader(table, "Detail", mutedFont);
    for (LoanApprovalComplianceReport.CheckFinding check : checks) {
      addBody(table, check.status().toUpperCase(), statusFont(check.status(), bodyFont));
      addBody(table, nvl(check.control()), bodyFont);
      addBody(table, nvl(check.title()), bodyFont);
      addBody(table, nvl(check.detail()), mutedFont);
    }
    return table;
  }

  private static void addHeader(PdfPTable table, String text, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setBorderColor(RULE);
    cell.setPadding(5f);
    cell.setBackgroundColor(new Color(245, 247, 250));
    table.addCell(cell);
  }

  private static void addBody(PdfPTable table, String text, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setBorderColor(RULE);
    cell.setPadding(5f);
    cell.setVerticalAlignment(Element.ALIGN_TOP);
    table.addCell(cell);
  }

  private static Font statusFont(String status, Font base) {
    Color color =
        switch (status == null ? "" : status.toLowerCase()) {
          case "pass" -> PASS;
          case "fail" -> FAIL;
          case "warn", "pending", "info" -> WARN;
          default -> INK;
        };
    return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, color);
  }

  private static String onOff(boolean enabled) {
    return enabled ? "on" : "off";
  }

  private static String nvl(String value) {
    return value == null || value.isBlank() ? "—" : value;
  }

  private static String value(Object value) {
    return value == null ? "—" : String.valueOf(value);
  }

  private static String join(String delimiter, String... parts) {
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      if (part == null || part.isBlank()) {
        continue;
      }
      if (!builder.isEmpty()) {
        builder.append(delimiter);
      }
      builder.append(part);
    }
    return builder.isEmpty() ? "model unknown" : builder.toString();
  }
}
