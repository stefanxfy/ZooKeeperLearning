package study.stefan.log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class Test {
    public static void main(String[] args) throws FileNotFoundException {
        String filepath = "C:\\study\\myStudy\\ZooKeeperLearning\\data\\zoo-1\\version-2\\snapshot.200000000";
        String dest = filepath + ".txt";
        FileOutputStream fileOutputStream = new FileOutputStream(dest, true);
        SystemLogHandler.startCapture(fileOutputStream);
        System.out.println("111111111111111111111");
        System.out.println("2222222222222222222222");
        SystemLogHandler.reset();
        System.out.println("333333333333333333333333");
        System.out.println("444444444444444444444444");
    }
}
