package study.stefan.parser;

import study.stefan.entity.ZkClientCons;
import study.stefan.util.ReflectUtil;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author stefan
 * @date 2021/10/14 16:19
 */
public class ZkClientConsLineParser implements LineParser<ZkClientCons> {
    private LineParser<String[]> paramParser = new KvPairLineParser();

    @Override
    public ZkClientCons parse(String line) throws ParseException {
        if (line == null || line.isEmpty()) {
            throw new ParseException("line is empty", -1);
        }
        ZkClientCons zkClientCons = new ZkClientCons();
        if (line.contains("/") && line.contains(":")) {
            zkClientCons.setIp(line.substring(line.indexOf("/") + 1, line.indexOf(":")));
            zkClientCons.setPort(Integer.parseInt(line.substring(line.indexOf(":") + 1, line.indexOf("["))));
        } else {
            zkClientCons.setIp(line.substring(0, line.indexOf("[")));
            zkClientCons.setPort(0);
        }
        zkClientCons.setIpport(zkClientCons.getIp() + ":" + zkClientCons.getPort());
        zkClientCons.setIndex(Integer.parseInt(line.substring(line.indexOf("[") + 1, line.indexOf("]"))));
        String paramStr = line.substring(line.indexOf("(") + 1, line.indexOf(")"));
        Map<String, String> attrMap = convertToMap(paramStr);
        ReflectUtil.assignAttrs(zkClientCons, attrMap);
        zkClientCons.setInfoLine(line);
        return zkClientCons;
    }

    private Map<String, String> convertToMap(String paramStr) throws ParseException {
        Map<String, String> attrMap = new HashMap<String, String>();
        String[] paramArray = paramStr.split(",");
        for (String paramPair : paramArray) {
            if (paramPair != null && !paramPair.equals("\n")) {
                String[] splits = paramParser.parse(paramPair);
                attrMap.put(splits[0], splits[1]);
            }
        }
        return attrMap;
    }
}
