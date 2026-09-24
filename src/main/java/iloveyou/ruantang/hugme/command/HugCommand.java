package iloveyou.ruantang.hugme.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import iloveyou.ruantang.hugme.HugMe;
import iloveyou.ruantang.hugme.hug.HugAnimation;
import iloveyou.ruantang.hugme.hug.HugManager;

import java.util.Arrays;
import java.util.List;

/**
 * The only entry point of the mod.
 *
 * <pre>
 *   /hugme hug &lt;target&gt; [animation]   start a hug with another player
 *   /hugme stop                       end your own hug early
 *   /hugme stop &lt;target&gt;              (permission level 2) end someone else's hug
 * </pre>
 */
@EventBusSubscriber(modid = HugMe.MODID)
public final class HugCommand {

    private static final SuggestionProvider<CommandSourceStack> ANIMATION_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(animationNames(), builder);

    private HugCommand() {
    }

    private static List<String> animationNames() {
        return Arrays.stream(HugAnimation.values()).map(HugAnimation::getSerializedName).toList();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hugme")
                .then(Commands.literal("hug")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> hug(context, HugAnimation.NORMAL_HUG))
                                .then(Commands.argument("animation", StringArgumentType.word())
                                        .suggests(ANIMATION_SUGGESTIONS)
                                        .executes(HugCommand::hugWithAnimation))))
                .then(Commands.literal("stop")
                        .executes(HugCommand::stopSelf)
                        .then(Commands.argument("target", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(HugCommand::stopOther))));

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("hugme");
        HugMe.LOGGER.debug("Registered /hugme with {} subcommand(s) and {} animation(s)",
                root == null ? -1 : root.getChildren().size(), HugAnimation.values().length);
    }

    private static int hugWithAnimation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "animation");
        HugAnimation animation = HugAnimation.byName(name);
        if (animation == null) {
            context.getSource().sendFailure(Component.translatable("hugme.command.unknown_animation", name)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        return hug(context, animation);
    }

    private static int hug(CommandContext<CommandSourceStack> context, HugAnimation animation) throws CommandSyntaxException {
        ServerPlayer initiator = context.getSource().getPlayerOrException();
        ServerPlayer partner = EntityArgument.getPlayer(context, "target");
        // Same entry point the interaction menu uses, so both report identically.
        return HugManager.requestAndReport(initiator, partner, animation) ? 1 : 0;
    }

    private static int stopSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (HugManager.stop(player.getUUID())) {
            context.getSource().sendSuccess(() -> Component.translatable("hugme.command.stop.success")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        context.getSource().sendFailure(Component.translatable("hugme.command.stop.none")
                .withStyle(ChatFormatting.RED));
        return 0;
    }

    private static int stopOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        if (HugManager.stop(target.getUUID())) {
            context.getSource().sendSuccess(() -> Component.translatable("hugme.command.stop.other.success",
                    target.getDisplayName()).withStyle(ChatFormatting.YELLOW), true);
            return 1;
        }
        context.getSource().sendFailure(Component.translatable("hugme.command.stop.other.none",
                target.getDisplayName()).withStyle(ChatFormatting.RED));
        return 0;
    }
}
