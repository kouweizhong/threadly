package org.threadly.test.concurrent;

import java.util.concurrent.Executor;

import org.threadly.test.concurrent.lock.TestableLock;

/**
 * An interface for executor which can handle VirtualRunnable's and VirtualCallables 
 * in a testing situation.
 * 
 * @author jent - Mike Jensen
 */
public interface TestableExecutor extends Executor {
  /**
   * should only be called from TestableLock.
   * 
   * @param lock lock referencing calling into scheduler
   * @throws InterruptedException thrown if the thread is interrupted while blocking
   */
  public void handleWaiting(TestableLock lock) throws InterruptedException;
  
  /**
   * should only be called from TestableLock.
   * 
   * @param lock lock referencing calling into scheduler
   * @param waitTimeInMs time to wait on lock
   * @throws InterruptedException thrown if the thread is interrupted while blocking
   */
  public void handleWaiting(final TestableLock lock, 
                            long waitTimeInMs) throws InterruptedException;

  /**
   * should only be called from TestableLock.
   * 
   * @param lock lock referencing calling into scheduler
   */
  public void handleSignal(TestableLock lock);

  /**
   * should only be called from TestableVirtualLock.
   * 
   * @param lock lock referencing calling into scheduler
   */
  public void handleSignalAll(TestableLock lock);

  /**
   * should only be called from TestableLock.
   * 
   * @param sleepTime time for thread to sleep
   * @throws InterruptedException thrown if the thread is interrupted while sleeping
   */
  public void handleSleep(long sleepTime) throws InterruptedException;
}