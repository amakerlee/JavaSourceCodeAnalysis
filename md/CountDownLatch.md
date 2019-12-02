### CountDownLatch

***
> 总结


CountDownLatch还提供了超时等待机制，在特定时间后就不会再阻塞当前线程；不可能重新初始化或者修改CountDownLatch对象的内部计数器的值。一个线程调用countDown方法happen-before，另外一个线程调用await方法。

CountDownLatch底层实现依赖于AQS共享锁的实现机制，首先初始化计数器count，调用countDown()方法时，计数器count减1，当计数器count等于0时，会唤醒AQS等待队列中的线程。调用await()方法，线程会被挂起，它会等待直到count值为0才继续执行，否则会加入到等待队列中，等待被唤醒。