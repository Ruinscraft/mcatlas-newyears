package net.mcatlas.newyears;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Town;

import net.iakovlev.timeshape.TimeZoneEngine;

public class NewYearsPlugin extends JavaPlugin {

	// engine used for time zone checking
	private TimeZoneEngine engine;

	// towns to be used in new years celebration
	private List<Town> newYearsTowns;

	// scaling of the map; used to translate from MC coords to irl coords
	private double scaling = 120;

	// dates before and after new years
	private LocalDateTime beforeNewYears = LocalDateTime.of(2019, 12, 31, 23, 59, 40);
	private LocalDateTime afterNewYears = LocalDateTime.of(2020, 01, 01, 00, 00, 10);

	// ghetto string used to say which time zone is celebrating new years
	private String mostRecentTimeZone;

	// amnt of each item dropped to broadcast on new years
	private int goldDropped = 0;
	private int specialItem = 0;

	// ghetto booleans to ensure that stuff works (still doesn't always work)
	private boolean newYearTownsLoaded;
	private boolean newYearHandled;

	@Override
	public void onEnable() {
		if (this.getServer().getPluginManager().getPlugin("Towny") == null) {
			System.out.println("Could not find Towny!");
			this.getServer().getPluginManager().disablePlugin(this);
			return;
		}

		engine = TimeZoneEngine.initialize();

		this.newYearsTowns = new ArrayList<Town>();

		// runs every second
		this.getServer().getScheduler().runTaskTimer(this, () -> {
			LocalDateTime now = LocalDateTime.now();

			checkTime(now);
		}, 0, 20 * 1);
	}

	// checks if it's time for new years stuff
	public void checkTime(LocalDateTime time) {
		if (time.getMinute() == 59 && time.getSecond() >= 40) {
			this.newYearHandled = false;
			// if towns not loaded and it's within a few seconds, do this stuff
			if (time.getSecond() == 45 || time.getSecond() == 46 && !newYearTownsLoaded) {
				CompletableFuture.runAsync(() -> {
					// is this safe?
					// loads the towns used for new years
					this.newYearTownsLoaded = true;
					this.newYearsTowns = getTownsForNewYears();
				});
			}

			// countdown (if there's towns in the timezone)
			if (!newYearsTowns.isEmpty() && time.getSecond() >= 50) {
				Bukkit.broadcastMessage(ChatColor.GOLD + "" + (60 - time.getSecond()) + "...");
			}
		}

		// actual new years stuff
		if (!newYearsTowns.isEmpty() && time.getMinute() == 00 && time.getSecond() >= 00 && !newYearHandled) {
			this.newYearHandled = true;
			this.newYearTownsLoaded = false;
			handleNewYear();
		}
	}

	// gets the towns that celebrate new years this time
	public List<Town> getTownsForNewYears() {
		List<Town> towns = new ArrayList<Town>();

		for (Town town : TownyAPI.getInstance().getDataSource().getTowns()) {
			try {
				Location spawn = town.getSpawn();
				if (isNearNewYears(spawn)) {
					towns.add(town);
				}
			} catch (TownyException e) {
				e.printStackTrace();
			}
		}

		return towns;
	}

	// returns real life coordinate!!
	public Coordinate getLifeFromMC(int mcX, int mcZ) {
		int x = (int) (mcX / scaling);
		int y = (int) (mcZ / scaling) * -1;
		return new Coordinate(x, y);
	}

	public ZoneId getTimeZoneFromMC(Location location) {
		return getTimeZoneFromMC(location.getBlockX(), location.getBlockZ());
	}

	// returns the time zone for the mc coord
	public ZoneId getTimeZoneFromMC(int mcX, int mcZ) {
		Coordinate coord = getLifeFromMC(mcX, mcZ);
		List<ZoneId> zones = engine.queryAll(coord.lat, coord.lon);
		if (zones.isEmpty()) return null;
		ZoneId first = zones.get(0);
		return first;
	}

	// nice name for the timezone
	public String getZoneName(ZoneId zone) {
		return zone.getDisplayName(TextStyle.FULL, Locale.US);
	}

	public boolean isNearNewYears(Location location) {
		return isNearNewYears(location.getBlockX(), location.getBlockZ());
	}

	// if it's going to be new years soon for this location
	public boolean isNearNewYears(int mcX, int mcZ) {
		ZoneId zone = getTimeZoneFromMC(mcX, mcZ);
		if (zone == null) return false;

		LocalDateTime localNow = LocalDateTime.now(zone);
		if (localNow.isAfter(beforeNewYears) || localNow.isEqual(beforeNewYears)) {
			if (localNow.isBefore(afterNewYears) || localNow.isEqual(afterNewYears)) {
				this.mostRecentTimeZone = getZoneName(zone);
				return true;
			}
		}
		return false;
	}

	// runs whenever it's a new year for the towns/players
	public void handleNewYear() {
		// gets players that are in the timezone celebrating new years
		List<Player> newYearsPlayers = new ArrayList<Player>();
		for (Player player : this.getServer().getWorlds().get(0).getPlayers()) {
			if (isNearNewYears(player.getLocation())) {
				newYearsPlayers.add(player);
			}
		}

		// main message
		if (this.mostRecentTimeZone == null) this.mostRecentTimeZone = "some time zone";
		Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Happy New Year in " 
				+ mostRecentTimeZone + " to " + newYearsTowns.size() 
				+ " towns and " + newYearsPlayers.size() + " players!");

		// does stuff for the towns and players (drops gold/fireworks)
		for (Town town : this.newYearsTowns) {
			newYearsAction(town);
		}
		for (Player player : newYearsPlayers) {
			newYearsAction(player);
		}

		// amnt of items done stuff with
		if (this.specialItem > 1) {
			Bukkit.broadcastMessage(ChatColor.GOLD + "" + goldDropped + " gold and " 
					+ specialItem + " special items have been dropped "
					+ "around those Towns and players");
		} else if (this.specialItem == 1) {
			Bukkit.broadcastMessage(ChatColor.GOLD + "" + goldDropped + " gold and " 
					+ specialItem + " special item have been dropped "
					+ "around those Towns and players.");
		} else {
			Bukkit.broadcastMessage(ChatColor.GOLD + "" + goldDropped + " gold has been dropped "
					+ "around those Towns and players.");
		}

		// clears stuff out
		this.newYearsTowns.clear();
		this.goldDropped = 0;
		this.specialItem = 0;
	}

	// does new years stuff for the town
	public void newYearsAction(Town town) {
		if (!town.hasSpawn()) return;

		Location townSpawn = null;
		try {
			townSpawn = town.getSpawn();
		} catch (TownyException e) {
			e.printStackTrace();
		}

		Location aboveTownSpawn = townSpawn.clone().add(0, 10, 0);

		// first firework

		Firework firework = (Firework) aboveTownSpawn.getWorld().spawnEntity(aboveTownSpawn, EntityType.FIREWORK);
		FireworkMeta fireworkMeta = firework.getFireworkMeta();

		fireworkMeta.setPower(3);
		fireworkMeta.addEffect(FireworkEffect.builder().withColor(Color.MAROON)
				.flicker(true).with(Type.BALL_LARGE).build());

		firework.setFireworkMeta(fireworkMeta);

		// second firework

		Firework fireworkTwo = (Firework) aboveTownSpawn.getWorld().spawnEntity(aboveTownSpawn, EntityType.FIREWORK);
		FireworkMeta fireworkMetaTwo = firework.getFireworkMeta();

		fireworkMetaTwo.setPower(2);
		fireworkMetaTwo.addEffect(FireworkEffect.builder().withColor(Color.ORANGE)
				.flicker(true).with(Type.STAR).build());

		fireworkTwo.setFireworkMeta(fireworkMetaTwo);

		// 3% chance of a nether star
		if (Math.random() < .03) {
			townSpawn.getWorld().dropItem(townSpawn, new ItemStack(Material.NETHER_STAR, 1));
			this.specialItem++;
		}

		// 30 + amount of residents = amount of gold dropped
		// multiplied by 2 if town cant be teleported to
		int amntResidents = town.getResidents().size() + 30;
		if (!town.isPublic()) amntResidents = amntResidents * 2;
		this.goldDropped = this.goldDropped + amntResidents;

		for (int i = 0; i < amntResidents; i++) {
			// spawn some gold in the sky (within ~32 blocks of spawn)
			Location goldSpawnLocation = aboveTownSpawn.clone().add((Math.random() - .5) * 65, 0, (Math.random() - .5) * 65);
			goldSpawnLocation.getWorld().dropItem(goldSpawnLocation, new ItemStack(Material.GOLD_INGOT, 1));
		}
	}

	// does new years stuff for the player
	public void newYearsAction(Player player) {
		// spawn firework
		Firework firework = (Firework) player.getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK);
		FireworkMeta fireworkMeta = firework.getFireworkMeta();

		fireworkMeta.setPower(2);
		fireworkMeta.addEffect(FireworkEffect.builder().withColor(Color.GREEN)
				.flicker(true).with(Type.BURST).build());

		firework.setFireworkMeta(fireworkMeta);

		// keep track of amount of gold, and drop 9 gold
		this.goldDropped = this.goldDropped + 9;
		player.getWorld().dropItem(player.getLocation().clone().add(0, 10, 0), new ItemStack(Material.GOLD_BLOCK, 1));
	}

	// used for translating mc coord/irl coord
	public class Coordinate {
		int lon; // longitude
		int lat; // latitude

		public Coordinate(int lon, int lat) {
			this.lon = lon;
			this.lat = lat;
		}
	}

}
