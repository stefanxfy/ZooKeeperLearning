package study.stefan.parser;

import study.stefan.entity.ZkClientCons;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author stefan
 * @date 2021/10/14 16:27
 */
public class ZkClientConsReaderParser implements ReaderParser<List<ZkClientCons>>{
    private LineParser<ZkClientCons> lineParser = new ZkClientConsLineParser();
    @Override
    public List<ZkClientCons> parseReader(BufferedReader reader) throws ParseException {
        String line = null;
        List<ZkClientCons> list = new ArrayList<ZkClientCons>();
        try {
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                ZkClientCons zkClientCons = lineParser.parse(line);
                list.add(zkClientCons);
            }
        } catch (IOException e) {
            throw new RuntimeException("reader.readLine err", e);
        }
        return list;
    }
}
