package org.finos.ai.demo.observability.business;

import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableConfigurationProperties(LoanApprovalBusinessMetricsProperties.class)
@ConditionalOnProperty(
    prefix = "fluxnova.observability.business",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LoanApprovalBusinessObservabilityConfiguration {

  @Bean
  LoanApprovalBusinessMetrics loanApprovalBusinessMetrics(
      MeterRegistry meterRegistry, LoanApprovalBusinessMetricsProperties properties) {
    return new LoanApprovalBusinessMetrics(meterRegistry, properties);
  }

  @Bean
  LoanApprovalBusinessBpmnParseListener loanApprovalBusinessBpmnParseListener(
      LoanApprovalBusinessMetrics metrics, ObjectProvider<RuntimeService> runtimeService) {
    return new LoanApprovalBusinessBpmnParseListener(
        new LoanApprovalBusinessExecutionListener(metrics, runtimeService, true));
  }

  @Bean
  ProcessEnginePlugin loanApprovalBusinessProcessEnginePlugin(
      LoanApprovalBusinessBpmnParseListener parseListener) {
    return new LoanApprovalBusinessProcessEnginePlugin(parseListener);
  }

}
