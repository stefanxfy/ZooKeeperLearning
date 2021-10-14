package study.stefan.util;

import study.stefan.constant.FourLetterCommand;
import study.stefan.parser.ListStringReaderParser;
import study.stefan.parser.ReaderParser;
import study.stefan.parser.StringReaderParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/**
 * @author stefan
 * @date 2021/10/14 15:05
 */
public class FourLetterCommandUtil {

    private static final int DEFAULT_TIME = 5000;

    public static List<String> doCommandToList(String host, int port, FourLetterCommand command) {
        return doCommand(host, port, command, new ListStringReaderParser());
    }

    public static String doCommand(String host, int port, FourLetterCommand command) {
        return doCommand(host, port, command, new StringReaderParser());
    }

    public static <T> T doCommand(String host, int port, FourLetterCommand command, ReaderParser<T> parser) {
        return doCommand(host, port, command.name(), parser);
    }

    public static <T> T doCommand(String host, int port, String command, ReaderParser<T> parser) {
        if (command == null || command.isEmpty()) {
            return null;
        }
        Socket socket = null;
        OutputStream outstream = null;
        BufferedReader reader = null;
        try {
            socket = new Socket();
            socket.setSoTimeout(DEFAULT_TIME);
            InetSocketAddress hostaddress = host != null ? new InetSocketAddress(host, port) :
                    new InetSocketAddress(InetAddress.getByName(null), port);
            socket.connect(hostaddress, DEFAULT_TIME);
            outstream = socket.getOutputStream();
            outstream.write(command.getBytes());
            outstream.flush();
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            return parser.parseReader(reader);
        } catch (Exception e) {
            throw new RuntimeException("doCommand err, " + host + ":" + port + ", " + command, e);
        } finally {
            if (outstream != null) {
                try {
                    outstream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
