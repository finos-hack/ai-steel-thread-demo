package org.finos.ai.demo.observability;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Mi4ObservabilityProperties.class)
public class Mi4ObservabilityConfiguration {}
