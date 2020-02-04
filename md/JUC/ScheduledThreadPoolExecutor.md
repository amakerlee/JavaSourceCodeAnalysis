## ScheduledThreadPoolExecutor

### 继承结构及完整源码解析

[Executor](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/Executor.java) | [ExecutorService](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ExecutorService.java) | [AbstractExecutorService](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/AbstractExecutorService.java) | [ThreadPoolExecutor](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ThreadPoolExecutor.java) | [ScheduledExecutorService](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ScheduledExecutorService.java) | [ScheduledThreadPoolExecutor](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ScheduledThreadPoolExecutor.java)

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ScheduledThreadPoolExecutor.png" width=70% />

### 重要方法

* schedule：达到给定延迟时间后执行任务

* scheduleAtFixedRate：定时任务。从上一个任务开始时计时，指定时间间隔过去后，如果上一个任务已经执行完毕，马上开始下一个任务，如果没有执行完毕，等上一个任务执行完后开启下一个任务。

* scheduleWithFixedDelay：达到延迟之后开始定期执行任务。上一个任务执行结束后到下一个任务开始之间，时间间隔为指定参数 delay。

### 参考

[JUC源码分析-线程池篇（三）：ScheduledThreadPoolExecutor](https://www.jianshu.com/p/8c97953f2751)
