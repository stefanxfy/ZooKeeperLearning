package study.stefan.test.api;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class Connect {
    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
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
    }
}
