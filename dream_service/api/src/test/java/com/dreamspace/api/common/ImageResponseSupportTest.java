package com.dreamspace.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamspace.common.persistence.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class ImageResponseSupportTest {
  @Test
  void returnsPrivateCacheHeadersAndNotModifiedForMatchingEtag() {
    var data = new ObjectStorage.ObjectData(new byte[] { 1, 2, 3 }, "image/webp");
    MockHttpServletRequest firstRequest = new MockHttpServletRequest();

    var first = ImageResponseSupport.inline(data, firstRequest, null);

    assertThat(first.getStatusCode().value()).isEqualTo(200);
    assertThat(first.getHeaders().getCacheControl()).isEqualTo("max-age=86400, private");
    assertThat(first.getHeaders().getETag()).isNotBlank();
    MockHttpServletRequest cachedRequest = new MockHttpServletRequest();
    cachedRequest.addHeader(HttpHeaders.IF_NONE_MATCH, first.getHeaders().getETag());

    var cached = ImageResponseSupport.inline(data, cachedRequest, null);

    assertThat(cached.getStatusCode().value()).isEqualTo(304);
    assertThat(cached.getBody()).isNull();
  }
}
