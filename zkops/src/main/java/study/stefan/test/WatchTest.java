package study.stefan.test;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;

public class WatchTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        String zkAddress = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
        ZooKeeper zooKeeper = new ZooKeeper(zkAddress, 20000, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (event.getState() == Event.KeeperState.SyncConnected) {
                    System.out.println("会话建立成功");
                }
            }
        });
        byte[] val = new byte[0];
        try {
            val = zooKeeper.getData("/test1", new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    System.out.println("watch..............");
                    String path = event.getPath();

                    if (Event.EventType.fromInt(event.getType().getIntValue()) == Event.EventType.NodeCreated) {
                        System.out.println("path=" + path + " created...");
                        try {
                            Stat stat = new Stat();
                            byte[] val = zooKeeper.getData(path, new Watcher() {
                                @Override
                                public void process(WatchedEvent event) {
                                    if (Event.EventType.fromInt(event.getType().getIntValue()) == Event.EventType.NodeDataChanged) {
                                        System.out.println("path=" + path + " changed...");
                                    }
                                }
                            }, stat);
                            System.out.println("val=" + new String(val));
                        } catch (KeeperException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    if (Event.EventType.fromInt(event.getType().getIntValue()) == Event.EventType.NodeDataChanged) {
                        System.out.println("path=" + path + " changed...");
                    }
                }
            }, null);
        } catch (KeeperException e) {
            e.printStackTrace();
        }
        System.out.println("val=" + new String(val));
        Thread.sleep(1000000);
    }
}
