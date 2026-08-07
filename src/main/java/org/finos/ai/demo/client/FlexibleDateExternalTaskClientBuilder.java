package org.finos.ai.demo.client;

import org.finos.fluxnova.bpm.client.impl.ExternalTaskClientBuilderImpl;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/** External task client builder that accepts both {@code -0400} and {@code +00:00} date offsets. */
public final class FlexibleDateExternalTaskClientBuilder extends ExternalTaskClientBuilderImpl {

  @Override
  protected void initJsonMapper() {
    jsonMapper =
        JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS)
            .disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .defaultDateFormat(new FlexibleEngineDateFormat())
            .build();
  }
}
