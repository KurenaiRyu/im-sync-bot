package kurenai.mybot.cache;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DelayItem<T> implements Delayed {

  /**
   * Base of nanosecond timings, to avoid wrapping
   */
  private static final long NANO_ORIGIN = System.nanoTime();

  /**
   * Returns nanosecond time offset by origin
   */
  static long now() {
    return System.nanoTime() - NANO_ORIGIN;
  }

  /**
   * Sequence number to break scheduling ties, and in turn to guarantee FIFO order among tied entries.
   */
  private static final AtomicLong sequencer = new AtomicLong(0);
  /**
   * Sequence number to break ties FIFO
   */
  private final        long       sequenceNumber;
  /**
   * The time the task is enabled to execute in nanoTime units
   */
  private final        long       time;
  private final        T          item;

  public DelayItem(T submit, long timeout) {
    this.time = now() + timeout;
    this.item = submit;
    this.sequenceNumber = sequencer.getAndIncrement();
  }

  public DelayItem(T submit, LocalDateTime expiration) {
    this.time = expiration.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1000000
            - DelayItem.NANO_ORIGIN;
    this.item = submit;
    this.sequenceNumber = sequencer.getAndIncrement();
  }

  public T getItem() {
    return this.item;
  }

  @Override
  public long getDelay(TimeUnit unit) {
    return unit.convert(time - now(), TimeUnit.NANOSECONDS);
  }

  @Override
  public int compareTo(@NotNull Delayed other) {
    if (other == this) { // compare zero ONLY if same object
      return 0;
    }
    if (other instanceof DelayItem) {
      DelayItem x    = (DelayItem) other;
      long      diff = time - x.time;
      if (diff < 0) {
        return -1;
      } else if (diff > 0) {
        return 1;
      } else if (sequenceNumber < x.sequenceNumber) {
        return -1;
      } else {
        return 1;
      }
    }
    long d = (getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS));
    return (d == 0) ? 0 : ((d < 0) ? -1 : 1);
  }
}