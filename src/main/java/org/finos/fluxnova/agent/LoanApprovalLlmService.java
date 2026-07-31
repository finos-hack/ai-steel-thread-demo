package org.finos.fluxnova.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.ResolvedContext;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.LlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.SpringAiLlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.finos.fluxnova.bpm.engine.shared.model.ConversationEntry;
import org.finos.fluxnova.bpm.engine.shared.model.LlmResponse;
import org.finos.fluxnova.bpm.engine.shared.model.ToolCallRequest;
import org.finos.fluxnova.observability.LlmTokenUsageMetrics;

/**
 * Reorders multi-tool LLM responses so {@code ServiceTask_CreditScoreCheck} is dispatched first
 * when the sequential orchestrator queues the remaining tools, and records token usage metrics.
 */
public class LoanApprovalLlmService implements LlmService {

  private final SpringAiLlmService delegate;
  private final LlmTokenUsageMetrics tokenUsageMetrics;

  public LoanApprovalLlmService(
      SpringAiLlmService delegate, LlmTokenUsageMetrics tokenUsageMetrics) {
    this.delegate = delegate;
    this.tokenUsageMetrics = tokenUsageMetrics;
  }

  @Override
  public LlmResponse call(AgentConfig agentConfig) {
    return recordUsage(agentConfig, null, delegate.call(agentConfig));
  }

  @Override
  public LlmResponse call(AgentConfig agentConfig, List<ConversationEntry> conversationHistory) {
    return recordUsage(agentConfig, null, delegate.call(agentConfig, conversationHistory));
  }

  @Override
  public LlmResponse call(
      AgentConfig agentConfig,
      ResolvedContext context,
      List<ConversationEntry> conversationHistory) {
    return recordUsage(
        agentConfig, null, delegate.call(agentConfig, context, conversationHistory));
  }

  @Override
  public LlmResponse call(
      AgentConfig agentConfig,
      AgentToolCatalogue catalogue,
      ResolvedContext context,
      List<ConversationEntry> conversationHistory) {
    LlmResponse response =
        delegate.call(agentConfig, catalogue, context, conversationHistory);
    LlmResponse result = response;
    if (catalogue != null
        && LoanApprovalAgentConfiguration.CREDIT_RISK_AGENT_ID.equals(catalogue.elementId())
        && response.toolCalls().size() > 1) {
      List<ToolCallRequest> reordered = prioritizeCreditScore(response.toolCalls());
      result = new LlmResponse(
          response.assistantText(), reordered, response.updatedHistory(), response.tokenUsage());
    }
    return recordUsage(agentConfig, catalogue == null ? null : catalogue.elementId(), result);
  }

  private LlmResponse recordUsage(AgentConfig agentConfig, String agentTaskId, LlmResponse response) {
    tokenUsageMetrics.record(agentConfig, agentTaskId, response.tokenUsage());
    return response;
  }

  private static List<ToolCallRequest> prioritizeCreditScore(List<ToolCallRequest> toolCalls) {
    List<ToolCallRequest> copy = new ArrayList<>(toolCalls);
    copy.sort(Comparator.comparing(
        request -> !LoanApprovalAgentConfigRegistry.CREDIT_SCORE_TOOL_ID.equals(request.toolId())));
    return copy;
  }

}
