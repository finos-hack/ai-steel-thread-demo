package org.finos.ai.demo;

import java.util.List;

/** App-owned form schema for a BPMN user task (Camunda formRef assets are not in this repo). */
public record HumanTaskFormSchema(
    String taskDefinitionKey, String title, String description, List<HumanTaskFormField> fields) {}
