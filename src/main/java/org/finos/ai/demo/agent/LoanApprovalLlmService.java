package org.finos.ai.demo.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.ResolvedContext;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.LlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.SpringAiLlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.finos.fluxnova.bpm.engine.shared.model.ConversationEntry;
import org.finos.fluxnova.bpm.engine.shared.model.LlmResponse;
import org.finos.fluxnova.bpm.engine.shared.model.Role;
import org.finos.fluxnova.bpm.engine.shared.model.ToolCallRequest;
import org.finos.ai.demo.dlp.Mi1DataLeakagePrevention;
import org.finos.ai.demo.observability.LlmTokenUsageMetrics;
import org.finos.ai.demo.tokenomics.TokenomicsService;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Reorders multi-tool LLM responses so {@code ServiceTask_CreditScoreCheck} is dispatched first
 * when the sequential orchestrator queues the remaining tools, records mi-4 LLM observability, and
 * enforces mi-1 DLP context allowlisting before hosted-model egress.
 */
public class LoanApprovalLlmService implements LlmService {

  private final SpringAiLlmService delegate;
  private final LlmTokenUsageMetrics tokenUsageMetrics;
  private final Mi1DataLeakagePrevention dataLeakagePrevention;
  private final ObjectProvider<TokenomicsService> tokenomicsService;

  public LoanApprovalLlmService(
      SpringAiLlmService delegate,
      LlmTokenUsageMetrics tokenUsageMetrics,
      Mi1DataLeakagePrevention dataLeakagePrevention,
      ObjectProvider<TokenomicsService> tokenomicsService) {
    this.delegate = delegate;
    this.tokenUsageMetrics = tokenUsageMetrics;
    this.dataLeakagePrevention = dataLeakagePrevention;
    this.tokenomicsService = tokenomicsService;
  }

  @Override
  public LlmResponse call(AgentConfig agentConfig) {
    return observe(agentConfig, null, null, () -> delegate.call(agentConfig));
  }

  @Override
  public LlmResponse call(AgentConfig agentConfig, List<ConversationEntry> conversationHistory) {
    return observe(
        agentConfig, null, null, () -> delegate.call(agentConfig, conversationHistory));
  }

  @Override
  public LlmResponse call(
      AgentConfig agentConfig,
      ResolvedContext context,
      List<ConversationEntry> conversationHistory) {
    ResolvedContext safeContext = applyMi1(null, context);
    return observe(
        agentConfig,
        null,
        safeContext,
        () -> delegate.call(agentConfig, safeContext, conversationHistory));
  }

  @Override
  public LlmResponse call(
      AgentConfig agentConfig,
      AgentToolCatalogue catalogue,
      ResolvedContext context,
      List<ConversationEntry> conversationHistory) {
    ResolvedContext safeContext = applyMi1(catalogue, context);
    return observe(
        agentConfig,
        catalogue == null ? null : catalogue.elementId(),
        safeContext,
        () -> {
          LlmResponse response =
              delegate.call(agentConfig, catalogue, safeContext, conversationHistory);
          if (catalogue != null
              && LoanApprovalAgentConfiguration.CREDIT_RISK_AGENT_ID.equals(catalogue.elementId())
              && response.toolCalls().size() > 1) {
            List<ToolCallRequest> reordered = prioritizeCreditScore(response.toolCalls());
            List<ConversationEntry> history =
                syncAssistantToolCallOrder(response.updatedHistory(), reordered);
            return new LlmResponse(
                response.assistantText(),
                reordered,
                history,
                response.tokenUsage());
          }
          return response;
        });
  }

  /**
   * Keep the last assistant history entry's tool_calls aligned with the dispatch order. Anthropic
   * validates tool_result IDs against the previous assistant tool_use list; reordering only the
   * dispatch list while leaving history unchanged is fine for IDs, but keeping both in sync avoids
   * confusing traces and provider edge cases.
   */
  private static List<ConversationEntry> syncAssistantToolCallOrder(
      List<ConversationEntry> history, List<ToolCallRequest> reordered) {
    if (history == null || history.isEmpty()) {
      return history;
    }
    List<ConversationEntry> copy = new ArrayList<>(history);
    int last = copy.size() - 1;
    ConversationEntry entry = copy.get(last);
    if (entry.role() != Role.ASSISTANT) {
      return history;
    }
    copy.set(last, ConversationEntry.assistant(entry.content(), reordered));
    return copy;
  }

  private ResolvedContext applyMi1(AgentToolCatalogue catalogue, ResolvedContext context) {
    if (context == null) {
      return null;
    }
    if (catalogue != null
        && !LoanApprovalAgentConfiguration.CREDIT_RISK_AGENT_ID.equals(catalogue.elementId())) {
      return context;
    }
    return dataLeakagePrevention.filterCreditRiskContext(context);
  }

  private LlmResponse observe(
      AgentConfig agentConfig,
      String agentTaskId,
      ResolvedContext context,
      Supplier<LlmResponse> call) {
    String workflow = LlmTokenUsageMetrics.workflowKey(agentConfig.processDefinitionId());
    TokenomicsService tokenomics = tokenomicsService.getIfAvailable();
    if (tokenomics != null) {
      tokenomics.enforceHardBudget(workflow);
    }
    String task =
        agentTaskId == null || agentTaskId.isBlank() ? agentConfig.elementId() : agentTaskId;
    List<String> contextKeys =
        context == null || context.variables() == null
            ? List.of()
            : List.copyOf(context.variables().keySet());
    long started = System.nanoTime();
    try {
      LlmResponse response = call.get();
      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      tokenUsageMetrics.recordCall(
          workflow,
          task,
          agentConfig.model() == null ? "unknown" : agentConfig.model(),
          agentConfig.provider() == null ? "unknown" : agentConfig.provider(),
          agentConfig.model() == null ? "pinned-config" : agentConfig.model(),
          durationMs,
          response.tokenUsage(),
          true,
          null,
          contextKeys);
      return response;
    } catch (RuntimeException exception) {
      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      tokenUsageMetrics.recordCall(
          workflow,
          task,
          agentConfig.model() == null ? "unknown" : agentConfig.model(),
          agentConfig.provider() == null ? "unknown" : agentConfig.provider(),
          agentConfig.model() == null ? "pinned-config" : agentConfig.model(),
          durationMs,
          null,
          false,
          exception.getClass().getSimpleName(),
          contextKeys);
      throw exception;
    }
  }

  private static List<ToolCallRequest> prioritizeCreditScore(List<ToolCallRequest> toolCalls) {
    List<ToolCallRequest> copy = new ArrayList<>(toolCalls);
    copy.sort(
        Comparator.comparing(
            request ->
                !LoanApprovalAgentConfigRegistry.CREDIT_SCORE_TOOL_ID.equals(request.toolId())));
    return copy;
  }
}
