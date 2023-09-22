/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.client.async;

import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.ScrollResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class OpenSearchAsyncDocumentOperations extends OpenSearchAsyncOperation {
  public OpenSearchAsyncDocumentOperations(Logger logger, OpenSearchAsyncClient openSearchAsyncClient) {
    super(logger, openSearchAsyncClient);
  }

  // TODO check unsued
  private CompletableFuture<ScrollResponse<Object>> scrollAsync(
    final ScrollRequest scrollRequest,
    Function<Exception, String> errorMessageSupplier) {
    return safe(() -> openSearchAsyncClient.scroll(scrollRequest, Object.class), errorMessageSupplier);
  }

  public CompletableFuture<DeleteByQueryResponse> delete(DeleteByQueryRequest.Builder requestBuilder,
    Function<Exception, String> errorMessageSupplier) {
    return safe(() -> openSearchAsyncClient.deleteByQuery(requestBuilder.build()), errorMessageSupplier);
  }

  // TODO check unsued
  private CompletableFuture<SearchResponse<Object>> searchAsync(
    final SearchRequest searchRequest,
    Function<Exception, String> errorMessageSupplier) {
    return safe(() -> openSearchAsyncClient.search(searchRequest, Object.class), errorMessageSupplier);
  }
}
