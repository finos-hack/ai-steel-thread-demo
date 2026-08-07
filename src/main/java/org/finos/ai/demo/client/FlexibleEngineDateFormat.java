package org.finos.ai.demo.client;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Parses engine REST dates that may use either RFC-822 offsets ({@code -0400}) or
 * ISO-8601 offsets ({@code +00:00}). Fluxnova/Jackson can emit either depending on path.
 */
public final class FlexibleEngineDateFormat extends DateFormat {

  private static final DateTimeFormatter WRITE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  private static final DateTimeFormatter[] READ_FORMATTERS = {
    DateTimeFormatter.ISO_OFFSET_DATE_TIME,
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
  };

  public FlexibleEngineDateFormat() {
    this.numberFormat = NumberFormat.getIntegerInstance(Locale.US);
    this.calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
  }

  @Override
  public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
    ZoneId zone = calendar != null ? calendar.getTimeZone().toZoneId() : ZoneOffset.UTC;
    toAppendTo.append(WRITE_FORMAT.withZone(zone).format(date.toInstant()));
    return toAppendTo;
  }

  @Override
  public Date parse(String source, ParsePosition pos) {
    if (source == null) {
      return null;
    }
    String trimmed = source.trim();
    String normalized = normalizeOffset(trimmed);
    for (DateTimeFormatter formatter : READ_FORMATTERS) {
      try {
        OffsetDateTime dateTime = OffsetDateTime.parse(normalized, formatter);
        pos.setIndex(trimmed.length());
        return Date.from(dateTime.toInstant());
      } catch (DateTimeParseException ignored) {
        // try next pattern
      }
    }
    try {
      Instant instant = Instant.parse(normalized);
      pos.setIndex(trimmed.length());
      return Date.from(instant);
    } catch (DateTimeParseException ignored) {
      pos.setErrorIndex(0);
      return null;
    }
  }

  /**
   * Converts trailing {@code +0000}/{@code -0400} into {@code +00:00}/{@code -04:00} so
   * ISO_OFFSET_DATE_TIME can parse engine-default dates.
   */
  static String normalizeOffset(String source) {
    if (source.length() < 5) {
      return source;
    }
    int offsetStart = source.length() - 5;
    char sign = source.charAt(offsetStart);
    if (sign != '+' && sign != '-') {
      return source;
    }
    // Already has a colon in the offset (+00:00)
    if (source.charAt(source.length() - 3) == ':') {
      return source;
    }
    String offset = source.substring(offsetStart);
    if (!offset.matches("[+-]\\d{4}")) {
      return source;
    }
    return source.substring(0, offsetStart)
        + offset.substring(0, 3)
        + ':'
        + offset.substring(3);
  }

  @Override
  public Object clone() {
    FlexibleEngineDateFormat copy = new FlexibleEngineDateFormat();
    copy.setTimeZone(getTimeZone());
    copy.setLenient(isLenient());
    return copy;
  }
}
