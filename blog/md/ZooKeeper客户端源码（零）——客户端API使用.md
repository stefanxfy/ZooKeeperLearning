# ZooKeeper客户端源码（零）——客户端API使用

![ZooKeeper客户端API](https://gitee.com/stefanpy/myimg/raw/master/img/ZooKeeper%E5%AE%A2%E6%88%B7%E7%AB%AFAPI.png)

## 一、建立连接和会话

客户端可以通过创建一个 `ZooKeeper`（`org.apache.zookeeper.ZooKeeper`）实例来连接`ZooKeeper`服务器。`ZooKeeper`构造函数有多个重载版本，可以传入的完全参数如下：

```java
public ZooKeeper(
    String connectString,
    int sessionTimeout,
    Watcher watcher,
    long sessionId,
    byte[] sessionPasswd,
    boolean canBeReadOnly,
    HostProvider hostProvider,
    ZKClientConfig clientConfig)
```

| 参数名         | 类型           | 说明                                                         |
| -------------- | -------------- | ------------------------------------------------------------ |
| connectString  | String         | 服务器地址，形式为`ip:port,ip:port,ip:port`，中间由英文逗号分隔；也可以指定节点路径，如`ip:port,ip:port,ip:port/test` |
| sessionTimeout | int            | 会话超时时间，单位毫秒                                       |
| watcher        | Watcher        | watcher事件，因为客户端建立连接时一个异步过程，需要通过注册watcher事件进行回调通知 |
| sessionId      | long           | 会话ID，初次连接可以不用传                                   |
| sessionPasswd  | byte[]         | 会话密码，服务器生成会话ID的同时会分配一个会话密码，通过会话ID和密码可以重新建立连接 |
| canBeReadOnly  | boolean        | 标识当前会话是否支持只读模式                                 |
| hostProvider   | HostProvider   | 服务地址选择器                                               |
| clientConfig   | ZKClientConfig | 客户端配置，可以将客户端一些配置信息如sasl、scoket实现类等放到一个文件中。一般可以不用传clientConfig |

客户端与服务端建立的连接，建立会话保持连接有状态，并且可以通过会话重连或者重用一条连接。

连接建立是一个异步过程，也就是说创建好`ZooKeeper`实例之后就会立刻返回，而此时可能还没有完成会话创建，所以需要传入一个`watcher`事件。当会话真正创建成功后，客户端会生成一个”已建立连接“（`SyncConnected`）的事件，进行回调通知。

如下代码演示，创建`ZooKeeper`实例，与服务端建立：

```java
CountDownLatch countDownLatch = new CountDownLatch(1);
String connectString = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
ZooKeeper zooKeeper = new ZooKeeper(connectString, 9999, new Watcher() {
    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Event.KeeperState.SyncConnected) {
            System.out.println("会话建立成功");
            countDownLatch.countDown();
        }
    }
});
countDownLatch.await();
```

通过上一次会话建立成功后的`sessionId`和`sessionPasswd`可以重新建立连接：

```java
CountDownLatch countDownLatch1 = new CountDownLatch(1);
// 第一次连接
String connectString = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
ZooKeeper zooKeeper1 = new ZooKeeper(connectString, 9999, new Watcher() {
    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Event.KeeperState.SyncConnected) {
            System.out.println("会话建立成功");
            countDownLatch1.countDown();
        }
    }
});
countDownLatch1.await();

// 获取sessionId和sessionPasswd，重新建立连接
long sessionId = zooKeeper1.getSessionId();
byte[] sessionPasswd = zooKeeper1.getSessionPasswd();
System.out.println(String.format("zookeeper1-----sessionId=%s;sessionPasswd=%s;", sessionId, sessionPasswd));
CountDownLatch countDownLatch2 = new CountDownLatch(1);

ZooKeeper zooKeeper2 = new ZooKeeper(connectString, 9999, new Watcher() {
    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Event.KeeperState.SyncConnected) {
            System.out.println("会话重新建立成功");
            countDownLatch2.countDown();
        }
    }
}, sessionId, sessionPasswd);
countDownLatch2.await();
long sessionId2 = zooKeeper2.getSessionId();
byte[] sessionPasswd2 = zooKeeper2.getSessionPasswd();
System.out.println(String.format("zookeeper2-----sessionId=%s;sessionPasswd=%s;", sessionId2, sessionPasswd2));

String data2 = new String(zooKeeper2.getData("/node1", false, new Stat()));
System.out.println(data2);

String data = new String(zooKeeper1.getData("/node1", false, new Stat()));
System.out.println(data);
```

经多次测试，得出如下结论：

- 通过已知`sessionId`和`sessionPasswd`可以在不同服务器上重复建立会话，甚至是共用会话。
- 如果重新建立会话的服务地址和上次建立的一样，则会覆盖上一次连接，也就是上一次连接会断开，并重置`sessionPasswd`。

![重新建立连接在不同服务上](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311150124142.png)

![重新建立连接在同一个服务是哪个](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311150044016.png)

## 二、创建节点

创建节点的API有两种：同步阻塞和异步回调。

```java
// 同步阻塞创建
public String create(
    final String path,
    byte[] data,
    List<ACL> acl,
    CreateMode createMode,
    Stat stat,
    long ttl)
```

```Java
// 异步回调创建
public void create(
    final String path,
    byte[] data,
    List<ACL> acl,
    CreateMode createMode,
    AsyncCallback cb,
    Object ctx,
    long ttl)
```

两者的区别就在于异步可以传一个`AsyncCallback`回调函数，这个回调函数可以传`StringCallback`或者`Create2Callback`。

这两种`Callback`有什么区别呢？

在用法上没多大区别，主要是为了区别新旧版本的节点类型，`StringCallback`用于旧节点类型，`Create2Callback`用于新拓展的节点类型。

原先节点类型有四种：持久节点（`PERSISTENT`）、持久顺序节点（`PERSISTENT_SEQUENTIAL`）、临时节点（`EPHEMERAL`）、临时顺序节点（`EPHEMERAL_SEQUENTIAL`），后来又在持久节点上拓展了两种新类型：持久带有效期节点（`PERSISTENT_WITH_TTL`）、持久顺序带有效期节点（`PERSISTENT_SEQUENTIAL_WITH_TTL`）。

持久节点又带有效期，那和临时节点有什么区别呢？

临时节点，连接断开后就会清除，持有节点带有效期，连接断开后不会清除，但是在过了有效期后没有更新过且其没有孩子节点，就会被清除。

| 参数名     | 类型          | 说明                                                         |
| ---------- | ------------- | ------------------------------------------------------------ |
| path       | String        | 节点路径，切记不支持递归创建                                 |
| data       | byte[]        | 节点值                                                       |
| acl        | List<ACL>     | 节点的ACL权限策略                                            |
| createMode | CreateMode    | 节点类型，是个枚举类型                                       |
| cb         | AsyncCallback | 异步回调函数，可以实现`StringCallback`或者`Create2Callback`接口 |
| ctx        | Object        | 异步回调上下文                                               |
| stat       | Stat          | 节点状态信息，创建节点时传空实例，创建成功响应后会填充stat，主要信息有czxid、mzxid、ctime、mtime、version、cversion、dataLength等 |
| ttl        | long          | 持久节点的有效期，单位毫秒，必须大于0                        |

`ACL`是节点的权限控制，如果实际应用场景不需要太高的权限要求，可以不用关注这个参数，只需要传``Ids.OPEN_ACL_UNSAFE`即可，这就表明之后对这个节点的任何操作都不需要权限。

如下代码演示节点创建，同步阻塞和异步回调方式：

```java
CountDownLatch countDownLatch1 = new CountDownLatch(1);
String connectString = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
ZooKeeper zooKeeper = new ZooKeeper(connectString, 9999, new Watcher() {
    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Event.KeeperState.SyncConnected) {
            System.out.println("会话建立成功");
            countDownLatch1.countDown();
        }
    }
});
countDownLatch1.await();
// 同步阻塞
Stat stat = new Stat();
String path = zooKeeper.create("/demo1", "hello world".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL, stat);
System.out.println("同步阻塞::path=" + path);
System.out.println(stat.toString());

// 异步回调
CountDownLatch countDownLatch2 = new CountDownLatch(1);
zooKeeper.create("/demo1", "hello world".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL, new AsyncCallback.StringCallback() {
    @Override
    public void processResult(int rc, String path, Object ctx, String name) {
        System.out.println(String.format("异步回调::rc=%s;path=%s;ctx=%s;name=%s;", rc, path, ctx, name));
        countDownLatch2.countDown();
    }
}, "context");
countDownLatch2.await();
```

![image-20220311161338163](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311161338163.png)

回调函数需要重写`processResult`方法，该方法有四个参数：

- `rc`，响应状态码，可参考`org.apache.zookeeper.KeeperException.Code`。常见状态码：响应成功：`OK(0)`，其他状态码就都是异常：`ConnectionLoss(-4)`连接断开、`NodeExists(-110)`节点已存在、`SessionExpired(-112)`会话过期等。
- `path`，API调用时传入的节点路径。
- `ctx`，API调用时传入的上下文对象。
- `name`，实际创建的节点名，比如顺序节点，会在原参数`path`基础上带个有序编号后缀。

注：后续所有API，都与创建节点一样有同步阻塞和异步回调两种方式。

继承了`AsyncCallback`的接口还有很多，不同的API可以对应不同的`AsyncCallback`接口，如下图是`AsyncCallback`接口继承关系图：

![AsyncCallback.drawio](https://gitee.com/stefanpy/myimg/raw/master/img/AsyncCallback.drawio.png)

## 三、获取节点

获取节点的API有两种：

```java
// 同步阻塞
public byte[] getData(final String path, Watcher watcher, Stat stat)
// 异步回调
public void getData(final String path, Watcher watcher, DataCallback cb, Object ctx)    
```

与创建节点不同，获取节点的API还可以注册一个`watcher`，如果节点发生了更新操作，就会触发回调`watcher`进行通知。

如下代码演示获取节点信息并注册一个`watcher`：

```java
CountDownLatch countDownLatch = new CountDownLatch(1);
String connectString = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
ZooKeeper zooKeeper = new ZooKeeper(connectString, 9999, new Watcher() {
    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Event.KeeperState.SyncConnected) {
            System.out.println("会话建立成功");
            countDownLatch.countDown();
        }
    }
});
countDownLatch.await();
Stat stat = new Stat();
byte[] data = zooKeeper.getData("/node1", new Watcher() {
    @Override
    public void process(WatchedEvent event) {
        System.out.println(event.toString());
    }
}, stat);
System.out.println("data: " + new String(data));
System.out.println(stat.toString());
Thread.sleep(1000000);
```

在另一个线程修改节点内容，触发了`NodeDataChanged`事件：

![image-20220311165410507](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311165410507.png)

还有其他事件如：`NodeCreated`、`NodeDeleted`、`NodeChildrenChanged`等。

显然通知信息`WatchedEvent`中没有节点原始数据内容和变更后的新数据内容，所以当通知节点更新后，如果想知道更新了什么，还需要主动`getData`一次。

## 四、更新节点

更新节点API有两种：

```java
// 同步阻塞
public Stat setData(final String path, byte[] data, int version)
// 异步回调
public void setData(final String path, byte[] data, int version, StatCallback cb, Object ctx)    
```

更新节点内容必须基于某个`version`更新，如果传入的`version`和该节点当前`version`不一致就会修改失败，就跟`CAS`（`Compare and Swap`）的原理差不多。所以需要先通过`getData`获取节点的`version`，然后再调用`setData`。

如下演示更新节点操作：

```java
CountDownLatch countDownLatch = new CountDownLatch(1);
String connectString = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
ZooKeeper zooKeeper = new ZooKeeper(connectString, 9999, new Watcher() {
    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Event.KeeperState.SyncConnected) {
            System.out.println("会话建立成功");
            countDownLatch.countDown();
        }
    }
});
countDownLatch.await();
// 1、获取节点信息
Stat stat = new Stat();
byte[] data = zooKeeper.getData("/node1", false, stat);
System.out.println("data: " + new String(data));
System.out.println(stat.toString());
// 2、更新节点
zooKeeper.setData("/node1", "你好, stefan".getBytes(), stat.getVersion());
// 3、再次获取节点
Stat stat1 = new Stat();
byte[] data1 = zooKeeper.getData("/node1", false, stat);
System.out.println("更新之后的：data: " + new String(data1));
System.out.println(stat1.toString());
```

![image-20220311171546137](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311171546137.png)

## 五、删除节点

删除节点API有两种：

```java
// 同步阻塞
public void delete(final String path, int version)
// 异步回调
public void delete(final String path, int version, VoidCallback cb, Object ctx)    
```

删除节点也属于节点更新范畴，所以也需要基于`version`，保证其操作的原子性。

## 六、权限控制

为了避免存储在 `ZooKeeper `服务器上的数据被其他进程干扰或人为操作修改，可以对`ZooKeeper`上的数据访问进行权限控制（Access Control）。

`ZooKeeper`提供了`ACL`（`Access Control List`）的权限控制机制，可以针对任意用户和组进行细粒度的权限控制：通过给节点设置`ACL`，来控制客户端对该节点的访问权限，如果一个客户端符合该 `ACL `控制，那么就可以对其进行访问，否则将无法操作。

`ACL`由三部分组成：权限模式（`scheme`）、授权对象（`id`）、权限（`permission`），通常使用`scheme:id:permission`表示一个有效的`ACL`信息。

权限模式有多种，如：`Digest`、`Auth`、`IP`、`World`、`Super`：

- `Digest`是最常用的权限控制模式，其以类似于`username:password`形式的权限标识进行权限配置，便于区分不同应用来进行权限控制。
- `Auth`用于授予权限，注意需要先创建用户。
- `IP`一种白名单的方式，授权给指定ip或ip段。

- `World`是一种最开放的权限控制模式，这种权限控制方式几乎没有任何作用，数据节点的访问权限对所有用户开放。`World`模式也可以看作是一种特殊的`Digest`模式，它只有一个权限标识，即`world:anyone`。
- `Super`顾名思义就是超级用户的意思，也是一种特殊的`Digest`模式，在`Super`模式下，超级用户可以对任意`ZooKeeper`上的数据节点进行任何操作。

如果要使用 `ZooKeeper `的权限控制功能，需要在完成 `ZooKeeper `会话创建后，给该会话添加上相关的权限信息（`AuthInfo`）:

```java 
public void addAuthInfo(String scheme, byte[] auth)
```

该接口主要用于为当前 `ZooKeeper `会话添加权限信息，之后凡是通过该会话对`ZooKeeper`服务端进行的任何操作，都会带上该权限信息。

如下只举例`Digest`权限模式使用：

```java 
CountDownLatch countDownLatch = new CountDownLatch(1);
String connectString = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
ZooKeeper zooKeeper = new ZooKeeper(connectString, 9999, new Watcher() {
    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Event.KeeperState.SyncConnected) {
            System.out.println("会话建立成功");
            countDownLatch.countDown();
        }
    }
});
countDownLatch.await();
// 给当前会话设置权限信息
zooKeeper.addAuthInfo("digest", "stefanxfy".getBytes());
// 创建节点
Stat stat = new Stat();
zooKeeper.create("/testAcl1", "hello world".getBytes(), ZooDefs.Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT, stat);
```

没有设置权限信息或者没有设置正确的权限信息的会话进行节点获取操作，抛出如下异常`NoAuthException`：

![image-20220311183532225](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311183532225.png)

设置了正确权限信息的会话就可以正常操作该节点。

经测试，对于删除操作而言，一个节点的权限作用范围是其子节点，也就是说，对一个数据节点添加权限信息后，依然可以自由地删除这个节点，但是对于这个节点的子节点，就必须使用相应的权限信息才能够删除掉它。

## 七、总结与参考

1. 客户端建立连接是一个异步过程，需要注册一个`watcher`来监听会话建立完成。
2. 可以通过`sessionId`和`sessionPasswd`重新建立连接。
3. 所有对节点的请求API都有同步阻塞和异步回调两种方式，异步回调就是传入一个回调函数，响应处理时进行回调通知。
4. 只有`getData`等非事务请求才能注册`watcher`，事务请求如`create`、`delete`、`setData`都不可以。
5. 创建节点时可以为其设置`ACL`权限控制，其他API只有在设置了正确权限的会话下，才有权操作；但是对于删除操作而言，一个节点的权限作用范围是其子节点，所以在没有权限的情况下是可以删除该节点的。

推荐阅读：《从Paxos到Zookeeper：分布式一致性原理与实践》倪超著。

## 八、推荐阅读

[ZooKeeper客户端源码（一）——向服务端建立连接+会话建立+心跳保持长连接](https://stefan.blog.csdn.net/article/details/123393612)

[ZooKeeper客户端源码（二）——向服务端发起请求（顺序响应+同步阻塞+异步回调）](https://stefan.blog.csdn.net/article/details/123455097)

[ZooKeeper客户端源码（三）——Watcher注册与通知](https://stefan.blog.csdn.net/article/details/123464459)



