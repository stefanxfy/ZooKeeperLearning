package study.stefan.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author stefan
 * @date 2021/10/14 15:56
 */
public class ListStringReaderParser implements ReaderParser<List<String>> {
    @Override
    public List<String> parseReader(BufferedReader reader) throws ParseException {
        List<String> list = new ArrayList<String>();
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                list.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }
}
