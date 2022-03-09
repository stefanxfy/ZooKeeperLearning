package study.stefan.test;

/**
 * @author fptang
 * @date 2022/3/8
 */
public class Test {
    // 题目2: 要求实现一个方法，以指定串为中心,反转两边的字符串。要求操作底层数组实现，不能直接调用工具类的api
    // 比如：传入Hello123World, 123, 反转后是World123Hello
    // 思路：将原字符串拆成左中右三个字符数组。
    public static void main(String[] args) {
        // String str1 = "Hello123World";
        // String str2 = "123";
        String str1 = "HelloWorld1234";
        String str2 = "1234";
        char[] c1 = str1.toCharArray();
        int x = 0, y = str1.length() / 2 + str2.length() / 2; // 双指针
        if (str2.length() % 2 != 0) { // 奇数下标加1
            y++;
        }
        // 交换
        char t = 0;
        while (y < c1.length) {
            t = c1[x];
            c1[x] = c1[y];
            c1[y] = t;
            x++;
            y++;
        }
        for (char c : c1) {
            System.out.print(c);
        }
    }
}
