package study.stefan.parser;

import java.text.ParseException;

/**
 * @author stefan
 * @date 2021/10/14 15:19
 */
public class KvPairLineParser implements LineParser<String[]> {
    public KvPairLineParser() {
        separator = DEFAULT_SEPARATOR;
    }
    public KvPairLineParser(String separator) {
        if (separator == null || separator.length() == 0) {
            throw new IllegalArgumentException("argument separator can't be empty");
        }

        this.separator = separator;
    }

    @Override
    public String[] parse(String line) throws ParseException {
        if (line == null || line.isEmpty()) {
            throw new ParseException("line is empty", -1);
        }
        String[] arr = line.split(separator);
        if (arr.length != 2) {
            throw new ParseException("line is not kv, separator=" + separator, 0);
        }
        return arr;
    }
    private String separator = null;
    private static final String DEFAULT_SEPARATOR = "=";

}
