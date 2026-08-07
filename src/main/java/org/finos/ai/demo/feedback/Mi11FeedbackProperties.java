package org.finos.ai.demo.feedback;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime settings for FINOS AIGF mi-11 (Human Feedback Loop for AI Systems).
 */
@ConfigurationProperties(prefix = "fluxnova.feedback.mi-11")
public class Mi11FeedbackProperties {

  private boolean enabled = true;

  private String aigfCatalogUrl =
      "https://air-governance-framework.finos.org/mitigations/mi-11_human-feedback-loop-for-ai-systems.html";

  /** BPMN user task definition key for loan officer review. */
  private String loanOfficerTaskDefinitionKey = "UserTask_LoanOfficerReview";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getAigfCatalogUrl() {
    return aigfCatalogUrl;
  }

  public void setAigfCatalogUrl(String aigfCatalogUrl) {
    this.aigfCatalogUrl = aigfCatalogUrl;
  }

  public String getLoanOfficerTaskDefinitionKey() {
    return loanOfficerTaskDefinitionKey;
  }

  public void setLoanOfficerTaskDefinitionKey(String loanOfficerTaskDefinitionKey) {
    this.loanOfficerTaskDefinitionKey = loanOfficerTaskDefinitionKey;
  }
}
