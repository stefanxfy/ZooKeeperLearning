package study.stefan.log;

import org.apache.jute.BinaryInputArchive;
import org.apache.jute.Record;
import org.apache.zookeeper.server.SnapshotFormatter;
import org.apache.zookeeper.server.persistence.FileHeader;
import org.apache.zookeeper.server.persistence.FileTxnLog;
import org.apache.zookeeper.server.util.SerializeUtils;
import org.apache.zookeeper.txn.TxnHeader;

import java.io.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

/**
 * @author stefan
 * @date 2021/10/20 16:43
 */
public class ZkLogTest {

    static {
        System.setOut(new SystemLogHandler(System.out));
        System.setErr(new SystemLogHandler(System.err));
    }
    public static void main(String[] args) throws IOException, InterruptedException {
/*        String filepath = "C:\\Users\\faisco\\Downloads\\zk\\log.5400000001";
        String dest = filepath + ".txt";
        logFormat(filepath, dest);*/
        // snapshot.5100000c94
        String filepath = "C:\\study\\myStudy\\ZooKeeperLearning\\data\\zoo-1\\version-2\\snapshot.200000000";
        String dest = filepath + ".txt";
        snapshotFormat(filepath, dest);
    }
    public static void snapshotFormat(String filepath, String dest) throws IOException, InterruptedException {
        FileOutputStream fileOutputStream = new FileOutputStream(dest, true);
        SystemLogHandler.startCapture(fileOutputStream);
        new SnapshotFormatter().run(filepath,false, false);
        SystemLogHandler.stopCapture();
    }

    /*public static void logFormat(String filepath, String dest) throws IOException {
        FileInputStream fis = new FileInputStream(filepath);
        BinaryInputArchive logStream = BinaryInputArchive.getArchive(fis);
        FileHeader fhdr = new FileHeader();
        fhdr.deserialize(logStream, "fileheader");

        if (fhdr.getMagic() != FileTxnLog.TXNLOG_MAGIC) {
            System.err.println("Invalid magic number for " + filepath);
            System.exit(2);
        }
        System.out.println("ZooKeeper Transactional Log File with dbid "
                + fhdr.getDbid() + " txnlog format version "
                + fhdr.getVersion());

        int count = 0;
        while (true) {
            long crcValue;
            byte[] bytes;
            try {
                crcValue = logStream.readLong("crcvalue");

                bytes = logStream.readBuffer("txnEntry");
            } catch (EOFException e) {
                System.out.println("EOF reached after " + count + " txns.");
                return;
            }
            if (bytes.length == 0) {
                // Since we preallocate, we define EOF to be an
                // empty transaction
                System.out.println("EOF reached after " + count + " txns.");
                return;
            }
            Checksum crc = new Adler32();
            crc.update(bytes, 0, bytes.length);
            if (crcValue != crc.getValue()) {
                throw new IOException("CRC doesn't match " + crcValue +
                        " vs " + crc.getValue());
            }
            TxnHeader hdr = new TxnHeader();
            Record txn = SerializeUtils.deserializeTxn(bytes, hdr);
            String content = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                    DateFormat.LONG).format(new Date(hdr.getTime()))
                    + " session 0x"
                    + Long.toHexString(hdr.getClientId())
                    + " cxid 0x"
                    + Long.toHexString(hdr.getCxid())
                    + " zxid 0x"
                    + Long.toHexString(hdr.getZxid())
                    + " " + TraceFormatter.op2String(hdr.getType()) + " " + txn + "\n";
//            FileEx.append(dest, content);
            if (logStream.readByte("EOR") != 'B') {
                throw new EOFException("Last transaction was partial.");
            }
            count++;
        }
    }*/
}
