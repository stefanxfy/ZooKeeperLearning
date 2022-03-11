package study.stefan.test.api;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class Update {
    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
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
        // 1、获取节点信息
        Stat stat = new Stat();
        byte[] data = zooKeeper.getData("/testAcl1", false, stat);
        System.out.println("data: " + new String(data));
        System.out.println(stat.toString());
        // 2、更新节点
        zooKeeper.setData("/testAcl1", "你好, stefan".getBytes(), stat.getVersion());
        // 3、再次获取节点
        Stat stat1 = new Stat();
        byte[] data1 = zooKeeper.getData("/node1", false, stat);
        System.out.println("更新之后的：data: " + new String(data1));
        System.out.println(stat1.toString());
    }
}
