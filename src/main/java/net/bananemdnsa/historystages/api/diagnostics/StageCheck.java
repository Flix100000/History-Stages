package net.bananemdnsa.historystages.api.diagnostics;

/**
 * Looks at one loaded stage and reports what is wrong with your part of it. Register it with
 * {@link RegisterStageChecksEvent}.
 *
 * <p>Runs when the world starts and after {@code /history reload}, once per stage, after History
 * Stages' own checks. Registries (items, entities, …) are complete by then. Whatever you report
 * lands in the debug report under that stage's file; errors and warnings also go to latest.log.
 * Keep it to reading: a check must not change the stage.
 */
@FunctionalInterface
public interface StageCheck {
    void check(StageCheckContext ctx);
}
