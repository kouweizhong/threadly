package org.threadly.util;

/**
 * This is a utility class for low-resolution timing which avoids
 * frequent System.currentTimeMillis() calls (which perform poorly 
 * because they require system calls).
 *
 * Each call to Clock.lastKnownTimeMillis() will return the value of
 * System.currentTimeMillis() as of the last call to Clock.accurateTime().
 * This means lastKnownTimeMillis() will only be as accurate as the
 * frequency with which accurateTime() is called.
 */
public class Clock {
  protected static final boolean UPDATE_CLOCK_AUTOMATICALLY = true;
  protected static final int AUTOMATIC_UPDATE_FREQUENCY_IN_MS = 100;
  
  private static final Object UPDATE_LOCK = new Object();
  private static volatile long now = System.currentTimeMillis();
  private static volatile boolean updateClock = false;
  
  static {
    if (UPDATE_CLOCK_AUTOMATICALLY) {
      startClockUpdateThread();
    }
  }
  
  protected static void startClockUpdateThread() {
    synchronized (UPDATE_LOCK) {
      if (updateClock) {
        return;
      } else {
        updateClock = true;
        
        Thread thread = new Thread() {
            public void run() {
              synchronized (UPDATE_LOCK) {
                while (updateClock) {
                  try {
                    accurateTime();
                    UPDATE_LOCK.wait(AUTOMATIC_UPDATE_FREQUENCY_IN_MS);
                  } catch (InterruptedException ignored) { }
                }
              }
            }
          };
    
        thread.setDaemon(true);
        thread.start();
      }
    }
  }
  
  protected static void stopClockUpdateThread() {
    synchronized (UPDATE_LOCK) {
      updateClock = false;
      
      UPDATE_LOCK.notifyAll();
    }
  }

  public static long lastKnownTimeMillis() {
    return now;
  }

  public static long accurateTime() {
    return now = System.currentTimeMillis();
  }
}
