# ZooKeeper客户端源码——向服务端建立连接

[TOC]

## 从ZooKeeper实例初始化开始

`ZooKeeper` 提供了原生的客户端库，虽然不好用，但是能够更好理解客户端与服务端建立连接和通信的过程。比较流行的`Apache Curator`也是对原生库的再封装。

向服务端建立连接，只需要实例化一个`ZooKeeper`对象，将服务器地址列表传进去即可。因为发起连接请求是一个异步的过程，所以实例化`ZooKeeper`时可以传一个`Watcher`，会话建立成功之后，客户端会生成一个 “已经建立连接（`SyncConnected`）” 的事件，进行回调通知。只有会话建立成功之后，才能与服务端进行通信。

```java
String connectString = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
ZooKeeper zooKeeper = new ZooKeeper(connectString, 20000, new Watcher() {
    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Event.KeeperState.SyncConnected) {
            System.out.println("会话建立成功");
        }
    }
});
```

`ZooKeeper`实例初始化流程如下：

![image-20220309175725090](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220309175725090.png)

### 创建HostProvider

`HostProvider`顾名思义就是“服务地址提供器”，默认实现类`StaticHostProvider`。`StaticHostProvider`核心思想是，将服务地址列表打乱，然后构成一个虚拟环，轮询向外提供服务地址。

如果`StaticHostProvider`不满足需求，可以自定义实现`HostProvider`。

#### 打乱服务地址列表

打乱地址就很简单了，用了Java官方提供的工具`java.util.Collections#shuffle`。

```java
// org.apache.zookeeper.client.StaticHostProvider#shuffle
private List<InetSocketAddress> shuffle(Collection<InetSocketAddress> serverAddresses) {
    List<InetSocketAddress> tmpList = new ArrayList<>(serverAddresses.size());
    tmpList.addAll(serverAddresses);
    Collections.shuffle(tmpList, sourceOfRandomness);
    return tmpList;
}
```

随机源生成：

```java
Random sourceOfRandomness = new Random(System.currentTimeMillis() ^ this.hashCode())
```

随机源的种子是当前时间毫秒值掺杂（`^`）当前`StaticHostProvider`实例的`hashCode`，使得每次生成的随机源更公平。

#### 构建虚拟环轮询负载

如何将一个服务地址列表构成一个环轮询负载的呢？

有两个游标，`currentIndex` 和 `lastIndex`是实现“虚拟环轮询负载”的关键：

- `currentIndex`，指向当前选择的位置。每选择一次就加一，如果等于服务地址列表长度，就重置为0，这样就形成了一个环。
- `lastIndex`，上次选择的位置。会话建立成功之后会将`currentIndex`赋值给`lastIndex`。

```java
public InetSocketAddress next(long spinDelay) {
    boolean needToSleep = false;
    InetSocketAddress addr;

    synchronized (this) {
        // 省略部分无关代码
        
        // currentIndex自增，如果等于服务地址列表长度，就重置为0
        ++currentIndex;
        if (currentIndex == serverAddresses.size()) {
            currentIndex = 0;
        }
        addr = serverAddresses.get(currentIndex);
        // 两个游标currentIndex、lastIndex
        // currentIndex 当前选择的位置，lastIndex上次选择的位置
        // lastIndex 什么时候设置呢？会话建立成功之后调用 onConnected，将currentIndex赋值给lastIndex
        needToSleep = needToSleep || (currentIndex == lastIndex && spinDelay > 0);
        if (lastIndex == -1) {
            lastIndex = 0;
        }
    }
    // 如果 currentIndex和lastIndex且spinDelay>0，就需要休眠spinDelay时间，
    if (needToSleep) {
        try {
            Thread.sleep(spinDelay);
        } catch (InterruptedException e) {
            LOG.warn("Unexpected exception", e);
        }
    }
    // 解析InetSocketAddress，
    // 如果一个主机映射了多个ip地址（InetAddress）
    // 就打乱选择其中一个地址返回
    return resolve(addr);
}
```

当`currentIndex`和`lastIndex`相等时，且`spinDelay>0`，就会休眠`spinDelay`毫秒，然后再将选择的服务地址返回，为什么会有这个逻辑呢？

这是因为，如果轮询了一圈服务地址都没有成功建立连接，与其一味不停地重试，还不如休眠一段时间再试，可能成功的概率更高一些。

### 创建ConnectStringParser

`ConnectStringParser` 就是将 `connectString` 按一定格式解析成 `InetSocketAddress` 列表。`connectString`格式如下：

> 127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183
>
> 127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183/test

如果在服务地址后面指定了路径，后续的操作都是在该路径下进行。

```java
public ConnectStringParser(String connectString) {
    // parse out chroot, if any
    // 解析chroot
    // connectString 可以指定某个路径，
    // 如127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183/test
    int off = connectString.indexOf('/');
    if (off >= 0) {
        String chrootPath = connectString.substring(off);
        // ignore "/" chroot spec, same as null
        if (chrootPath.length() == 1) {
            this.chrootPath = null;
        } else {
            PathUtils.validatePath(chrootPath);
            this.chrootPath = chrootPath;
        }
        connectString = connectString.substring(0, off);
    } else {
        this.chrootPath = null;
    }
    // 按 , 分割
    List<String> hostsList = split(connectString, ",");
    for (String host : hostsList) {
        int port = DEFAULT_PORT;
        String[] hostAndPort = NetUtils.getIPV6HostAndPort(host);
        if (hostAndPort.length != 0) {
            host = hostAndPort[0];
            if (hostAndPort.length == 2) {
                port = Integer.parseInt(hostAndPort[1]);
            }
        } else {
            int pidx = host.lastIndexOf(':');
            if (pidx >= 0) {
                // otherwise : is at the end of the string, ignore
                if (pidx < host.length() - 1) {
                    port = Integer.parseInt(host.substring(pidx + 1));
                }
                host = host.substring(0, pidx);
            }
        }
        // 封装未解析的InetSocketAddress
        serverAddresses.add(InetSocketAddress.createUnresolved(host, port));
    }
}
```

### 创建并启动ClientCnxn

`ClientCnxn`是对客户端连接的抽象和封装，负责网络连接管理和`watcher`管理。

还记得从`ZooKeeper`构造器传入的`Watcher`吗？它会作为默认`Watcher`传给`ZKWatchManager`，后续其他请求注册`watcher`时，可以不用再定义，直接使用默认的`Watcher`。

初始化`SendThread`时会传入一个`ClientCnxnSocket`，`ClientCnxnSocket`是对网络底层的封装，默认实现类为`ClientCnxnSocketNIO`。

![image-20220309180338984](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220309180338984.png)

`ClientCnxn`创建好后会进行启动操作，就是启动`SendThread`和`EventThread`两个线程。后续的网络连接建立和通信都是由`SendThread`线程负责。

## 向服务端建立连接

`SendThread`启动后，会进入一个循环状态，首先判断是否已经建立连接，如果没有就通过`hostProvider`选择一个服务地址发起连接。

![image-20220309212527148](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220309212527148.png)

网络底层处理类`ClientCnxnSocket`，以`ClientCnxnSocketNIO`实现为准，如下图是建立连接的过程：

![建立连接过程.drawio](../../Users/faisco/Desktop/建立连接过程.drawio.png)

`ClientCnxnSocketNIO`底层是创建了非阻塞的`SocketChannel`，然后注册`OP_CONNECT`事件，并发起连接，如果此时能立刻连上，就继续进行会话建立流程。

![image-20220309212424388](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220309212424388.png)

如下模拟服务器一直连不上，多次通过`hostProvider`轮询选择服务器重连的效果：

服务器地址选择了一圈以后，会休眠`spinDelay`毫秒，也符合源码逻辑。

![image-20220309211717688](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220309211717688.png)

## 会话建立请求

连接建立之后，需要继续进行会话建立请求，因为每条连接都是有状态的，临时节点的归属就是以会话为依托，连接断开，会话失效，临时节点也就跟随着清除。

每条连接跟会话绑定，如果连接因为网络等问题断开，在会话有效期内重连上，就可以恢复之前的工作场景，而如果在会话失效之后重连上，就是非法连接，需要重新进行会话建立。

![会话建立.drawio](../../Users/faisco/Desktop/会话建立.drawio.png)

### 发起会话建立请求

发起会话建立，首先构建一个`ConnectRequest`请求体：

```java
ConnectRequest conReq = new ConnectRequest(0, lastZxid, sessionTimeout, sessId, sessionPasswd);
```

`ConnectRequest`需要传入客户端最近一次的事务`zxid`，会话超时时间，会话id，会话密码。首次建立会话，`sessionId`为0，`sessionPasswd`为空。

其次，将`ConnectRequest`请求体包装进`Packet`对象，`Packet`是`ZooKeeper`通信的最小单元，所有请求体都会包装进一个`Packet`对象再序列化发送给服务端。

```java
Packet(RequestHeader requestHeader,ReplyHeader replyHeader,Record request,
    Record response,
    WatchRegistration watchRegistration,
    boolean readOnly)
```

对于`ConnectRequest`包装的`Packet`，请求头`RequestHeader`为null，并且会被放在请求队列`outgoingQueue`的首位。

```java
outgoingQueue.addFirst(new Packet(null, null, conReq, null, null, readOnly));
```

接着注册网络IO读写事件，后续请求包就会被发送给服务端了，发送完成后再将会话建立的`Packet`从`outgoingQueue`中移除。

![image-20220309222231305](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220309222231305.png)

注：发送到服务端的会话创建流程，暂时不需要了解，后面讲解服务端源码时会详细讲述。

### 会话建立响应

会话建立响应，需要和普通请求响应分开处理，如果接收到响应信息，判断客户端还未初始化完成，就认为这个响应一定是会话建立响应。

![image-20220309223709509](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220309223709509.png)

首先从底层网络缓冲区读取数据反序列化构建`ConnectResponse`响应体，解析出经过服务器协商好的`sessionTimeout`以及`sessionId` 和 `sessionPasswd`。

![image-20220309224130473](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220309224130473.png)

根据协商的`sessionTimeout`重新设置`readTimeout`和`connectTimeout`，`readTimeout`是`negotiatedSessionTimeout`的2/3，`connectTimeout`是协商`negotiatedSessionTimeout`与服务地址列表个数平均。

![image-20220309224446200](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220309224446200.png)

最后生成一个`SyncConnected`的`watcher`事件，交由`EventThread`线程进行回调，这是唯一一个不需要向服务端注册的`watcher`事件，完全由客户端自己生成和触发。

## 心跳保持长连接

网络建立连接，会话建立都完成后，就可以与服务端通信了，为了保持长连接的会话一直有效，在没有向服务端发送请求的一段时间内会发送心跳请求。而一段时间是多久？如下是计算下一次发送心跳请求时间的算法：

```java
// clientCnxnSocket.getIdleSend为距离上次发送的时长
// readTimeout = sessionTimeout * 2 / 3
int timeToNextPing = readTimeout / 2
                     - clientCnxnSocket.getIdleSend()
                     - ((clientCnxnSocket.getIdleSend() > 1000) ? 1000 : 0);
```

根据计算，每隔`sessionTimeout/3`如果没有发送任何请求，就发送一次心跳。多减1秒是为了防止因为竞态情况而丢失心跳请求。

发送的心跳请求数据很简单，没有请求体，只有请求头：

![image-20220309233736903](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220309233736903.png)

如下是在源码中加了日志后的心跳过程：

协商`sessionTimeout`为9999，`readTimeout`计算得6666，`timeToNextPing`为3333，每次会多减1秒，大概每隔3秒发一次心跳。

![image-20220309233219366](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220309233219366.png)

## 总结

1. 客户端与服务端建立的是长连接，如果连接失败，服务地址列表会轮询重试，直到连接成功，官方提供了默认的服务地址负载算法 `StaticHostProvider`，也可以自己实现。

2. 每条连接是有状态的，只有建立了会话，才能真正开始与服务端通信。会话建立成功之后，会生成一个`SyncConnected`事件进行回调通知。

3. 会话是临时节点的基础，在会话有效期内断开重连，可以恢复上一次工作场景。

4. 为了保持长连接的会话一直有效，在没有向服务端发送请求的一段时间内会发送心跳请求，心跳间隔时间为`sessionTimeout/3`。

