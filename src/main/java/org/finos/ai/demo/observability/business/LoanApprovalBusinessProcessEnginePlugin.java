package org.finos.ai.demo.observability.business;

import org.finos.fluxnova.bpm.engine.spring.SpringProcessEngineConfiguration;
import org.finos.fluxnova.bpm.spring.boot.starter.util.SpringBootProcessEnginePlugin;

class LoanApprovalBusinessProcessEnginePlugin extends SpringBootProcessEnginePlugin {

  private final LoanApprovalBusinessBpmnParseListener parseListener;

  LoanApprovalBusinessProcessEnginePlugin(LoanApprovalBusinessBpmnParseListener parseListener) {
    this.parseListener = parseListener;
  }

  @Override
  public void preInit(SpringProcessEngineConfiguration processEngineConfiguration) {
    processEngineConfiguration.getCustomPostBPMNParseListeners().add(parseListener);
  }

}
