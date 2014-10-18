package xsmeral.pipe.interfaces;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Definition of processor's input/output type.
 * 
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ObjectProcessorInterface {

    /**
     * The input type
     */
    Class in() default Object.class;

    /**
     * The output type
     */
    Class out() default Object.class;
}
