package mc.util.commands;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

import org.bukkit.command.CommandSender;


/**
 * <b>This annotation is effective only for subclasses of {@link BaseCommand}.</b>
 * <p>
 * Command Names may contain spaces, in which case they will be handled appropriately.
 * <p>
 * Used on a <b>Class</b>, this annotation may specify the Command Name that everything is based on. Normally, without this annotation
 * present, the Command Name will be the package name that the class resides in. To further customize the Command Name, override the
 * {@link BaseCommand#isCommandName isCommandName} method.
 * <p>
 * Used on a <b>Method</b>, this annotation specifies the Subcommand Name that the annotated method handles.<br>
 * For the automatic subcommand dispatch, this annotation must be present, and the annotated method must fulfill the following constraints:
 * <li>The method signature must consist only of primitives or their boxed variations, Enum Types, Strings, and at most <b>1</b>
 * <code>String[]</code> (or {@link List List&lt;String&gt;}). This single argument may have a length of 0.
 * <li>Normally, a method will only be called if the number of parameters it has been passed matches the number of consumed arguments. While
 * having a String[] parameter in the method signature naturally consumes any extra arguments, you can also set the
 * {@link ActionHandler#strictArgs() strictArgs} switch to false in the annotation definition. This will silently consume, but not pass
 * along, extra commands.
 * <li>Additionally, your annotated method may contain {@link CommandSender} (or subclasses) arguments. Any such arguments will
 * automatically be filled with the original CommandSender argument. If the sender is not an instance of the requested subclass, the
 * function will not be called.
 * <li>If {@link ActionHandler#concat() concat} is set to true, and an array/collection type is in the signature, that argument will be of
 * length 1 and contain the collected Strings joined by the specified {@link ActionHandler#delimiter() delimiter}.
 * <p>
 * Return values will be handled as follows:
 * <li>A void will be interpreted as true as long as it completes.
 * <li>Boolean types will be interpreted as-is.
 * <li>Numeric types, and characters, will be treated as true as long as they evaluate to something different that 0.
 * <li>Object references will be true as long as they are not <code>null</code>.
 * <p>
 * 
 * <pre>
 * package example.commands.teleport;
 * 
 * &#64;ActionHandler("tp")
 * public class TeleportCommand extends BaseCommand {
 * 	// this class would usually handle the command "/teleport", but it has been specified to handle "/tp" instead
 * }
 * 
 * 
 * package example.commands.fly;
 * 
 * &#64;ActionHandler({ "", "soar" })
 * public class FlyCommand extends BaseCommand {
 * 	// this class handles the commands "fly" and "soar" (because of the "" default value) as if they were the same
 * 
 * 	&#64;ActionHandler
 * 	public boolean blazer(int trail, String color); // this command will be handled as "/fly blazer int String"
 * 
 * 	&#64;ActionHandler("colors")
 * 	public void trail(String[] colors, boolean random); // this command will be handled as "/fly colors [String...] boolean"
 * 
 * 	&#64;ActionHandler(value = { "", "undo" }, strictArgs = false)
 * 	public static void reset(CommandSender sender); // this command will be handled as "/fly [reset|undo] [*]"
 * 
 * 	&#64;ActionHandler
 * 	public void invalid(CommandSender sender, String[] args1, String[] args2); // this command will throw an exception
 * 	                                                                           // Override onCommand for customization
 * 
 * 	&#64;Override
 * 	protected boolean handleNoArgs(CommandSender sender); // this will be called when only "/fly" is entered.
 * }
 * 
 * &#64;ActionHandler("example command")
 * public class Example extends BaseCommand {
 * 	// this class handles the commands "/example command [...]"
 * 
 * 	&#64;ActionHandler("whatever")
 * 	&#64;Override
 * 	public boolean handleNoArgs(); // handle "/example command" and "example command whatever" in exactly the same way
 * }
 * 
 * &#64;ActionHandler("concat")
 * public class Concat extends BaseCommand {
 * 	&#64;Override
 * 	protected List&lt;String&gt; getSubcommandNameArgs(List&lt;String&gt; test, Method method) {
 * 		List&lt;String&gt; keep = super.isSubcommandName(test, method);
 * 		ActionHandler handler = method.getAnnotation(ActionHandler.class);
 * 
 * 		// if super.isSubcommandName found a match
 * 		// or if the method is unsuitable for automatic dispatch
 * 		// or if it isn't the "do_concat" method
 * 		// just return the value of super.isSubcommandName
 * 		if (keep != null || handler == null || !handler.value()[0].equalsIgnoreCase("do_concat"))
 * 			return keep;
 * 
 * 		// otherwise, we want to directly handle the arguments as if the specified subcommand had been "do_concat"
 * 		return test;
 * 	}
 * 
 * 	&#64;ActionHandler(value = "do_concat", concat = true, delimiter = " + ")
 * 	public void concatAll(String[] args); // the args String[] has length 1 and all collected Strings concatenated into one
 * 	                                      // for example, "/concat 5 2 33 7" calls this method (thanks to the overridden isSubCommand
 * 	                                      // with args={ "5 + 2 + 33 + 7" }
 * }
 * </pre>
 */
@Documented
@Retention(RUNTIME)
@Target({ TYPE, METHOD })
public @interface ActionHandler {
	public String[] value()

	default "";

	public boolean strictArgs()

	default true;

	public boolean concat()

	default false;

	public String delimiter() default " ";
}
