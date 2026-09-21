package io.github.capsicum0907.cella;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class KeptCommand {
    private static final String KEPT = "kept";
    private static final String ID = "id";
    private static final String FORM = "form";

    private static final int SHOWN = 20;

    private static final long TICKS_A_DAY = 24000L;
    private static final long TICKS_AN_HOUR = 1000L;

    private KeptCommand() {
    }

    private static final SuggestionProvider<CommandSourceStack> FILED = (context, builder) ->
            SharedSuggestionProvider.suggest(Kept.of(context.getSource().getLevel())
                    .map(kept -> kept.list().stream().map(trace -> trace.id().toString()).toList())
                    .orElse(List.of()), builder);

    private static final SuggestionProvider<CommandSourceStack> FORMS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    Arrays.stream(Kind.values()).map(Kind::id).toList(), builder);

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Cella.MODID)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal(KEPT)
                        .then(Commands.literal("list")
                                .executes(KeptCommand::list))
                        .then(Commands.literal("give")
                                .then(Commands.argument(ID, UuidArgument.uuid())
                                        .suggests(FILED)
                                        .executes(context -> give(context, Optional.empty()))
                                        .then(Commands.argument(FORM, StringArgumentType.word())
                                                .suggests(FORMS)
                                                .executes(KeptCommand::giveAs))))
                        .then(Commands.literal("forget")
                                .then(Commands.argument(ID, UuidArgument.uuid())
                                        .suggests(FILED)
                                        .executes(KeptCommand::forget)))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Kept kept = store(source);
        List<Kept.Trace> traces = kept.list();
        if (traces.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.cella.kept.none"), false);
            return 0;
        }

        long slots = traces.stream().mapToLong(Kept.Trace::used).sum();
        source.sendSuccess(() -> traces.size() == 1
                ? Component.translatable("commands.cella.kept.header.one", Held.count(slots))
                : Component.translatable("commands.cella.kept.header",
                        traces.size(), Held.count(slots)), false);

        long now = source.getLevel().getGameTime();
        for (Kept.Trace trace : traces.subList(0, Math.min(SHOWN, traces.size()))) {
            source.sendSuccess(() -> row(trace, now), false);
        }

        if (traces.size() > SHOWN) {
            source.sendSuccess(() -> Component.translatable("commands.cella.kept.more",
                    traces.size() - SHOWN), false);
        }
        return traces.size();
    }

    private static int give(CommandContext<CommandSourceStack> context, Optional<Kind> asked)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        UUID id = UuidArgument.getUuid(context, ID);
        Optional<Kept.Trace> found = store(source).trace(id);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("commands.cella.kept.missing",
                    id.toString()));
            return 0;
        }

        Kept.Trace trace = found.get();

        int before = store(source).hand(id);

        Kind kind = asked.or(trace::kind).orElseGet(() -> Kind.fitting(trace.slots()));
        ItemStack stack = new ItemStack(CellaRegistry.item(kind).get());

        stack.set(CellaRegistry.KEPT.get(), new Held(List.of(id), trace.used(), trace.slots(),
                trace.experience(), kind.growth()));
        player.getInventory().placeItemBackInInventory(stack);

        source.sendSuccess(() -> Component.translatable("commands.cella.kept.gave",
                Component.literal(kind.displayName()),
                Held.count(trace.used()), Held.count(trace.slots())), true);

        if (before > 1) {
            source.sendSuccess(() -> Component.translatable("commands.cella.kept.again",
                    before).withStyle(net.minecraft.ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int giveAs(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String named = StringArgumentType.getString(context, FORM);
        Optional<Kind> kind = Kind.named(named);
        if (kind.isEmpty()) {
            context.getSource().sendFailure(
                    Component.translatable("commands.cella.kept.noform", named));
            return 0;
        }
        return give(context, kind);
    }

    private static int forget(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID id = UuidArgument.getUuid(context, ID);
        Kept kept = store(source);
        Optional<Kept.Trace> found = kept.trace(id);
        if (found.isEmpty() || !kept.forget(id)) {
            source.sendFailure(Component.translatable("commands.cella.kept.missing",
                    id.toString()));
            return 0;
        }

        Kept.Trace trace = found.get();
        source.sendSuccess(() -> Component.translatable("commands.cella.kept.forgot",
                form(trace), Held.count(trace.used()), Held.count(trace.slots())), true);

        if (trace.claimed()) {
            source.sendSuccess(() -> Component.translatable("commands.cella.kept.forgot.claimed",
                    trace.names()).withStyle(net.minecraft.ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static Component row(Kept.Trace trace, long now) {
        String key = trace.claimed()
                ? "commands.cella.kept.row.claimed"
                : "commands.cella.kept.row";
        Component age = trace.dated()
                ? Component.translatable("commands.cella.kept.age",
                        elapsed(Math.max(0L, now - trace.when())))
                : Component.translatable("commands.cella.kept.undated");
        return Component.translatable(key, form(trace),
                        Held.count(trace.used()), Held.count(trace.slots()), age)
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                "/" + Cella.MODID + " " + KEPT + " give " + trace.id()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.cella.kept.click",
                                        trace.id().toString()))));
    }

    private static Component form(Kept.Trace trace) {
        return trace.kind()
                .map(kind -> Component.literal(kind.displayName()))
                .orElseGet(() -> trace.formed()
                        ? Component.translatable("commands.cella.kept.foreign", trace.named())
                        : Component.translatable("commands.cella.kept.unformed"));
    }

    private static Component elapsed(long ticks) {
        long days = ticks / TICKS_A_DAY;
        long hours = ticks % TICKS_A_DAY / TICKS_AN_HOUR;
        if (days > 0) {
            return Component.translatable("commands.cella.kept.days", days, hours);
        }
        return hours > 0
                ? Component.translatable("commands.cella.kept.hours", hours)
                : Component.translatable("commands.cella.kept.recent");
    }

    private static Kept store(CommandSourceStack source) {
        return Kept.of(source.getLevel()).orElseThrow();
    }
}
