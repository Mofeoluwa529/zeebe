/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.impl.AggregatedClientStream.LogicalId;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

class AggregatedClientStreamTest {

  private final DirectBuffer streamType = BufferUtil.wrapString("foo");
  private final SerializableData metadata = new SerializableData().data(1234);
  final AggregatedClientStream<SerializableData> stream =
      new AggregatedClientStream<>(UUID.randomUUID(), new LogicalId<>(streamType, metadata));

  @Test
  void shouldRetryWithAllAvailableClientsIfPushFailed() {
    // given
    final List<ClientStreamId> executedClients = new ArrayList<>();
    final List<ClientStreamId> expected =
        List.of(
            addFailingClient(executedClients::add),
            addFailingClient(executedClients::add),
            addFailingClient(executedClients::add));

    // when - then

    assertThatException()
        .isThrownBy(() -> stream.getClientStreamConsumer().push(null))
        .isInstanceOf(StreamExhaustedException.class);

    assertThat(executedClients).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void shouldSucceedWhenOneClientSucceeds() {
    // given
    addFailingClient(s -> {});
    addFailingClient(s -> {});

    final AtomicBoolean pushSucceeded = new AtomicBoolean(false);
    final ClientStreamIdImpl streamId = getNextStreamId();
    addClient(streamId, p -> pushSucceeded.set(true));

    // when - then

    assertThatNoException().isThrownBy(() -> stream.getClientStreamConsumer().push(null));
    assertThat(pushSucceeded.get()).isTrue();
  }

  private ClientStreamIdImpl getNextStreamId() {
    return new ClientStreamIdImpl(stream.getStreamId(), stream.nextLocalId());
  }

  private ClientStreamId addFailingClient(final Consumer<ClientStreamId> consumer) {
    final ClientStreamIdImpl streamId = getNextStreamId();
    addClient(
        streamId,
        p -> {
          consumer.accept(streamId);
          throw new RuntimeException("fail");
        });
    return streamId;
  }

  private void addClient(final ClientStreamIdImpl streamId, final ClientStreamConsumer consumer) {
    stream.addClient(new ClientStream<>(streamId, stream, streamType, metadata, consumer));
  }

  private static class SerializableData implements BufferReader, BufferWriter {

    private int data;

    public int data() {
      return data;
    }

    public SerializableData data(final int data) {
      this.data = data;
      return this;
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      data = buffer.getInt(0);
    }

    @Override
    public int getLength() {
      return Integer.BYTES;
    }

    @Override
    public void write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putInt(offset, data);
    }
  }
}
