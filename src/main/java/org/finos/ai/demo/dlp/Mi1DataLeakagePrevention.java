package org.finos.ai.demo.dlp;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.ResolvedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Enforces FINOS AIGF mi-1 at LLM egress points: credit-risk agent context firewall,
 * document-intelligence authorized-path audit, and status-API redaction helpers.
 */
@Service
public class Mi1DataLeakagePrevention {

  public static final String EDGE_DOCUMENT_INTELLIGENCE = "document-intelligence-egress";
  public static final String EDGE_AGENT_LLM = "agent-llm-egress";
  public static final String CATEGORY_DOCUMENT_IMAGE = "document-image-for-extraction";
  public static final String CATEGORY_UNDERWRITING_CONTEXT = "underwriting-context";

  private static final Logger LOG = LoggerFactory.getLogger(Mi1DataLeakagePrevention.class);

  private final Mi1DlpProperties properties;
  private final MeterRegistry meterRegistry;

  public Mi1DataLeakagePrevention(Mi1DlpProperties properties, MeterRegistry meterRegistry) {
    this.properties = properties;
    this.meterRegistry = meterRegistry;
  }

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  /**
   * Filters credit-risk agent context to the allowlist. Forbidden keys are blocked
   * (stripped, or throw when strict).
   */
  public ResolvedContext filterCreditRiskContext(ResolvedContext context) {
    if (!properties.isEnabled() || context == null || context.variables() == null) {
      return context;
    }

    Set<String> allowlist = properties.allowlistSet();
    Map<String, Object> original = context.variables();
    Map<String, Object> filtered = new LinkedHashMap<>();
    Set<String> blocked = new LinkedHashSet<>();

    for (Map.Entry<String, Object> entry : original.entrySet()) {
      String key = entry.getKey();
      if (key == null) {
        continue;
      }
      if (allowlist.contains(key) && !isForbiddenName(key)) {
        filtered.put(key, entry.getValue());
      } else {
        blocked.add(key);
      }
    }

    if (!blocked.isEmpty()) {
      recordEgress("blocked", EDGE_AGENT_LLM, "forbidden-or-unlisted-context");
      LOG.warn(
          "mi-1 DLP blocked credit-risk agent context variables from LLM egress: {}",
          blocked.stream().sorted().collect(Collectors.joining(", ")));
      if (properties.isStrict()) {
        throw new Mi1DataLeakageException(
            "mi-1 DLP strict mode: credit-risk agent context contained disallowed variables: "
                + blocked);
      }
    }

    if (!filtered.isEmpty()) {
      recordEgress("allowed", EDGE_AGENT_LLM, CATEGORY_UNDERWRITING_CONTEXT);
    }

    return new ResolvedContext(Map.copyOf(filtered));
  }

  /** Records an authorized document-intelligence media egress (mi-1 allowlisted path). */
  public void auditDocumentIntelligenceEgress(int imageBytes) {
    if (!properties.isEnabled()) {
      return;
    }
    if (imageBytes <= 0) {
      throw new Mi1DataLeakageException(
          "mi-1 DLP: document-intelligence egress requires a non-empty identity document image");
    }
    recordEgress("allowed", EDGE_DOCUMENT_INTELLIGENCE, CATEGORY_DOCUMENT_IMAGE);
    LOG.info(
        "mi-1 DLP allowed document-intelligence egress category={} bytes={}",
        CATEGORY_DOCUMENT_IMAGE,
        imageBytes);
  }

  public Object redactForStatusApi(String variableName, Object value) {
    return redactForStatusApi(
        variableName, value, properties.isEnabled() && properties.isRedactStatusApi());
  }

  /**
   * Status-API redaction using an explicit enforce flag (e.g. per-instance compliance snapshot).
   */
  public Object redactForStatusApi(String variableName, Object value, boolean enforce) {
    if (!enforce || variableName == null) {
      return value;
    }
    if (isForbiddenName(variableName) || isSensitiveTraceName(variableName)) {
      return value == null ? null : "<redacted:mi-1>";
    }
    return value;
  }

  public boolean isForbiddenName(String variableName) {
    if (variableName == null) {
      return false;
    }
    if (properties.forbiddenSet().contains(variableName)) {
      return true;
    }
    int sep = variableName.indexOf("__");
    if (sep > 0) {
      return properties.forbiddenSet().contains(variableName.substring(0, sep));
    }
    return false;
  }

  private static boolean isSensitiveTraceName(String variableName) {
    return variableName.startsWith("agentConversation")
        || "agentLastResponse".equals(variableName);
  }

  private void recordEgress(String outcome, String edge, String category) {
    meterRegistry
        .counter(
            "fluxnova.dlp.mi1.egress",
            "outcome",
            outcome,
            "edge",
            edge,
            "category",
            category)
        .increment();
  }
}
