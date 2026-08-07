package org.finos.ai.demo.worker.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ExtractDataFromDocumentWorkerTest {

  @Test
  void toProcessVariables_mapsExtractedFields() {
    IdentityDocumentData extracted = new IdentityDocumentData(
        "Jane",
        "Doe",
        "1990-05-12",
        "2030-05-12",
        "2020-05-12",
        "10 Main Street",
        "F",
        "X1234567",
        "PASSPORT");

    Map<String, Object> variables = ExtractDataFromDocumentWorker.toProcessVariables(extracted, false);

    assertThat(variables)
        .containsEntry(ExtractDataFromDocumentWorker.VAR_IS_INVALID, false)
        .containsEntry(ExtractDataFromDocumentWorker.VAR_FIRST_NAME, "Jane")
        .containsEntry(ExtractDataFromDocumentWorker.VAR_LAST_NAME, "Doe")
        .containsEntry(ExtractDataFromDocumentWorker.VAR_DATE_OF_BIRTH, "1990-05-12")
        .containsEntry(ExtractDataFromDocumentWorker.VAR_ID_NUMBER, "X1234567")
        .containsEntry(ExtractDataFromDocumentWorker.VAR_ID_TYPE, "PASSPORT");
  }

  @Test
  void toProcessVariables_marksInvalidWhenExtractionIsEmpty() {
    Map<String, Object> variables =
        ExtractDataFromDocumentWorker.toProcessVariables(new IdentityDocumentData(
            null, null, null, null, null, null, null, null, null), true);

    assertThat(variables)
        .containsEntry(ExtractDataFromDocumentWorker.VAR_IS_INVALID, true)
        .doesNotContainKey(ExtractDataFromDocumentWorker.VAR_FIRST_NAME);
  }

}
