package xsmeral.pipe.interfaces;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the value of the field should be retrieved from the processor's initialization parameter map.
 * If {@code value} is empty, the parameter name is the field's name.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Param {

    /**
     * If empty, the parameter name is the field's name.
     */
    String value() default "";
}
