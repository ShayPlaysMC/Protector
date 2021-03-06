package co.protector.bot.core;

import co.protector.bot.util.Text;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.guild.GuildBanEvent;
import net.dv8tion.jda.core.events.guild.member.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.core.events.user.UserNameUpdateEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class ModLog extends ListenerAdapter {
    private final DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    private final Cache<String, Optional<Message>> MessageCache = CacheBuilder.newBuilder().concurrencyLevel(10).maximumSize(2500).build();
    private final Cache<String, Optional<String>> SelfCache = CacheBuilder.newBuilder().concurrencyLevel(10).maximumSize(2500).build();

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent e) {
        Message msg = e.getMessage();
        if (msg != null) {
            MessageCache.put(msg.getId(), Optional.of(msg));
        }
    }

    private boolean isEnabled(Guild guild) {
        String modLogChannel = Settings.getSetting(guild).modlog;
        return !modLogChannel.isEmpty() && guild.getTextChannelById(modLogChannel) != null && guild.getTextChannelById(modLogChannel).canTalk();
    }

    private TextChannel getChannel(Guild guild) {
        return guild.getTextChannelById(Settings.getSetting(guild).modlog);
    }

    private String getTime() {
        return dateFormat.format(new Date());
    }

    private String getUser(Member member) {
        if(member == null) return "Unknown member";
        return member.getUser().getName().replace("@", "@\u200b") + "#" + member.getUser().getDiscriminator();
    }

    private Message getFromCache(String id) {
        try {
            return MessageCache.get(id, Optional::empty).orElse(null);
        } catch (ExecutionException e1) {
            e1.printStackTrace();
            return null;
        }
    }

    @Override
    public void onGuildMessageDelete(GuildMessageDeleteEvent e) {
        if (!isEnabled(e.getGuild())) {
            return;
        }
        TextChannel channel = getChannel(e.getGuild());
        if (channel == null) {
            System.out.println("Could not find channel for id " + Settings.getSetting(e.getGuild()).modlog);
            return;
        }
        if (!e.getGuild().getId().equals(channel.getGuild().getId())) return;
        try {
            String SelfMessage = SelfCache.get(e.getMessageId(), Optional::empty).orElse(null);
            if (SelfMessage != null) {
                channel.sendMessage(SelfMessage).queue(msg -> SelfCache.put(msg.getId(), Optional.of(msg.getRawContent())));
                return;
            }
        } catch (ExecutionException e1) {
            e1.printStackTrace();
        }
        Message DeletedMessage;
        DeletedMessage = getFromCache(e.getMessageId());
        if (DeletedMessage == null) return;
        String content = DeletedMessage.getStrippedContent();
        if (content.isEmpty()) {
            return;
        } else if (content.length() > 1550) {
            content = "Message too long, created url " + Text.paste(content);
        }
        String time = getTime();
        Member author = DeletedMessage.getGuild().getMember(DeletedMessage.getAuthor());
        String user = getUser(author);
        channel.sendMessage(String.format("\uD83D\uDCDD `[%s]` %s **%s's** message has been deleted `%s`", time, e.getChannel().getAsMention(), user, content)).queue(msg -> SelfCache.put(msg.getId(), Optional.of(msg.getRawContent())));
    }

    @Override
    public void onGuildMessageUpdate(GuildMessageUpdateEvent e) {
        if (!isEnabled(e.getGuild())) {
            return;
        }
        TextChannel channel = getChannel(e.getGuild());
        if (channel == null) {
            System.out.println("Could not find channel for id " + Settings.getSetting(e.getGuild()).modlog);
            return;
        }
        if (!e.getGuild().getId().equals(channel.getGuild().getId())) return;
        Message after = e.getMessage();
        if (after == null) return;
        Message before = getFromCache(after.getId());
        if (before == null) return;
        if(after.getRawContent().equals(before.getRawContent())) return;
        String time = getTime();
        Member author = before.getGuild().getMember(before.getAuthor());
        String user = getUser(author);
        channel.sendMessage(String.format("\uD83D\uDCDD `[%s]` %s **%s's** message has been edited\n\nBefore: `%s`\n\nAfter: `%s`", time, e.getChannel().getAsMention(), user, before.getStrippedContent(), after.getStrippedContent())).queue(msg -> SelfCache.put(msg.getId(), Optional.of(msg.getRawContent())));
        MessageCache.put(after.getId(), Optional.of(after));
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent e) {
        if (!isEnabled(e.getGuild())) {
            return;
        }
        TextChannel channel = getChannel(e.getGuild());
        if (channel == null) {
            System.out.println("Could not find channel for id " + Settings.getSetting(e.getGuild()).modlog);
            return;
        }
        if (!e.getGuild().getId().equals(channel.getGuild().getId())) return;
        String time = getTime();
        Member author = e.getMember();
        String user = getUser(author);
        channel.sendMessage(String.format("\u274C `[%s]` **%s** has left the server or was kicked. Total members `%s`", time, user, e.getGuild().getMembers().size())).queue(msg -> SelfCache.put(msg.getId(), Optional.of(msg.getRawContent())));
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent e) {
        if (!isEnabled(e.getGuild())) {
            return;
        }
        TextChannel channel = getChannel(e.getGuild());
        if (channel == null) {
            System.out.println("Could not find channel for id " + Settings.getSetting(e.getGuild()).modlog);
            return;
        }
        if (!e.getGuild().getId().equals(channel.getGuild().getId())) return;
        String time = getTime();
        Member author = e.getMember();
        String user = getUser(author);
        long days = Duration.between(author.getUser().getCreationTime(), OffsetDateTime.now()).toDays();
        String created = days > 10 ? String.format("**Created %s days ago.**", days) : String.format("\u26A0 **New User - joined %s days ago.**", days);
        channel.sendMessage(String.format("\u2705 `[%s]` **%s** joined the server. %s Total members `%s`", time, user, created, e.getGuild().getMembers().size())).queue(msg -> SelfCache.put(msg.getId(), Optional.of(msg.getRawContent())));
    }

    @Override
    public void onGuildBan(GuildBanEvent e) {
        UserData.onBan(e.getUser().getId());
        if (!isEnabled(e.getGuild())) {
            return;
        }
        TextChannel channel = getChannel(e.getGuild());
        if (channel == null) {
            System.out.println("Could not find channel for id " + Settings.getSetting(e.getGuild()).modlog);
            return;
        }
        if (!e.getGuild().getId().equals(channel.getGuild().getId())) return;
        String time = getTime();
        User author = e.getUser();
        String user = author.getName() + "#" + author.getDiscriminator();
        channel.sendMessage(String.format("\uD83D\uDD28 `[%s]` **%s**(%s) has been banned. Total members `%s`", time, user, author.getId(), e.getGuild().getMembers().size())).queue(msg -> SelfCache.put(msg.getId(), Optional.of(msg.getRawContent())));

    }

    @Override
    public void onGuildMemberNickChange(GuildMemberNickChangeEvent e) {
        if (!isEnabled(e.getGuild())) {
            return;
        }
        TextChannel channel = getChannel(e.getGuild());
        if (channel == null) {
            System.out.println("Could not find channel for id " + Settings.getSetting(e.getGuild()).modlog);
            return;
        }
        if (!e.getGuild().getId().equals(channel.getGuild().getId())) return;
        String time = getTime();
        Member author = e.getMember();
        String user = getUser(author);
        channel.sendMessage(String.format("\uD83C\uDFF7 `[%s]` **%s** Changed their nickname `%s` ➥ `%s`", time, user, e.getPrevNick() == null ? e.getMember().getUser().getName() : e.getPrevNick(), e.getNewNick() == null ? e.getMember().getUser().getName() : e.getNewNick())).queue(msg -> SelfCache.put(msg.getId(), Optional.of(msg.getRawContent())));
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent e) {
        if (!isEnabled(e.getGuild())) {
            return;
        }
        TextChannel channel = getChannel(e.getGuild());
        if (channel == null) {
            System.out.println("Could not find channel for id " + Settings.getSetting(e.getGuild()).modlog);
            return;
        }
        if (!e.getGuild().getId().equals(channel.getGuild().getId())) return;
        String time = getTime();
        Member author = e.getMember();
        String user = getUser(author);
        String AddedRoles = String.join(", ", e.getRoles().stream().map(Role::getName).collect(Collectors.toList()));
        channel.sendMessage(String.format("\u2611 `[%s]` a role has been added to **%s** - `%s`", time, user, AddedRoles)).queue(msg -> SelfCache.put(msg.getId(), Optional.of(msg.getRawContent())));
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent e) {
        if (!isEnabled(e.getGuild())) {
            return;
        }
        TextChannel channel = getChannel(e.getGuild());
        if (channel == null) {
            System.out.println("Could not find channel for id " + Settings.getSetting(e.getGuild()).modlog);
            return;
        }
        if (!e.getGuild().getId().equals(channel.getGuild().getId())) return;
        String time = getTime();
        Member author = e.getMember();
        String user = getUser(author);
        String AddedRoles = String.join(", ", e.getRoles().stream().map(Role::getName).collect(Collectors.toList()));
        channel.sendMessage(String.format("\u274C `[%s]` a role has been removed from **%s** - `%s`", time, user, AddedRoles)).queue(msg -> SelfCache.put(msg.getId(), Optional.of(msg.getRawContent())));
    }

    @Override
    public void onUserNameUpdate(UserNameUpdateEvent e) {
        String before = e.getOldName() + "#" + e.getOldDiscriminator();
        String after = e.getUser().getName() + "#" + e.getUser().getDiscriminator();
        List<Guild> userGuilds = e.getJDA().getGuilds().stream().filter(g -> g.getMembers().stream().map(Member::getUser).collect(Collectors.toList()).contains(e.getUser())).collect(Collectors.toList());
        for (Guild guild : userGuilds) {
            if (!isEnabled(guild)) {
                continue;
            }
            TextChannel channel = getChannel(guild);
            if (channel == null) {
                System.out.println("Could not find channel for id " + Settings.getSetting(guild).modlog);
                continue;
            }
            String time = getTime();
            channel.sendMessage(String.format("\uD83C\uDFF7 `[%s]` **%s** Changed their username `%s` ➥ `%s`", time, before, before, after)).queue(msg -> SelfCache.put(msg.getId(), Optional.of(msg.getRawContent())));
        }
    }

}
