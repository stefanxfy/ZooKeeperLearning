package study.stefan.test;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;

/**
 * @author stefan
 * @date 2022/3/9 13:49
 */
public class Test {
    public static void main(String[] args) throws IOException, InterruptedException {
        String connectString = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
        ZooKeeper zooKeeper = new ZooKeeper(connectString, 20000, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (event.getState() == Event.KeeperState.SyncConnected) {
                    System.out.println("会话建立成功");
                }
            }
        });
    }
}
