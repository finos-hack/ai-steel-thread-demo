package org.finos.ai.demo;

import java.util.Map;

/** Payload to complete a claimed BPMN user task. */
public record HumanTaskCompleteRequest(Map<String, Object> variables) {}
