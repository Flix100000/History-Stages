/**
 * The public surface of HistoryStages — what an addon implements, calls, and can rely on.
 *
 * <p>Everything outside this package is internal: it may be public in the Java sense, because a
 * facade in here has to reach it, but it carries no promise and can be renamed, split or deleted
 * without notice.
 *
 * <p>The promise is <em>not</em> "this never changes". It is <strong>"what changes here is in the
 * changelog, and breaking changes wait for a major version"</strong>. What an addon author gets is
 * not permanence but warning.
 *
 * <p>Two tests hold that line rather than a convention. {@code ApiSurfaceGuardTest} fails when a
 * type in here names an internal type in a public signature — which would drag that type into the
 * contract whether anyone meant it or not. {@code DemoUsesOnlyApiTest} fails when the demo addon
 * has to reach past this package, because whatever the demo needs, a real addon needs too.
 *
 * <p>A few types the api hands you — {@code StageEntry} above all — live in {@code data} rather
 * than here. That is not an oversight: {@code StageEntry} is the mod's central data structure,
 * named in over a hundred files by the loader, the editor, the network layer and the graph, and
 * moving it in here would mislabel it. They are as stable as anything in this package.
 *
 * <p>Where to start, by question:
 * <ul>
 *   <li>Does the world or a player have a stage? Unlock or relock one? —
 *       {@code stage.StageStates}, with {@code stage.StageEvent} for reacting to changes and
 *       refusing an unlock, and {@code stage.StageDefinitionsChangedEvent} for reloads.</li>
 *   <li>How far along is a stage, and what comes next? — {@code stage.StageProgress} and
 *       {@code stage.StageGraph}; {@code stage.StageAnnounceEvent} to take over the unlock
 *       messages; {@code team.Teams} for who shares with whom.</li>
 *   <li>Is something locked for a player? — {@code lock.ItemLocks} for items,
 *       {@code lock.PlaceLocks} for positions,
 *       {@code lock.CategoryLocks} for everything else (with {@code lock.Subjects}), and
 *       {@code lock.LockFeedbacks} to tell the player.</li>
 *   <li>The same on the client, for screens and tooltips — {@code client.ClientStages} and
 *       {@code client.ClientLocks}.</li>
 *   <li>Adding something of your own — the {@code Register…Event}s, one per extension point.</li>
 * </ul>
 *
 * <p><strong>Threads.</strong> Call the server-side api from the server thread, the client-side
 * api from the client thread. Nothing in here locks, and the saved data behind it is not safe to
 * touch from a worker thread; hop over with {@code server.execute(...)} first.
 *
 * <p><strong>How to use any of this is documented in the wiki:</strong>
 * <a href="https://historystages.github.io/api/addon-development">Addon
 * Development</a>.
 */
package net.bananemdnsa.historystages.api;
