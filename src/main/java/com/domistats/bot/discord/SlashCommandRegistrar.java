package com.domistats.bot.discord;

import com.domistats.bot.config.JdaConfig;
import com.domistats.bot.discord.commands.CommandHandler;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SlashCommandRegistrar {

    private final JDA jda;
    private final SlashCommandListener listener;
    private final JdaConfig.DiscordProperties discordProperties;

    public SlashCommandRegistrar(JDA jda, SlashCommandListener listener, JdaConfig.DiscordProperties discordProperties) {
        this.jda = jda;
        this.listener = listener;
        this.discordProperties = discordProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerCommandsAndListener() {
        jda.addEventListener(listener);

        List<CommandData> definitions = listener.allHandlers().stream()
                .map(CommandHandler::definition)
                .toList();

        if (discordProperties.getDevGuildId() != null && !discordProperties.getDevGuildId().isBlank()) {
            Guild devGuild = jda.getGuildById(discordProperties.getDevGuildId());
            if (devGuild != null) {
                devGuild.updateCommands().addCommands(definitions).queue(
                        ok -> log.info("Registered {} slash commands to dev guild {}", definitions.size(), devGuild.getId()),
                        err -> log.error("Failed to register dev-guild slash commands", err));
                return;
            }
            log.warn("DISCORD_DEV_GUILD_ID set but guild not found; falling back to global registration");
        }

        jda.updateCommands().addCommands(definitions).queue(
                ok -> log.info("Registered {} global slash commands (may take up to 1 hour to propagate)", definitions.size()),
                err -> log.error("Failed to register global slash commands", err));
    }
}
