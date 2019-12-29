## TreeSet

### 完整源码解析

[TreeSet](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/TreeSet.java)

### 小结

TreeSet 是实现了 Set 接口的集合类，集合中不允许出现重复元素。

TreeSet 完全基于 TreeMap 实现（将 TreeMap 实例作为一个属性），将 Map 中的 key 用来存储元素。
