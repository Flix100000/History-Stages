package net.bananemdnsa.historystages.data.logic;

import net.bananemdnsa.historystages.api.stage.StageScope;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The shape the round 1 editor shows: groups joined by AND, each group "all of" or "one of" a
 * list of terms, each term "is unlocked" or "is not unlocked".
 *
 * <p>The file holds a real tree; this is only the view of it the editor can draw. A tree that does
 * not fit comes back empty from {@link #fromCondition}, and the editor then shows it read-only
 * rather than flattening it into something that means less.
 */
public record LogicGroups(List<Group> groups) {

    public static final int MAX_GROUPS = 5;

    public record Group(boolean all, List<Term> terms) {}

    /** @param unlocked true for "is unlocked", false for "is not unlocked" */
    public record Term(String stageId, @Nullable StageScope scope, boolean unlocked) {}

    public Condition toCondition() {
        List<Condition> out = new ArrayList<>(groups.size());
        for (Group group : groups) {
            List<Condition> terms = new ArrayList<>(group.terms().size());
            for (Term term : group.terms()) terms.add(toCondition(term));
            out.add(group.all() ? new Condition.All(List.copyOf(terms)) : new Condition.Any(List.copyOf(terms)));
        }
        return new Condition.All(List.copyOf(out));
    }

    public static Optional<LogicGroups> fromCondition(Condition condition) {
        Term single = asTerm(condition);
        if (single != null) return Optional.of(new LogicGroups(List.of(new Group(true, List.of(single)))));

        if (condition instanceof Condition.Any any) {
            List<Term> terms = termsOf(any.children());
            return terms == null ? Optional.empty()
                    : Optional.of(new LogicGroups(List.of(new Group(false, terms))));
        }
        if (!(condition instanceof Condition.All all)) return Optional.empty();

        // All children plain terms: one "all of" group, the way a hand-written file says it.
        List<Term> flat = termsOf(all.children());
        if (flat != null) return Optional.of(new LogicGroups(List.of(new Group(true, flat))));

        List<Group> groups = new ArrayList<>(all.children().size());
        for (Condition child : all.children()) {
            Term term = asTerm(child);
            if (term != null) {
                groups.add(new Group(true, List.of(term)));
                continue;
            }
            List<Term> terms = switch (child) {
                case Condition.All a -> termsOf(a.children());
                case Condition.Any a -> termsOf(a.children());
                default -> null;
            };
            if (terms == null) return Optional.empty();
            groups.add(new Group(child instanceof Condition.All, terms));
        }
        return Optional.of(new LogicGroups(List.copyOf(groups)));
    }

    private static Condition toCondition(Term term) {
        Condition unlocked = new Condition.Unlocked(term.stageId(), term.scope());
        return term.unlocked() ? unlocked : new Condition.Not(unlocked);
    }

    @Nullable
    private static List<Term> termsOf(List<Condition> children) {
        List<Term> terms = new ArrayList<>(children.size());
        for (Condition child : children) {
            Term term = asTerm(child);
            if (term == null) return null;
            terms.add(term);
        }
        return List.copyOf(terms);
    }

    @Nullable
    private static Term asTerm(Condition c) {
        if (c instanceof Condition.Unlocked u) return new Term(u.stageId(), u.scope(), true);
        if (c instanceof Condition.Not(Condition.Unlocked u)) return new Term(u.stageId(), u.scope(), false);
        return null;
    }
}
