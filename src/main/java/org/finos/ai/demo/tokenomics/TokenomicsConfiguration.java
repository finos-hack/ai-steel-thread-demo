package org.finos.ai.demo.tokenomics;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TokenomicsProperties.class)
public class TokenomicsConfiguration {}
