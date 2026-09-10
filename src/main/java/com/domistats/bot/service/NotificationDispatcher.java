package com.domistats.bot.service;

import com.domistats.bot.entity.GuildConfig;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/** Sends notification embeds to a guild's configured notification channel, if any. */
@Slf4j
@Service
public class NotificationDispatcher {

    private final JDA jda;

    public NotificationDispatcher(JDA jda) {
        this.jda = jda;
    }

    public void send(GuildConfig config, EmbedBuilder embed) {
        if (config.getNotificationChannelId() == null) {
            return; // Server hasn't configured a notification channel yet.
        }
        MessageChannel channel = jda.getChannelById(MessageChannel.class, config.getNotificationChannelId());
        if (channel == null) {
            log.warn("Configured notification channel {} not found for guild {}",
                    config.getNotificationChannelId(), config.getGuildId());
            return;
        }
        channel.sendMessageEmbeds(embed.build()).queue(
                message -> scheduleAutoDelete(config, message),
                error -> log.warn("Failed to send notification to guild {}: {}", config.getGuildId(), error.getMessage())
        );
    }

    /** Deletes the message after the guild's configured auto-delete delay (0 = never). */
    private void scheduleAutoDelete(GuildConfig config, Message message) {
        int ttlSeconds = config.getAutoDeleteSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        message.delete().queueAfter(ttlSeconds, TimeUnit.SECONDS,
                success -> log.debug("Auto-deleted notification after {}s in guild {}", ttlSeconds, config.getGuildId()),
                error -> log.warn("Failed to auto-delete notification in guild {}: {}", config.getGuildId(), error.getMessage()));
    }
}
