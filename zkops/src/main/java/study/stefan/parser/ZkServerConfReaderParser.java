package study.stefan.parser;

import study.stefan.entity.ZkServerConf;
import study.stefan.entity.ZkServerState;
import study.stefan.util.ReflectUtil;

import java.io.BufferedReader;
import java.text.ParseException;
import java.util.Map;

/**
 * @author stefan
 * @date 2021/10/14 15:45
 */
public class ZkServerConfReaderParser implements ReaderParser<ZkServerConf>{
    private ReaderParser<Map<String, String>> mapReaderParser = new MapReaderParser();

    @Override
    public ZkServerConf parseReader(BufferedReader reader) throws ParseException {
        Map<String, String> map = mapReaderParser.parseReader(reader);
        ZkServerConf zkServerConf = new ZkServerConf();
        ReflectUtil.assignAttrs(zkServerConf, map);
        return zkServerConf;
    }
}
