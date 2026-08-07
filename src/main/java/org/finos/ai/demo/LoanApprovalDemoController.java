package org.finos.ai.demo;

import org.finos.ai.demo.acceptance.Mi5AcceptanceReport;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/loan-approval")
public class LoanApprovalDemoController {

  private final LoanApprovalDemoService loanApprovalDemoService;
  private final HumanTaskService humanTaskService;

  public LoanApprovalDemoController(
      LoanApprovalDemoService loanApprovalDemoService, HumanTaskService humanTaskService) {
    this.loanApprovalDemoService = loanApprovalDemoService;
    this.humanTaskService = humanTaskService;
  }

  @GetMapping("/options")
  public LoanApprovalDemoOptions options() {
    return loanApprovalDemoService.options();
  }

  @PatchMapping("/controls")
  public LoanApprovalDemoOptions updateControls(@RequestBody ControlEnforcementUpdate update) {
    return loanApprovalDemoService.updateControlEnforcement(update);
  }

  @GetMapping("/acceptance-tests")
  public Mi5AcceptanceReport acceptanceTests() {
    return loanApprovalDemoService.lastAcceptanceReport();
  }

  @PostMapping("/acceptance-tests/run")
  public Mi5AcceptanceReport runAcceptanceTests() {
    return loanApprovalDemoService.runAcceptanceSuite();
  }

  @GetMapping("/instances")
  public LoanApprovalInstanceList instances() {
    return loanApprovalDemoService.listInstances();
  }

  @GetMapping("/instances/{processInstanceId}")
  public LoanApprovalInstanceSummary instance(
      @PathVariable("processInstanceId") String processInstanceId) {
    return loanApprovalDemoService.getInstance(processInstanceId);
  }

  @GetMapping("/samples/{filename:.+}")
  public ResponseEntity<Resource> sample(@PathVariable("filename") String filename) {
    Resource sample = loanApprovalDemoService.sampleDocumentResource(filename);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header("Cache-Control", "public, max-age=3600")
        .body(sample);
  }

  @GetMapping("/instances/{processInstanceId}/status")
  public ProcessInstanceStatus status(@PathVariable("processInstanceId") String processInstanceId) {
    return loanApprovalDemoService.processInstanceStatus(processInstanceId);
  }

  @GetMapping("/instances/{processInstanceId}/compliance-report")
  public LoanApprovalComplianceReport complianceReport(
      @PathVariable("processInstanceId") String processInstanceId) {
    return loanApprovalDemoService.complianceReport(processInstanceId);
  }

  @GetMapping("/instances/{processInstanceId}/compliance-report.pdf")
  public ResponseEntity<byte[]> complianceReportPdf(
      @PathVariable("processInstanceId") String processInstanceId) {
    byte[] pdf = loanApprovalDemoService.complianceReportPdf(processInstanceId);
    String filename = "compliance-evidence-" + processInstanceId + ".pdf";
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
  }

  @GetMapping("/instances/{processInstanceId}/human-review")
  public HumanReviewStatus humanReview(
      @PathVariable("processInstanceId") String processInstanceId) {
    return loanApprovalDemoService.humanReview(processInstanceId);
  }

  @PostMapping("/instances/{processInstanceId}/human-feedback")
  public HumanReviewStatus submitHumanFeedback(
      @PathVariable("processInstanceId") String processInstanceId,
      @RequestBody HumanFeedbackRequest request) {
    return loanApprovalDemoService.submitHumanFeedback(processInstanceId, request);
  }

  @GetMapping("/tasks")
  public HumanTaskList tasks(
      @RequestParam(name = "processInstanceId", required = false) String processInstanceId) {
    return humanTaskService.listOpenTasks(processInstanceId);
  }

  @GetMapping("/tasks/{taskId}")
  public HumanTaskDetail task(@PathVariable("taskId") String taskId) {
    return humanTaskService.getTask(taskId);
  }

  @PostMapping("/tasks/{taskId}/claim")
  public HumanTaskDetail claimTask(@PathVariable("taskId") String taskId) {
    return humanTaskService.claim(taskId);
  }

  @PostMapping("/tasks/{taskId}/complete")
  public HumanTaskDetail completeTask(
      @PathVariable("taskId") String taskId, @RequestBody(required = false) HumanTaskCompleteRequest request) {
    return humanTaskService.complete(taskId, request);
  }

  @PostMapping("/start")
  public StartLoanApprovalResponse start(@RequestBody(required = false) StartLoanApprovalRequest request) {
    return loanApprovalDemoService.startWithSampleIdCard(request);
  }

  @PostMapping("/start-with-sample-id")
  public StartLoanApprovalResponse startWithSampleId(
      @RequestParam(name = "businessKey", required = false) String businessKey,
      @RequestParam(name = "requestedAmount", required = false) Integer requestedAmount,
      @RequestParam(name = "loanPurpose", required = false) String loanPurpose,
      @RequestParam(name = "sampleDocument", required = false) String sampleDocument,
      @RequestParam(name = "creditRiskAgentProvider", required = false)
          String creditRiskAgentProvider,
      @RequestParam(name = "creditRiskAgentModel", required = false)
          String creditRiskAgentModel,
      @RequestParam(name = "creditRiskAgentCacheStrategy", required = false)
          String creditRiskAgentCacheStrategy,
      @RequestParam(name = "creditRiskAgentPromptCacheKey", required = false)
          String creditRiskAgentPromptCacheKey,
      @RequestParam(name = "documentProcessingAgentProvider", required = false)
          String documentProcessingAgentProvider,
      @RequestParam(name = "documentProcessingAgentModel", required = false)
          String documentProcessingAgentModel,
      @RequestParam(name = "documentProcessingAgentCacheStrategy", required = false)
          String documentProcessingAgentCacheStrategy,
      @RequestParam(name = "documentProcessingAgentPromptCacheKey", required = false)
          String documentProcessingAgentPromptCacheKey) {
    return loanApprovalDemoService.startWithSampleIdCard(
        businessKey,
        requestedAmount,
        loanPurpose,
        sampleDocument,
        creditRiskAgentProvider,
        creditRiskAgentModel,
        creditRiskAgentCacheStrategy,
        creditRiskAgentPromptCacheKey,
        documentProcessingAgentProvider,
        documentProcessingAgentModel,
        documentProcessingAgentCacheStrategy,
        documentProcessingAgentPromptCacheKey);
  }

}
