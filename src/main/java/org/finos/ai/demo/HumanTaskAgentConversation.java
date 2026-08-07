package org.finos.ai.demo;

/**
 * Unredacted credit-risk agent conversation for authorized human reviewers (loan officer).
 * Intentionally bypasses mi-1 status-API redaction — this is not an LLM egress path.
 */
public record HumanTaskAgentConversation(String lastResponse, String trace, Object history) {

  public boolean isEmpty() {
    return isBlank(lastResponse) && isBlank(trace) && history == null;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
