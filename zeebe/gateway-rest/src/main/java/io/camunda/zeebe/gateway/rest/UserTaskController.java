/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.util.Either;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ServerWebExchange;

@ZeebeRestController
public class UserTaskController {

  private final BrokerClient brokerClient;

  @Autowired
  public UserTaskController(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
  }

  @PostMapping(
      path = "/user-tasks/{userTaskKey}/completion",
      produces = "application/json",
      consumes = "application/json")
  public CompletableFuture<ResponseEntity<Object>> completeUserTask(
      final ServerWebExchange context,
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final UserTaskCompletionRequest completionRequest) {

    return fold(
        RequestMapper.toUserTaskCompletionRequest(completionRequest, userTaskKey, context),
        this::sendBrokerRequest,
        UserTaskController::handleRequestMappingError);
  }

  @PostMapping(
      path = "/user-tasks/{userTaskKey}/assignment",
      produces = "application/json",
      consumes = "application/json")
  public CompletableFuture<ResponseEntity<Object>> assignUserTask(
      final ServerWebExchange context,
      @PathVariable final long userTaskKey,
      @RequestBody final UserTaskAssignmentRequest assignmentRequest) {

    return fold(
        RequestMapper.toUserTaskAssignmentRequest(assignmentRequest, userTaskKey, context),
        this::sendBrokerRequest,
        UserTaskController::handleRequestMappingError);
  }

  @PatchMapping(
      path = "/user-tasks/{userTaskKey}",
      produces = "application/json",
      consumes = "application/json")
  public CompletableFuture<ResponseEntity<Object>> updateUserTask(
      final ServerWebExchange context,
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final UserTaskUpdateRequest updateRequest) {

    return fold(
        RequestMapper.toUserTaskUpdateRequest(updateRequest, userTaskKey, context),
        this::sendBrokerRequest,
        UserTaskController::handleRequestMappingError);
  }

  private CompletableFuture<ResponseEntity<Object>> sendBrokerRequest(
      final BrokerRequest<?> brokerRequest) {
    return brokerClient
        .sendRequest(brokerRequest)
        .handleAsync(
            (response, error) ->
                RestErrorMapper.getResponse(
                        response, error, UserTaskController::mapRejectionToProblem)
                    .orElseGet(() -> ResponseEntity.noContent().build()));
  }

  private static CompletableFuture<ResponseEntity<Object>> handleRequestMappingError(
      final ProblemDetail problemDetail) {
    return CompletableFuture.completedFuture(ResponseEntity.of(problemDetail).build());
  }

  private static ProblemDetail mapRejectionToProblem(final BrokerRejection rejection) {
    final String message =
        String.format(
            "Command '%s' rejected with code '%s': %s",
            rejection.intent(), rejection.type(), rejection.reason());
    final String title = rejection.type().name();
    return switch (rejection.type()) {
      case NOT_FOUND:
        yield RestErrorMapper.createProblemDetail(HttpStatus.NOT_FOUND, message, title);
      case INVALID_STATE:
        yield RestErrorMapper.createProblemDetail(HttpStatus.CONFLICT, message, title);
      case INVALID_ARGUMENT:
      case ALREADY_EXISTS:
        yield RestErrorMapper.createProblemDetail(HttpStatus.BAD_REQUEST, message, title);
      default:
        {
          yield RestErrorMapper.createProblemDetail(
              HttpStatus.INTERNAL_SERVER_ERROR, message, title);
        }
    };
  }

  private static <T, L, R> T fold(
      final Either<L, R> either, final Function<R, T> rightFn, final Function<L, T> leftFn) {
    final var responseOption = either.map(rightFn).mapLeft(leftFn);
    return responseOption.isLeft() ? responseOption.getLeft() : responseOption.get();
  }
}
