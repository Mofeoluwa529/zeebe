package org.camunda.tngp.dispatcher;

import static org.camunda.tngp.dispatcher.impl.PositionUtil.*;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.dispatcher.impl.DispatcherContext;
import org.camunda.tngp.dispatcher.impl.Subscription;
import org.camunda.tngp.dispatcher.impl.log.LogBuffer;
import org.camunda.tngp.dispatcher.impl.log.LogBufferAppender;
import org.camunda.tngp.dispatcher.impl.log.LogBufferPartition;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.status.AtomicLongPosition;
import uk.co.real_logic.agrona.concurrent.status.Position;

/**
 * Component for sending and receiving messages between different threads.
 *
 */
public class Dispatcher implements AutoCloseable
{
    public final static int MODE_PUB_SUB = 1;
    public final static int MODE_PIPELINE = 2;

    final DispatcherContext context;

    protected final LogBuffer logBuffer;
    protected final LogBufferAppender logAppender;

    protected final Position publisherLimit;
    protected final Position publisherPosition;
    protected Subscription[] subscriptions;

    protected final int maxFrameLength;
    protected final int partitionSize;
    protected final int initialPartitionId;
    protected int logWindowLength;
    protected final String name;

    protected final int mode;

    Dispatcher(
            LogBuffer logBuffer,
            LogBufferAppender logAppender,
            Position publisherLimit,
            Position publisherPosition,
            int logWindowLength,
            int mode,
            DispatcherContext context,
            String name)
    {
        this.logBuffer = logBuffer;
        this.logAppender = logAppender;
        this.publisherLimit = publisherLimit;
        this.publisherPosition = publisherPosition;
        this.logWindowLength = logWindowLength;
        this.mode = mode;
        this.context = context;
        this.name = name;

        this.initialPartitionId = logBuffer.getInitialPartitionId();
        this.partitionSize = logBuffer.getPartitionSize();
        this.maxFrameLength = partitionSize / 16;

        subscriptions = new Subscription[0];
    }

    public long offer(DirectBuffer msg)
    {
        return offer(msg, 0, msg.capacity(), 0);
    }

    public long offer(DirectBuffer msg, int streamId)
    {
        return offer(msg, 0, msg.capacity(), streamId);
    }

    public long offer(DirectBuffer msg, int start, int length)
    {
        return offer(msg, start, length, 0);
    }

    public long offer(DirectBuffer msg, int start, int length, int streamId)
    {
        final long limit = publisherLimit.getVolatile();

        final int initialPartitionId = logBuffer.getInitialPartitionId();
        final int activePartitionId = logBuffer.getActivePartitionIdVolatile();
        final int partitionIndex = (activePartitionId - initialPartitionId) % logBuffer.getPartitionCount();
        final LogBufferPartition partition = logBuffer.getPartition(partitionIndex);

        final int partitionOffset = partition.getTailCounterVolatile();
        final long position = position(activePartitionId, partitionOffset);

        long newPosition = -1;

        if(position < limit)
        {
            int newOffset;

            if (length < maxFrameLength)
            {
                newOffset = logAppender.appendFrame(partition,
                        activePartitionId,
                        msg,
                        start,
                        length,
                        streamId);
            }
            else
            {
                throw new RuntimeException("Message length of "+length+" is larger than max frame length of "+maxFrameLength);
            }

            newPosition = updatePublisherPosition(initialPartitionId, activePartitionId, newOffset);

            publisherPosition.proposeMaxOrdered(newPosition);
        }
        return newPosition;
    }

    public long claim(ClaimedFragment claim, int length)
    {
        return claim(claim, length, 0);
    }

    public long claim(ClaimedFragment claim, int length, int streamId)
    {

        final long limit = publisherLimit.getVolatile();

        final int initialPartitionId = logBuffer.getInitialPartitionId();
        final int activePartitionId = logBuffer.getActivePartitionIdVolatile();
        final int partitionIndex = (activePartitionId - initialPartitionId) % logBuffer.getPartitionCount();
        final LogBufferPartition partition = logBuffer.getPartition(partitionIndex);

        final int partitionOffset = partition.getTailCounterVolatile();
        final long position = position(activePartitionId, partitionOffset);

        long newPosition = -1;

        if(position < limit)
        {
            int newOffset;

            if (length < maxFrameLength)
            {
                newOffset = logAppender.claim(partition,
                        activePartitionId,
                        claim,
                        length,
                        streamId);
            }
            else
            {
                throw new RuntimeException("Cannot claim more than "+maxFrameLength+ " bytes.");
            }

            newPosition = updatePublisherPosition(initialPartitionId, activePartitionId, newOffset);

            publisherPosition.proposeMaxOrdered(newPosition);
        }

        return newPosition;

    }

    protected long updatePublisherPosition(final int initialPartitionId, final int activePartitionId, int newOffset)
    {
        long newPosition = -1;

        if(newOffset > 0)
        {
            newPosition = position(activePartitionId, newOffset);
        }
        else if(newOffset == -2)
        {
            logBuffer.onActiveParitionFilled(activePartitionId);
            newPosition = -2;
        }

        return newPosition;
    }

    public long subscriberLimit(Subscription subscription)
    {
        long limit = -1;

        if(mode == MODE_PUB_SUB)
        {
            limit = publisherPosition.get();
        }
        else
        {
            final int subscriberId = subscription.getSubscriberId();
            if(subscriberId == 0)
            {
                limit = publisherPosition.get();
            }
            else
            {
                // in pipelining mode, a subscriber's limit is the position of the
                // previous subscriber
                limit = subscriptions[subscriberId - 1].getPosition();
            }
        }

        return limit;
    }

    public int updatePublisherLimit()
    {
        long lastSubscriberPosition = -1;

        if(subscriptions.length > 0)
        {
            lastSubscriberPosition = subscriptions[subscriptions.length -1].getPosition();

            if (MODE_PUB_SUB == mode && subscriptions.length > 1)
            {
                for (int i = 0; i < subscriptions.length - 1; i++)
                {
                    lastSubscriberPosition = Math.min(lastSubscriberPosition, subscriptions[i].getPosition());
                }
            }
        }
        else
        {
            lastSubscriberPosition = publisherLimit.get() - logWindowLength;
        }

        int partitionId = partitionId(lastSubscriberPosition);
        int partitionOffset = partitionOffset(lastSubscriberPosition) + logWindowLength;
        if(partitionOffset >= logBuffer.getPartitionSize())
        {
            ++partitionId;
            partitionOffset = logWindowLength;

        }
        final long proposedPublisherLimit = position(partitionId, partitionOffset);

        if(publisherLimit.proposeMaxOrdered(proposedPublisherLimit))
        {
            return 1;
        }

        return 0;
    }

    public synchronized Subscription openSubscription()
    {
        return doOpenSubscription();
    }

    public Subscription doOpenSubscription()
    {
        final Subscription[] newSubscriptions = new Subscription[subscriptions.length + 1];
        System.arraycopy(subscriptions, 0, newSubscriptions, 0, subscriptions.length);

        final int subscriberId = newSubscriptions.length -1;
        final Subscription subscription = new Subscription(new AtomicLongPosition(), subscriberId, this);

        newSubscriptions[subscriberId] = subscription;

        this.subscriptions = newSubscriptions;

        return subscription;
    }

    public void doCloseSubscription(Subscription subscriptionToClose)
    {
        int index = subscriptionToClose.getSubscriberId();
        int len = subscriptions.length;
        if(mode == MODE_PIPELINE && index != len -1)
        {
            throw new RuntimeException("Cannot close subscriptions out of order when in pipelining mode");
        }

        for (int i = 0; i < len; i++)
        {
            if(subscriptionToClose == subscriptions[i])
            {
                index = i;
                break;
            }
        }

        Subscription[] newSubscriptions = null;

        final int numMoved = len - index - 1;

        if (numMoved == 0)
        {
            newSubscriptions = Arrays.copyOf(subscriptions, len - 1);
        }
        else
        {
            newSubscriptions = new Subscription[len - 1];
            System.arraycopy(subscriptions, 0, newSubscriptions, 0, index);
            System.arraycopy(subscriptions, index + 1, newSubscriptions, index, numMoved);
        }

        this.subscriptions = newSubscriptions;
    }

    public void closeSubscription(Subscription subscriptionToClose)
    {
       final CompletableFuture<Void> future = new CompletableFuture<Void>();

       context.getDispatcherCommandQueue().add((d) -> d.closeSubscription(subscriptionToClose, future));

       future.join();
    }

    public LogBuffer getLogBuffer()
    {
        return logBuffer;
    }

    public void setPublisherLimitOrdered(int limit)
    {
        this.publisherLimit.setOrdered(limit);

    }

    public void close()
    {
      publisherLimit.close();
      publisherPosition.close();

      for (Subscription subscription : subscriptions)
      {
          subscription.close();
      }

      logBuffer.close();
      context.close();
    }

    public int getMaxFrameLength()
    {
        return maxFrameLength;
    }

    public long getPublisherPosition()
    {
        return publisherPosition.get();
    }

    public int getSubscriberCount()
    {
        return subscriptions.length;
    }

    @Override
    public String toString()
    {
        return "Dispatcher ["+name+"]";
    }

}
