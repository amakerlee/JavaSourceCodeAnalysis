## SynchronousQueue

没有容量的阻塞队列，每个插入操作都要等待其他线程的删除操作，每个删除操作都要等待插入操作，实际相当于将数据从一个线程传递到另一个线程。包括公平和非公平两种模式。

公平模式通过队列（FIFO）实现，非公平模式通过栈（LIFO）实现。

### 完整源码解析

[SynchronousQueue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/JUCCollections/SynchronousQueue.java)

### 类属性


### 成员函数


### 参考

[死磕 java集合之SynchronousQueue源码分析](https://www.cnblogs.com/tong-yuan/p/SynchronousQueue.html)


