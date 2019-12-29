package net.mcatlas.newyears;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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

	private LocalDateTime beforeNewYears = LocalDateTime.of(2019, 12, 31, 23, 59, 49);
	private LocalDateTime newYears = LocalDateTime.of(2020, 1, 01, 00, 00, 00);
	private LocalDateTime afterNewYears = LocalDateTime.of(2020, 1, 01, 00, 00, 10);

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
		if (time.getMinute() == 59 && time.getSecond() >= 49) {
			if (time.getSecond() == 49) {
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

	public boolean isNearNewYears(Location location) {
		return isNearNewYears(location.getBlockX(), location.getBlockZ());
	}

	public boolean isNearNewYears(int mcX, int mcZ) {
		Coordinate coord = getLifeFromMC(mcX, mcZ);
		List<ZoneId> zones = engine.queryAll(coord.lat, coord.lon);
		ZoneId first = zones.get(0);
		LocalDateTime localNow = LocalDateTime.now(first);
		if (localNow.isAfter(beforeNewYears) || localNow.isEqual(beforeNewYears)) {
			if (localNow.isBefore(afterNewYears) || localNow.isEqual(afterNewYears)) {
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

		Bukkit.broadcastMessage(ChatColor.BLUE + "" + ChatColor.BOLD + "Happy New Year to " + 
				newYearsTowns.size() + " towns and " + newYearsPlayers.size() + " players!");

		for (Town town : this.newYearsTowns) {
			newYearsAction(town);
		}
		for (Player player : newYearsPlayers) {
			newYearsAction(player);
		}

		this.newYearsTowns.clear();
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
		
		Location aboveTownSpawn = townSpawn.clone().add(0, 20, 0);
		if (aboveTownSpawn.getBlock().getType() != Material.AIR) {
			aboveTownSpawn = townSpawn.clone().add(0, 40, 0);
			if (aboveTownSpawn.getBlock().getType() != Material.AIR) return;
		}
		Firework firework = (Firework) aboveTownSpawn.getWorld().spawnEntity(aboveTownSpawn, EntityType.FIREWORK);
		FireworkMeta fireworkMeta = firework.getFireworkMeta();

		fireworkMeta.setPower(6);
		fireworkMeta.addEffect(FireworkEffect.builder().withColor(Color.AQUA)
				.flicker(true).with(Type.BALL_LARGE).build());

		firework.setFireworkMeta(fireworkMeta);

		int amntResidents = town.getResidents().size();
		if (amntResidents < 20) amntResidents = 20;
		for (int i = 0; i < amntResidents; i++) {
			// spawn some gold in the sky
		}
	}

	public void newYearsAction(Player player) {
		// fireworks come out of the player or something
		// maybe rain some items too
	}

	public class Coordinate {
		int lon; // longitude
		int lat; // latitude

		public Coordinate(int x, int y) {
			this.lon = x;
			this.lat = y;
		}
	}

}
