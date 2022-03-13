

# ZooKeeper客户端源码（三）——Watcher注册与通知

![客户端Watcher注册与通知](https://gitee.com/stefanpy/myimg/raw/master/img/%E5%AE%A2%E6%88%B7%E7%AB%AFWatcher%E6%B3%A8%E5%86%8C%E4%B8%8E%E9%80%9A%E7%9F%A5.png)

`ZooKeeper` 提供了分布式数据的发布/订阅功能。一个典型的发布/订阅模型系统定义了一种一对多的订阅关系，能够让多个订阅者同时监听某一个主题对象，当这个主题对象自身状态变化时，会通知所有订阅者，使它们能够做出相应的处理。

`ZooKeeper` 允许客户端向服务端注册一个 `Watcher` 监听，当服务端的一些更新操作触发了这个 `Watcher`，就会向指定客户端发送一个事件通知来实现分布式的通知功能。

![watcher概念图](https://gitee.com/stefanpy/myimg/raw/master/img/watcher%E6%A6%82%E5%BF%B5%E5%9B%BE.png)

本篇仅基于客户端对`Watcher`注册与通知过程进行讲解，涉及到`Watcher`如何在服务端注册管理，又如何触发事件远程通知客户端的原理后续在讲解服务端源码时会补充。

不过也不要觉得服务端的`Watcher`注册与通知逻辑就复杂，其实也比较简单，这里简单陈述以保内容完整：

`ZooKeeper`的`Watcher`机制是一个跨进程的发布/订阅功能，客户端与服务端都需要保存数据节点和`Watcher`的关系，当节点的状态信息变更时就会触发一些事件，服务端先从自己的内存中找出节点对应的`Watcher`列表，然后一个个遍历生成事件通知消息，再远程发送给客户端；客户端接收到对应消息后，解析出`Wather`事件信息，得知是哪个数据节点，触发什么事件类型，然后客户端同样从内存中找到节点对应的`Watcher`列表，真正触发事件回调。

## 一、基础类

![基础类关系](https://gitee.com/stefanpy/myimg/raw/master/img/%E5%9F%BA%E7%A1%80%E7%B1%BB%E5%85%B3%E7%B3%BB.png)

### 1、Watcher
用户注册`watcher`都需要实现`Watcher`接口，实现`process`方法。
```java
org.apache.zookeeper.Watcher
public interface Watcher {
    void process(WatchedEvent event);
}
```
### 2、WatchedEvent
`process(WatchedEvent event)`的参数是`WatchedEvent`，定义事件信息：

```java
// org.apache.zookeeper.WatchedEvent
public class WatchedEvent {
    private final KeeperState keeperState;
    private final EventType eventType;
    private String path;
	... ...
	/**
	 *  将WatchedEvent转换为可以通过网络发送的类型
	 *  Convert WatchedEvent to type that can be sent over network
	 */
	public WatcherEvent getWrapper() {
		return new WatcherEvent(eventType.getIntValue(), keeperState.getIntValue(), path);
	}
}
```
`WatchedEvent`有3个变量，通知状态`keeperState`、节点事件类型`eventType`、节点`path`：
`keeperState`和`eventType`都是`Watcher`中的枚举类。

#### （1）KeeperState

| KeeperState          | 说明                                                         |
| -------------------- | ------------------------------------------------------------ |
| Disconnected(0)      | 客户端与服务端断开连接                                       |
| SyncConnected(3)     | 客户端与服务端处于连接状态                                   |
| AuthFailed(4)        | 授权失败                                                     |
| ConnectedReadOnly(5) | 客户端连接到只读服务器。接收到这个状态后，唯一允许的操作是读取操作。这个状态只在只读客户端产生，读写客户端是不允许连接只读服务器的 |
| SaslAuthenticated(6) | 用于通知客户端他们已经通过了SaslAuthenticated，以后可以用sasl授权的权限执行Zookeeper动作 |
| Expired(-112)        | 会话超时                                                     |
| Closed(7)            | 客户端已关闭。这个状态永远不会由服务器生成，由客户端本地生成。 |

#### （2）EventType

| EventType                  | 说明                                                         |
| -------------------------- | ------------------------------------------------------------ |
| None(-1)                   | KeeperState为SyncConnected(3)时，表示客户端与服务端成功建立会话 |
| NodeCreated(1)             | 数据节点创建                                                 |
| NodeDeleted(2)             | 数据节点被删除                                               |
| NodeDataChanged(3)         | 数据节点的状态信息更新，即使更新内容一样，版本号，一样会触发 |
| NodeChildrenChanged(4)     | 数据节点的孩子节点列表发生变更，特指子节点个数和组成情况的变更，即新增子节点或删除子节点，而子节点内容的变化是不会触发这个事件的 |
| DataWatchRemoved(5)        | 数据节点的watcher被主动移除                                  |
| ChildWatchRemoved(6)       | 孩子节点的watcher被主动移除                                  |
| PersistentWatchRemoved (7) | 持久有效的watcher被主动移除                                  |

### 3、WatcherEvent
`WatcherEvent`是可以通过网络发送的事件信息封装。

![WatcherEvent](https://gitee.com/stefanpy/myimg/raw/master/img/WatcherEvent.png)

`WatcherEvent`和`WatchedEvent`表示的是同一个事物，都是对一个`watcher`事件信息的封装，不同的是，`WatchedEvent` 是一个逻辑事件，用于服务端和客户端程序执行过程中所需的逻辑对象，而 `WatcherEvent` 因为实现了序列化接口，因此可以用于网络传输：

- `serialize()`，可以将`Watcher`信息序列化到网络字节流中，然后发送到网络中。服务端远程通知客户端`watcher`时使用。
- `deserialize()`，可以从网络字节流中反序列化出`Watcher`信息。客户端接收到服务端远程通知消息时使用。

无论是`WatchedEvent`还是`WatcherEvent`，其对`watcher`事件信息的封装都是极其简单的，客户端无法直接从事件信息中获取对应数据节点的原始数据内容以及变更后的新数据内容，而是需要客户端再次主动去获取数据。

### 4、WatchRegistration
`WatchRegistration`是对`watcher`注册方式的抽象：
![WatchRegistration关系](https://gitee.com/stefanpy/myimg/raw/master/img/WatchRegistration%E5%85%B3%E7%B3%BB.png)
注册的动作是一样的，只是需要注册到不同的集合中，具体继承类，需要实现方法`WatchRegistration#getWatches`，获取相应集合，将`Watcher`加入节点`path`对应的列表中。

如下是抽象类`WatchRegistration`部分代码：
```java 
protected abstract Map<String, Set<Watcher>> getWatches(int rc);

public void register(int rc) {
    if (rc == KeeperException.Code.OK.intValue()) {
        Map<String, Set<Watcher>> watches = getWatches(rc);
        synchronized (watches) {
            Set<Watcher> watchers = watches.get(clientPath);
            if (watchers == null) {
                watchers = new HashSet<Watcher>();
                watches.put(clientPath, watchers);
            }
            watchers.add(watcher);
        }
    }
}
```
### 5、WatcherSetEventPair
用户可能会对一个节点注册多个`watcher`，服务端远程触发客户端的`watcher`时，客户端需要将该节点对应的所有`watcher`都触发一次。
所以`WatcherSetEventPair`对`WatchedEvent`和`watchers`列表进行封装，方便`EventThread`线程处理`watcher`触发工作。

![WatcherSetEventPair](https://gitee.com/stefanpy/myimg/raw/master/img/WatcherSetEventPair.png)

### 6、ZKWatchManager
`ZKWatchManager`作为客户端`watcher`管理器，实现了接口`ClientWatchManager`:

![ClientWatchManager](https://gitee.com/stefanpy/myimg/raw/master/img/ClientWatchManager.png)

`ZKWatchManager`中用5个集合对应5种不同的`watcher`注册场景：

- `dataWatches`，在调用`getData`、`getConfig`时注册了`watcher`，会使用`dataWatches`来存储`watcher`。
- `existWatches`，对应`exists`。
- `childWatches`，对应`getChildren`。
- `persistentWatches`，给定节点持续有效的`watcher`集合，触发之后不会被移除。
- `persistentRecursiveWatches`，给定节点及其递归所有子节点都持续有效的`watcher`集合，触发之后不会被移除。

![ZKWatchManager](https://gitee.com/stefanpy/myimg/raw/master/img/ZKWatchManager.png)

之前网上一直说`Zookeeper`的观察者注册一次只能触发一次，触发的同时会被移除，如果需要注册一次，可多次有效触发，客户端使用起来比较麻烦。

所以官方弥补了这种场景，新加了`persistentWatches`和`persistentRecursiveWatches`两种集合来存储持续有效的`watcher`，触发之后不会被移除，如果要移除需要调用指定方法`ZKWatchManager#removeWatcher`，如果想注册持续有效的观察者，也是需要单独调用指定方法`ZooKeeper#addWatch`。

`ZKWatchManager`实现了接口`ClientWatchManager`，主要实现了`ClientWatchManager#materialize`方法，获取一个应该被触发事件的`watcher`列表：

```java 
org.apache.zookeeper.ZKWatchManager#materialize
@Override
public Set<Watcher> materialize(
    Watcher.Event.KeeperState state,
    Watcher.Event.EventType type,
    String clientPath
) {
    final Set<Watcher> result = new HashSet<>();

    switch (type) {
    case None:
	    // ... ...省略None情况,
        // 无类型事件，判断 通知状态KeeperState，如果KeeperState不是SyncConnected 就把所有的 watcher容器都清空
        // 根据 EventType 从不同的集合中获取观察者列表
        // dataWatches、existWatches、childWatches在获取watcher列表时有移除操作
        // persistentWatches、persistentRecursiveWatches没有移除操作
    case NodeDataChanged:
    case NodeCreated:
        synchronized (dataWatches) {
            addTo(dataWatches.remove(clientPath), result);
        }
        synchronized (existWatches) {
            addTo(existWatches.remove(clientPath), result);
        }
        addPersistentWatches(clientPath, result);
        break;
    case NodeChildrenChanged:
        synchronized (childWatches) {
            addTo(childWatches.remove(clientPath), result);
        }
        addPersistentWatches(clientPath, result);
        break;
    case NodeDeleted:
        synchronized (dataWatches) {
            addTo(dataWatches.remove(clientPath), result);
        }
        // TODO This shouldn't be needed, but just in case
        synchronized (existWatches) {
            Set<Watcher> list = existWatches.remove(clientPath);
            if (list != null) {
                addTo(list, result);
                LOG.warn("We are triggering an exists watch for delete! Shouldn't happen!");
            }
        }
        synchronized (childWatches) {
            addTo(childWatches.remove(clientPath), result);
        }
        addPersistentWatches(clientPath, result);
        break;
    default:
        String errorMsg = String.format(
            "Unhandled watch event type %s with state %s on path %s",
            type,
            state,
            clientPath);
        LOG.error(errorMsg);
        throw new RuntimeException(errorMsg);
    }
    return result;
}

private void addPersistentWatches(String clientPath, Set<Watcher> result) {
    synchronized (persistentWatches) {
        addTo(persistentWatches.get(clientPath), result);
    }
    synchronized (persistentRecursiveWatches) {
        for (String path : PathParentIterator.forAll(clientPath).asIterable()) {
            addTo(persistentRecursiveWatches.get(path), result);
        }
    }
}
```
由源码可见，从 `dataWatches`、`existWatches`、`childWatches` 集合中获取`watcher`列表时有移除操作，而从`persistentWatches`、`persistentRecursiveWatches`获取时没有移除操作。

## 二、Watcher注册流程

可以注册`watcher`的请求都是非事务请求，比如：
``` java
public ZooKeeper(String connectString, int sessionTimeout, Watcher watcher)
public byte[] getData(final String path, Watcher watcher, Stat stat)
public Stat exists(final String path, Watcher watcher)
public List<String> getChildren(final String path, Watcher watcher, Stat stat)
... ...
```

### 1、构建WatchRegistration

需要注册的`Watcher`会被封装进一个`WatchRegistration`对象中，`WatchRegistration`抽象了注册的方式，会和请求体等一并包装进 `Packet`。

需要注意，`Watcher`注册信息不会发送给服务端，而是只发送一个布尔值标注是否注册`Watcher`（`watch=true`），这样就减少了数据包的大小，降低了网络压力，同时也使得`Watcher`注册流程简单。

以`getData`为例：

![getData注册Watcher](https://gitee.com/stefanpy/myimg/raw/master/img/getData%E6%B3%A8%E5%86%8CWatcher.png)

### 2、响应成功后注册Watcher

需要注册`Watcher`的请求发给服务端后，客户端并不会立刻在自己内存中存储`Watcher`关系，而是还需要根据请求的响应状态，如果响应状态OK，才会把`Watcher`注册到`ZKWatchManager`。

![finishPacket](https://gitee.com/stefanpy/myimg/raw/master/img/finishPacket.png)

如下图是`Wacther`注册流程：

![watcher注册过程](https://gitee.com/stefanpy/myimg/raw/master/img/watcher%E6%B3%A8%E5%86%8C%E8%BF%87%E7%A8%8B.png)

## 三、Watcher通知流程

### 1、处理事件通知信息

数据节点的状态信息发生变更后，服务端找到该节点的`watcher`列表，遍历生成事件通知信息发送给客户端。客户端接收到事件通知信息后，反解析出`WatcherEvent`对象，又转换成`WatchedEvent`，再提交到`EventThread`线程处理。

如下是客户端处理事件通知信息`NOTIFICATION`的部分源码：

![NOTIFICATION消息处理](https://gitee.com/stefanpy/myimg/raw/master/img/NOTIFICATION%E6%B6%88%E6%81%AF%E5%A4%84%E7%90%86.png)

### 2、提交给EventThread线程

从事件通知信息中解析出`WatchedEvent`后，通过`WatchedEvent`的三个属性`keeperState`、`eventType`和 `path`从`ZKWatchManager`中取出符合要求的`Watcher`列表，然后将`WachedEvent`对象和`Watcher`列表封装进 `WatcherSetEventPair`并添加到`waitingEvents`队列。

![提交给EventThread线程](https://gitee.com/stefanpy/myimg/raw/master/img/queueEvent.png)

### 3、遍历waitingEvents队列

![遍历waitingEvents队列](https://gitee.com/stefanpy/myimg/raw/master/img/%E9%81%8D%E5%8E%86waitingEvents%E9%98%9F%E5%88%97.png)

### 4、真正触发Watcher#process

![真正触发Watcher#process](C:\study\myStudy\ZooKeeperLearning\blog\img\真正触发Watcher%23process.png)

如下图是`Watcher`通知流程：

![watcher通知过程](https://gitee.com/stefanpy/myimg/raw/master/img/watcher%E9%80%9A%E7%9F%A5%E8%BF%87%E7%A8%8B.png)

## 四、总结

1、`Watcher`注册时，客户端只发送了一个布尔值给服务端声明是否需要注册`Watcher`；只有当服务端那边`Wacther`注册成功了，且响应成功，客户端这边才会保存`Watcher`和节点的关系。

2、`Wacther`通知时，只能从通知信息中得知是哪个节点发生什么事件，而无法得知具体发生了什么变更，要想得知必须再主动获取一次节点信息。

本文源码基于`ZooKeeper3.7.0`版本。

推荐阅读：《从Paxos到Zookeeper：分布式一致性原理与实践》倪超著。
