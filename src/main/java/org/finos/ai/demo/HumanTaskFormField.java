package org.finos.ai.demo;

import java.util.List;

/** One field in an app-owned human-task form schema. */
public record HumanTaskFormField(
    String id,
    String label,
    String type,
    boolean required,
    List<String> options,
    String help) {}
