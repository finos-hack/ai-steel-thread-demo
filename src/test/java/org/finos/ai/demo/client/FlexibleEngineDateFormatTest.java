package org.finos.ai.demo.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParsePosition;
import java.util.Date;

import org.junit.jupiter.api.Test;

class FlexibleEngineDateFormatTest {

  private final FlexibleEngineDateFormat format = new FlexibleEngineDateFormat();

  @Test
  void parsesIsoOffsetWithColon() {
    Date parsed = parse("2026-07-31T17:51:41.703+00:00");
    assertThat(parsed).isEqualTo(Date.from(java.time.Instant.parse("2026-07-31T17:51:41.703Z")));
  }

  @Test
  void parsesRfc822OffsetWithoutColon() {
    Date parsed = parse("2026-07-31T11:14:09.419-0400");
    assertThat(parsed)
        .isEqualTo(Date.from(java.time.OffsetDateTime.parse("2026-07-31T11:14:09.419-04:00").toInstant()));
  }

  @Test
  void normalizeOffsetInsertsColon() {
    assertThat(FlexibleEngineDateFormat.normalizeOffset("2026-07-31T11:14:09.419-0400"))
        .isEqualTo("2026-07-31T11:14:09.419-04:00");
    assertThat(FlexibleEngineDateFormat.normalizeOffset("2026-07-31T17:51:41.703+00:00"))
        .isEqualTo("2026-07-31T17:51:41.703+00:00");
  }

  private Date parse(String source) {
    ParsePosition position = new ParsePosition(0);
    Date parsed = format.parse(source, position);
    assertThat(parsed).isNotNull();
    assertThat(position.getIndex()).isEqualTo(source.length());
    return parsed;
  }
}
