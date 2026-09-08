package com.domistats.bot.discord;

import com.domistats.bot.discord.commands.CommandHandler;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SlashCommandListener extends ListenerAdapter {

    private final Map<String, CommandHandler> handlersByName;

    public SlashCommandListener(List<CommandHandler> handlers) {
        this.handlersByName = handlers.stream().collect(Collectors.toMap(CommandHandler::name, h -> h));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        CommandHandler handler = handlersByName.get(event.getName());
        if (handler == null) {
            event.reply("Unknown command.").setEphemeral(true).queue();
            return;
        }
        try {
            handler.handle(event);
        } catch (Exception e) {
            log.error("Command /{} failed", event.getName(), e);
            String message = "Something went wrong running that command. Please try again shortly.";
            if (event.isAcknowledged()) {
                event.getHook().sendMessage(message).setEphemeral(true).queue();
            } else {
                event.reply(message).setEphemeral(true).queue();
            }
        }
    }

    public List<CommandHandler> allHandlers() {
        return List.copyOf(handlersByName.values());
    }
}
