package net.bananemdnsa.historystages.data.diagnostics;

/** Everything an addon can register with History Stages, as the debug report names it. */
public enum ApiExtension {
    LOCK_CATEGORY("lock category", "lock categories"),
    REQUIREMENT("requirement", "requirements"),
    TRIGGER_TYPE("trigger type", "trigger types"),
    SETTINGS_GROUP("settings group", "settings groups"),
    CONFIG_SECTION("config section", "config sections"),
    INDIVIDUAL_RECIPE_TYPE("individual recipe type", "individual recipe types"),
    TEAM_PROVIDER("team provider", "team providers"),
    STAGE_CHECK("stage check", "stage checks"),
    REPORT_SECTION("report section", "report sections");

    private final String one;
    private final String many;

    ApiExtension(String one, String many) {
        this.one = one;
        this.many = many;
    }

    public String label(int count) {
        return count + " " + (count == 1 ? one : many);
    }
}
