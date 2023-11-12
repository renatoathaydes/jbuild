package jbuild.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for constructor parameters of jb tasks' configuration classes
 * which indicates that the field's value comes from {@code jb}'s own configuration,
 * not that of the task.
 * <p>
 * The parameter name should match exactly the name of the {@code jb} configuration property,
 * or the name of the property may be explicitly provided by {@link JbConfigProperty#propertyName()}.
 * <p>
 * See {@link JbTask} for more details.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface JbConfigProperty {

    /**
     * @return the name of the {@code jb} property to use for the value of the parameter.
     * If empty (the default), the parameter name itself is used.
     */
    String propertyName() default "";
}
