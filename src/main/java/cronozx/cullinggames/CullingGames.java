package cronozx.cullinggames;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.Optional;

@Plugin(id = "cullinggames", name = "cullinggames", version = BuildConstants.VERSION, authors = {"cronozx"})
public class CullingGames {

    public static final MinecraftChannelIdentifier IDENTIFIER = MinecraftChannelIdentifier.from("cullinggames:main");
    private final Logger logger;
    private final ProxyServer server;
    private JedisPool jedisPool;

    @Inject
    public CullingGames(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.jedisPool = new JedisPool(new JedisPoolConfig(), "", 0, 2000, "");

        new Thread(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        if (message.startsWith("teleportTo:")) {
                            String[] parts = message.split(":");

                            if (parts.length < 3) {
                                logger.warn("Invalid message format. Message: {}", message);
                                return;
                            }

                            String serverName = parts[1];
                            String playerName = parts[2];
                            Optional<Player> player = server.getPlayer(playerName);
                            Optional<RegisteredServer> targetServer = server.getServer(serverName);

                            if (player.isPresent() && targetServer.isPresent()) {
                                player.get().createConnectionRequest(targetServer.get()).fireAndForget();
                            }
                        }
                    }
                }, "cullinggames:jedis");
            } catch (Exception e) {
                logger.error("Error subscribing to Redis channel: " + e.getMessage());
            }
        }).start();
    }

    @Subscribe
    public void onProxyDisable(ProxyShutdownEvent event) {
        jedisPool.close();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(IDENTIFIER);
    }
}
