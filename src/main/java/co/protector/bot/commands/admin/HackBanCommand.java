package co.protector.bot.commands.admin;

import co.protector.bot.core.listener.command.Command;
import co.protector.bot.util.Emoji;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.utils.PermissionUtil;

public class HackBanCommand extends Command {
    @Override
    public String getTrigger() {
        return "hackban";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"hb", "hban"};
    }

    @Override
    public String getDescription() {
        return "Ban a user without them being in the server";
    }

    @Override
    public String getUsage() {
        return "";
    }


    @Override
    public void execute(Message trigger, String args) {
        TextChannel channel = trigger.getTextChannel();
        Guild guild = trigger.getGuild();
        boolean hasPerms = PermissionUtil.checkPermission(trigger.getMember(), Permission.BAN_MEMBERS);
        if (!hasPerms) {
            channel.sendMessage(String.format(Emoji.REDX + " It seems like you don't have permission to %s! Make sure that you are able to **%s**", getTrigger(), Permission.BAN_MEMBERS.getName())).queue();
            return;
        }
        if (!PermissionUtil.checkPermission(guild.getSelfMember(), Permission.BAN_MEMBERS)) {
            channel.sendMessage(String.format(Emoji.REDX + " **I am not allowed to %s members!** Make sure that I have permission to **%s**", getTrigger(), Permission.BAN_MEMBERS.getName())).queue();
            return;
        }
        String[] parsedArgs = args.split("\\s+");
        if (parsedArgs[0].equals(guild.getJDA().getSelfUser().getId())) {
            channel.sendMessage("I'm not going to ban myself..!").queue();
            return;
        }
        if (parsedArgs[0].equals(trigger.getAuthor().getId())) {
            channel.sendMessage("Don't be so hard on yourself! \uD83D\uDC96 \u2728").queue();
            return;
        }

        int delete = 0;
        if (parsedArgs.length > 1) {
            try {
                delete = Integer.parseInt(parsedArgs[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        guild.getController().ban(parsedArgs[0], delete).complete();
        channel.sendMessage(Emoji.BAN).queue();
    }

}
