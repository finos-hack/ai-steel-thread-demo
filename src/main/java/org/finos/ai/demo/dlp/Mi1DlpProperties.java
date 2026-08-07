package org.finos.ai.demo.dlp;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime enforcement settings for FINOS AIGF mi-1 (AI Data Leakage Prevention),
 * aligned with controls declared on {@code architecture/loan-approval.calm.json}.
 */
@ConfigurationProperties(prefix = "fluxnova.dlp.mi-1")
public class Mi1DlpProperties {

  /** Master switch for mi-1 enforcement. */
  private boolean enabled = true;

  /**
   * When true, attempts to send forbidden variables to the credit-risk agent throw
   * instead of silently stripping. Allowed variables are always forwarded.
   */
  private boolean strict = false;

  /** When true, status API / demo responses redact forbidden variable values. */
  private boolean redactStatusApi = true;

  /**
   * Process variables the credit-risk agent may include in LLM context (CALM
   * {@code context-allowlist}).
   */
  private List<String> creditRiskContextAllowlist =
      List.of(
          "creditScore",
          "creditRiskBand",
          "aiRiskScore",
          "employmentVerified",
          "annualIncome",
          "approvalDecision",
          "requestedAmount",
          "loanPurpose");

  /**
   * Variable names that must never leave toward the hosted model on the agent path,
   * and are redacted from status API output when {@link #redactStatusApi} is true.
   */
  private List<String> forbiddenVariableNames =
      List.of(
          "attachedFile",
          "idNumber",
          "firstName",
          "lastName",
          "dateOfBirth",
          "address",
          "expirationDate",
          "issueDate",
          "sex",
          "agentConversationTrace",
          "agentConversationHistory",
          "agentLastResponse");

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isStrict() {
    return strict;
  }

  public void setStrict(boolean strict) {
    this.strict = strict;
  }

  public boolean isRedactStatusApi() {
    return redactStatusApi;
  }

  public void setRedactStatusApi(boolean redactStatusApi) {
    this.redactStatusApi = redactStatusApi;
  }

  public List<String> getCreditRiskContextAllowlist() {
    return creditRiskContextAllowlist;
  }

  public void setCreditRiskContextAllowlist(List<String> creditRiskContextAllowlist) {
    this.creditRiskContextAllowlist = creditRiskContextAllowlist;
  }

  public List<String> getForbiddenVariableNames() {
    return forbiddenVariableNames;
  }

  public void setForbiddenVariableNames(List<String> forbiddenVariableNames) {
    this.forbiddenVariableNames = forbiddenVariableNames;
  }

  public Set<String> allowlistSet() {
    return Set.copyOf(new LinkedHashSet<>(creditRiskContextAllowlist));
  }

  public Set<String> forbiddenSet() {
    return Set.copyOf(new LinkedHashSet<>(forbiddenVariableNames));
  }
}
