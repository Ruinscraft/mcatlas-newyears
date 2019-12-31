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

	private TimeZoneEngine engine;

	private List<Town> newYearsTowns;

	private double scaling = 120;

	private LocalDateTime beforeNewYears = LocalDateTime.of(2019, 12, 31, 23, 59, 40);
	private LocalDateTime newYears = LocalDateTime.of(2020, 1, 01, 00, 00, 00);
	private LocalDateTime afterNewYears = LocalDateTime.of(2020, 01, 01, 00, 00, 10);

	private String mostRecentTimeZone;
	private int goldDropped = 0;
	private int specialItem = 0;

	@Override
	public void onEnable() {
		if (this.getServer().getPluginManager().getPlugin("Towny") == null) {
			System.out.println("Could not find Towny!");
			this.getServer().getPluginManager().disablePlugin(this);
			return;
		}

		engine = TimeZoneEngine.initialize();

		this.newYearsTowns = new ArrayList<Town>();

		this.getServer().getScheduler().runTaskTimer(this, () -> {
			LocalDateTime now = LocalDateTime.now();

			checkTime(now);
		}, 0, 20 * 1);
	}

	public void checkTime(LocalDateTime time) {
		if (time.getMinute() == 59 && time.getSecond() >= 40) {
			if (time.getSecond() == 45) {
				CompletableFuture.runAsync(() -> {
					// is this safe?
					this.newYearsTowns = getTownsForNewYears();
				});
			}

			if (!newYearsTowns.isEmpty() && time.getSecond() >= 50) {
				Bukkit.broadcastMessage(ChatColor.BLUE + "" + (60 - time.getSecond()) + "...");
			}
		}

		if (!newYearsTowns.isEmpty() && time.getMinute() == 00 && time.getSecond() == 00) {
			handleNewYear();
		}
	}

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

	public ZoneId getTimeZoneFromMC(int mcX, int mcZ) {
		Coordinate coord = getLifeFromMC(mcX, mcZ);
		List<ZoneId> zones = engine.queryAll(coord.lat, coord.lon);
		if (zones.isEmpty()) return null;
		ZoneId first = zones.get(0);
		return first;
	}

	public String getZoneName(ZoneId zone) {
		return zone.getDisplayName(TextStyle.FULL, Locale.US);
	}

	public boolean isNearNewYears(Location location) {
		return isNearNewYears(location.getBlockX(), location.getBlockZ());
	}

	public boolean isNearNewYears(int mcX, int mcZ) {
		ZoneId zone = getTimeZoneFromMC(mcX, mcZ);
		if (zone == null) return false;

		LocalDateTime localNow = LocalDateTime.now(zone);
		if (localNow.isAfter(beforeNewYears) || localNow.isEqual(beforeNewYears)) {
			if (localNow.isBefore(afterNewYears) || localNow.isEqual(afterNewYears)) {
				mostRecentTimeZone = getZoneName(zone);
				return true;
			}
		}
		return false;
	}

	public void handleNewYear() {
		List<Player> newYearsPlayers = new ArrayList<Player>();
		for (Player player : this.getServer().getWorlds().get(0).getPlayers()) {
			if (isNearNewYears(player.getLocation())) {
				newYearsPlayers.add(player);
			}
		}

		if (mostRecentTimeZone == null) mostRecentTimeZone = "some time zone";
		Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Happy New Year in " 
				+ mostRecentTimeZone + " to " + newYearsTowns.size() 
				+ " towns and " + newYearsPlayers.size() + " players!");

		for (Town town : this.newYearsTowns) {
			newYearsAction(town);
		}
		for (Player player : newYearsPlayers) {
			newYearsAction(player);
		}

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

		this.newYearsTowns.clear();
		this.goldDropped = 0;
		this.specialItem = 0;
	}

	public void newYearsAction(Town town) {
		// special large fireworks come out of the town spawn
		// rain some gold/items in the general vicinity?
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

		if (Math.random() < .03) {
			aboveTownSpawn.getWorld().dropItem(aboveTownSpawn, new ItemStack(Material.NETHER_STAR, 1));
			this.specialItem++;
		}

		int amntResidents = town.getResidents().size() * 2;
		if (amntResidents < 30) amntResidents = 30;
		if (!town.isPublic()) amntResidents = amntResidents * 2;
		goldDropped = goldDropped + amntResidents;

		for (int i = 0; i < amntResidents; i++) {
			// spawn some gold in the sky (within ~30 blocks of spawn)
			Location goldSpawnLocation = aboveTownSpawn.clone().add((Math.random() - .5) * 65, 0, (Math.random() - .5) * 65);
			goldSpawnLocation.getWorld().dropItem(goldSpawnLocation, new ItemStack(Material.GOLD_INGOT, 1));
		}
	}

	public void newYearsAction(Player player) {
		// fireworks come out of the player or something
		// maybe rain some items too
		Firework firework = (Firework) player.getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK);
		FireworkMeta fireworkMeta = firework.getFireworkMeta();

		fireworkMeta.setPower(2);
		fireworkMeta.addEffect(FireworkEffect.builder().withColor(Color.GREEN)
				.flicker(true).with(Type.BURST).build());

		firework.setFireworkMeta(fireworkMeta);

		goldDropped = goldDropped + 9;

		player.getWorld().dropItem(player.getLocation().clone().add(0, 10, 0), new ItemStack(Material.GOLD_BLOCK, 1));
	}

	public class Coordinate {
		int lon; // longitude
		int lat; // latitude

		public Coordinate(int lon, int lat) {
			this.lon = lon;
			this.lat = lat;
		}
	}

}
