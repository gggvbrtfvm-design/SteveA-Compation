package dev.ayran.steveai;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class SteveAICompanion implements ModInitializer {
    public static final String MOD_ID = "steve_ai_companion";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Map<UUID, UUID> COMPANIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> FOLLOWING = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        GeminiConfig.load();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("aicall")
                .executes(context -> summon(context.getSource().getPlayerOrException())));
            dispatcher.register(Commands.literal("aidespawn")
                .executes(context -> dismiss(context.getSource().getPlayerOrException())));
            dispatcher.register(Commands.literal("aiste")
                .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                    .executes(context -> ask(context.getSource().getPlayerOrException(),
                        StringArgumentType.getString(context, "mesaj")))));
        });
        ServerTickEvents.END_SERVER_TICK.register(SteveAICompanion::tickFollowers);
        LOGGER.info("Steve AI Companion initialized.");
    }

    private static int summon(ServerPlayer player) {
        Level level = player.level();
        UUID oldId = COMPANIONS.get(player.getUUID());
        if (oldId != null && level.getEntity(oldId) instanceof Mob oldMob && oldMob.isAlive()) {
            player.sendSystemMessage(Component.literal("Steve zaten yaninda."));
            return Command.SINGLE_SUCCESS;
        }
        Mob companion = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (companion == null) {
            player.sendSystemMessage(Component.literal("Steve olusturulamadi."));
            return 0;
        }
        companion.setPos(player.getX() + 1.5, player.getY(), player.getZ());
        companion.setYRot(player.getYRot());
        companion.setCustomName(Component.literal("Steve"));
        companion.setCustomNameVisible(true);
        companion.setPersistenceRequired();
        companion.setSilent(true);
        level.addFreshEntity(companion);
        COMPANIONS.put(player.getUUID(), companion.getUUID());
        FOLLOWING.put(player.getUUID(), player.getUUID());
        player.sendSystemMessage(Component.literal("Steve geldi. Ona /aiste <komut> yazarak talimat verebilirsin."));
        player.sendSystemMessage(Component.literal("Gemini API anahtari icin config/steve-ai-companion.properties dosyasini duzenle."));
        return Command.SINGLE_SUCCESS;
    }

    private static int dismiss(ServerPlayer player) {
        UUID id = COMPANIONS.remove(player.getUUID());
        FOLLOWING.remove(player.getUUID());
        if (id != null && player.serverLevel().getEntity(id) instanceof Mob mob) {
            mob.discard();
            player.sendSystemMessage(Component.literal("Steve gitti."));
        } else {
            player.sendSystemMessage(Component.literal("Cagirilan Steve bulunamadi."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int ask(ServerPlayer player, String message) {
        UUID companionId = COMPANIONS.get(player.getUUID());
        if (companionId == null || !(player.serverLevel().getEntity(companionId) instanceof Mob)) {
            player.sendSystemMessage(Component.literal("Once /aicall yaz."));
            return 0;
        }
        if (GeminiConfig.apiKey().isBlank()) {
            player.sendSystemMessage(Component.literal("Gemini API anahtari ayarlanmamis. config/steve-ai-companion.properties dosyasina api_key ekle."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Steve dusunuyor..."));
        GeminiClient.ask(message).whenComplete((reply, error) -> player.server.execute(() -> {
            if (!player.isAlive()) return;
            if (error != null) {
                LOGGER.warn("Gemini request failed", error);
                player.sendSystemMessage(Component.literal("Steve: Yapay zeka baglantisinda sorun oldu. API anahtarini ve interneti kontrol et."));
                return;
            }
            applyReply(player, reply);
        }));
        return Command.SINGLE_SUCCESS;
    }

    private static void applyReply(ServerPlayer player, GeminiClient.Reply reply) {
        UUID id = COMPANIONS.get(player.getUUID());
        if (id == null || !(player.serverLevel().getEntity(id) instanceof Mob mob)) return;
        String text = reply.text();
        if (!text.isBlank()) player.sendSystemMessage(Component.literal("Steve: " + text));
        switch (reply.action()) {
            case "FOLLOW" -> {
                FOLLOWING.put(player.getUUID(), player.getUUID());
                player.sendSystemMessage(Component.literal("Steve seni takip edecek."));
            }
            case "STOP" -> {
                FOLLOWING.remove(player.getUUID());
                mob.getNavigation().stop();
                player.sendSystemMessage(Component.literal("Steve oldugu yerde durdu."));
            }
            case "COME" -> {
                mob.getNavigation().moveTo(player, 1.15);
                player.sendSystemMessage(Component.literal("Steve yanina geliyor."));
            }
            case "STAY" -> {
                FOLLOWING.remove(player.getUUID());
                mob.getNavigation().stop();
            }
            default -> {
                // Conversation-only response. Unknown actions are never executed.
            }
        }
    }

    private static void tickFollowers(MinecraftServer server) {
        for (var entry : FOLLOWING.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            var entity = player.serverLevel().getEntity(COMPANIONS.get(player.getUUID()));
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            if (mob.distanceToSqr(player) > 9.0 && mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(player, 1.12);
            }
        }
    }
}
