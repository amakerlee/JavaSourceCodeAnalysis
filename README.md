### Java Collections in java.util

#### List, Stack and Queue

* [ArrayList](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/ArrayList.md) | [LinkedList](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/LinkedList.md)

* [Stack](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/Stack.md)

* [ArrayDeque](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/ArrayDeque.md)

* [PriorityQueue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/PriorityQueue.md)

#### Set

* [HashSet（未完成）](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/HashSet.md) | [TreeSet（未完成）](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/TreeSet.md)

#### Map

* [TreeMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/TreeMap.md) | [HashMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/HashMap.md) | [LinkedHashMap（未完成）](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/LinkedHashMap.md)

&nbsp;

### Java Concurrency Tools in java.util.concurrent

#### CAS, AQS and ThreadLocal

* [CAS](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/CASAndAQS.md)

    > CAS（Compare And Swap，比较和交换），是基于乐观锁的操作，不需要阻塞就可以实现原子操作的一种方式。

* [AQS](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/CASAndAQS.md)

    > AbstractQueuedSynchronizer 抽象类，队列同步控制器，是 Java 并发用来控制锁和其他同步组件的基础框架

* [ThreadLocal](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/ThreadLocal.md)

    > ThreadLocal 是属于 java.lang 包的类。它为每个使用该变量的线程提供独立的变量副本，所以每一个线程都可以独立地改变自己的副本，而不会影响其它线程所对应的副本。可以简单地理解为为指定线程存储数据，只有指定线程可以读取。

#### Lock

* [ReentrantLock](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/ReentrantLock.md) | [ReentrantReadWriteLock](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/ReentrantReadWriteLock.md)

    > ReentrantLock 是 Lock 接口的实现，翻译为可重入锁，支持同一个线程重入，并在获取和释放时记录重入次数。ReentrantReadWriteLock 是 Lock 接口的实现，翻译为可重入读写锁，实现了可重入读锁和可重入写锁，也即共享锁和互斥锁。

* [CountDownLatch](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/CountDownLatch.md) | [CyclicBarrier](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/CyclicBarrier.md) | [Semaphore](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/Semaphore.md)

    > 基于 AQS 实现的三个同步辅助类，用于线程计数、线程等待、并行处理等场景。

#### Concurrency Collections

* [CopyOnWriteArrayList](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/JUCCollections/CopyOnWriteArrayList.md)
    
    > 对应于常用集合中的 ArrayList，使用 COW（Copy On Write，写时复制）保证线程安全。

* [ConcurrentHashMap（未完成）](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/JUCCollections/ConcurrentHashMap.md) | [ConcurrentSkipListMap（未完成）](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/JUCCollections/ConcurrentSkipListMap.md)

    > ConcurrentHashMap 对应于常用集合中的 HashMap，ConcurrentSkipListMap 是基于跳跃表（SkipList）的 Map 集合。

* [ArrayBlockingQueue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/JUCCollections/ArrayBlockingQueue.md) | [LinkedBlockingQueue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/JUCCollections/LinkedBlockingQueue.md) | [LinkedBlockingDeque（未完成）](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/JUCCollections/LinkedBlockingDeque.md) | [PriorityBlockingQueue（未完成）](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/JUCCollections/PriorityBlockingQueue.md)

    > BlockingQueue 的四个具体实现，阻塞队列的实现都使用了显式锁保证线程安全。ArrayBlockingQueue 是基于数组的有界阻塞队列，LinkedBlockingQueue 是基于链表的单向有界阻塞队列，LinkedBlockingQueue 是基于链表的双向有界阻塞队列，PriorityBlockingQueue 是基于堆（数组）的优先级阻塞队列（对应常用集合里的 PriorityQueue）。

* [ConcurrentLinkedQueue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/JUCCollections/ConcurrentLinkedQueue.md) | [ConcurrentLinkedDeque](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/JUCCollections/ConcurrentLinkedDeque.md)

    > 不使用显式锁，而使用 CAS 构建的两个阻塞队列，不需要阻塞线程就能实现线程安全。基础数据结构为链表。

#### Thread Pool

* [ThreadPoolExecutor](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/ThreadPoolExecutor.md) | [ScheduledThreadPoolExecutor（未完成）](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/ScheduledThreadPoolExecutor.md)

* [ForkJoinPool（未完成）](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/ThreadPoolExecutor.md)
