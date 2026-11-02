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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
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
        Mob companion = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
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
        if (id != null && player.level().getEntity(id) instanceof Mob mob) {
            mob.discard();
            player.sendSystemMessage(Component.literal("Steve gitti."));
        } else {
            player.sendSystemMessage(Component.literal("Cagirilan Steve bulunamadi."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int ask(ServerPlayer player, String message) {
        UUID companionId = COMPANIONS.get(player.getUUID());
        if (companionId == null || !(player.level().getEntity(companionId) instanceof Mob)) {
            player.sendSystemMessage(Component.literal("Once /aicall yaz."));
            return 0;
        }
        if (GeminiConfig.apiKey().isBlank()) {
            player.sendSystemMessage(Component.literal("Gemini API anahtari ayarlanmamis. config/steve-ai-companion.properties dosyasina api_key ekle."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Steve dusunuyor..."));
        GeminiClient.ask(message).whenComplete((reply, error) -> player.level().getServer().execute(() -> {
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
        if (id == null || !(player.level().getEntity(id) instanceof Mob mob)) return;
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
            case "BUILD_HOUSE" -> {
                buildStarterHouse(mob);
                player.sendSystemMessage(Component.literal("Steve baslangic evini insa etti."));
            }
            default -> {
                // Conversation-only response. Unknown actions are never executed.
            }
        }
    }

    private static void buildStarterHouse(Mob builder) {
        Level level = builder.level();
        BlockPos origin = BlockPos.containing(builder.getX() + 4, builder.getY(), builder.getZ());
        int width = 7;
        int depth = 7;
        int wallHeight = 3;

        // Clear only the small construction footprint; never replace solid terrain or fluids.
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                for (int y = 1; y <= wallHeight + 1; y++) {
                    BlockPos p = origin.offset(x, y, z);
                    if (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced()) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                level.setBlock(origin.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        }

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                boolean edge = x == 0 || x == width - 1 || z == 0 || z == depth - 1;
                if (!edge) continue;
                for (int y = 1; y <= wallHeight; y++) {
                    boolean doorway = z == 0 && x == 3 && y <= 2;
                    boolean window = y == 2 && ((z == 0 && (x == 1 || x == 5))
                        || (z == depth - 1 && (x == 1 || x == 5))
                        || (x == 0 && z == 3) || (x == width - 1 && z == 3));
                    if (doorway) continue;
                    BlockPos p = origin.offset(x, y, z);
                    level.setBlock(p, window ? Blocks.GLASS.defaultBlockState()
                        : Blocks.OAK_PLANKS.defaultBlockState(), 3);
                }
            }
        }
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                level.setBlock(origin.offset(x, wallHeight + 1, z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        }
    }

    private static void tickFollowers(MinecraftServer server) {
        for (var entry : FOLLOWING.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            var entity = player.level().getEntity(COMPANIONS.get(player.getUUID()));
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            if (mob.distanceToSqr(player) > 9.0 && mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(player, 1.12);
            }
        }
    }
}
