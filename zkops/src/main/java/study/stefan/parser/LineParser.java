package study.stefan.parser;


import java.text.ParseException;

/**
 * @author stefan
 * @date 2021/10/14 15:05
 */
public interface LineParser<T> {
    T parse(String line) throws ParseException;
}
