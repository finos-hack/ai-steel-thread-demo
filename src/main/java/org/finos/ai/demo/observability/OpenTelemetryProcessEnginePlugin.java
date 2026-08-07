package org.finos.ai.demo.observability;

import org.finos.fluxnova.bpm.engine.spring.SpringProcessEngineConfiguration;
import org.finos.fluxnova.bpm.spring.boot.starter.util.SpringBootProcessEnginePlugin;

class OpenTelemetryProcessEnginePlugin extends SpringBootProcessEnginePlugin {

  private final OpenTelemetryBpmnParseListener parseListener;

  OpenTelemetryProcessEnginePlugin(OpenTelemetryBpmnParseListener parseListener) {
    this.parseListener = parseListener;
  }

  @Override
  public void preInit(SpringProcessEngineConfiguration processEngineConfiguration) {
    processEngineConfiguration.getCustomPostBPMNParseListeners().add(parseListener);
  }

}
