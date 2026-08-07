package org.finos.ai.demo.acceptance;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Mi5AcceptanceProperties.class)
public class Mi5AcceptanceConfiguration {}
