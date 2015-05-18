Threadly
========

A library of java tools to assist with development of concurrent java applications. It includes a collection of tools to help with a wide range of concurrent development and testing needs. This is designed to be a complement to java.util.concurrent and uses java.util.concurrent to help assist in it's implementations where it makes sense.

For information about compiling, importing into eclipse, or contributing to the project, please look at the 'BUILD_INSTRUCTIONS' file.

If you find this library useful, feel free to donate LTC/Litecoin to: LiTeCoinLRQrrP2B6xAq2aZ5WFHpHc28Ry

For a complete list of features in threadly please view the features page on the wiki:

https://github.com/threadly/threadly/wiki/Threadly-Features

-- General Concurrency Tools --

*    PriorityScheduler - A thread pool which makes different trade offs from java.util.concurrent.ScheduledThreadPoolExecutor.  It offers a few advantages and disadvantages.  Often times it can be better performing, or at least equally performing.

Advantages compared to ScheduledThreadPoolExecutor:

Better .execute task performance.  Because it uses different structures for scheduled/recurring tasks from execute task we are able to use a structure which fits the job better.  This provides a DRAMATIC improvement in the performance of executed jobs.

The ability to provide a priority with a task means that things which are more critical are impacted less by things which are recurring or a delay wont matter as long as those low priority tasks are not starved.  Low priority tasks will delay longer if there is high usage on the pool, until they reach their maximum wait time (assuming that high priority tasks are not further delayed).  Using multiple priorities also reduces lock contention between the different priorities.

PriorityScheduler is only focused on the Runnable provided into it.  So for example removing a task you can provide the original runnable (not a returned future).  The runnables returned in .shutdownNow() are the original runnables, not wrapped in future tasks, etc, etc.

PriorityScheduler provides calls that do, and do not return a future, so if a future is not necessary the performance hit can be avoided.

If you need a thread pool that implements java.util.concurrent.ScheduledExecutorService you can wrap it in "PrioritySchedulerServiceWrapper".

The other large difference compared to ScheduledThreadPoolExecutor is that the pool size is adjustable at runtime.  In ScheduledThreadPoolExecutor you can only provide one size, and that pool can grow, but never shrink once started.  In this implementation you construct with a start size, and if you ever want to adjust the size it can be done at any point with calls to "setPoolSize".  This will NEVER interrupt or stop running tasks, but as they finish the threads will be destroyed.

*    ExecutorLimiter, SimpleSchedulerLimiter, SchedulerServiceLimiter and PrioritySchedulerLimiter - These are designed so you can control the amount of concurrency in different parts of code, while still taking maximum benefit of having one large thread pool.

The design is such so that you create one large pool, and then wrap it in one of these two wrappers.  You then pass the wrapper to your different parts of code.  It relies on the large pool in order to actually get a thread, but this prevents any one section of code from completely dominating the thread pool.

*    KeyDistributedExecutor and KeyDistributedScheduler - Provide you the ability to execute (or schedule) tasks with a given key such that tasks with the same key hash code will NEVER run concurrently. This is designed as an ability to help the developer from having to deal with concurrent issues when ever possible. It allows you to have multiple runnables or tasks that share memory, but don't force the developer to deal with synchronization and memory barriers (assuming they all share the same key).  These now also allow you to continue to use Future's with the key based execution.

*    NoThreadScheduler - Sometimes even one thread is too many.  This provides you the ability to schedule tasks, or execute tasks on the scheduler, but they wont be run till you call .tick() on the scheduler.  This allows you to control which thread these tasks run on (since you have to explicitly call the .tick()).  A great example of where this could be useful is if you want to schedule tasks which can only run on a GUI thread.  Another example would be in NIO programming, where you want to modify the selector, you can just call .tick() before you call .select() on the selector to apply any modifications you need in a thread safe way (without worrying about blocking).

*    ConcurrentArrayList - A thread safe array list that also implements a Dequeue. It may be better performing than a CopyOnWriteArrayList depending on what the use case is. It is able to avoid copies for some operations, primarily adding and removing from the ends of a list (and can be tuned for the specific application to possibly make copies very rare).

*    FutureUtils - Provides some nice utilities for working with futures. It provides many things when dealing with collections of futures, for example canceling any which have not finished in a collection. A couple of other the nice operations are the ability to combine several futures, and either block till all have finished, or get a future which combines all the results for once they have completed. As well as being able to get different formats (for example give me a future which provides a list of all futures in the collection which had an error).

*    ListenerHelper and RunnableListenerHelper - Listeners are a very common design pattern for asynchronous designs. ListenerHelper helps in building these designs (no matter what the interface for the listeners is), RunnableListenerHelper is a very efficent implementation designed around the common "Runnable" interface.  I am sure we have all done similar implementations a million times, this is one robust implementation that will hopefully reduce duplicated code in the future.  In addition there are varriants of these, "AsyncCallListenerHelper", and "DefaultExecutorListenerHelper" (the same exists for the Runnable version as well) which allow different threading designs around how listeners are called.

-- Debugging utilities --

*    DebugLogger - often times logging gives a bad conception of what order of operations things are happening in concurrent designs. Or often times just adding logging can cause race conditions to disappear. DebugLogger attempts to solve those problems (or at least help). It does this by collecting log messages, storing the time which they came in (in nano seconds), and then order them. Then in batches it prints the log messages out. Since we are logging out asynchronously, and we try to have the storage of the log message very cheap, it hopefully will not impact the race condition your attempting to investigate.

*    Profiler and ControlledThreadProfiler - These are utilities for helping to understand where the bottlenecks of your application are. These break down what each thread is doing, as well as the system as a whole. So you can understand where your CPU heavy operations are, where your lock contention exists, and other resources as well.


-- Unit Test Tools --

*    AsyncVerifier - Used to verify operations which occurred asynchronously.  The AsyncVerifier allows you to assert failures/successes in other threads, and allow the main test thread to throw exceptions if any failures occur.  Thus blocking the main test thread until the sub-threads have completed.

*    TestCondition - often times in doing unit test for asynchronous operations you have to wait for a condition to be come true. This class gives a way to easily wait for those conditions to be true, or throw an exception if they do not happen after a given timeout. The implementation of TestRunnable gives a good example of how this can be used.

*    TestRunnable - a runnable structure that has common operations already implemented. It gives two functions handleRunStart and handleRunFinish to allow people to optionally override to provide any test specific operation which is necessary. You can see many examples in our own unit test code of how we are using this.

*    TestableScheduler - this is very similar to "NoThreadScheduler", except it allows you to supply the time, thus allowing things to happen faster than real time. If you have an implementation with a recurring task and you want to unit test what happens when it runs the 5th time, while keeping the unit test fast, you can supply a time faster than real time to cause these executions.
