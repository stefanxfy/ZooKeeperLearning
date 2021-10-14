package study.stefan.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author stefan
 * @date 2021/10/14 15:34
 */
public class MapReaderParser implements ReaderParser<Map<String, String>> {
    private LineParser<String[]> lineParser = null;

    public MapReaderParser() {
        lineParser = new KvPairLineParser();
    }

    public MapReaderParser(String separator) {
        this.lineParser = new KvPairLineParser(separator);
    }

    public MapReaderParser(LineParser lineParser) {
        this.lineParser = lineParser;
    }

    @Override
    public Map<String, String> parseReader(BufferedReader reader) throws ParseException {
        try {
            Map<String, String> result = new HashMap<String, String>();
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] splits = lineParser.parse(line);
                result.put(splits[0], splits[1]);
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("reader.readLine err", e);
        }
    }
}
