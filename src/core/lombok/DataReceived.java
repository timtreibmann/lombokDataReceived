package lombok;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Tim Treibmann
 * Only Classes are a valid target for the Annotation @DataReceived
 * Annotated classes will be injected with dataReceived(ConnectionEvent evt){...}
 * value is a string that defines the custom command id
 * controller is the name of the controller class as string - controller class must be in the same package as annotated class
 * classes annotated with this annotation must also annotated with @CommensLog
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface DataReceived {

    String value() default "VALID";
    String controller() default "Mavenproject1Controller";

}

