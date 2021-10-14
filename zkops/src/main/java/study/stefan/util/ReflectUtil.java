package study.stefan.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * @author stefan
 * @date 2021/10/14 15:18
 */
public class ReflectUtil {

    public static <T> void assignAttrs(T object, Map<String, String> attrMap) {
        for (String key : attrMap.keySet()) {
            try {
                Field field = object.getClass().getDeclaredField(key);
                String originValue = attrMap.get(key);
                if (field != null && originValue != null) {
                    String fieldType = field.getGenericType().toString();
                    Object value = originValue;
                    if (fieldType.equals("class java.lang.Integer")) {
                        value = Integer.valueOf(originValue);
                    } else if (fieldType.equals("class java.lang.Long")) {
                        value = Long.valueOf(originValue);
                    }

                    String setMethodName = getSetMethodName(key);
                    Method method = object.getClass().getDeclaredMethod(setMethodName, field.getType());
                    if (method != null) {
                        method.invoke(object, value);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("assignAttrs err, key=" + key, e);
            }
        }
    }

    /**
     * 获取set打头的方法名称
     *
     * @param name 名称
     * @return
     */
    private static String getSetMethodName(String name) {
        name = name.substring(0, 1).toUpperCase() + name.substring(1);
        return "set" + name;
    }
}
