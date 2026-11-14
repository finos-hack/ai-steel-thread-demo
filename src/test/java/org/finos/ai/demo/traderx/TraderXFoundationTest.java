package org.finos.ai.demo.traderx;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class TraderXFoundationTest {
  @Test void largeJsonUsesBinaryStorageAndLegacyStringsStillRead() {
    String large="x".repeat(4001);
    assertThat(TraderXWorkflowService.storeJson(large)).isInstanceOf(byte[].class);
    assertThat(TraderXWorkflowService.readJson(TraderXWorkflowService.storeJson(large))).isEqualTo(large);
    assertThat(TraderXWorkflowService.readJson("[]")).isEqualTo("[]");
  }
  @Test void absentWorkflowsAreNotStartable() {
    var client=mock(TraderXClient.class);
    var service=new TraderXWorkflowService(client,null,null,null,List.of());
    assertThatThrownBy(() -> service.start("missing",Map.of())).hasMessageContaining("unavailable");
    verifyNoInteractions(client);
  }
}
