package study.stefan.parser;

import study.stefan.entity.ZkServerState;
import study.stefan.util.ReflectUtil;

import java.io.BufferedReader;
import java.text.ParseException;
import java.util.Map;

/**
 * @author stefan
 * @date 2021/10/14 15:45
 */
public class ZkServerStateReaderParser implements ReaderParser<ZkServerState>{
    private ReaderParser<Map<String, String>> mapReaderParser = new MapReaderParser("\t");

    @Override
    public ZkServerState parseReader(BufferedReader reader) throws ParseException {
        Map<String, String> map = mapReaderParser.parseReader(reader);
        ZkServerState zkServerState = new ZkServerState();
        ReflectUtil.assignAttrs(zkServerState, map);
        return zkServerState;
    }
}
