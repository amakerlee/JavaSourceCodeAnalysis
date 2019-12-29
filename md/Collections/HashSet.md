## HashSet

### 完整源码解析

[HashSet](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/HashSet.java)

### 小结

HashSet 是实现了 Set 接口的集合类，集合中不允许出现重复元素。

HashSet 完全基于 HashMap 实现（将 HashMap 实例作为一个属性），将 Map 中的 key 用来存储元素。
