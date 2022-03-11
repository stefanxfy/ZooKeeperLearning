package study.stefan.test.api;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class Create {
    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        CountDownLatch countDownLatch1 = new CountDownLatch(1);
        // 第一次连接
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
    }
}
