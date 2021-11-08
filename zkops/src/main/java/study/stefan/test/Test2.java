package study.stefan.test;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;

public class Test2 {
    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
        String zkAddress = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
        ZooKeeper zooKeeper = new ZooKeeper(zkAddress, 10000, null);
        Stat stat = new Stat();
/*        String res = zooKeeper.create("/test1", "hello world!".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, stat);
        System.out.println(res);
        System.out.println(stat.toString());*/
        Stat stat1 = zooKeeper.setData("/test1", "hhhhhh".getBytes(), -1);
        System.out.println(stat1.toString());
    }
}
