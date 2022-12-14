/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorContext;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.util.SynchronousLogStream;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorListener;
import java.util.Arrays;
import java.util.Optional;

public class StreamProcessingComposite {

  private static final String STREAM_NAME = "stream-";

  private final TestStreams streams;
  private final int partitionId;
  private final ZeebeDbFactory<?> zeebeDbFactory;
  private MutableZeebeState zeebeState;

  public StreamProcessingComposite(
      final TestStreams streams, final int partitionId, final ZeebeDbFactory<?> zeebeDbFactory) {
    this.streams = streams;
    this.partitionId = partitionId;
    this.zeebeDbFactory = zeebeDbFactory;
  }

  public SynchronousLogStream getLogStream(final int partitionId) {
    return streams.getLogStream(getLogName(partitionId));
  }

  public LogStreamWriter newLogStreamWriter(final int partitionId) {
    final String logName = getLogName(partitionId);
    return streams.newLogStreamWriter(logName);
  }

  public StreamProcessor startTypedStreamProcessor(
      final StreamProcessorTestFactory factory,
      final Optional<StreamProcessorListener> streamProcessorListenerOpt) {
    return startTypedStreamProcessor(
        (processingContext) -> createTypedRecordProcessors(factory, processingContext),
        streamProcessorListenerOpt);
  }

  private TypedRecordProcessors createTypedRecordProcessors(
      final StreamProcessorTestFactory factory,
      final TypedRecordProcessorContext typedRecordProcessorContext) {
    zeebeState = typedRecordProcessorContext.getZeebeState();

    return factory.build(
        TypedRecordProcessors.processors(
            zeebeState.getKeyGenerator(), typedRecordProcessorContext.getWriters()),
        typedRecordProcessorContext);
  }

  public StreamProcessor startTypedStreamProcessor(
      final TypedRecordProcessorFactory factory,
      final Optional<StreamProcessorListener> streamProcessorListenerOpt) {
    return startTypedStreamProcessor(partitionId, factory, streamProcessorListenerOpt);
  }

  public StreamProcessor startTypedStreamProcessor(
      final int partitionId,
      final TypedRecordProcessorFactory factory,
      final Optional<StreamProcessorListener> streamProcessorListenerOpt) {
    final var result =
        streams.startStreamProcessor(
            getLogName(partitionId),
            zeebeDbFactory,
            (processingContext -> {
              zeebeState = processingContext.getZeebeState();

              return factory.createProcessors(processingContext);
            }),
            streamProcessorListenerOpt);

    return result;
  }

  public void pauseProcessing(final int partitionId) {
    streams.pauseProcessing(getLogName(partitionId));
  }

  public void resumeProcessing(final int partitionId) {
    streams.resumeProcessing(getLogName(partitionId));
  }

  public void snapshot(final int partitionId) {
    streams.snapshot(getLogName(partitionId));
  }

  public void closeStreamProcessor(final int partitionId) {
    try {
      streams.closeProcessor(getLogName(partitionId));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public StreamProcessor getStreamProcessor(final int partitionId) {
    return streams.getStreamProcessor(getLogName(partitionId));
  }

  public MutableZeebeState getZeebeState() {
    return zeebeState;
  }

  public RecordStream events() {
    return new RecordStream(streams.events(getLogName(partitionId)));
  }

  public long writeBatch(final RecordToWrite... recordsToWrite) {
    return streams
        .newLogStreamWriter(getLogName(partitionId))
        .tryWrite(Arrays.asList(recordsToWrite));
  }

  public long writeCommandOnPartition(
      final int partition, final Intent intent, final UnifiedRecordValue value) {
    return streams
        .newRecord(getLogName(partition))
        .recordType(RecordType.COMMAND)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommandOnPartition(
      final int partition, final long key, final Intent intent, final UnifiedRecordValue value) {
    return streams
        .newRecord(getLogName(partition))
        .key(key)
        .recordType(RecordType.COMMAND)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommand(final long key, final Intent intent, final UnifiedRecordValue value) {
    return streams
        .newRecord(getLogName(partitionId))
        .recordType(RecordType.COMMAND)
        .key(key)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommand(final Intent intent, final UnifiedRecordValue value) {
    return streams
        .newRecord(getLogName(partitionId))
        .recordType(RecordType.COMMAND)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommand(
      final int requestStreamId,
      final long requestId,
      final Intent intent,
      final UnifiedRecordValue value) {
    return streams
        .newRecord(getLogName(partitionId))
        .recordType(RecordType.COMMAND)
        .requestId(requestId)
        .requestStreamId(requestStreamId)
        .intent(intent)
        .event(value)
        .write();
  }

  public static String getLogName(final int partitionId) {
    return STREAM_NAME + partitionId;
  }

  @FunctionalInterface
  public interface StreamProcessorTestFactory {
    TypedRecordProcessors build(
        TypedRecordProcessors builder, TypedRecordProcessorContext typedRecordProcessorContext);
  }
}
