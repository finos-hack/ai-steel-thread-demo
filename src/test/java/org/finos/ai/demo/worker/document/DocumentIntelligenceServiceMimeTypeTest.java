package org.finos.ai.demo.worker.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.util.MimeTypeUtils;

class DocumentIntelligenceServiceMimeTypeTest {

  @Test
  void detectImageMimeType_recognizesJpegMagicBytes() {
    byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00};
    assertThat(DocumentIntelligenceService.detectImageMimeType(jpeg))
        .isEqualTo(MimeTypeUtils.IMAGE_JPEG);
  }

  @Test
  void resolveImageMimeType_prefersBytesOverWrongDeclaredPng() {
    byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00};
    assertThat(
            DocumentIntelligenceService.resolveImageMimeType(
                "image/png", "sample-id-card.png", jpeg))
        .isEqualTo(MimeTypeUtils.IMAGE_JPEG);
  }

  @Test
  void resolveImageMimeType_fallsBackToFilenameWhenBytesUnknown() {
    assertThat(DocumentIntelligenceService.resolveImageMimeType(null, "doc.webp", new byte[12]))
        .isEqualTo(MimeTypeUtils.parseMimeType("image/webp"));
  }
}
