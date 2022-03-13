# ZooKeeper客户端源码（二）——向服务端发起请求

![向服务端发起请求](https://gitee.com/stefanpy/myimg/raw/master/img/%E5%90%91%E6%9C%8D%E5%8A%A1%E7%AB%AF%E5%8F%91%E8%B5%B7%E8%AF%B7%E6%B1%82.png)

## 一、向服务端发起请求

客户端与服务端通信的最小单元是`Packet`。所有请求在发送给服务端之前，都需要先构建一个`Packet`，再将`Packet`提交给请求处理队列`outgoingQueue`并唤醒`SendThread`线程，最后处理写事件，从`outgoingQueue`中取出`Packet`，将其序列化写入网络发送缓冲区。

### 1、构建协议包

`Packet`中包含请求头、请求体、响应头、响应体、本地回调函数、`watcher`注册等信息。

#### （1）请求体和响应体

不同的请求API有不同的请求体和响应体，比如`getData`的请求体是`GetDataRequest`，响应体是`GetDataResponse`，`setData`的请求体是`SetDataRequest`，响应体是`SetDataResponse`。

如下是不同请求体和响应体的类关系图：

![Record-Request.drawio](https://gitee.com/stefanpy/myimg/raw/master/img/Record-Request.drawio.png)

![Record-Response.drawio](https://gitee.com/stefanpy/myimg/raw/master/img/Record-Response.drawio.png)

如下图是常见的几个请求体和响应体的内容结构：

![请求体和响应体内容](https://gitee.com/stefanpy/myimg/raw/master/img/%E8%AF%B7%E6%B1%82%E4%BD%93%E5%92%8C%E5%93%8D%E5%BA%94%E4%BD%93%E5%86%85%E5%AE%B9.png)

#### （2）请求头

请求头`RequestHeader`定义了操作类型`OpCode`和请求序号`xid`。

- 最常见`OpCode`有`create=1`、`delete=2`、`exists=3`、`getData=5`、`setData=6`、`ping=11`等，详细参考`org.apache.zookeeper.ZooDefs.OpCode`。
- `xid`用于记录客户端请求发起的先后序号，用来确保单个客户端请求的响应顺序。正常从1开始自增，但是也有几个特殊的`xid`定义，`NOTIFICATION_XID=-1` `watcher`通知信息，`PING_XID=-2`心跳请求，`AUTHPACKET_XID=-4`授权数据包请求，`SET_WATCHES_XID=-8`设置`watcher`请求。

根据协议规定，除非是“会话创建”请求，其他所有的客户端请求都会带上请求头。

#### （3）getData源码示例

以`getData`源码为例，其他类似：

![ZooKeeper.getData](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311221525761.png)

### 2、发送数据包

#### （1）提交给outgoingQueue

构建好`Packet`，就提交给`outgoingQueue`队列，然后通知`SendThread`线程：

![ClientCnxn.queuePacket](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311222333377.png)

#### （2）SendThread处理写事件

`SendThread`线程轮询`SelectionKey`列表，处理写事件：

![ClientCnxnSocketNIO.doIO](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311224128255.png)

除了会话建立请求、心跳请求，其他正常请求发送完毕后，都需要添加到`pendingQueue`队列，其目的是按顺序处理响应。

#### （3）网络包序列化

真正要发给服务端的只有请求头和请求体以及长度等少量信息。

![请求协议组成](https://gitee.com/stefanpy/myimg/raw/master/img/%E8%AF%B7%E6%B1%82%E5%8D%8F%E8%AE%AE%E7%BB%84%E6%88%90.png)

如下是`Packet`序列化过程：

![ClientCnxn.Packet.createBB](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311225500544.png)

发送的网络包，需要序列化为`byte`数组，而`ZooKeeper`并没有使用多么高深的序列化技术，实则还是用的Java原生的序列化和反序列化技术`ByteArrayOutputStream`+`DataOutputStream`。

## 二、接收服务端响应

### 1、按顺序处理响应

正常请求，如`getData`、`setData`、`create`、`delete`等的响应都需要按顺序处理。接收服务端发来的响应信息按顺序和`pendingQueue`队列中的`Packet`对比`xid`是否相等，相等就是同一个请求，不相等就说明顺序乱了，抛出异常。

如下是处理响应的部分源码：

```java
// org.apache.zookeeper.ClientCnxn.SendThread#readResponse
void readResponse(ByteBuffer incomingBuffer) throws IOException {
    ByteBufferInputStream bbis = new ByteBufferInputStream(incomingBuffer);
    BinaryInputArchive bbia = BinaryInputArchive.getArchive(bbis);
    ReplyHeader replyHdr = new ReplyHeader();
    // 解码
    replyHdr.deserialize(bbia, "header");
    // 暂时省略 Xid事件的处理
    
    // 必须按顺序处理响应，pendingQueue 按顺序出队列
    Packet packet;
    synchronized (pendingQueue) {
        if (pendingQueue.size() == 0) {
            throw new IOException("Nothing in the queue, but got " + replyHdr.getXid());
        }
        // 从 pendingQueue 中取出 packet
        packet = pendingQueue.remove();
    }
    /*
     * Since requests are processed in order, we better get a response to the first request!
     */
    try {
        // 对比xid是否一致，若不一致则抛出Xid out of order异常
        if (packet.requestHeader.getXid() != replyHdr.getXid()) {
            packet.replyHeader.setErr(KeeperException.Code.CONNECTIONLOSS.intValue());
            throw new IOException("Xid out of order. Got Xid " + replyHdr.getXid()
                                  + " with err " + replyHdr.getErr()
                                  + " expected Xid " + packet.requestHeader.getXid()
                                  + " for a packet with details: " + packet);
        }
        // 填充 replyHeader
        packet.replyHeader.setXid(replyHdr.getXid());
        packet.replyHeader.setErr(replyHdr.getErr());
        packet.replyHeader.setZxid(replyHdr.getZxid());
        if (replyHdr.getZxid() > 0) {
            lastZxid = replyHdr.getZxid();
        }
        if (packet.response != null && replyHdr.getErr() == 0) {
            // 反序列化 response
            packet.response.deserialize(bbia, "response");
        }

        LOG.debug("Reading reply session id: 0x{}, packet:: {}", Long.toHexString(sessionId), packet);
    } finally {
        // 进行packet处理的收尾工作，如注册watcher、唤醒同步阻塞的主线程、触发本地回调函数等
        finishPacket(packet);
    }
}
```

从网络底层读取数据，然后反序列化出响应头和响应体。

![响应协议](https://gitee.com/stefanpy/myimg/raw/master/img/%E5%93%8D%E5%BA%94%E5%8D%8F%E8%AE%AE.png)

### 2、唤醒同步阻塞

请求的同步阻塞方式到底如何实现的呢？

以同步阻塞方式等待响应结果的请求API，都是调用方法`org.apache.zookeeper.ClientCnxn#submitRequest`：

![submitRequest](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311232244168.png)

将`packet`提交给`outgoingQueue`队列后，就调用`packet.wait()`阻塞当前线程。接收到响应，解析对比完`packet`后，调用`finishPacket()`方法进行收尾工作，如果没有设置`Callback`，就调用`packet.notifyAll()`唤醒刚才阻塞的线程。

![finishPacket](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311232533509.png)

### 3、异步回调通知

以异步回调通知响应结果，就直接调用的`org.apache.zookeeper.ClientCnxn#queuePacket`，直接将`packet`添加到`outgoingQueue`队列。

在调用`finishPacket()`方法进行收尾工作时，判断如果设置了`Callback`，就将`packet`交给`EventThread`进行回调通知。

首先将`packet`添加到`EventThread`线程的`waitingEvents`队列，然后`EventThread`线程循环遍历`waitingEvents`队列取出`packet`处理：

![EventThread.queuePacket](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311233540966.png)

![EventThread.run](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311233641644.png)

![processEvent部分源码](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311233925234.png)

## 三、总结与参考

一图以蔽之。

![请求发起和响应过程.drawio](https://gitee.com/stefanpy/myimg/raw/master/img/%E8%AF%B7%E6%B1%82%E5%8F%91%E8%B5%B7%E5%92%8C%E5%93%8D%E5%BA%94%E8%BF%87%E7%A8%8B.drawio.png)

本文源码基于`ZooKeeper3.7.0`版本。

推荐阅读：《从Paxos到Zookeeper：分布式一致性原理与实践》倪超著。

