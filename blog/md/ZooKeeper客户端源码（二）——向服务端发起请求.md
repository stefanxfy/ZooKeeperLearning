# ZooKeeper客户端源码（二）——向服务端发起请求

[TOC]

## 向服务端发起请求

客户端月服务端通信的最小单元是`Packet`，`Packet`中包含请求头、请求体、响应头、响应体、本地回调函数、watcher注册等信息。而真正要发给服务端的只有请求头和请求体以及请求体长度等少量信息：

![ClientCnxn.Packet.createBB](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311225500544.png)

所有请求在发送给服务端之前，都需要先构建一个`Packet`，再将`Packet`提交给请求处理队列`outgoingQueue`并唤醒`SendThread`线程，最后，处理写事件，从`outgoingQueue`中取出`Packet`，将其序列化写入网络发送缓冲区。

### 构建数据包

不同的请求API有不同的请求体和响应体，比如`getData`的请求体是`GetDataRequest`，响应体是`GetDataResponse`，`setData`的请求体是`SetDataRequest`，响应体是`SetDataResponse`。

如下是不同请求体和响应体的类关系图：

![Record-Request.drawio](https://gitee.com/stefanpy/myimg/raw/master/img/Record-Request.drawio.png)

![Record-Response.drawio](https://gitee.com/stefanpy/myimg/raw/master/img/Record-Response.drawio.png)

请求头`RequestHeader`定义操作类型`OpCode`和`xid`。

- 最常见`OpCode`有`create=1`、`delete=2`、`exists=3`、`getData=5`、`setData=6`、`ping=11`等，详细参考`org.apache.zookeeper.ZooDefs.OpCode`。
- `xid`是用来标识每个请求的唯一单机唯一性的，正常从1开始自增，但是也有几个特殊的xid定义，`NOTIFICATION_XID=-1`watch通知响应，`PING_XID=-2`心跳请求，`AUTHPACKET_XID=-4`授权数据包请求，`SET_WATCHES_XID=-8`设置`watch`请求。

以`getData`源码为例，其他类似：

![ZooKeeper.getData](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311221525761.png)

### 发送数据包

构建好`Packet`，就提交给`outgoingQueue`队列，然后通知`SendThread`线程，有请求提交了：

![ClientCnxn.queuePacket](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311222333377.png)

`SendThread`线程轮询`SelectionKey`列表，处理写事件：

![ClientCnxnSocketNIO.doIO](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311224128255.png)

除了会话建立请求、心跳请求，其他正常请求发送完毕后，都需要添加到`pendingQueue`队列，其目的是按顺序处理响应。

## 接收服务端响应

### 按顺序处理响应

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

### 唤醒同步阻塞

请求的同步阻塞方式到底如何实现的呢？

以同步阻塞方式等待响应结果的请求API，都是调用方法`org.apache.zookeeper.ClientCnxn#submitRequest`：

![image-20220311232244168](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311232244168.png)

将`packet`提交给`outgoingQueue`队列后，就调用`packet.wait()`阻塞当前线程。解析对比完`packet`后，调用`finishPacket()`方法进行收尾工作，如果没有设置`Callback`，就调用`packet.notifyAll()`唤醒刚才阻塞的线程。

![image-20220311232533509](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311232533509.png)

### 异步回调通知

以异步回调通知响应结果，就直接调用的`org.apache.zookeeper.ClientCnxn#queuePacket`，直接将`packet`添加到`outgoingQueue`队列。

在调用`finishPacket()`方法进行收尾工作时，判断如果设置了`Callback`，就将packet交给`EventThread`进行回调通知。

首先将`packet`添加到`EventThread`线程的`waitingEvents`队列，然后`EventThread`线程循环遍历`waitingEvents`队列取出`packet`处理：

![EventThread.queuePacket](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311233540966.png)

![EventThread.run](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311233641644.png)

![processEvent部分源码](https://gitee.com/stefanpy/myimg/raw/master/img/image-20220311233925234.png)

### 注册watcher



### watcher触发通知