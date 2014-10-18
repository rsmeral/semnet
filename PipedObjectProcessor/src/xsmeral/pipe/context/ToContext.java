package xsmeral.pipe.context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Signifies, that a field's value is shared in the processing context<br />
 * The field's value should be set in the processor's constructor or in
 * {@link xsmeral.pipe.interfaces.ObjectProcessor#initialize(java.util.Map) ObjectProcessor.initialize()}
 *
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ToContext {

    /**
     * Name of the context parameter. If empty, the parameter name is equal to the name of the field.
     */
    String value() default "";

}
