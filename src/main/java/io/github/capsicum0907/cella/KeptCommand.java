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

/**
 * {@code /cella kept} — what to do about contents nobody is holding the name of.
 *
 * <p><b>A tool for a person to look with, and not a sweep.</b> {@link Kept} says why
 * there is no automatic one: a name is unreachable only if no item anywhere in the world
 * holds it, and no count of "anywhere" is trustworthy while a chunk can be unloaded. A
 * sweep that is wrong deletes contents somebody still owns, so the wrongness has to be a
 * person's, deliberately, one entry at a time.
 *
 * <p><b>Handing back comes before deleting.</b> Contents with a lost name are not damaged
 * — a name is all that reaches them — so the first thing this offers is a new item that
 * names one. Deleting is there too, and it is the one that has to be typed at.
 *
 * <p>The listing writes the commands for you: every row carries the click that fills in
 * its own name, because a UUID is not something to read off a screen and type.
 */
public final class KeptCommand {
    private static final String KEPT = "kept";
    private static final String ID = "id";
    private static final String FORM = "form";

    /**
     * How many rows one listing prints, and the reason it says how many it did not.
     *
     * <p>A store with three hundred orphans in it would otherwise scroll the useful part
     * of the chat away, and a listing that quietly stopped would read as a complete one.
     */
    private static final int SHOWN = 20;

    /** A Minecraft day, and an hour of one, in ticks. What game time is counted in. */
    private static final long TICKS_A_DAY = 24000L;
    private static final long TICKS_AN_HOUR = 1000L;

    private KeptCommand() {
    }

    /**
     * Names already in the store, so that nobody types a UUID.
     *
     * <p>Read at completion time rather than held, because the store changes underneath a
     * player who is using these — every {@code forget} takes one out of it.
     */
    private static final SuggestionProvider<CommandSourceStack> FILED = (context, builder) ->
            SharedSuggestionProvider.suggest(Kept.of(context.getSource().getLevel())
                    .map(kept -> kept.list().stream().map(trace -> trace.id().toString()).toList())
                    .orElse(List.of()), builder);

    private static final SuggestionProvider<CommandSourceStack> FORMS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    Arrays.stream(Kind.values()).map(Kind::id).toList(), builder);

    /**
     * All of it behind the gamemaster level, including the listing.
     *
     * <p>Two of these change the world and the third says what is in every chest anybody
     * on the server ever picked up, which is not a thing to read over somebody's shoulder.
     */
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

    /** Oldest first, since those are the ones worth deciding about. */
    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Kept kept = store(source);
        List<Kept.Trace> traces = kept.list();
        if (traces.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.cella.kept.none"), false);
            return 0;
        }

        // A long, because ten thousand orphaned Cella Maxes is more than an int holds and
        // the number that overflows would be the reassuring one.
        long slots = traces.stream().mapToLong(Kept.Trace::used).sum();
        source.sendSuccess(() -> Component.translatable("commands.cella.kept.header",
                traces.size(), Held.count(slots)), false);

        long now = source.getLevel().getGameTime();
        for (Kept.Trace trace : traces.subList(0, Math.min(SHOWN, traces.size()))) {
            source.sendSuccess(() -> row(trace, now), false);
        }
        // Said rather than left to be noticed. A listing that stops without saying so is a
        // listing that reads as the whole of it.
        if (traces.size() > SHOWN) {
            source.sendSuccess(() -> Component.translatable("commands.cella.kept.more",
                    traces.size() - SHOWN), false);
        }
        return traces.size();
    }

    /**
     * Hands out an item naming a filed chest, <b>without taking it</b>.
     *
     * <p>The contents stay where they are until something is placed, which is the one rule
     * that keeps any of this safe: they come out of the store exactly once, by
     * {@link Kept#take}, whoever asked and however often. Two items naming one chest is
     * already a shape this mod tolerates — the first of them to be placed is the one
     * looking at anything — so running this twice makes a spare item and not a spare
     * chest.
     */
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
        // What was asked for, else what it was, else what would hold it. The last is a
        // derivation from the size and is not pretending to be the history: see
        // Kind.fitting.
        Kind kind = asked.or(trace::kind).orElseGet(() -> Kind.fitting(trace.slots()));
        ItemStack stack = new ItemStack(CellaRegistry.item(kind).get());
        stack.set(CellaRegistry.KEPT.get(), new Held(List.of(id), trace.used(), trace.slots()));
        player.getInventory().placeItemBackInInventory(stack);

        source.sendSuccess(() -> Component.translatable("commands.cella.kept.gave",
                Component.literal(kind.displayName()),
                Held.count(trace.used()), Held.count(trace.slots())), true);
        return 1;
    }

    /** The same, with the form named — and an unknown name is a refusal, not a fallback. */
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

    /**
     * Destroys one, and says what it was.
     *
     * <p>What was in it is read before it goes, so that the answer is a description of
     * what was destroyed rather than an acknowledgement that something was. There is no
     * undoing it and nothing asks twice: the name typed in is the confirmation.
     */
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
        return 1;
    }

    /** One entry, with the click that writes its own name into the next command. */
    private static Component row(Kept.Trace trace, long now) {
        Component age = trace.dated()
                ? Component.translatable("commands.cella.kept.age",
                        elapsed(Math.max(0L, now - trace.when())))
                : Component.translatable("commands.cella.kept.undated");
        return Component.translatable("commands.cella.kept.row", form(trace),
                        Held.count(trace.used()), Held.count(trace.slots()), age)
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                "/" + Cella.MODID + " " + KEPT + " give " + trace.id()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.cella.kept.click",
                                        trace.id().toString()))));
    }

    /**
     * Which form it was, in the three answers there are.
     *
     * <p>An id this version does not have is told apart from no id at all, because they
     * mean different things to whoever is reading: the first says the world has met
     * another version of this mod, and the second only says the entry is old.
     */
    private static Component form(Kept.Trace trace) {
        return trace.kind()
                .map(kind -> Component.literal(kind.displayName()))
                .orElseGet(() -> trace.formed()
                        ? Component.translatable("commands.cella.kept.foreign", trace.named())
                        : Component.translatable("commands.cella.kept.unformed"));
    }

    /**
     * How long ago, in the days and hours this world has had rather than in ticks.
     *
     * <p>Game time is what was filed and it counts up from nought, so the difference is
     * the answer — but it is only the answer while the world it is measured in is the one
     * it was written in. A world put back from a backup can be younger than its own store,
     * which is why the caller floors it rather than printing a negative age.
     */
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

    /**
     * The store. A command source is a server, so there is always one.
     */
    private static Kept store(CommandSourceStack source) {
        return Kept.of(source.getLevel()).orElseThrow();
    }
}
