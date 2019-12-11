### CopyOnWriteArrayList

***
> 总结

CopyOnWriteArrayList是线程安全的

    CopyOnWriteArrayList使用了一种叫写时复制的方法，
        当有新元素添加到CopyOnWriteArrayList时，
        先从原有的数组中拷贝一份出来，然后在新的数组做写操作，
        写完之后，再将原来的数组引用指向到新数组。
            创建新数组，并往新数组中加入一个新元素,这个时候，array这个引用仍然是指向原数组的。​​​​​​​
            当元素在新数组添加成功后，将array这个引用指向新数组。
        CopyOnWriteArrayList的整个add操作都是在锁的保护下进行的。
            这样做是为了避免在多线程并发add的时候，
            复制出多个副本出来,把数据搞乱了，导致最终的数组数据不是我们期望的。

由于所有的写操作都是在新数组进行的，这个时候如果有线程并发的写，则通过锁来控制，

如果有线程并发的读，则分几种情况： 

    1、如果写操作未完成，那么直接读取原数组的数据； 
    2、如果写操作完成，但是引用还未指向新数组，那么也是读取原数组数据； 
    3、如果写操作完成，并且引用已经指向了新的数组，那么直接从新数组中读取数据。

可见，CopyOnWriteArrayList的读操作是可以不用加锁的。
CopyOnWriteArrayList 有几个缺点： 

 

    由于写操作的时候，需要拷贝数组，会消耗内存，
        如果原数组的内容比较多的情况下，可能导致young gc或者full gc
    不能用于实时读的场景，像拷贝数组、新增元素都需要时间，
        所以调用一个set操作后，读取到数据可能还是旧的,
        虽然CopyOnWriteArrayList 能做到最终一致性,但是还是没法满足实时性要求；

CopyOnWriteArrayList 合适读多写少的场景，不过这类慎用 

    因为谁也没法保证CopyOnWriteArrayList 到底要放置多少数据，
    万一数据稍微有点多，每次add/set都要重新复制数组，这个代价实在太高昂了。
    在高性能的互联网应用中，这种操作分分钟引起故障。

CopyOnWriteArrayList透露的思想

1、读写分离，读和写分开 
2、最终一致性 
3、使用另外开辟空间的思路，来解决并发冲突

***
> 引用

* [CopyOnWriteArrayList是线程安全的](https://my.oschina.net/u/3847203/blog/2988423)

