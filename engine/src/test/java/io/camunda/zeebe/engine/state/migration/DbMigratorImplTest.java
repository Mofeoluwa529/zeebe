/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.engine.state.mutable.MutableMigrationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DbMigratorImplTest {

  private TransactionContext transactionContext;
  private ZeebeDbTransaction transaction;

  @BeforeEach
  void setup() {
    transaction = mock(ZeebeDbTransaction.class);
    transactionContext = new RunInTransactionContext(transaction);
  }

  @Test
  void shouldRunMigrationThatNeedsToBeRun() {
    // given
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(mockProcessingState)).thenReturn(true);

    final var sut =
        new DbMigratorImpl(
            mockProcessingState,
            transactionContext,
            () -> Collections.singletonList(mockMigration));

    // when
    sut.runMigrations();

    // then
    verify(mockMigration).runMigration(mockProcessingState);
  }

  @Test
  void shouldNotRunMigrationThatDoesNotNeedToBeRun() {
    // given
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(mockProcessingState)).thenReturn(false);

    final var sut =
        new DbMigratorImpl(
            mockProcessingState,
            transactionContext,
            () -> Collections.singletonList(mockMigration));

    // when
    sut.runMigrations();

    // then
    verify(mockMigration, never()).runMigration(mockProcessingState);
  }

  @Test
  void shouldRunMigrationsInOrder() {
    // given
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);
    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(mockProcessingState)).thenReturn(true);
    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(mockProcessingState)).thenReturn(true);

    final var sut =
        new DbMigratorImpl(
            mockProcessingState, transactionContext, () -> List.of(mockMigration1, mockMigration2));

    // when
    sut.runMigrations();

    // then
    final var inOrder = Mockito.inOrder(mockMigration1, mockMigration2);

    inOrder.verify(mockMigration1).runMigration(mockProcessingState);
    inOrder.verify(mockMigration2).runMigration(mockProcessingState);
  }

  @Test
  void shouldNotRunAnyMigrationIfAbortSignalWasReceivedInTheVeryBeginning() {
    // given
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(mockProcessingState)).thenReturn(false);

    final var sut =
        new DbMigratorImpl(
            mockProcessingState,
            transactionContext,
            () -> Collections.singletonList(mockMigration));

    // when
    sut.abort();
    sut.runMigrations();

    // then
    verify(mockMigration, never()).needsToRun(any());
    verify(mockMigration, never()).runMigration(any());
  }

  @Test
  void shouldNotRunSubsequentMigrationsAfterAbortSignalWasReceived() {
    // given
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);
    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(mockProcessingState)).thenReturn(true);
    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(mockProcessingState)).thenReturn(true);

    final var sut =
        new DbMigratorImpl(
            mockProcessingState, transactionContext, () -> List.of(mockMigration1, mockMigration2));

    doAnswer(
            (invocationOnMock) -> {
              // send abort signal during first migration
              sut.abort();
              return null;
            })
        .when(mockMigration1)
        .runMigration(mockProcessingState);

    // when
    sut.runMigrations();

    // then
    verify(mockMigration1).runMigration(mockProcessingState);
    verify(mockMigration2, never()).runMigration(mockProcessingState);
  }

  private static final class RunInTransactionContext implements TransactionContext {

    private final ZeebeDbTransaction transaction;

    private RunInTransactionContext(final ZeebeDbTransaction transaction) {
      this.transaction = transaction;
    }

    @Override
    public void runInTransaction(final TransactionOperation operations) {
      try {
        operations.run();
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public ZeebeDbTransaction getCurrentTransaction() {
      return transaction;
    }
  }
}
