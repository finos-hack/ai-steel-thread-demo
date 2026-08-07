package org.finos.ai.demo.agent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.finos.fluxnova.bpm.engine.ai.agent.discovery.extract.AdHocSubProcessCatalogueBuilder;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.extract.AgentContextSpecBuilder;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.extract.AgentToolCatalogueBuilder;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.extract.BpmnExtensionContextSpecBuilder;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentContextSpec;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolEntry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.ContextVariableDeclaration;
import org.finos.fluxnova.bpm.engine.impl.pvm.process.ActivityImpl;
import org.finos.fluxnova.bpm.engine.impl.util.xml.Element;
import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.ai.agent.extract.AgentConfigExtractor;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.provider.AgentProviderRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.LlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.SpringAiLlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.tool.AgentToolSchemaConverter;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service.AgentTerminationHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.state.AgentStateManager;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentToolCatalogueRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.registry.AgentConfigRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.service.ToolInvocationService;
import org.finos.ai.demo.observability.LlmTokenUsageMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

/**
 * Enriches agent tool metadata and context for the loan approval process when the
 * deployed BPMN omits {@code camunda:inputOutput} mappings and {@code agent:context}
 * variable declarations on {@code AgentTask_CreditRisk}.
 */
@Configuration
public class LoanApprovalAgentConfiguration {

  static final String CREDIT_RISK_AGENT_ID = "AgentTask_CreditRisk";

  private static final List<ContextVariableDeclaration> CREDIT_RISK_CONTEXT = List.of(
      variable("creditScore"),
      variable("creditRiskBand"),
      variable("aiRiskScore"),
      variable("employmentVerified"),
      variable("annualIncome"),
      variable("approvalDecision"),
      variable("requestedAmount"),
      variable("loanPurpose"));

  private static final Map<String, ToolIoHints> TOOL_IO_HINTS = Map.of(
      "ServiceTask_CreditScoreCheck",
      new ToolIoHints(Set.of(), Set.of("creditScore", "creditRiskBand", "aiRiskScore")),
      "ServiceTask_VerifyEmployment",
      new ToolIoHints(Set.of(), Set.of("employmentVerified", "annualIncome")),
      "ServiceTask_EvaluateCreditScore",
      new ToolIoHints(Set.of("creditScore", "aiRiskScore"), Set.of("approvalDecision")));

  @Bean
  @Primary
  AgentConfigRegistry agentConfigRegistry(AgentConfigExtractor extractor) {
    return new LoanApprovalAgentConfigRegistry(extractor);
  }

  @Bean
  SpringAiLlmService springAiLlmService(
      AgentProviderRegistry registry, AgentToolSchemaConverter converter) {
    return new SpringAiLlmService(registry, converter);
  }

  @Bean
  @Primary
  LlmService loanApprovalLlmService(
      SpringAiLlmService springAiLlmService,
      LlmTokenUsageMetrics llmTokenUsageMetrics,
      org.finos.ai.demo.dlp.Mi1DataLeakagePrevention dataLeakagePrevention,
      org.springframework.beans.factory.ObjectProvider<
              org.finos.ai.demo.tokenomics.TokenomicsService>
          tokenomicsService) {
    return new LoanApprovalLlmService(
        springAiLlmService, llmTokenUsageMetrics, dataLeakagePrevention, tokenomicsService);
  }

  @Bean
  @Primary
  AgentTerminationHandler creditRiskAwareTerminationHandler(
      @Lazy RepositoryService repositoryService,
      AgentToolCatalogueRegistry toolCatalogueRegistry,
      AgentStateManager stateManager,
      ToolInvocationService toolInvocationService) {
    return new CreditRiskAwareTerminationHandler(
        repositoryService, toolCatalogueRegistry, stateManager, toolInvocationService);
  }

  @Bean
  AgentAdHocCompletionGuardParseListener agentAdHocCompletionGuardParseListener() {
    return new AgentAdHocCompletionGuardParseListener();
  }

  @Bean
  org.finos.fluxnova.bpm.engine.impl.cfg.ProcessEnginePlugin
      agentAdHocCompletionGuardProcessEnginePlugin(
          AgentAdHocCompletionGuardParseListener parseListener) {
    return new AgentAdHocCompletionGuardProcessEnginePlugin(parseListener);
  }

  @Bean
  AgentToolCatalogueBuilder agentToolCatalogueBuilder() {
    AdHocSubProcessCatalogueBuilder delegate = new AdHocSubProcessCatalogueBuilder();
    return scope -> {
      AgentToolCatalogue catalogue = delegate.build(scope);
      if (!CREDIT_RISK_AGENT_ID.equals(scope.getId())) {
        return catalogue;
      }
      List<AgentToolEntry> tools = catalogue.tools().stream()
          .map(LoanApprovalAgentConfiguration::enrichTool)
          .toList();
      return new AgentToolCatalogue(catalogue.processDefinitionId(), catalogue.elementId(), tools);
    };
  }

  @Bean
  AgentContextSpecBuilder agentContextSpecBuilder(
      org.finos.ai.demo.dlp.Mi1DlpProperties mi1DlpProperties) {
    BpmnExtensionContextSpecBuilder delegate = new BpmnExtensionContextSpecBuilder();
    Set<String> allowlist = mi1DlpProperties.allowlistSet();
    return (element, processDefinitionId) -> {
      AgentContextSpec spec = delegate.build(element, processDefinitionId);
      if (!CREDIT_RISK_AGENT_ID.equals(element.attribute("id"))) {
        return spec;
      }
      List<ContextVariableDeclaration> declared = spec.declaredVariables();
      if (declared.isEmpty()) {
        return new AgentContextSpec(processDefinitionId, CREDIT_RISK_AGENT_ID, CREDIT_RISK_CONTEXT);
      }
      // mi-1: even BPMN-declared context is intersected with the allowlist.
      List<ContextVariableDeclaration> filtered =
          declared.stream().filter(v -> allowlist.contains(v.name())).toList();
      if (filtered.isEmpty()) {
        return new AgentContextSpec(processDefinitionId, CREDIT_RISK_AGENT_ID, CREDIT_RISK_CONTEXT);
      }
      return new AgentContextSpec(processDefinitionId, CREDIT_RISK_AGENT_ID, filtered);
    };
  }

  private static ContextVariableDeclaration variable(String name) {
    return new ContextVariableDeclaration(name);
  }

  private static AgentToolEntry enrichTool(AgentToolEntry tool) {
    ToolIoHints hints = TOOL_IO_HINTS.get(tool.elementId());
    String description = tool.description();
    if (LoanApprovalAgentConfigRegistry.CREDIT_SCORE_TOOL_ID.equals(tool.elementId())) {
      description = "Required first step before any other credit risk tool. "
          + (description == null ? "Checks the applicant credit score." : description);
    }
    if (hints == null) {
      if (description == null || description.equals(tool.description())) {
        return tool;
      }
      return new AgentToolEntry(
          tool.elementId(), tool.name(), description, tool.reads(), tool.writes());
    }
    return new AgentToolEntry(
        tool.elementId(),
        tool.name(),
        description,
        merge(tool.reads(), hints.reads()),
        merge(tool.writes(), hints.writes()));
  }

  private static Set<String> merge(Set<String> fromBpmn, Set<String> hints) {
    if (!fromBpmn.isEmpty()) {
      return fromBpmn;
    }
    return hints;
  }

  private record ToolIoHints(Set<String> reads, Set<String> writes) {
    ToolIoHints {
      reads = Set.copyOf(new LinkedHashSet<>(reads));
      writes = Set.copyOf(new LinkedHashSet<>(writes));
    }
  }

}
