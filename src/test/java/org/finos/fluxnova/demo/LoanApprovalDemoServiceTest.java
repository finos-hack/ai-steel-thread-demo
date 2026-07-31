package org.finos.fluxnova.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.repository.ProcessDefinitionQuery;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.variable.VariableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LoanApprovalDemoServiceTest {

  private RuntimeService runtimeService;
  private RepositoryService repositoryService;
  private ProcessDefinitionQuery processDefinitionQuery;
  private LoanApprovalDemoService service;

  @BeforeEach
  void setUp() throws Exception {
    runtimeService = mock(RuntimeService.class);
    repositoryService = mock(RepositoryService.class);
    processDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ResourcePatternResolver resourcePatternResolver = mock(ResourcePatternResolver.class);

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefinitionQuery);
    when(processDefinitionQuery.processDefinitionKey("LoanApprovalProcess_AI"))
        .thenReturn(processDefinitionQuery);
    when(resourcePatternResolver.getResources("classpath:sample/sample-id-card*.png"))
        .thenReturn(
            new Resource[] {
              sampleResource("sample-id-card.png"),
              sampleResource("sample-id-card-2.png")
            });

    service =
        new LoanApprovalDemoService(
            runtimeService,
            repositoryService,
            resourcePatternResolver,
            "LoanApprovalProcess_AI",
            "attachedFile",
            "classpath:sample/sample-id-card*.png");
  }

  @Test
  void startWithSampleIdCard_startsProcessWithAttachedFile() {
    when(processDefinitionQuery.count()).thenReturn(1L);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getProcessInstanceId()).thenReturn("pi-123");
    AtomicReference<VariableMap> capturedVariables = new AtomicReference<>();
    when(runtimeService.startProcessInstanceByKey(
            eq("LoanApprovalProcess_AI"), eq("loan-42"), any(VariableMap.class)))
        .thenAnswer(
            invocation -> {
              capturedVariables.set(invocation.getArgument(2));
              return processInstance;
            });

    StartLoanApprovalResponse response = service.startWithSampleIdCard("loan-42", null, null);

    assertThat(response.processInstanceId()).isEqualTo("pi-123");
    assertThat(response.businessKey()).isEqualTo("loan-42");
    assertThat(response.processDefinitionKey()).isEqualTo("LoanApprovalProcess_AI");
    assertThat(response.attachedFileVariable()).isEqualTo("attachedFile");
    assertThat(response.sampleDocument())
        .isIn("sample-id-card.png", "sample-id-card-2.png");
    assertThat(response.requestedAmount()).isBetween(10_000, 100_000);
    assertThat(response.loanPurpose())
        .isIn(
            "Personal",
            "Debt Consolidation",
            "Home Renovation",
            "Education",
            "Emergency",
            "Travel",
            "Medical Emergency",
            "Car");

    assertThat(capturedVariables.get().get("requestedAmount")).isEqualTo(response.requestedAmount());
    assertThat(capturedVariables.get().get("loanPurpose")).isEqualTo(response.loanPurpose());
    assertThat(capturedVariables.get().get("attachedFile")).isNotNull();

    verify(runtimeService)
        .startProcessInstanceByKey(
            eq("LoanApprovalProcess_AI"), eq("loan-42"), any(VariableMap.class));
  }

  @Test
  void startWithSampleIdCard_picksRandomlyAmongSampleImages() {
    when(processDefinitionQuery.count()).thenReturn(1L);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getProcessInstanceId()).thenReturn("pi-123");
    when(runtimeService.startProcessInstanceByKey(
            eq("LoanApprovalProcess_AI"), any(), any(VariableMap.class)))
        .thenReturn(processInstance);

    Set<String> selectedDocuments =
        IntStream.range(0, 20)
            .mapToObj(i -> service.startWithSampleIdCard(null, null, null).sampleDocument())
            .collect(Collectors.toSet());

    assertThat(selectedDocuments)
        .containsExactlyInAnyOrder("sample-id-card.png", "sample-id-card-2.png");
  }

  @Test
  void startWithSampleIdCard_failsWhenProcessNotDeployed() {
    when(processDefinitionQuery.count()).thenReturn(0L);

    assertThatThrownBy(() -> service.startWithSampleIdCard("loan-42", null, null))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            exception -> {
              ResponseStatusException responseStatusException = (ResponseStatusException) exception;
              assertThat(responseStatusException.getStatusCode())
                  .isEqualTo(HttpStatus.PRECONDITION_FAILED);
              assertThat(responseStatusException.getReason()).contains("not deployed");
            });
  }

  @Test
  void startWithSampleIdCard_usesProvidedLoanDetails() {
    when(processDefinitionQuery.count()).thenReturn(1L);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getProcessInstanceId()).thenReturn("pi-123");
    AtomicReference<VariableMap> capturedVariables = new AtomicReference<>();
    when(runtimeService.startProcessInstanceByKey(
            eq("LoanApprovalProcess_AI"), eq("loan-42"), any(VariableMap.class)))
        .thenAnswer(
            invocation -> {
              capturedVariables.set(invocation.getArgument(2));
              return processInstance;
            });

    StartLoanApprovalResponse response =
        service.startWithSampleIdCard("loan-42", 25_000, "Car");

    assertThat(response.requestedAmount()).isEqualTo(25_000);
    assertThat(response.loanPurpose()).isEqualTo("Car");
    assertThat(capturedVariables.get().get("requestedAmount")).isEqualTo(25_000);
    assertThat(capturedVariables.get().get("loanPurpose")).isEqualTo("Car");
  }

  @Test
  void startWithSampleIdCard_rejectsInvalidLoanPurpose() {
    when(processDefinitionQuery.count()).thenReturn(1L);

    assertThatThrownBy(() -> service.startWithSampleIdCard("loan-42", 25_000, "Vacation"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            exception -> {
              ResponseStatusException responseStatusException = (ResponseStatusException) exception;
              assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(responseStatusException.getReason()).contains("loanPurpose");
            });
  }

  private static Resource sampleResource(String filename) {
    return new ByteArrayResource(filename.getBytes(StandardCharsets.UTF_8)) {
      @Override
      public String getFilename() {
        return filename;
      }
    };
  }

}
