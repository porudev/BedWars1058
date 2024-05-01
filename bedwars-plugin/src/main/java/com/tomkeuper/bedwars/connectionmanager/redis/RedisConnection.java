/*
 * BedWars2023 - A bed wars mini-game.
 * Copyright (C) 2024 Tomas Keuper
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Contact e-mail: contact@fyreblox.com
 */

package com.tomkeuper.bedwars.connectionmanager.redis;

import com.google.gson.JsonObject;
import com.tomkeuper.bedwars.BedWars;
import com.tomkeuper.bedwars.api.arena.IArena;
import com.tomkeuper.bedwars.api.communication.IRedisClient;
import com.tomkeuper.bedwars.api.configuration.ConfigPath;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RedisConnection implements IRedisClient {

    private final String channel;
    private final JedisPool dataPool;
    private final JedisPool subscriptionPool;
    private final RedisPubSubListener redisPubSubListener;

    private final ExecutorService listenerPool = Executors.newCachedThreadPool();

    public RedisConnection() {
        JedisPoolConfig config = new JedisPoolConfig();
        dataPool = new JedisPool(config, BedWars.config.getString(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_REDIS_HOST),
                BedWars.config.getInt(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_REDIS_PORT),0,
                BedWars.config.getString(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_REDIS_PASSWORD));

        // Need a new pool for the subscriptions since they will allow only `(P|S)SUBSCRIBE / (P|S)UNSUBSCRIBE / PING / QUIT / RESET` commands while being subscribed.
        subscriptionPool = new JedisPool(config, BedWars.config.getString(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_REDIS_HOST),
                BedWars.config.getInt(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_REDIS_PORT),0,
                BedWars.config.getString(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_REDIS_PASSWORD));

        this.channel = BedWars.config.getYml().getString(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_REDIS_CHANNEL);

        // Clean up any instances that might still be in the database.
        cleanupRedisEntries();

        redisPubSubListener = new RedisPubSubListener(channel);
    }

    public boolean connect(){
        try {
            listenerPool.execute(() -> {
                BedWars.debug("Subscribing to redis channel: " + channel);
                try (final Jedis listenerConnection = subscriptionPool.getResource()){
                    listenerConnection.subscribe(redisPubSubListener, channel);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                /*
                 * Since Jedis PubSub channel listener is thread-blocking,
                 * we can shut down thread when the pub-sub listener stops
                 * or fails.
                 */
                BedWars.debug("Unsubscribing from redis channel: " + channel);
                listenerPool.shutdown();
            });
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }


    public void cleanupRedisEntries(){
        try (Jedis jedis = dataPool.getResource()) {
            // Get all keys starting with the server identifier.
            Set<String> keys = jedis.keys("bwa-" + BedWars.config.getString(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_SERVER_ID)+"*");

            for (String key : keys) {
                jedis.del(key);
                BedWars.debug("Deleted arena redis with key: " + key);
            }
        } catch (Exception ignored) {
        }
    }

    public void cleanupRedisEntry(IArena a){
        try (Jedis jedis = dataPool.getResource()) {
            String key = "bwa-" + BedWars.config.getString(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_SERVER_ID) + "-" + a.getWorldName();
            jedis.del(key);
            BedWars.debug("Deleted arena redis with key: " + key);
        } catch (Exception ignored) {
        }
    }

    /**
     * Stores information about the arena in the data store.
     * The information includes various details such as the server name, arena name, identifier, status, current players,
     * maximum players, maximum players in a team, group, and whether spectating is allowed.
     *
     * @param a The IArena object for which the information needs to be stored.
     * @return True if the arena information is successfully stored, otherwise false.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean storeArenaInformation(IArena a) {
        if (a == null) return false;
        if (a.getWorldName() == null) return false;

            // Create a map for the arena information.
        Map<String, String> arenaInfoMap = new HashMap<>();
        arenaInfoMap.put("server_name", BedWars.config.getString(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_SERVER_ID));
        arenaInfoMap.put("arena_name", a.getArenaName());
        arenaInfoMap.put("arena_identifier", a.getWorldName());
        arenaInfoMap.put("arena_status", a.getStatus().toString().toUpperCase());
        arenaInfoMap.put("arena_current_players", String.valueOf(a.getPlayers().size()));
        arenaInfoMap.put("arena_max_players", String.valueOf(a.getMaxPlayers()));
        arenaInfoMap.put("arena_max_in_team", String.valueOf(a.getMaxInTeam()));
        arenaInfoMap.put("arena_group", a.getGroup().toUpperCase());
        arenaInfoMap.put("allow_spectate", String.valueOf(a.isAllowSpectate()));

        try (Jedis jedis = dataPool.getResource()) {
            // Store the map as a hash table in Redis using a separate connection
            String key = "bwa-" + BedWars.config.getString(ConfigPath.GENERAL_CONFIGURATION_BUNGEE_OPTION_SERVER_ID) + "-" + a.getWorldName();
            jedis.hmset(key, arenaInfoMap);

            BedWars.debug("Storing arena info for: " + a.getArenaName() + " - " + arenaInfoMap);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if the server settings are stored and or matching the default settings.
     *
     * @return True if correct or matching the default settings, otherwise false.
     */
    public boolean checkSettings(String redisSettingIdentifier, String defaultSetting){
        try (Jedis jedis = dataPool.getResource()) {
            String key = "settings";;
            if (jedis.exists(key)) {
                String retrievedSetting = jedis.hget(key, redisSettingIdentifier);
                if (!defaultSetting.equals(retrievedSetting)) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Setting '"+ redisSettingIdentifier +"' does not match the stored value of '" + retrievedSetting + "' is '" + defaultSetting + "'.");
                    return false;
                }
            } else {
                jedis.hset(key, redisSettingIdentifier, defaultSetting);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Store the settings in the Redis database.
     *
     * @param redisSettingIdentifier the identifier of the setting to be checked.
     * @param setting the setting to be stored.
     */
    public void storeSettings(String redisSettingIdentifier, String setting) {
        try (Jedis jedis = dataPool.getResource()) {
            String key = "settings";
            jedis.hset(key, redisSettingIdentifier, setting);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieve the data associated with a specific identifier from the Redis database.
     *
     * @param redisSettingIdentifier the identifier of the setting to be checked.
     * @return the data as a string associated with the specified identifier
     */
    public String retrieveSetting(String redisSettingIdentifier){
        try (Jedis jedis = dataPool.getResource()) {
            String key = "settings";;
            if (jedis.exists(key)) {
                String retrievedSetting = jedis.hget(key, redisSettingIdentifier);
                return retrievedSetting;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Send data to all subscribed redis clients.
     *
     * @param message the message to be sent
     */
    public void sendMessage(String message) {
        if (message == null) return;
        if (message.isEmpty()) return;

        try (Jedis jedis = dataPool.getResource()) {
            // Publish the message to the specified channel
            BedWars.debug("sending message: " + message + " on channel: " + channel);
            jedis.publish(channel, message);
        } catch (Exception e) {
            // Handle the exception
            e.printStackTrace();
        }
    }

    @Override
    public void sendMessage(@NotNull JsonObject data, @NotNull String addonIdentifier) {
        try (Jedis jedis = dataPool.getResource()) {
            // Publish the message to the specified channel

            JsonObject json = new JsonObject();
            json.addProperty("type", "AM");
            json.addProperty("addon_name", addonIdentifier); // PR = Party Remove
            json.addProperty("addon_data", data.toString());

            BedWars.debug("sending message: " + json + " on channel: " + channel);
            jedis.publish(channel, json.toString());
        } catch (Exception e) {
            // Handle the exception
            e.printStackTrace();
        }
    }

    public void close(){
        BedWars.debug("Closing redis connections...");
        cleanupRedisEntries();
        redisPubSubListener.unsubscribe();
        dataPool.close();
    }

}
