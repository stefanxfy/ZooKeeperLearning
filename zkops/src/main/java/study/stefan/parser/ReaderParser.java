package study.stefan.parser;

import java.io.BufferedReader;
import java.text.ParseException;

/**
 * @author stefan
 * @date 2021/10/14 15:05
 */
public interface ReaderParser<T> {

    T parseReader(BufferedReader reader) throws ParseException;

}
