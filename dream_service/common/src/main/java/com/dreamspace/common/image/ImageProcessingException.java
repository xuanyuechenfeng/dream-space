package com.dreamspace.common.image;

public final class ImageProcessingException extends RuntimeException {
  private final String code;

  public ImageProcessingException(String code, String message) {
    super(message);
    this.code = code;
  }

  public ImageProcessingException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
