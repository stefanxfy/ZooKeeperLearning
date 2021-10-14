package study.stefan.entity;

import java.io.Serializable;

/**
 * 四字命令cons
 * @author stefan
 * @date 2021/10/14 15:12
 */
public class ZkClientCons implements Serializable {
    private static final long serialVersionUID = -7058063219072303145L;
    /**
     * ip
     */
    private String ip;

    /**
     * 端口
     */
    private Integer port;

    /**
     * host:port，唯一识别一个连接信息
     */
    private String ipport;

    /**
     * 索引
     */
    private Integer index;

    /**
     * 堆积请求数
     */
    private Long queued;

    /**
     * 收包数
     */
    private Long recved;

    /**
     * 发包数
     */
    private Long sent;

    /**
     * session id
     */
    private String sid;

    /**
     * 最后操作命令
     */
    private String lop;

    /**
     * 连接时间戳
     */
    private Long est;

    /**
     * 超时时间
     */
    private Integer to;

    /**
     * 最后客户端请求id
     */
    private String lcxid;

    /**
     * 最后事务id（状态变更id）
     */
    private String lzxid;

    /**
     * 最后响应时间戳
     */
    private Long lresp;

    /**
     * 最新延时
     */
    private Integer llat;

    /**
     * 最小延时
     */
    private Integer minlat;

    /**
     * 平均延时
     */
    private Integer avglat;

    /**
     * 最大延时
     */
    private Integer maxlat;

    /**
     * 原信息
     */
    private String infoLine;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public Long getQueued() {
        return queued;
    }

    public void setQueued(Long queued) {
        this.queued = queued;
    }

    public Long getRecved() {
        return recved;
    }

    public void setRecved(Long recved) {
        this.recved = recved;
    }

    public Long getSent() {
        return sent;
    }

    public void setSent(Long sent) {
        this.sent = sent;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getLop() {
        return lop;
    }

    public void setLop(String lop) {
        this.lop = lop;
    }

    public Long getEst() {
        return est;
    }

    public void setEst(Long est) {
        this.est = est;
    }

    public Integer getTo() {
        return to;
    }

    public void setTo(Integer to) {
        this.to = to;
    }

    public String getLcxid() {
        return lcxid;
    }

    public void setLcxid(String lcxid) {
        this.lcxid = lcxid;
    }

    public String getLzxid() {
        return lzxid;
    }

    public void setLzxid(String lzxid) {
        this.lzxid = lzxid;
    }

    public Long getLresp() {
        return lresp;
    }

    public void setLresp(Long lresp) {
        this.lresp = lresp;
    }

    public Integer getLlat() {
        return llat;
    }

    public void setLlat(Integer llat) {
        this.llat = llat;
    }

    public Integer getMinlat() {
        return minlat;
    }

    public void setMinlat(Integer minlat) {
        this.minlat = minlat;
    }

    public Integer getAvglat() {
        return avglat;
    }

    public void setAvglat(Integer avglat) {
        this.avglat = avglat;
    }

    public Integer getMaxlat() {
        return maxlat;
    }

    public void setMaxlat(Integer maxlat) {
        this.maxlat = maxlat;
    }

    public String getInfoLine() {
        return infoLine;
    }

    public void setInfoLine(String infoLine) {
        this.infoLine = infoLine;
    }

    public String getIpport() {
        return ipport;
    }

    public void setIpport(String ipport) {
        this.ipport = ipport;
    }

    @Override
    public String toString() {
        return "ZkClientCons{" +
                "ip='" + ip + '\'' +
                ", port=" + port +
                ", ipport='" + ipport + '\'' +
                ", index=" + index +
                ", queued=" + queued +
                ", recved=" + recved +
                ", sent=" + sent +
                ", sid='" + sid + '\'' +
                ", lop='" + lop + '\'' +
                ", est=" + est +
                ", to=" + to +
                ", lcxid='" + lcxid + '\'' +
                ", lzxid='" + lzxid + '\'' +
                ", lresp=" + lresp +
                ", llat=" + llat +
                ", minlat=" + minlat +
                ", avglat=" + avglat +
                ", maxlat=" + maxlat +
                ", infoLine='" + infoLine + '\'' +
                '}';
    }
}
