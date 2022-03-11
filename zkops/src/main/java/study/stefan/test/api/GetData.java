package study.stefan.test.api;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class GetData {
    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        // 第一次连接
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
        zooKeeper.addAuthInfo("digest", "stefanxfy".getBytes());

        Stat stat = new Stat();
        byte[] data = zooKeeper.getData("/testAcl1", new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                System.out.println(event.toString());
            }
        }, stat);
        System.out.println("data: " + new String(data));
        System.out.println(stat.toString());
        Thread.sleep(1000000);
    }
}
