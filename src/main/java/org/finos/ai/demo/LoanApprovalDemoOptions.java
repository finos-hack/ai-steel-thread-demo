package org.finos.ai.demo;

import java.util.List;
import java.util.Map;

import org.finos.ai.demo.acceptance.Mi5AcceptanceReport;

public record LoanApprovalDemoOptions(
    String processDefinitionKey,
    int minRequestedAmount,
    int maxRequestedAmount,
    List<String> loanPurposes,
    List<String> sampleDocuments,
    List<String> creditRiskAgentProviders,
    List<String> creditRiskAgentModels,
    Map<String, List<String>> agentModelsByProvider,
    List<String> anthropicCacheStrategies,
    Mi1DlpStatus mi1Dlp,
    Mi4ObservabilityStatus mi4Observability,
    Mi5AcceptanceStatus mi5Acceptance,
    Mi11FeedbackStatus mi11Feedback) {

  public record Mi1DlpStatus(
      boolean enabled,
      boolean strict,
      boolean redactStatusApi,
      List<String> creditRiskContextAllowlist,
      List<String> forbiddenVariableNames) {}

  public record Mi4ObservabilityStatus(
      boolean enabled,
      boolean sanitizedAuditLog,
      boolean persistCallMetadata,
      String modelVersionLabel,
      String grafanaBaseUrl,
      List<String> dashboardPaths) {}

  public record Mi5AcceptanceStatus(
      boolean enabled,
      double passRateThreshold,
      String aigfCatalogUrl,
      List<String> criteriaCategories,
      Mi5AcceptanceReport lastReport) {}

  public record Mi11FeedbackStatus(
      boolean enabled, String aigfCatalogUrl, String loanOfficerTaskDefinitionKey) {}
}
