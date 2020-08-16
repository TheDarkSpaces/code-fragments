package mc.util.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;


@ActionHandler("test")
public class TestCMD extends BaseCommand {
	private static class ChatListener implements Listener {
		private Player player;
		private int counter = 0;

		public ChatListener(Player player) {
			if (player == null)
				throw new NullPointerException();

			this.player = player;

			player.sendMessage("Listener enabled!");
		}

		@EventHandler
		public void handleChat(AsyncPlayerChatEvent chat) {
			String message;
			if (player.equals(chat.getPlayer())) {
				message = "You said something: %s";
			} else {
				message = "Someone else said: %s";
			}
			message = String.format(message, chat.getMessage());
			player.sendMessage(message);

			counter++;
			if (counter >= 5) {
				player.sendMessage("Lalala~ I can't hear you!");
				HandlerList.unregisterAll(this);
			}
		}
	}

	private Plugin plugin;

	public TestCMD(Plugin plugin) {
		if (plugin == null)
			throw new NullPointerException();

		this.plugin = plugin;
	}

	@Override
	protected boolean handleNoArgs(CommandSender sender) {
		if (sender instanceof Player) {
			Player player = (Player) sender;
			ChatListener listener = new ChatListener(player);

			Bukkit.getPluginManager().registerEvents(listener, plugin);
			return true;
		}

		return false;
	}
}
