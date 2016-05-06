package lombok.extern.jbosslog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Causes lombok to generate a logger field.
 * <p>
 * Complete documentation is found at <a href="https://projectlombok.org/features/Log.html">the project lombok features page for lombok log annotations</a>.
 * <p>
 * Example:
 * <pre>
 * &#64;JBossLog
 * public class LogExample {
 * }
 * </pre>
 * 
 * will generate:
 * 
 * <pre>
 * public class LogExample {
 *     private static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(LogExample.class);
 * }
 * </pre>
 * 
 * This annotation is valid for classes and enumerations.<br />
 * @see org.jboss.logging.Logger org.jboss.logging.Logger
 * @see org.jboss.logging.Logger#getLogger(java.lang.Class) org.jboss.logging.Logger.getLogger(Class target)
 * @see lombok.extern.apachecommons.CommonsLog &#64;CommonsLog
 * @see lombok.extern.java.Log &#64;Log
 * @see lombok.extern.log4j.Log4j &#64;Log4j
 * @see lombok.extern.log4j.Log4j2 &#64;Log4j2
 * @see lombok.extern.slf4j.XSlf4j &#64;XSlf4j
 * @see lombok.extern.jbosslog.JBossLog &#64;JBossLog
 *  */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface JBossLog {
	
	/**
	 * Sets the category of the constructed Logger. By default, it will use the type where the annotation is placed.
	 */
	String topic() default "";
}