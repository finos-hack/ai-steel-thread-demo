package org.finos.ai.demo;

import org.finos.fluxnova.bpm.client.ExternalTaskClient;
import org.finos.fluxnova.bpm.client.interceptor.auth.BasicAuthProvider;
import org.finos.fluxnova.bpm.client.spring.annotation.EnableExternalTaskClient;
import org.finos.fluxnova.bpm.spring.boot.starter.annotation.EnableProcessApplication;
import org.finos.ai.demo.client.FlexibleDateExternalTaskClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication(excludeName = {
    "org.finos.fluxnova.bpm.client.spring.boot.starter.impl.ClientAutoConfiguration"
})
@EnableProcessApplication
@EnableScheduling
@EnableExternalTaskClient(baseUrl = "http://localhost:8080/engine-rest")
public class Application {

  public static void main(String... args) {
    SpringApplication.run(Application.class, args);
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  public BasicAuthProvider externalTaskClientBasicAuth(
      @Value("${fluxnova.bpm.admin-user.id:demo}") String username,
      @Value("${fluxnova.bpm.admin-user.password:demo}") String password) {
    return new BasicAuthProvider(username, password);
  }

  /**
   * Replaces the annotation-created client so Jackson can parse both engine date offsets
   * ({@code -0400} and {@code +00:00}).
   */
  @Bean(destroyMethod = "stop")
  public ExternalTaskClient externalTaskClient(BasicAuthProvider externalTaskClientBasicAuth) {
    return new FlexibleDateExternalTaskClientBuilder()
        .baseUrl("http://localhost:8080/engine-rest")
        // Used when serializing date variables back to the engine.
        .dateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        .addInterceptor(externalTaskClientBasicAuth)
        .build();
  }

}
