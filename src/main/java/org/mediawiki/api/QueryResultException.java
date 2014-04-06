package org.mediawiki.api;

import java.io.IOException;

public final class QueryResultException extends IOException {

  private static final long serialVersionUID = 1L;

  public QueryResultException() {
  }

  public QueryResultException(final String message) {
    super(message);
  }

  public QueryResultException(final Throwable cause) {
    super(cause);
  }

  public QueryResultException(final String message, final Throwable cause) {
    super(message, cause);
  }

}
