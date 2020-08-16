package mc.util.commands;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;


public abstract class BaseCommand implements CommandExecutor {
	private static class EnumCompare implements Predicate<Object> {
		private String name;
		private Function<Enum<?>, String> converter;

		public EnumCompare(String name, boolean getName) {
			this.name = name;
			converter = getName ? Enum::name : Enum::toString;
		}

		@Override
		public boolean test(Object test) {
			return converter.apply((Enum<?>) test).equalsIgnoreCase(name);
		}
	}

	private static class Caller implements Callable<Boolean> {
		private final Method method;
		private final Object instance;
		private final CommandSender sender;
		private final List<String> args;

		public Caller(CommandSender sender, BaseCommand instance, Method method, List<String> args) {
			if (sender == null || instance == null || method == null || args == null)
				throw new NullPointerException();

			this.method = method;
			this.instance = instance;
			this.sender = sender;
			this.args = args;
		}

		private Object[] compileArguments() throws Exception {
			Class<?>[] types = method.getParameterTypes();

			int start = -1;
			int followargs = 0;
			int pos = 0;

			// pass 1: gather data
			for (int i = 0; i < types.length; i++) {
				Class<?> argclass = types[i];

				// argclass is a subtype of CommandSender
				if (CommandSender.class.isAssignableFrom(argclass)) {
					// if argclass may accept the sender, everything's fine
					if (argclass.isInstance(sender))
						continue;

					// otherwise, abort now
					return null;
				}

				if (argclass.isAssignableFrom(String[].class) || argclass.isAssignableFrom(List.class)) {
					// we've already encountered an Array or Collection type, and I can't deal with two (or more) of them
					if (start >= 0) {
						String fail = String.format("There are too many array/collection types in the method!\n%s", method);
						throw new RuntimeException(fail);
					}

					start = pos;
					continue;
				}

				// if we've already encountered an Array or Collection type, count the arguments following that
				// this is important because types.length might also include the Event class, which we don't count
				if (start >= 0)
					followargs++;
				// only increase the argument counter now because neither Array or Collection types (those may be empty) nor the Event count
				pos++;

				// check if the class is a (boxed) Java Primitive
				for (Class<?> deftype : new Class[] { Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class, Boolean.class,
				        Character.class }) {
					Class<?> primitive = (Class<?>) deftype.getField("TYPE").get(null);
					if (argclass.equals(deftype) || argclass.equals(primitive))
						continue;
				}

				// the class we want can be kept as a String
				if (argclass.isAssignableFrom(String.class))
					continue;

				if (argclass.isEnum())
					continue;

				// I can't identify the type, so I fail
				String fail = String.format("There's an argument of incompatible type in the method!\n%s", method);
				throw new RuntimeException(fail);
			}

			// insufficient arguments to call the function
			if (pos > args.size())
				return null;

			ActionHandler handler = method.getAnnotation(ActionHandler.class);
			// too many arguments to call the function (specified as strictArgs, and there was no Array or Collection argument)
			if (pos < args.size() && handler.strictArgs() && start < 0)
				return null;

			List<Object> out = new LinkedList<>();
			pos = 0;
			assignment: for (Class<?> argclass : types) {
				if (CommandSender.class.isAssignableFrom(argclass)) {
					out.add(sender);
					continue;
				}

				if (argclass.isAssignableFrom(String[].class) || argclass.isAssignableFrom(List.class)) {
					List<String> sublist = args.subList(pos, args.size() - followargs);

					if (handler.concat())
						sublist = Collections.singletonList(String.join(handler.delimiter(), sublist));

					out.add(argclass.isAssignableFrom(String[].class) ? sublist.toArray(new String[sublist.size()]) : sublist);
					pos = args.size() - followargs;
					continue;
				}

				String arg = args.get(pos);
				pos++;

				if (argclass.isAssignableFrom(String.class)) {
					out.add(arg);
					continue;
				}

				if (argclass.isEnum()) {
					Enum<?> value = null;
					try {
						@SuppressWarnings({ "unchecked", "rawtypes" })
						Enum<?> temp = Enum.valueOf((Class) argclass, arg);
						value = temp;
					} catch (IllegalArgumentException exc) {
						Object[] values = Arrays.stream(argclass.getEnumConstants()).filter(new EnumCompare(arg, true)).toArray();
						if (values.length == 0)
							values = Arrays.stream(argclass.getEnumConstants()).filter(new EnumCompare(arg, false)).toArray();

						if (values.length == 1)
							value = (Enum<?>) values[0];
					}

					if (value == null)
						return null;

					out.add(value);
					continue;
				}

				if (argclass.equals(Character.class) || argclass.equals(Character.TYPE)) {
					// the argument must be a single char!
					if (arg.length() != 1)
						return null;

					out.add(arg.charAt(0));
					continue;
				}

				if (arg.isEmpty())
					arg = "0";

				if (argclass.equals(Boolean.class) || argclass.equals(Boolean.TYPE)) {
					boolean value = Boolean.parseBoolean(arg);
					if (!value) {
						try {
							long v = Long.parseLong(arg);
							value = v != 0L;
						} catch (NumberFormatException exc) {}
					}

					out.add(value);
					continue;
				}

				for (Class<?> deftype : new Class[] { Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class }) {
					Class<?> primitive = (Class<?>) deftype.getField("TYPE").get(null);
					if (argclass.equals(deftype) || argclass.equals(primitive)) {
						String name = primitive.getSimpleName();
						name = String.format("parse%s%s", name.substring(0, 1).toUpperCase(), name.substring(1));
						Method converter = deftype.getMethod(name, String.class);

						try {
							out.add(converter.invoke(null, arg));
						} catch (InvocationTargetException exc) {
							return null; // couldn't parse the number
						}
						continue assignment;
					}
				}
			}

			return out.toArray();
		}

		@Override
		public Boolean call() throws Exception {
			Object[] arguments = compileArguments();
			if (arguments == null)
				return Boolean.FALSE;

			boolean access = method.isAccessible();
			Object ret = null;
			try {
				if (!access)
					method.setAccessible(true);
				ret = method.invoke(instance, arguments);
			} finally {
				method.setAccessible(access);
			}
			Class<?> returntype = method.getReturnType();

			// if the return type is void, method completion means success
			if (returntype.equals(Void.TYPE))
				return true;

			// if null is returned, interpret that as failure
			if (ret == null)
				return false;

			// if a char is returned, treat is the same as a number
			if (returntype.equals(Character.class) || returntype.equals(Character.TYPE))
				ret = (int) (char) ret;
			// if a number (or char, see above) is returned, interpret 0 as failure, and 1 as success
			if (ret instanceof Number)
				return ((Number) ret).doubleValue() != 0;

			// if the return is a boolean, use that
			if (returntype.equals(Boolean.class) || returntype.equals(Boolean.TYPE))
				return (boolean) ret;

			// assume that any return that's not null means success
			return true;
		}
	}

	private static class MultiCommand implements CommandExecutor {
		private List<CommandExecutor> executors;

		public MultiCommand(List<CommandExecutor> executors) {
			if (executors == null || executors.stream().anyMatch(Objects::isNull))
				throw new NullPointerException();

			this.executors = executors;
		}

		@Override
		public boolean onCommand(CommandSender sender, Command command, String arg2, String[] arguments) {
			boolean out = false;

			for (CommandExecutor executor : executors)
				out |= executor.onCommand(sender, command, arg2, arguments);

			return out;
		}
	}

	public static void registerCommands(JavaPlugin plugin, BaseCommand... commands) {
		registerCommands(plugin, false, commands);
	}

	public static void registerCommands(JavaPlugin plugin, boolean overwrite, BaseCommand... commands) {
		if (plugin == null || commands == null)
			throw new NullPointerException();

		Map<String, LinkedList<CommandExecutor>> cmdMap = new HashMap<>();
		for (BaseCommand command : commands) {
			if (command == null)
				throw new NullPointerException();

			for (String cmdName : command.getCommandNames()) {
				String cmd = cmdName.split("\\s+")[0];

				if (!cmdMap.containsKey(cmd))
					cmdMap.put(cmd, new LinkedList<>());
				LinkedList<CommandExecutor> list = cmdMap.get(cmd);

				if (!overwrite) {
					CommandExecutor previous = plugin.getCommand(cmd).getExecutor();
					if (previous != null)
						list.add(previous);
				}

				list.add(command);
			}
		}

		for (Entry<String, LinkedList<CommandExecutor>> entry : cmdMap.entrySet()) {
			String key = entry.getKey();
			LinkedList<CommandExecutor> value = entry.getValue();

			if (value.isEmpty())
				continue;

			CommandExecutor executor = value.removeFirst();
			if (!value.isEmpty()) {
				if (executor instanceof MultiCommand) {
					MultiCommand accumulator = (MultiCommand) executor;
					accumulator.executors.addAll(value);
				} else {
					value.addFirst(executor);
					executor = new MultiCommand(value);
				}
			}

			plugin.getCommand(key).setExecutor(executor);
		}
	}

	private static List<String> getCommandArgs(String[] command, List<String> args) {
		// not enough arguments to fully consume the command
		if (command.length > args.size())
			return null;

		if (command.length == 0)
			return args;

		for (int i = 0; i < command.length; i++)
			if (!args.get(i).equalsIgnoreCase(command[i]))
				return null;

		if (command.length == args.size())
			return Collections.emptyList();
		else if (command.length == args.size() - 1)
			return Collections.singletonList(args.get(command.length));

		return args.subList(command.length, args.size());
	}

	private String[] getCommandNames() {
		ActionHandler handler = this.getClass().getAnnotation(ActionHandler.class);

		String packageName = this.getClass().getPackage().getName();
		packageName = packageName.substring(packageName.lastIndexOf('.') + 1);
		if (handler == null)
			return new String[] { packageName };

		String[] names = handler.value();
		for (int i = 0; i < names.length; i++)
			if (names[i].isEmpty())
				names[i] = packageName;

		return names;
	}

	protected List<String> getCommandNameArgs(String test, String[] args) {
		// refuse to handle nulls of any kind
		if (test == null || args == null || Arrays.stream(args).anyMatch(Objects::isNull))
			return null;

		List<String> arguments = Arrays.asList(args);
		for (String refName : getCommandNames()) {
			String[] refParts = refName.split("\\s+");
			if (!refParts[0].equalsIgnoreCase(test))
				continue;

			String[] restParts = new String[refParts.length - 1];
			System.arraycopy(refParts, 1, restParts, 0, restParts.length);

			List<String> restArgs = getCommandArgs(restParts, arguments);
			if (restArgs != null)
				return restArgs;
		}

		return null;
	}

	@SuppressWarnings("static-method")
	protected List<String> getSubcommandNameArgs(List<String> test, Method method) {
		ActionHandler handler = method.getAnnotation(ActionHandler.class);
		// only decorated methods qualify
		if (handler == null)
			return null;

		String[] defaultCmd = { method.getName() };
		for (String cmd : handler.value()) {
			List<String> args = getCommandArgs(cmd.isEmpty() ? defaultCmd : cmd.split("\\s+"), test);
			if (args != null)
				return args;
		}

		return null;
	}

	@SuppressWarnings("static-method")
	protected boolean handleNoArgs(CommandSender sender) {
		return false;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String arg2, String[] arguments) {
		List<String> args = getCommandNameArgs(command.getName(), arguments);
		if (args == null)
			return false;

		if (args.isEmpty())
			return this.handleNoArgs(sender);

		boolean result = false;
		for (Method method : this.getClass().getMethods()) {
			List<String> subArgs = getSubcommandNameArgs(args, method);
			if (subArgs == null)
				continue;

			try {
				result |= new Caller(sender, this, method, subArgs).call();
			} catch (Exception exc) {
				throw new RuntimeException(exc);
			}
		}

		return result;
	}
}
