package study.stefan.entity;

import java.io.Serializable;

/**
 * 四字命令conf，一般获取一次即可，后面可手动刷新，
 * 获取dataDir和dataLogDir可用于日志文件备份和迁移
 * @author stefan
 * @date 2021/10/14 15:11
 */
public class ZkServerConf implements Serializable {
    private static final long serialVersionUID = 4048225087874237797L;
    /**
     * 客户端端口
     */
    private Integer clientPort;

    /**
     * 快照文件目录
     */
    private String dataDir;

    /**
     * 指定事物日志目录
     */
    private String dataLogDir;

    /**
     * 间隔单位时间
     */
    private Integer tickTime;

    /**
     * 最大连接数
     */
    private Integer maxClientCnxns;

    /**
     * 最小session超时
     */
    private Integer minSessionTimeout;

    /**
     * 最大session超时
     */
    private Integer maxSessionTimeout;

    /**
     * server id
     */
    private Integer serverId;

    /**
     * 初始化时间
     */
    private Integer initLimit;

    /**
     * 心跳时间间隔
     */
    private Integer syncLimit;

    /**
     * 选举算法 默认3
     */
    private Integer electionAlg;

    /**
     * 选举端口
     */
    private Integer electionPort;

    /**
     * 法人端口
     */
    private Integer quorumPort;

    /**
     * 服务类型，包括PARTICIPANT, OBSERVER
     */
    private Integer peerType;

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }

    public Integer getClientPort() {
        return clientPort;
    }

    public void setClientPort(Integer clientPort) {
        this.clientPort = clientPort;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getDataLogDir() {
        return dataLogDir;
    }

    public void setDataLogDir(String dataLogDir) {
        this.dataLogDir = dataLogDir;
    }

    public Integer getTickTime() {
        return tickTime;
    }

    public void setTickTime(Integer tickTime) {
        this.tickTime = tickTime;
    }

    public Integer getMaxClientCnxns() {
        return maxClientCnxns;
    }

    public void setMaxClientCnxns(Integer maxClientCnxns) {
        this.maxClientCnxns = maxClientCnxns;
    }

    public Integer getMinSessionTimeout() {
        return minSessionTimeout;
    }

    public void setMinSessionTimeout(Integer minSessionTimeout) {
        this.minSessionTimeout = minSessionTimeout;
    }

    public Integer getMaxSessionTimeout() {
        return maxSessionTimeout;
    }

    public void setMaxSessionTimeout(Integer maxSessionTimeout) {
        this.maxSessionTimeout = maxSessionTimeout;
    }

    public Integer getServerId() {
        return serverId;
    }

    public void setServerId(Integer serverId) {
        this.serverId = serverId;
    }

    public Integer getInitLimit() {
        return initLimit;
    }

    public void setInitLimit(Integer initLimit) {
        this.initLimit = initLimit;
    }

    public Integer getSyncLimit() {
        return syncLimit;
    }

    public void setSyncLimit(Integer syncLimit) {
        this.syncLimit = syncLimit;
    }

    public Integer getElectionAlg() {
        return electionAlg;
    }

    public void setElectionAlg(Integer electionAlg) {
        this.electionAlg = electionAlg;
    }

    public Integer getElectionPort() {
        return electionPort;
    }

    public void setElectionPort(Integer electionPort) {
        this.electionPort = electionPort;
    }

    public Integer getQuorumPort() {
        return quorumPort;
    }

    public void setQuorumPort(Integer quorumPort) {
        this.quorumPort = quorumPort;
    }

    public Integer getPeerType() {
        return peerType;
    }

    public void setPeerType(Integer peerType) {
        this.peerType = peerType;
    }

    @Override
    public String toString() {
        return "ZkClinetCons{" +
                "clientPort=" + clientPort +
                ", dataDir='" + dataDir + '\'' +
                ", dataLogDir='" + dataLogDir + '\'' +
                ", tickTime=" + tickTime +
                ", maxClientCnxns=" + maxClientCnxns +
                ", minSessionTimeout=" + minSessionTimeout +
                ", maxSessionTimeout=" + maxSessionTimeout +
                ", serverId=" + serverId +
                ", initLimit=" + initLimit +
                ", syncLimit=" + syncLimit +
                ", electionAlg=" + electionAlg +
                ", electionPort=" + electionPort +
                ", quorumPort=" + quorumPort +
                ", peerType=" + peerType +
                '}';
    }
}
