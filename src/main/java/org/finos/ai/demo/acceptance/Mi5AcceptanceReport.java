package org.finos.ai.demo.acceptance;

import java.util.List;

/** Result of a mi-5 System Acceptance Testing suite run. */
public record Mi5AcceptanceReport(
    /** ISO-8601 UTC timestamp (string avoids Jackson 3 / jsr310 module mismatch). */
    String ranAt,
    boolean accepted,
    double passRate,
    double passRateThreshold,
    int total,
    int passed,
    int failed,
    List<CaseResult> cases) {

  public record CaseResult(
      String id,
      String category,
      String title,
      boolean passed,
      String detail) {}
}
