package com.intelli.home.application.service;

public class VersionConflictException extends RuntimeException {
  public VersionConflictException(String message) {
    super(message);
  }
}
