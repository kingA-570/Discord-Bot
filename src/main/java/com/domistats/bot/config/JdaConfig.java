package com.domistats.bot.config;

import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JdaConfig {

    @Bean
    @ConfigurationProperties(prefix = "discord")
    public DiscordProperties discordProperties() {
        return new DiscordProperties();
    }

    @Bean
    public JDA jda(DiscordProperties props) throws InterruptedException {
        if (props.getToken() == null || props.getToken().isBlank()) {
            throw new IllegalStateException(
                    "DISCORD_BOT_TOKEN is not set. Set it as an environment variable before starting the bot.");
        }
        JDA jda = JDABuilder.createDefault(props.getToken())
                .enableIntents(GatewayIntent.GUILD_MESSAGES)
                .build();
        jda.awaitReady();
        return jda;
    }

    @Getter
    @Setter
    public static class DiscordProperties {
        private String token;
        private String devGuildId;
    }
}
