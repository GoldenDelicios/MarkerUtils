package org.slabserver.plugin.markerutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.yaml.snakeyaml.Yaml;

public class MarkerUtils extends JavaPlugin {
	final File markersFile = this.getDataFolder().toPath().resolveSibling("dynmap/markers.yml").toFile();
	long markersModified;
	Map<String, Marker> allMarkers;
	
	public class Marker {
		String id, label, world;
		double x, y, z;
	}
	
	public MarkerUtils() {
		
	}

	public MarkerUtils(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
		super(loader, description, dataFolder, file);
	}

	@Override
	public void onEnable() {
		this.getCommand("markers").setTabCompleter((sender, command, alias, args) -> {
			updateConditionally();
			List<String> list = new ArrayList<>();
			if (args.length == 1) {
				String arg = args[0].toLowerCase();
				for (String option : new String[] {"delete", "info", "tp"}) {
					if (option.startsWith(arg))
						list.add(option);
				}
			}
			else {
				String query = args[1];
				for (int i = 2; i < args.length; i++)
					query += " " + args[i];
				
				for (Marker marker : allMarkers.values()) {
					String label = marker.label.toLowerCase();
					if (label.contains(query)) {
						list.add(marker.label + " (" + marker.id + ")");
					}
				}
				list.sort(String.CASE_INSENSITIVE_ORDER);
			}
			return list;
		});
	}

	@Override
	public void onDisable() {
		
	}

	/**
	 * Update markers from file only if file has been modified.
	 */
	public void updateConditionally() {
		try {
			if (markersFile.lastModified() != markersModified) {
				this.getLogger().info("Updating markers");
				update();
				markersModified = markersFile.lastModified();
			}
		} catch (SecurityException e) {
			e.printStackTrace();
		}
	}

	public void update() {
		Map<String, Marker> allMarkers = new HashMap<>();
		try {
			Map<?,?> loaded = new Yaml().load(new FileInputStream(markersFile));
			Map<?,?> sets = (Map<?, ?>) loaded.get("sets");
			for (Object set : sets.values()) {
				Map<?,?> markers = (Map<?, ?>) ((Map<?, ?>) set).get("markers");
				for (Entry<?,?> entry : markers.entrySet()) {
					Map<?, ?> map = ((Map<?,?>) entry.getValue());
					Marker marker = new Marker();
					marker.id = (String) entry.getKey();
					marker.label = (String) map.get("label");
					marker.world = (String) map.get("world");
					marker.x = (double) map.get("x");
					marker.y = (double) map.get("y");
					marker.z = (double) map.get("z");
					allMarkers.put(marker.id, marker);
				}
			}
			this.allMarkers = allMarkers;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (args.length < 2) {
			return false;
		}
		else {
			updateConditionally();
			String query = args[args.length - 1];
			
			// if incomplete parentheses, backtrack until it is completed
			if (query.endsWith(")")) {
				for (int i = args.length - 2; i >= 0 && !query.startsWith("("); i--) {
					query = args[i] + " " + query;
				}
			}
			// remove parentheses
			if (query.startsWith("(") && query.endsWith(")"))
				query = query.substring(1, query.length() - 1);
			
			Marker marker = allMarkers.get(query);
			if (marker == null) {
				sender.sendMessage("No marker found by ID '" + query + "'");
			}
			else {
				switch (args[0]) {
				case "delete":
					if (!sender.hasPermission("markerutils.delete")) {
						sender.sendMessage("You do not have permission to delete markers");
					}
					else {
						this.getServer().dispatchCommand(sender, "dmarker delete id:" + marker.id);
					}
					break;
				
				case "tp":
					if (!sender.hasPermission("markerutils.tp")) {
						sender.sendMessage("You do not have permission to teleport to markers");
					}
					else if (!(sender instanceof Player)) {
						sender.sendMessage("Only players can be teleported");
					}
					else {
						World world = this.getServer().getWorld(marker.world);
						if (world == null) {
							sender.sendMessage("Could not find world '" + marker.world + "'");
						}
						else {
							Location location = new Location(world, marker.x, marker.y, marker.z);
							((Player) sender).teleport(location);
						}
					}
					break;
				
				case "info":
					if (!sender.hasPermission("markerutils.info")) {
						sender.sendMessage("You do not have permission to view marker information");
					}
					else {
						sender.sendMessage(String.format("id:%s label:%s, world:%s, x:%s, y:%s, z:%s", 
								marker.id, marker.label, marker.world, marker.x, marker.y, marker.z));
					}
					break;
				default:
					return false;
				}
			}
			return true;
		}
	}

}
