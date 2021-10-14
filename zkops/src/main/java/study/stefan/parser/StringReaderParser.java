package study.stefan.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.ParseException;

/**
 * @author stefan
 * @date 2021/10/14 15:52
 */
public class StringReaderParser implements ReaderParser<String>{
    @Override
    public String parseReader(BufferedReader reader) throws ParseException {
        StringBuilder stringBuilder = new StringBuilder();
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }
}
