package org.finos.ai.demo.agent;

import org.finos.fluxnova.bpm.engine.spring.SpringProcessEngineConfiguration;
import org.finos.fluxnova.bpm.spring.boot.starter.util.SpringBootProcessEnginePlugin;

class AgentAdHocCompletionGuardProcessEnginePlugin extends SpringBootProcessEnginePlugin {

  private final AgentAdHocCompletionGuardParseListener parseListener;

  AgentAdHocCompletionGuardProcessEnginePlugin(AgentAdHocCompletionGuardParseListener parseListener) {
    this.parseListener = parseListener;
  }

  @Override
  public void preInit(SpringProcessEngineConfiguration processEngineConfiguration) {
    processEngineConfiguration.getCustomPostBPMNParseListeners().add(parseListener);
  }
}
