/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zookeeper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Date;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import org.apache.zookeeper.ClientCnxn.EndOfStreamException;
import org.apache.zookeeper.ClientCnxn.Packet;
import org.apache.zookeeper.ZooDefs.OpCode;
import org.apache.zookeeper.client.ZKClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientCnxnSocketNIO extends ClientCnxnSocket {

    private static final Logger LOG = LoggerFactory.getLogger(ClientCnxnSocketNIO.class);

    private final Selector selector = Selector.open();

    private SelectionKey sockKey;

    private SocketAddress localSocketAddress;

    private SocketAddress remoteSocketAddress;

    ClientCnxnSocketNIO(ZKClientConfig clientConfig) throws IOException {
        this.clientConfig = clientConfig;
        initProperties();
    }

    @Override
    boolean isConnected() {
        return sockKey != null;
    }

    /**
     * @throws InterruptedException
     * @throws IOException
     */
    void doIO(Queue<Packet> pendingQueue, ClientCnxn cnxn) throws InterruptedException, IOException {
        SocketChannel sock = (SocketChannel) sockKey.channel();
        if (sock == null) {
            throw new IOException("Socket is null!");
        }
        if (sockKey.isReadable()) {
            // 每次请求分2次读，第一次读len，第二次读请求 session
            // 读取 响应数据 至 incomingBuffer
            int rc = sock.read(incomingBuffer);
            if (rc < 0) {
                throw new EndOfStreamException("Unable to read additional data from server sessionid 0x"
                                               + Long.toHexString(sessionId)
                                               + ", likely server has closed socket");
            }
            if (!incomingBuffer.hasRemaining()) {
                incomingBuffer.flip();
                if (incomingBuffer == lenBuffer) {
                    // java.nio.DirectByteBuffer[pos=0 lim=4 cap=4]
//                    LOG.info("Readable len, incomingBuffer={}", incomingBuffer.toString());
                    recvCount.getAndIncrement();
                    readLength();
                } else if (!initialized) {
                    // 如果尚未完成初始化，那么就认为该响应一定是会话创建请求的响应，
                    // 直接交由readConnectResult方法来处理该响应。
                    // java.nio.HeapByteBuffer[pos=0 lim=37 cap=37]
//                    LOG.info("Readable not initialized, incomingBuffer={}", incomingBuffer.toString());
                    readConnectResult();
                    enableRead();

                    if (findSendablePacket(outgoingQueue, sendThread.tunnelAuthInProgress()) != null) {
                        enableWrite();
                    }
                    lenBuffer.clear();
                    incomingBuffer = lenBuffer;
                    updateLastHeard();
                    initialized = true;
                } else {
                    // 非会话建立请求响应 + 回调
//                    LOG.info("Readable readResponse, incomingBuffer={}", incomingBuffer.toString());
                    sendThread.readResponse(incomingBuffer);
                    lenBuffer.clear();
                    incomingBuffer = lenBuffer;
                    updateLastHeard();
                }
            }
        }
        // org.apache.zookeeper.ClientCnxnSocketNIO.doIO
        if (sockKey.isWritable()) {
            // 从 outgoingQueue中取出可以发送的 Packet
            Packet p = findSendablePacket(outgoingQueue, sendThread.tunnelAuthInProgress());
            if (p != null) {
                updateLastSend();
                // If we already started writing p, p.bb will already exist
                if (p.bb == null) {
                    if ((p.requestHeader != null)
                        && (p.requestHeader.getType() != OpCode.ping)
                        && (p.requestHeader.getType() != OpCode.auth)) {
                        p.requestHeader.setXid(cnxn.getXid());
                    }
                    // 编码
                    p.createBB();
                }
                // 写入网络缓冲区
                sock.write(p.bb);
                if (!p.bb.hasRemaining()) {
                    // 如果还有剩余没有发完，再次接着发送，
                    sentCount.getAndIncrement();
                    outgoingQueue.removeFirstOccurrence(p);
                    if (p.requestHeader != null
                        && p.requestHeader.getType() != OpCode.ping
                        && p.requestHeader.getType() != OpCode.auth) {
                        // 发送完了，就从队列中删除，并加到pendingQueue，等待响应
                        synchronized (pendingQueue) {
                            pendingQueue.add(p);
                        }
                    }
                }
            }
            if (outgoingQueue.isEmpty()) {
                // No more packets to send: turn off write interest flag.
                // Will be turned on later by a later call to enableWrite(),
                // from within ZooKeeperSaslClient (if client is configured
                // to attempt SASL authentication), or in either doIO() or
                // in doTransport() if not.
                disableWrite();
            } else if (!initialized && p != null && !p.bb.hasRemaining()) {
                // On initial connection, write the complete connect request
                // packet, but then disable further writes until after
                // receiving a successful connection response.  If the
                // session is expired, then the server sends the expiration
                // response and immediately closes its end of the socket.  If
                // the client is simultaneously writing on its end, then the
                // TCP stack may choose to abort with RST, in which case the
                // client would never receive the session expired event.  See
                // http://docs.oracle.com/javase/6/docs/technotes/guides/net/articles/connection_release.html
                disableWrite();
            } else {
                // Just in case
                // 没有写完 ，继续注册写
                enableWrite();
            }
        }
    }

    private Packet findSendablePacket(LinkedBlockingDeque<Packet> outgoingQueue, boolean tunneledAuthInProgres) {
        if (outgoingQueue.isEmpty()) {
            return null;
        }
        // If we've already starting sending the first packet, we better finish
        if (outgoingQueue.getFirst().bb != null || !tunneledAuthInProgres) {
            // 如果 第一个包已经在发送了，即已编码  or 已经认证（无论失败或者成功）
            return outgoingQueue.getFirst();
        }
        // Since client's authentication with server is in progress,
        // send only the null-header packet queued by primeConnection().
        // This packet must be sent so that the SASL authentication process
        // can proceed, but all other packets should wait until
        // SASL authentication completes.
        Iterator<Packet> iter = outgoingQueue.iterator();
        while (iter.hasNext()) {
            Packet p = iter.next();
            if (p.requestHeader == null) {
                // We've found the priming-packet. Move it to the beginning of the queue.
                iter.remove();
                outgoingQueue.addFirst(p);
                return p;
            } else {
                // Non-priming packet: defer it until later, leaving it in the queue
                // until authentication completes.
                LOG.debug("Deferring non-priming packet {} until SASL authentication completes.", p);
            }
        }
        return null;
    }

    @Override
    void cleanup() {
        if (sockKey != null) {
            SocketChannel sock = (SocketChannel) sockKey.channel();
            sockKey.cancel();
            try {
                sock.socket().shutdownInput();
            } catch (IOException e) {
                LOG.debug("Ignoring exception during shutdown input", e);
            }
            try {
                sock.socket().shutdownOutput();
            } catch (IOException e) {
                LOG.debug("Ignoring exception during shutdown output", e);
            }
            try {
                sock.socket().close();
            } catch (IOException e) {
                LOG.debug("Ignoring exception during socket close", e);
            }
            try {
                sock.close();
            } catch (IOException e) {
                LOG.debug("Ignoring exception during channel close", e);
            }
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            LOG.debug("SendThread interrupted during sleep, ignoring");
        }
        sockKey = null;
    }

    @Override
    void close() {
        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Doing client selector close");
            }

            selector.close();

            if (LOG.isTraceEnabled()) {
                LOG.trace("Closed client selector");
            }
        } catch (IOException e) {
            LOG.warn("Ignoring exception during selector close", e);
        }
    }

    /**
     * create a socket channel.
     * @return the created socket channel
     * @throws IOException
     */
    SocketChannel createSock() throws IOException {
        SocketChannel sock;
        sock = SocketChannel.open();
        sock.configureBlocking(false);
        sock.socket().setSoLinger(false, -1);
        sock.socket().setTcpNoDelay(true);
        return sock;
    }
    /**
     * register with the selection and connect
     * @param sock the {@link SocketChannel}
     * @param addr the address of remote host
     * @throws IOException
     */
    void registerAndConnect(SocketChannel sock, InetSocketAddress addr) throws IOException {
        // 注册 OP_CONNECT 和 发起连接
        sockKey = sock.register(selector, SelectionKey.OP_CONNECT);
        boolean immediateConnect = sock.connect(addr);
        if (immediateConnect) {
            // 如果 立刻连接上了，则 primeConnection，建立会话
            sendThread.primeConnection();
        }
    }
    @Override
    void connect(InetSocketAddress addr) throws IOException {
        // 创建非阻塞的 SocketChannel
        SocketChannel sock = createSock();
        try {
            // 注册 OP_CONNECT 和 发起连接
            registerAndConnect(sock, addr);
        } catch (UnresolvedAddressException | UnsupportedAddressTypeException | SecurityException | IOException e) {
            LOG.error("Unable to open socket to {}", addr);
            sock.close();
            throw e;
        }
        initialized = false;
        /*
         * Reset incomingBuffer
         */
        lenBuffer.clear();
        incomingBuffer = lenBuffer;
    }

    /**
     * Returns the address to which the socket is connected.
     *
     * @return ip address of the remote side of the connection or null if not
     *         connected
     */
    @Override
    SocketAddress getRemoteSocketAddress() {
        return remoteSocketAddress;
    }

    /**
     * Returns the local address to which the socket is bound.
     *
     * @return ip address of the remote side of the connection or null if not
     *         connected
     */
    @Override
    SocketAddress getLocalSocketAddress() {
        return localSocketAddress;
    }

    private void updateSocketAddresses() {
        Socket socket = ((SocketChannel) sockKey.channel()).socket();
        localSocketAddress = socket.getLocalSocketAddress();
        remoteSocketAddress = socket.getRemoteSocketAddress();
    }

    @Override
    void packetAdded() {
        wakeupCnxn();
    }

    @Override
    void onClosing() {
        wakeupCnxn();
    }

    private synchronized void wakeupCnxn() {
        selector.wakeup();
    }

    @Override
    void doTransport(
        int waitTimeOut,
        Queue<Packet> pendingQueue,
        ClientCnxn cnxn) throws IOException, InterruptedException {
        selector.select(waitTimeOut);
        Set<SelectionKey> selected;
        synchronized (this) {
            selected = selector.selectedKeys();
        }
//        LOG.info("doTransport, selected={}", selected);
        for (SelectionKey selectionKey : selected) {
/*            LOG.info("selectionKey,OP_CONNECT={},OP_READ={},OP_WRITE={}", (selectionKey.readyOps() & SelectionKey.OP_CONNECT) != 0,
                    (selectionKey.readyOps() & SelectionKey.OP_READ) != 0, (selectionKey.readyOps() & SelectionKey.OP_WRITE) != 0);*/
        }

        // Everything below and until we get back to the select is
        // non blocking, so time is effectively a constant. That is
        // Why we just have to do this once, here
        updateNow();
        for (SelectionKey k : selected) {
            SocketChannel sc = ((SocketChannel) k.channel());
            if ((k.readyOps() & SelectionKey.OP_CONNECT) != 0) {
                if (sc.finishConnect()) {
                    updateLastSendAndHeard();
                    updateSocketAddresses();
                    // 连接建立之后，开始建立session
                    sendThread.primeConnection();
                }
            } else if ((k.readyOps() & (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) != 0) {
                doIO(pendingQueue, cnxn);
            }
        }
        if (sendThread.getZkState().isConnected()) {
            if (findSendablePacket(outgoingQueue, sendThread.tunnelAuthInProgress()) != null) {
                enableWrite();
            }
        }
        selected.clear();
    }

    //TODO should this be synchronized?
    @Override
    void testableCloseSocket() throws IOException {
        LOG.info("testableCloseSocket() called");
        // sockKey may be concurrently accessed by multiple
        // threads. We use tmp here to avoid a race condition
        SelectionKey tmp = sockKey;
        if (tmp != null) {
            ((SocketChannel) tmp.channel()).socket().close();
        }
    }

    @Override
    void saslCompleted() {
        enableWrite();
    }

    synchronized void enableWrite() {
        int i = sockKey.interestOps();
        if ((i & SelectionKey.OP_WRITE) == 0) {
            sockKey.interestOps(i | SelectionKey.OP_WRITE);
        }
    }

    private synchronized void disableWrite() {
        int i = sockKey.interestOps();
        if ((i & SelectionKey.OP_WRITE) != 0) {
            sockKey.interestOps(i & (~SelectionKey.OP_WRITE));
        }
    }

    private synchronized void enableRead() {
        int i = sockKey.interestOps();
        if ((i & SelectionKey.OP_READ) == 0) {
            sockKey.interestOps(i | SelectionKey.OP_READ);
        }
    }

    @Override
    void connectionPrimed() {
        sockKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    Selector getSelector() {
        return selector;
    }

    @Override
    void sendPacket(Packet p) throws IOException {
        SocketChannel sock = (SocketChannel) sockKey.channel();
        if (sock == null) {
            throw new IOException("Socket is null!");
        }
        p.createBB();
        ByteBuffer pbb = p.bb;
        sock.write(pbb);
    }

}
