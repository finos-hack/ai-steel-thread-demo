package org.finos.ai.demo.agent;

import java.util.Optional;

import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.ai.agent.extract.AgentConfigExtractor;
import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.finos.fluxnova.bpm.engine.ai.agent.registry.AgentConfigRegistry;

/**
 * Augments the credit risk agent system prompt so the LLM always runs credit score before
 * other checks and before finishing the ad-hoc subprocess.
 */
public class LoanApprovalAgentConfigRegistry extends AgentConfigRegistry {

  static final String CREDIT_SCORE_TOOL_ID = "ServiceTask_CreditScoreCheck";

  private static final String CREDIT_RISK_PROMPT_SUFFIX = """

      Workflow rules (mandatory):
      1. Always invoke tool %s first if creditScore is not yet available in context.
      2. Invoke at most one tool per turn.
      3. Do not finish until creditScore has been collected.
      4. After creditScore and any other required checks, stop invoking tools so the subprocess can continue.
      """.formatted(CREDIT_SCORE_TOOL_ID);

  public LoanApprovalAgentConfigRegistry(AgentConfigExtractor extractor) {
    super(extractor);
  }

  @Override
  public Optional<AgentConfig> resolve(
      RepositoryService repositoryService, String processDefinitionId, String elementId) {
    return super.resolve(repositoryService, processDefinitionId, elementId)
        .map(config -> enrichCreditRiskConfig(config, elementId));
  }

  private static AgentConfig enrichCreditRiskConfig(AgentConfig config, String elementId) {
    if (!LoanApprovalAgentConfiguration.CREDIT_RISK_AGENT_ID.equals(elementId)) {
      return config;
    }
    String prompt = config.systemPrompt() == null ? "" : config.systemPrompt().strip();
    if (!prompt.isBlank()) {
      prompt = prompt + "\n\n";
    }
    prompt = prompt + CREDIT_RISK_PROMPT_SUFFIX.strip();
    return new AgentConfig(
        config.processDefinitionId(),
        config.elementId(),
        config.provider(),
        config.model(),
        prompt,
        config.toolScopeElementId());
  }

}
