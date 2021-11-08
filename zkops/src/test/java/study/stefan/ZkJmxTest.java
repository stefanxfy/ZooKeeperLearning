package study.stefan;

import org.apache.zookeeper.server.ConnectionMXBean;
import org.apache.zookeeper.server.DataTreeMXBean;
import org.apache.zookeeper.server.ZooKeeperServerMXBean;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.*;
import java.util.*;

/**
 * @author stefan
 * @date 2021/11/5 14:09
 */
public class ZkJmxTest {
    static JMXConnector connector;
    /**
     * @param args
     * @throws IOException
     * @throws MalformedObjectNameException
     * @throws InstanceNotFoundException
     * @throws ReflectionException
     * @throws IntrospectionException
     */
    public static void main(String[] args) throws IOException, MalformedObjectNameException,
            InstanceNotFoundException, IntrospectionException, ReflectionException {
        OperatingSystemMXBean osbean = ManagementFactory.getOperatingSystemMXBean();
        System.out.println("体系结构:" + osbean.getArch());//操作系统体系结构
        System.out.println("处理器核数:" + osbean.getAvailableProcessors());///核数
        System.out.println("名字:" + osbean.getName());//名字
        System.out.println(osbean.getVersion());//操作系统版本
        ThreadMXBean threadBean= ManagementFactory.getThreadMXBean();
        System.out.println("活动线程:" + threadBean.getThreadCount());//总线程数
        ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
        CompilationMXBean compilationMXBean = ManagementFactory.getCompilationMXBean();
        System.out.println("===========");
        // 通过 MBeanServer间接地访问 MXBean 接口
        MBeanServerConnection mbsc = createMBeanServer("119.23.73.197", "5006", "controlRole", "123456");
        // 操作系统
        ObjectName os = new ObjectName("java.lang:type=OperatingSystem");
        System.out.println("体系结构:" + getAttribute(mbsc, os, "Arch"));//体系结构
        System.out.println("处理器核数:" + getAttribute(mbsc, os, "AvailableProcessors"));//核数
        System.out.println("总物理内存:" + getAttribute(mbsc, os, "TotalPhysicalMemorySize"));//总物理内存
        System.out.println("空闲物理内存:" + getAttribute(mbsc, os, "FreePhysicalMemorySize"));//空闲物理内存
        System.out.println("总交换空间:" + getAttribute(mbsc, os, "TotalSwapSpaceSize"));//总交换空间
        System.out.println("空闲交换空间:" + getAttribute(mbsc, os, "FreeSwapSpaceSize"));//空闲交换空间
        System.out.println("操作系统:" + getAttribute(mbsc, os, "Name")+ getAttribute(mbsc, os, "Version"));//操作系统
        System.out.println("提交的虚拟内存:" + getAttribute(mbsc, os, "CommittedVirtualMemorySize"));//提交的虚拟内存
        System.out.println("系统cpu使用率:" + getAttribute(mbsc, os, "SystemCpuLoad"));//系统cpu使用率
        System.out.println("进程cpu使用率:" + getAttribute(mbsc, os, "ProcessCpuLoad"));//进程cpu使用率
        System.out.println("============");//
        // 线程
        ObjectName Threading = new ObjectName("java.lang:type=Threading");
        System.out.println("活动线程:" + getAttribute(mbsc, Threading, "ThreadCount"));// 活动线程
        System.out.println("守护程序线程:" + getAttribute(mbsc, Threading, "DaemonThreadCount"));// 守护程序线程
        System.out.println("峰值:" + getAttribute(mbsc, Threading, "PeakThreadCount"));// 峰值
        System.out.println("启动的线程总数:" + getAttribute(mbsc, Threading, "TotalStartedThreadCount"));// 启动的线程总数
        ThreadMXBean threadBean2 = ManagementFactory.newPlatformMXBeanProxy
                (mbsc, ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class);
        System.out.println("活动线程:" + threadBean2.getThreadCount());// 活动线程
        ThreadMXBean threadBean3 = ManagementFactory.getThreadMXBean();
        System.out.println("本地活动线程:" + threadBean3.getThreadCount());// 本地活动线程
        System.out.println("============");//
        ObjectName Compilation = new ObjectName("java.lang:type=Compilation");
        System.out.println("总编译时间 毫秒:" + getAttribute(mbsc, Compilation, "TotalCompilationTime"));// 总编译时间 毫秒
        System.out.println("============");//
        ObjectName ClassLoading = new ObjectName("java.lang:type=ClassLoading");
        System.out.println("已加载类总数:" + getAttribute(mbsc, ClassLoading, "TotalLoadedClassCount"));// 已加载类总数
        System.out.println("已加装当前类:" + getAttribute(mbsc, ClassLoading, "LoadedClassCount"));// 已加装当前类
        System.out.println("已卸载类总数:" + getAttribute(mbsc, ClassLoading, "UnloadedClassCount"));// 已卸载类总数
        System.out.println("==========================================================");//
        // http://zookeeper.apache.org/doc/r3.4.6/zookeeperJMX.html
        // org.apache.ZooKeeperService:name0=ReplicatedServer_id1,name1=replica.1,name2=Follower
        ObjectName replica = new ObjectName("org.apache.ZooKeeperService:name0=ReplicatedServer_id1,name1=replica.1");
        System.out.println("replica.1运行状态:" + getAttribute(mbsc, replica, "State"));// 运行状态
//        mbsc = createMBeanServer("192.168.1.100", "9992", "controlRole", "123456");
        System.out.println("==============节点树对象===========");
        ObjectName dataTreePattern = new ObjectName("org.apache.ZooKeeperService:name0=ReplicatedServer_id?,name1=replica.?,name2=*,name3=InMemoryDataTree");
        Set<ObjectName> dataTreeSets = mbsc.queryNames(dataTreePattern, null);
        Iterator<ObjectName> dataTreeIterator = dataTreeSets.iterator();
        // 只有一个
        while (dataTreeIterator.hasNext()) {
            ObjectName dataTreeObjectName = dataTreeIterator.next();
            DataTreeMXBean dataTree = JMX.newMBeanProxy(mbsc, dataTreeObjectName, DataTreeMXBean.class);
            System.out.println("节点总数:" + dataTree.getNodeCount());// 节点总数
            System.out.println("Watch总数:" + dataTree.getWatchCount());// Watch总数
            System.out.println("临时节点总数:" + dataTree.countEphemerals());// Watch总数
            System.out.println("节点名及字符总数:" + dataTree.approximateDataSize());// 节点全路径和值的总字符数
            Map<String, String> dataTreeMap = dataTreeObjectName.getKeyPropertyList();
            String replicaId = dataTreeMap.get("name1").replace("replica.", "");
            String role = dataTreeMap.get("name2");// Follower,Leader,Observer,Standalone
            String canonicalName = dataTreeObjectName.getCanonicalName();
            int roleEndIndex = canonicalName.indexOf(",name3");
            ObjectName roleObjectName = new ObjectName(canonicalName.substring(0, roleEndIndex));
            System.out.println("==============zk服务状态===========");
            ZooKeeperServerMXBean ZooKeeperServer = JMX.newMBeanProxy(mbsc, roleObjectName, ZooKeeperServerMXBean.class);
            System.out.println(role + " 的IP和端口:" + ZooKeeperServer.getClientPort());// IP和端口
            System.out.println(role + " 活着的连接数:" + ZooKeeperServer.getNumAliveConnections());// 连接数
            System.out.println(role + " 未完成请求数:" + ZooKeeperServer.getOutstandingRequests());// 未完成的请求数
            System.out.println(role + " 接收的包:" + ZooKeeperServer.getPacketsReceived());// 收到的包
            System.out.println(role + " 发送的包:" + ZooKeeperServer.getPacketsSent());// 发送的包
            System.out.println(role + " 平均延迟（毫秒）:" + ZooKeeperServer.getAvgRequestLatency());
            System.out.println(role + " 最大延迟（毫秒）:" + ZooKeeperServer.getMaxRequestLatency());
            System.out.println(role + " 每个客户端IP允许的最大连接数:" + ZooKeeperServer.getMaxClientCnxnsPerHost());
            System.out.println(role + " 最大Session超时（毫秒）:" + ZooKeeperServer.getMaxSessionTimeout());
            System.out.println(role + " 心跳时间（毫秒）:" + ZooKeeperServer.getTickTime());
            System.out.println(role + " 版本:" + ZooKeeperServer.getVersion());// 版本
            // 三个重置操作
//      ZooKeeperServer.resetLatency(); //重置min/avg/max latency statistics
//      ZooKeeperServer.resetMaxLatency(); //重置最大延迟统计
//      ZooKeeperServer.resetStatistics(); // 重置包和延迟所有统计
            System.out.println("==============所有客户端的连接信息===========");
            ObjectName connectionPattern = new ObjectName("org.apache.ZooKeeperService:name0=ReplicatedServer_id?,name1=replica.?,name2=*,name3=Connections,*");
            Set<ObjectName> connectionSets = mbsc.queryNames(connectionPattern, null);
            List<ObjectName> connectionList = new ArrayList<ObjectName>(connectionSets.size());
            connectionList.addAll(connectionSets);
            Collections.sort(connectionList);
            for (ObjectName connectionON : connectionList) {
                System.out.println("=========================");
                ConnectionMXBean connectionBean = JMX.newMBeanProxy(mbsc, connectionON, ConnectionMXBean.class);
                System.out.println(" IP+Port:" + connectionBean.getSourceIP());//
                System.out.println(" SessionId:" + connectionBean.getSessionId());//
                System.out.println(" PacketsReceived:" + connectionBean.getPacketsReceived());// 收到的包
                System.out.println(" PacketsSent:" + connectionBean.getPacketsSent());// 发送的包
                System.out.println(" MinLatency:" + connectionBean.getMinLatency());//
                System.out.println(" AvgLatency:" + connectionBean.getAvgLatency());//
                System.out.println(" MaxLatency:" + connectionBean.getMaxLatency());//
                System.out.println(" StartedTime:" + connectionBean.getStartedTime());//
                System.out.println(" EphemeralNodes:" + connectionBean.getEphemeralNodes().length);//
                System.out.println(" EphemeralNodes:" + Arrays.asList(connectionBean.getEphemeralNodes()));//
                System.out.println(" OutstandingRequests:" + connectionBean.getOutstandingRequests());//
                //connectionBean.resetCounters();
                //connectionBean.terminateConnection();
                //connectionBean.terminateSession();
            }
        }
        // close connection
        if (connector != null) {
            connector.close();
        }
    }
    /**
     * 建立连接
     * @param ip
     * @param jmxport
     * @return
     */
    public static MBeanServerConnection createMBeanServer(String ip,
                                                          String jmxport, String userName, String password) {
        try {
            String jmxURL = "service:jmx:rmi:///jndi/rmi://" + ip + ":"
                    + jmxport + "/jmxrmi";
            // jmxurl
            JMXServiceURL serviceURL = new JMXServiceURL(jmxURL);
            Map<String, String[]> map = new HashMap<String, String[]>();
  /*          String[] credentials = new String[] { userName, password };
            map.put("jmx.remote.credentials", credentials);*/
            connector = JMXConnectorFactory.connect(serviceURL, null);
            MBeanServerConnection mbsc = connector.getMBeanServerConnection();
            return mbsc;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.err.println(ip + ":" + jmxport + " 连接建立失败");
        }
        return null;
    }
    /**
     * 使用MBeanServer获取对象名为[objName]的MBean的[objAttr]属性值
     * <p>
     * 静态代码: return MBeanServer.getAttribute(ObjectName name, String attribute)
     * @param mbeanServer
     *      - MBeanServer实例
     * @param objName
     *      - MBean的对象名
     * @param objAttr
     *      - MBean的某个属性名
     * @return 属性值
     */
    private static String getAttribute(MBeanServerConnection mbeanServer,
                                       ObjectName objName, String objAttr) {
        if (mbeanServer == null || objName == null || objAttr == null)
            throw new IllegalArgumentException();
        try {
            return String.valueOf(mbeanServer.getAttribute(objName, objAttr));
        } catch (Exception e) {
            return null;
        }
    }
}
