package org.finos.fluxnova.demo;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/loan-approval")
public class LoanApprovalDemoController {

  private final LoanApprovalDemoService loanApprovalDemoService;

  public LoanApprovalDemoController(LoanApprovalDemoService loanApprovalDemoService) {
    this.loanApprovalDemoService = loanApprovalDemoService;
  }

  @PostMapping("/start-with-sample-id")
  public StartLoanApprovalResponse startWithSampleId(
      @RequestParam(name = "businessKey", required = false) String businessKey,
      @RequestParam(name = "requestedAmount", required = false) Integer requestedAmount,
      @RequestParam(name = "loanPurpose", required = false) String loanPurpose) {
    return loanApprovalDemoService.startWithSampleIdCard(
        businessKey, requestedAmount, loanPurpose);
  }

}
