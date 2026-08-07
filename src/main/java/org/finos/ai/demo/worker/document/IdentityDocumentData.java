package org.finos.ai.demo.worker.document;

/**
 * Identity document fields extracted from an attached image by the document intelligence worker.
 */
public record IdentityDocumentData(
    String firstName,
    String lastName,
    String dateOfBirth,
    String expirationDate,
    String issueDate,
    String address,
    String sex,
    String idNumber,
    String idType) {

  public boolean isSubstantivelyEmpty() {
    return isBlank(firstName)
        && isBlank(lastName)
        && isBlank(dateOfBirth)
        && isBlank(idNumber);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

}
