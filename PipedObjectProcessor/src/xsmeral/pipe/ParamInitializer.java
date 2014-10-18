package xsmeral.pipe;

import java.lang.reflect.Field;
import java.util.Map;
import xsmeral.pipe.interfaces.Param;

/**
 * Initializes values of fields annotated with {@link Param}
 * with values from the initialization parameter map. The parameter name is
 * either the field name or a name specified as an argument of {@code Param}.
 */
public class ParamInitializer {

    /**
     * Performs the initialization and conversion.
     */
    public static void initialize(Object obj, Map<String, String> params) throws Exception {
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field f : fields) {
            Param annot = f.getAnnotation(Param.class);
            if (annot != null) {
                Class<?> type = f.getType();
                if (String.class.equals(type) || Boolean.class.equals(type) || Character.class.equals(type) || Number.class.isAssignableFrom(type) || type.isPrimitive()) {
                    String paramName = !annot.value().isEmpty() ? annot.value() : f.getName();
                    String paramValue = params.get(paramName);
                    Object value = null;
                    f.setAccessible(true);
                    if (paramValue != null) {
                        try {
                            if (String.class.equals(type)) {
                                value = paramValue;
                            } else if (Enum.class.isAssignableFrom(type)) {
                                value = Enum.valueOf((Class) type, paramValue);
                            } else if (Boolean.class.equals(type) || Boolean.TYPE.equals(type)) {
                                value = Boolean.valueOf(paramValue);
                            } else if (Integer.class.equals(type) || Integer.TYPE.equals(type)) {
                                value = Integer.valueOf(paramValue);
                            } else if (Byte.class.equals(type) || Byte.TYPE.equals(type)) {
                                value = Byte.valueOf(paramValue);
                            } else if (Short.class.equals(type) || Short.TYPE.equals(type)) {
                                value = Short.valueOf(paramValue);
                            } else if (Double.class.equals(type) || Double.TYPE.equals(type)) {
                                value = Double.valueOf(paramValue);
                            } else if (Float.class.equals(type) || Float.TYPE.equals(type)) {
                                value = Float.valueOf(paramValue);
                            } else if (Long.class.equals(type) || Long.TYPE.equals(type)) {
                                value = Long.valueOf(paramValue);
                            } else if (Character.class.equals(type) || Character.TYPE.equals(type)) {
                                value = Character.valueOf(paramValue.charAt(0));
                            }
                            f.set(obj, value);
                        } catch (IndexOutOfBoundsException ex) {
                            throw new Exception("Conversion error", ex);
                        } catch (NumberFormatException ex) {
                            throw new Exception("Conversion error", ex);
                        }
                    } else {
                        Object currentValue = f.get(obj);
                        if (currentValue == null) {
                            throw new Exception(f.getDeclaringClass().getName() + ": Field '" + f.getName() + "' not initialized and its required parameter '" + paramName + "' not found");
                        }
                    }
                } else {
                    throw new Exception("Initialization-time auto-wiring only supports Enum, Boolean, Character, String and Number. Field " + f.getName() + " has type " + type.getName());
                }
            }
        }
    }
}
