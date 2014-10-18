package xsmeral.pipe.context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Signifies, that a field's value should be obtained from {@link ProcessingContext}.<br />
 * The field's value is set by {@link xsmeral.pipe.Pipe} after
 * {@link xsmeral.pipe.interfaces.ObjectProcessor#initialize(java.util.Map) ObjectProcessor.initialize()}
 * and before {@link xsmeral.pipe.interfaces.ObjectProcessor#run() }<br />
 * 
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FromContext {

    /**
     * If no value is set, the context parameter name is the field's name.
     * @return Context parameter name
     */
    String value() default "";
}
