package org.finos.ai.demo.feedback;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Mi11FeedbackProperties.class)
public class Mi11FeedbackConfiguration {}
