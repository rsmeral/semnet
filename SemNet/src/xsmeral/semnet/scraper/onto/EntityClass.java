package xsmeral.semnet.scraper.onto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates the field as an entity class.
 * Should be used on fields of type {@link org.openrdf.model.URI}.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntityClass {
}
