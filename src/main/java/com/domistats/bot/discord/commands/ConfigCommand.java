package com.domistats.bot.discord.commands;

import com.domistats.bot.entity.GuildConfig;
import com.domistats.bot.service.GuildConfigService;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.Permission;
import org.springframework.stereotype.Component;

@Component
public class ConfigCommand implements CommandHandler {

    private final GuildConfigService guildConfigService;

    public ConfigCommand(GuildConfigService guildConfigService) {
        this.guildConfigService = guildConfigService;
    }

    @Override
    public String name() {
        return "config";
    }

    @Override
    public CommandData definition() {
        return Commands.slash(name(), "Configure the War Radar bot for this server.")
                .setDefaultPermissions(net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
                        .enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(
                        new SubcommandData("channel", "Set the channel for war/spin notifications.")
                                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "Notification channel", true)),
                        new SubcommandData("dashboard", "Set the channel for the live War Radar status dashboard.")
                                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "Dashboard channel", true)),
                        new SubcommandData("notifications", "Enable or disable a notification type.")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "type", "Notification type", true)
                                                .addChoice("New spin", "spin")
                                                .addChoice("War found", "war_found")
                                                .addChoice("Watchlist activity", "watchlist")
                                                .addChoice("War finished", "war_finished")
                                                .addChoice("Glory change", "glory")
                                                .addChoice("Member change", "members"),
                                        new OptionData(OptionType.BOOLEAN, "enabled", "Enable (true) or disable (false)", true)
                                ),
                        new SubcommandData("autodelete", "Auto-delete notification messages after N seconds (0 = keep forever).")
                                .addOptions(new OptionData(OptionType.INTEGER, "seconds", "Seconds to keep notification messages (0 disables auto-delete)", true)),
                        new SubcommandData("status", "Show current configuration for this server.")
                );
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        String guildId = event.getGuild().getId();

        switch (sub == null ? "" : sub) {
            case "channel" -> {
                GuildChannelUnion channel = event.getOption("channel").getAsChannel();
                guildConfigService.setNotificationChannel(guildId, channel.getId());
                event.reply("✅ Notification channel set to " + channel.getAsMention() + ".").queue();
            }
            case "dashboard" -> {
                GuildChannelUnion channel = event.getOption("channel").getAsChannel();
                guildConfigService.setDashboardChannel(guildId, channel.getId());
                event.reply("✅ Dashboard channel set to " + channel.getAsMention() + ". The War Radar status will post there shortly.").queue();
            }
            case "notifications" -> {
                String type = event.getOption("type").getAsString();
                boolean enabled = event.getOption("enabled").getAsBoolean();
                guildConfigService.toggleNotification(guildId, type, enabled);
                event.reply("✅ `" + type + "` notifications " + (enabled ? "enabled" : "disabled") + ".").queue();
            }
            case "autodelete" -> {
                int seconds = event.getOption("seconds").getAsInt();
                guildConfigService.setAutoDeleteSeconds(guildId, seconds);
                event.reply("✅ Notification messages will self-delete after **" + seconds + "**s"
                        + (seconds == 0 ? " (auto-delete disabled)." : ".")).queue();
            }
            case "status" -> {
                GuildConfig config = guildConfigService.getOrCreate(guildId);
                String summary = """
                        **War Radar Configuration**
                        Notification channel: %s
                        Dashboard channel: %s
                        Auto-delete notifications: %s
                        New spin: %s | War found: %s | Watchlist: %s
                        War finished: %s | Glory change: %s | Member change: %s
                        """.formatted(
                        mention(config.getNotificationChannelId()),
                        mention(config.getDashboardChannelId()),
                        config.getAutoDeleteSeconds() == 0 ? "_off_" : config.getAutoDeleteSeconds() + "s",
                        onOff(config.isNotifyNewSpin()), onOff(config.isNotifyWarFound()), onOff(config.isNotifyWatchlistActivity()),
                        onOff(config.isNotifyWarFinished()), onOff(config.isNotifyGloryChange()), onOff(config.isNotifyMemberChange())
                );
                event.reply(summary).queue();
            }
            default -> event.reply("Unknown /config subcommand.").setEphemeral(true).queue();
        }
    }

    private String mention(String channelId) {
        return channelId == null ? "_not set_" : "<#" + channelId + ">";
    }

    private String onOff(boolean b) {
        return b ? "✅" : "❌";
    }
}
