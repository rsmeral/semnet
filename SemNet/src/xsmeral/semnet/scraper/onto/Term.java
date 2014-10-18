package xsmeral.semnet.scraper.onto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates the field as a part of a vocabulary.
 * Should be used on fields of type {@link org.openrdf.model.URI}.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Term {
    // optional definition of the term
    String value() default "";
}
