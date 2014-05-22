package org.threadly.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.threadly.concurrent.collections.ConcurrentArrayList;
import org.threadly.util.ListUtils;

/**
 * <p>Executor which has no threads itself.  This allows you to have the same 
 * scheduler abilities (schedule tasks, recurring tasks, etc, etc), without having 
 * to deal with multiple threads, memory barriers, or other similar concerns.  
 * This class can be very useful in GUI development (if you want it to run on the GUI 
 * thread).  It also can be useful in android development in a very similar way.</p>
 * 
 * <p>The tasks in this scheduler are only progressed forward with calls to .tick().  
 * Since it is running on the calling thread, calls to .wait() and .sleep() from sub 
 * tasks will block (possibly forever).  The call to .tick() will not unblock till there 
 * is no more work for the scheduler to currently handle.</p>
 * 
 * @author jent - Mike Jensen
 * @since 2.0.0
 */
public class NoThreadScheduler extends AbstractSubmitterScheduler 
                               implements SchedulerServiceInterface {
  protected static final int QUEUE_FRONT_PADDING = 0;
  protected static final int QUEUE_REAR_PADDING = 2;
  
  protected final boolean tickBlocksTillAvailable;
  protected final ConcurrentArrayList<TaskContainer> taskQueue;
  protected final ClockWrapper clockWrapper;
  
  /**
   * Constructs a new {@link NoThreadScheduler} scheduler.
   * 
   * @param tickBlocksTillAvailable true if calls to .tick() should block till there is something to run
   */
  public NoThreadScheduler(boolean tickBlocksTillAvailable) {
    this.tickBlocksTillAvailable = tickBlocksTillAvailable;
    taskQueue = new ConcurrentArrayList<TaskContainer>(QUEUE_FRONT_PADDING, 
                                                       QUEUE_REAR_PADDING);
    clockWrapper = new ClockWrapper();
  }

  /**
   * Abstract call to get the value the scheduler should use to represent the current time.  
   * This can be overridden if someone wanted to artificially change the time.
   * 
   * @param estimateOkay true if it is okay to just estimate the time
   * @return current time in milliseconds
   */
  protected long nowInMillis() {
    return clockWrapper.getSemiAccurateTime();
  }
  
  /**
   * Called to indicate that we are about to insert or reposition a value in 
   * the queue.  It is critical that until "endInsertion" nowInMillis calls will 
   * return the exact same value.
   */
  protected void startInsertion() {
    clockWrapper.stopForcingUpdate();
  }
  
  /**
   * Called after insertion or reposition of a task has completed.
   */
  protected void endInsertion() {
    clockWrapper.resumeForcingUpdate();
  }
  
  /**
   * Progresses tasks for the current time.  This will block as it runs
   * as many scheduled or waiting tasks as possible.  It is CRITICAL that 
   * only one thread at a time calls the .tick() function.  While this class 
   * is in general thread safe, if multiple threads call .tick() at the same 
   * time, it is possible a given task may run more than once.  In order to 
   * maintain high performance, threadly does not guard against this condition.
   * 
   * Depending on how this class was constructed, this may or may not block 
   * if there are no tasks to run yet.
   * 
   * If any tasks throw a RuntimeException, they will be bubbled up to this 
   * tick call.  Any tasks past that task will not run till the next call to 
   * tick.  So it is important that the implementor handle those exceptions.  
   * 
   * This call is NOT thread safe, calling tick in parallel could cause the 
   * same task to be run multiple times in parallel.
   * 
   * @return qty of tasks run during this tick call
   * @throws InterruptedException thrown if thread is interrupted waiting for task to run
   *           (this can only throw if constructed with a true to allow blocking)
   */
  public int tick() throws InterruptedException {
    int tasks = 0;
    while (true) {  // will break from loop at bottom
      TaskContainer nextTask;
      while ((nextTask = getNextReadyTask()) != null) {
        tasks++;
        
        // call will remove task from queue, or reposition as necessary
        nextTask.runTask();
      }
      
      if (tickBlocksTillAvailable && tasks == 0) {
        synchronized (taskQueue.getModificationLock()) {
          nextTask = taskQueue.peekFirst();
          if (nextTask == null) {
            taskQueue.getModificationLock().wait();
          } else {
            long nextTaskDelay = nextTask.getDelay(TimeUnit.MILLISECONDS);
            if (nextTaskDelay > 0) {
              taskQueue.getModificationLock().wait(nextTaskDelay);
            }
          }
        }
      } else {
        // we ran a task, or don't want to block, so return
        return tasks;
      }
    }
  }

  @Override
  protected void doSchedule(Runnable task, long delayInMillis) {
    add(new OneTimeTask(task, delayInMillis));
  }

  @Override
  public void scheduleWithFixedDelay(Runnable task, 
                                     long initialDelay, 
                                     long recurringDelay) {
    if (task == null) {
      throw new IllegalArgumentException("Task can not be null");
    } else if (initialDelay < 0) {
      throw new IllegalArgumentException("initialDelay can not be negative");
    } else if (recurringDelay < 0) {
      throw new IllegalArgumentException("recurringDelay can not be negative");
    }
    
    add(new RecurringTask(task, initialDelay, recurringDelay));
  }
  
  protected void add(TaskContainer runnable) {
    synchronized (taskQueue.getModificationLock()) {
      startInsertion();
      try {
        // we can only change delay between start/end insertion calls
        runnable.setInitialDelay();
        
        int insertionIndex = ListUtils.getInsertionEndIndex(taskQueue, runnable, true);
          
        taskQueue.add(insertionIndex, runnable);
      } finally {
        endInsertion();
      }
      
      taskQueue.getModificationLock().notifyAll();
    }
  }
  
  @Override
  public boolean remove(Runnable task) {
    synchronized (taskQueue.getModificationLock()) {
      return ContainerHelper.remove(taskQueue, task);
    }
  }
  
  @Override
  public boolean remove(Callable<?> task) {
    synchronized (taskQueue.getModificationLock()) {
      return ContainerHelper.remove(taskQueue, task);
    }
  }

  @Override
  public boolean isShutdown() {
    return false;
  }
  
  /**
   * Call to get the next task that is ready to be run.  If there are no 
   * tasks, or the next task still has a remaining delay, this will return 
   * null.
   * 
   * @return next ready task, or null if there are none
   */
  protected TaskContainer getNextReadyTask() {
    TaskContainer nextTask = taskQueue.peekFirst();
    if (nextTask != null && nextTask.getDelay(TimeUnit.MILLISECONDS) <= 0) {
      return nextTask;
    } else {
      return null;
    }
  }
  
  /**
   * Checks if there are tasks ready to be run on the scheduler.  If this returns 
   * true, the next .tick() call is guaranteed to run at least one task.
   * 
   * @return true if there are task waiting to run.
   */
  public boolean hasTaskReadyToRun() {
    return getNextReadyTask() != null;
  }
  
  /**
   * Removes any tasks waiting to be run.  Will not interrupt any tasks currently running if 
   * .tick() is being called.  But will avoid additional tasks from being run on the current 
   * .tick() call.
   */
  public void clearTasks() {
    taskQueue.clear();
  }
  
  /**
   * <p>Container abstraction to hold runnables for scheduler.</p>
   * 
   * @author jent - Mike Jensen
   * @since 1.0.0
   */
  protected abstract class TaskContainer extends AbstractDelayed 
                                         implements RunnableContainerInterface {
    protected final Runnable runnable;
    
    protected TaskContainer(Runnable runnable) {
      this.runnable = runnable;
    }

    @Override
    public Runnable getContainedRunnable() {
      return runnable;
    }
    
    protected void runTask() {
      prepareForRun();
      
      runnable.run();
    }
    
    protected abstract void prepareForRun();
    
    protected abstract void setInitialDelay();
  }
  
  /**
   * <p>Runnable container for runnables that only run once
   * with an optional delay.</p>
   * 
   * @author jent - Mike Jensen
   * @since 1.0.0
   */
  protected class OneTimeTask extends TaskContainer {
    private final long delay;
    private long runTime;
    
    public OneTimeTask(Runnable runnable, long delay) {
      super(runnable);
      
      this.delay = delay;
      this.runTime = -1;
    }
    
    @Override
    protected void setInitialDelay() {
      runTime = nowInMillis() + delay;
    }
    
    @Override
    protected void prepareForRun() {
      synchronized (taskQueue.getModificationLock()) {
        // can be removed since this is a one time task
        taskQueue.remove(this);
      }
    }

    @Override
    public long getDelay(TimeUnit timeUnit) {
      return timeUnit.convert(runTime - nowInMillis(), 
                              TimeUnit.MILLISECONDS);
    }
  }
  
  /**
   * <p>Container for runnables which run multiple times.</p>
   * 
   * @author jent - Mike Jensen
   * @since 1.0.0
   */
  protected class RecurringTask extends TaskContainer {
    private final long initialDelay;
    private final long recurringDelay;
    private long nextRunTime;
    
    public RecurringTask(Runnable runnable, long initialDelay, long recurringDelay) {
      super(runnable);
      
      this.initialDelay = initialDelay;
      this.recurringDelay = recurringDelay;
      nextRunTime = -1;
    }
    
    @Override
    protected void setInitialDelay() {
      nextRunTime = nowInMillis() + initialDelay;
    }
    
    @Override
    public void prepareForRun() {
      synchronized (taskQueue.getModificationLock()) {
        startInsertion();
        try {
          int insertionIndex = ListUtils.getInsertionEndIndex(taskQueue, recurringDelay, true);
          
          /* provide the option to search backwards since the item 
           * will most likely be towards the back of the queue */
          taskQueue.reposition(this, insertionIndex, false);
          
          nextRunTime = nowInMillis() + recurringDelay;
        } finally {
          endInsertion();
        }
      }
    }

    @Override
    public long getDelay(TimeUnit timeUnit) {
      return timeUnit.convert(nextRunTime - nowInMillis(), 
                              TimeUnit.MILLISECONDS);
    }
  }
}
