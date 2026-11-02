package dev.ayran.steveai;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SteveAICompanion implements ModInitializer {
    public static final String MOD_ID = "steve_ai_companion";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(net.minecraft.commands.Commands.literal("aicall")
                .executes(context -> summon(context.getSource().getPlayerOrException())));
            dispatcher.register(net.minecraft.commands.Commands.literal("aidespawn")
                .executes(context -> dismiss(context.getSource().getPlayerOrException())));
        });
        LOGGER.info("Steve AI Companion loaded. Prototype commands are ready.");
    }

    private static int summon(ServerPlayer player) {
        Level level = player.level();
        Mob companion = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (companion == null) return 0;
        companion.moveTo(player.getX() + 1.5, player.getY(), player.getZ(), player.getYRot(), 0);
        companion.setCustomName(Component.literal("Steve"));
        companion.setCustomNameVisible(true);
        companion.setPersistenceRequired();
        level.addFreshEntity(companion);
        player.sendSystemMessage(Component.literal("Steve yaninda! (Gecici prototip karakter.)"));
        return Command.SINGLE_SUCCESS;
    }

    private static int dismiss(ServerPlayer player) {
        Level level = player.level();
        int removed = 0;
        for (var entity : level.getEntities(player, player.getBoundingBox().inflate(32))) {
            if (entity instanceof Mob mob && mob.getCustomName() != null && mob.getCustomName().getString().equals("Steve")) {
                mob.discard();
                removed++;
            }
        }
        player.sendSystemMessage(Component.literal(removed > 0 ? "Steve gonderildi." : "Yakininda Steve yok."));
        return Command.SINGLE_SUCCESS;
    }
}