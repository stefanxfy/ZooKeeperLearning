package study.stefan;

import org.junit.Test;
import study.stefan.constant.FourLetterCommand;
import study.stefan.entity.ZkClientCons;
import study.stefan.entity.ZkServerConf;
import study.stefan.entity.ZkServerState;
import study.stefan.parser.ZkClientConsReaderParser;
import study.stefan.parser.ZkServerConfReaderParser;
import study.stefan.parser.ZkServerStateReaderParser;
import study.stefan.util.FourLetterCommandUtil;

import java.util.List;

/**
 * @author stefan
 * @date 2021/10/14 16:01
 */
public class FourLetterCommandTest {

    @Test
    public void test() {
        String res = FourLetterCommandUtil.doCommand("119.23.73.197", 2382, FourLetterCommand.ruok);
        System.out.println(res);
    }

    @Test
    public void test1() {
        List<String> list = FourLetterCommandUtil.doCommandToList("119.23.73.197", 2381, FourLetterCommand.conf);
        System.out.println(list);
    }

    @Test
    public void testZkServerConf() {
        ZkServerConf zkServerConf = FourLetterCommandUtil.doCommand("119.23.73.197", 2381,
                FourLetterCommand.conf, new ZkServerConfReaderParser());
        System.out.println(zkServerConf);
    }

    @Test
    public void testZkServerState() {
        ZkServerState zkServerState = FourLetterCommandUtil.doCommand("119.23.73.197", 2381,
                FourLetterCommand.mntr, new ZkServerStateReaderParser());
        System.out.println(zkServerState);
    }

    @Test
    public void testZkServerCons() {
        List<ZkClientCons> zkClientConsList = FourLetterCommandUtil.doCommand("119.23.73.197", 2181,
                FourLetterCommand.cons, new ZkClientConsReaderParser());
        for (ZkClientCons zkClientCons : zkClientConsList) {
            System.out.println(zkClientCons);
        }
    }
}
