package study.stefan.test;

import org.apache.zookeeper.*;
import org.apache.zookeeper.client.ZKClientConfig;
import org.apache.zookeeper.client.ZooKeeperSaslClient;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * sessionTimeout = 10000
 * negotiatedSessionTimeout 会跟服务端协商，最小4000
 *
 * loop1
 * Sun Dec 05 21:46:58 CST 2021timeToNextPing=-1002, to=3332
 * 发送ping -2 11
 * Sun Dec 05 21:46:58 CST 2021sendPing=-1002--------------------------------------------
 * Sun Dec 05 21:46:58 CST 2021to=3332
 * selector []
 *
 * loop2
 * Sun Dec 05 21:46:58 CST 2021timeToNextPing=3331, to=3330
 * Sun Dec 05 21:46:58 CST 2021to=3330
 * write事件
 *
 * loop3
 * Sun Dec 05 21:46:58 CST 2021timeToNextPing=3333, to=3330
 * Sun Dec 05 21:46:58 CST 2021to=3330
 * read len
 *
 * loop4
 * Sun Dec 05 21:46:58 CST 2021timeToNextPing=3332, to=3329
 * Sun Dec 05 21:46:58 CST 2021to=3329
 * read response，重置 heard time
 *
 * loop5
 * Sun Dec 05 21:46:58 CST 2021timeToNextPing=3331, to=6666
 * Sun Dec 05 21:46:58 CST 2021to=3331
 * select阻塞3秒
 *
 * Sun Dec 05 21:47:01 CST 2021timeToNextPing=-1013, to=3322
 * Sun Dec 05 21:47:01 CST 2021sendPing=-1013--------------------------------------------
 * Sun Dec 05 21:47:01 CST 2021to=3322
 * Sun Dec 05 21:47:01 CST 2021timeToNextPing=3332, to=3321
 * Sun Dec 05 21:47:01 CST 2021to=3321
 * Sun Dec 05 21:47:01 CST 2021timeToNextPing=3333, to=3321
 * Sun Dec 05 21:47:01 CST 2021to=3321
 * Sun Dec 05 21:47:01 CST 2021timeToNextPing=3332, to=3320
 * Sun Dec 05 21:47:01 CST 2021to=3320
 * Sun Dec 05 21:47:01 CST 2021timeToNextPing=3331, to=6666
 * Sun Dec 05 21:47:01 CST 2021to=3331
 */
public class TestZk {
    public static void main(String[] args) throws IOException, InterruptedException {
        String connectString = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
        // 可以关闭sasl认证
        System.setProperty(ZKClientConfig.ENABLE_CLIENT_SASL_KEY, "false");
        int size = 1;
        List<ZkCli> zooKeeperList = new ArrayList<ZkCli>(size);

        for (int i = 0; i < size; i++) {
            ZkCli zkCli = new ZkCli(false);
            synchronized (zkCli) {
                long start = System.currentTimeMillis();
                ZooKeeper zooKeeper = new ZooKeeper(connectString, 30000, new ConnectedWatcher(zkCli, start));
                while (true) {
                    if (zkCli.isConnected()) {
                        long end = System.currentTimeMillis();
                        zkCli.setZooKeeper(zooKeeper);
                        zooKeeperList.add(zkCli);
                        System.out.println("add......cost=" + (end - start));
                        break;
                    } else {
                        System.out.println("wait......");
                        zkCli.wait(1000);
                    }
                }
            }
        }

        String path = "/test1";
        ZooKeeper zooCli = zooKeeperList.get(0).getZooKeeper();
        byte[] val = null;
        Stat stat = new Stat();
        try {
            val = zooCli.getData(path, new CommonWatcher(zooCli), stat);
        } catch (KeeperException e) {
            e.printStackTrace();
        }
        System.out.println("val=" + new String(val));

        zooCli.setData(path, "lllllll22".getBytes(), stat.getVersion(), new MyStatCallback(), stat);

        Thread.sleep(100000000);
    }


    private static class ZkCli {
        private ZooKeeper zooKeeper;
        private boolean connected;

        public ZkCli(boolean connected) {
            this.connected = connected;
        }

        public ZooKeeper getZooKeeper() {
            return zooKeeper;
        }

        public void setZooKeeper(ZooKeeper zooKeeper) {
            this.zooKeeper = zooKeeper;
        }

        public boolean isConnected() {
            return connected;
        }

        public void setConnected(boolean connected) {
            this.connected = connected;
        }
    }

    private static class ConnectedWatcher implements Watcher {
        private ZkCli zooKeeper;
        private long startTime = System.currentTimeMillis();

        public ConnectedWatcher(ZkCli zooKeeper, long startTime) {
            this.zooKeeper = zooKeeper;
            this.startTime = startTime;
        }

        @Override
        public void process(WatchedEvent watchedEvent) {
            if (watchedEvent.getState() == Event.KeeperState.SyncConnected) {
                synchronized (zooKeeper) {
                    long endTime = System.currentTimeMillis();
                    System.out.println("connected。。。notify。。。cost=" + (endTime - startTime));
                    zooKeeper.setConnected(true);
                    zooKeeper.notify();
                }
                return;
            }
        }
    }

    private static class CommonWatcher implements Watcher {
        private ZooKeeper zooCli;

        public CommonWatcher(ZooKeeper zooCli) {
            this.zooCli = zooCli;
        }

        @Override
        public void process(WatchedEvent event) {
            String path = event.getPath();
            switch (event.getType()) {
                case NodeCreated:
                    try {
                        byte[] val = zooCli.getData(path, this, null);
                        System.out.println("created...path=" + path + ", val=" + new String(val));
                    } catch (KeeperException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return;
                case NodeDeleted:
                    System.out.println("deleted...path=" + path);
                    return;
                case NodeDataChanged:
                    try {
                        byte[] val = zooCli.getData(path, this, null);
                        System.out.println("data chaned...path=" + path + ", val=" + new String(val));
                    } catch (KeeperException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return;
                default:
                    System.out.println("not foud process, event=" + event.toString());
            }
        }
    }

    private static class MyStatCallback implements AsyncCallback.StatCallback {

        @Override
        public void processResult(int rc, String path, Object ctx, Stat stat) {
            System.out.println("MyStatCallback.processResult, rc=" + rc + ", path=" + path+ ", ctx=" + ctx + ", version=" + (stat != null ? stat.getVersion(): -1));
        }
    }
}
