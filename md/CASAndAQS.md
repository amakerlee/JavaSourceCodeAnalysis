### CAS

***
> CAS 原理

> CAS 的问题


### AQS

***
> 完整源码解析

[AbstractOwnableSynchronizer](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/AbstractOwnableSynchronizer.java) | [AbstractQueuedSynchronizer](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/AbstractQueuedSynchronizer.java)

***
> 内部类


> 类属性


> 成员函数

AbstractQueuedSynchronizer并不实现任何同步接口，它提供了一些可以被具体实现类直接调用的一些原子操作方法来重写相应的同步逻辑。AQS同时提供了互斥模式（exclusive）和共享模式（shared）两种不同的同步逻辑。一般情况下，子类只需要根据需求实现其中一种模式，当然也有同时实现两种模式的同步类，如ReadWriteLock


从setHead()的实现以及所有调用的地方可以看出，head指向的节点必定是拿到锁（或是竞争资源）的节点，而head的后继节点则是有资格争夺锁的节点，再后续的节点，就是阻塞着的了。
head指向的节点，曾经关联的线程必定已经获取到资源，在执行了，所以head无需再关联到该线程了。head所指向的节点，也无需再参与任何的竞争操作了。