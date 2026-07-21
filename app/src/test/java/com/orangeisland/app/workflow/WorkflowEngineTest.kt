package com.orangeisland.app.workflow

import com.orangeisland.app.model.ActionNode
import com.orangeisland.app.model.BranchNode
import com.orangeisland.app.model.Comparison
import com.orangeisland.app.model.EdgeGuard
import com.orangeisland.app.model.FlowEdge
import com.orangeisland.app.model.FlowNode
import com.orangeisland.app.model.NodeValue
import com.orangeisland.app.model.StartNode
import com.orangeisland.app.model.TriggerSpec
import com.orangeisland.app.model.Workflow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for the workflow engine graph layer. Pure JVM — no Android, no Room, no ToolDispatcher.
 *
 * Covers the algorithmic invariants that the engine's correctness rests on: cycle rejection,
 * reachability trimming, implicit-reference dependency edges, condition/edge-guard evaluation, and
 * the topological execution loop's happy/error/branching paths.
 */
class WorkflowEngineTest {

    private val engine = WorkflowEngine()

    // ── GraphBuilder: cycle detection ───────────────────────────────────────

    @Test
    fun `cycle is detected and run fails`() = runTest {
        // A → B → A (two-node cycle), with a StartNode attached so it's a valid workflow shape.
        val start = start("s1")
        val a = action("a", "web_search")
        val b = action("b", "web_search")
        val wf = workflow(
            nodes = listOf(start, a, b),
            edges = listOf(
                edge(start, a),
                edge(a, b),
                edge(b, a)   // back-edge → cycle
            )
        )
        val result = engine.execute(wf, TriggerSource.Manual, toolRunner = noOpRunner())
        assertFalse(result.success)
        assertTrue(result.message.contains("cycle", ignoreCase = true))
    }

    // ── GraphBuilder: reachability trims orphan branches ────────────────────

    @Test
    fun `orphan node unreachable from start is skipped`() = runTest {
        val calls = mutableListOf<String>()
        val start = start("s1")
        val reached = action("reached", "web_search")
        val orphan = action("orphan", "web_search")
        val wf = workflow(
            nodes = listOf(start, reached, orphan),
            edges = listOf(edge(start, reached))   // no edge to orphan
        )
        val result = engine.execute(
            wf, TriggerSource.Manual,
            toolRunner = recordingRunner(calls)
        )
        assertTrue(result.success)
        assertEquals(listOf("web_search"), calls)   // orphan's tool never called
    }

    // ── ValueResolver: implicit dependency from NodeValue_Ref ───────────────

    @Test
    fun `action node referencing another runs after it and sees its output`() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        val start = start("s1")
        // first action returns a literal; second references the first's output as an argument.
        val first = action("a1", "web_search")
        val second = action("a2", "echo", args = mapOf("text" to NodeValue.Ref("a1")))
        val wf = workflow(
            nodes = listOf(start, first, second),
            edges = listOf(edge(start, first))   // no explicit edge first→second, but the Ref adds one
        )
        val result = engine.execute(
            wf, TriggerSource.Manual,
            toolRunner = { name, args ->
                calls += name to args
                // simulate "web_search" returning a fixed string so the echo sees it
                if (name == "web_search") "RESULT-42" else "echoed($args)"
            }
        )
        assertTrue(result.message, result.success)
        assertEquals(listOf("web_search", "echo"), calls.map { it.first })
        // The echo's args must contain the resolved output of the web_search node.
        assertTrue(calls[1].second.contains("RESULT-42"))
    }

    // ── ConditionEvaluator: numeric vs string comparison ────────────────────

    @Test
    fun `numeric comparison is numeric not lexicographic`() {
        // "9" > "10" lexicographically (because '9' > '1'), but numerically 9 < 10.
        assertTrue(ConditionEvaluator.compare("10", "9", Comparison.GT))
        assertFalse(ConditionEvaluator.compare("9", "10", Comparison.GT))
    }

    @Test
    fun `string equality falls back when not numeric`() {
        assertTrue(ConditionEvaluator.compare("foo", "foo", Comparison.EQ))
        assertFalse(ConditionEvaluator.compare("foo", "bar", Comparison.EQ))
    }

    @Test
    fun `in operator matches against comma list`() {
        assertTrue(ConditionEvaluator.compare("apple", "apple,banana", Comparison.IN))
        assertFalse(ConditionEvaluator.compare("cherry", "apple,banana", Comparison.IN))
        assertTrue(ConditionEvaluator.compare("cherry", "apple,banana", Comparison.NOT_IN))
    }

    @Test(expected = ConditionEvaluator.TypeMismatch::class)
    fun `mixed numeric_string comparison throws rather than coerce`() {
        ConditionEvaluator.compare("5", "five", Comparison.GT)
    }

    // ── EdgeGuard evaluation ────────────────────────────────────────────────

    @Test
    fun `on_success fires only on Done, on_failure only on Errored`() {
        assertTrue(ConditionEvaluator.edgeFires(EdgeGuard.OnSuccess, NodeState.Done("x")))
        assertFalse(ConditionEvaluator.edgeFires(EdgeGuard.OnSuccess, NodeState.Errored("x")))
        assertTrue(ConditionEvaluator.edgeFires(EdgeGuard.OnFailure, NodeState.Errored("x")))
        assertFalse(ConditionEvaluator.edgeFires(EdgeGuard.OnFailure, NodeState.Done("x")))
    }

    @Test
    fun `bool guard parses source output as boolean`() {
        assertTrue(ConditionEvaluator.edgeFires(EdgeGuard.Bool(true), NodeState.Done("yes")))
        assertFalse(ConditionEvaluator.edgeFires(EdgeGuard.Bool(true), NodeState.Done("no")))
        // Non-boolean output → no fire
        assertFalse(ConditionEvaluator.edgeFires(EdgeGuard.Bool(true), NodeState.Done("maybe")))
    }

    @Test
    fun `regex guard matches against source output`() {
        assertTrue(ConditionEvaluator.edgeFires(EdgeGuard.Regex("error: \\d+"), NodeState.Done("error: 42")))
        assertFalse(ConditionEvaluator.edgeFires(EdgeGuard.Regex("error: \\d+"), NodeState.Done("ok")))
    }

    @Test
    fun `parseBool accepts common truthy and falsy strings`() {
        assertEquals(true, ConditionEvaluator.parseBool("TRUE"))
        assertEquals(true, ConditionEvaluator.parseBool("1"))
        assertEquals(false, ConditionEvaluator.parseBool("off"))
        assertNull(ConditionEvaluator.parseBool("perhaps"))
    }

    // ── Engine: branch + merge routing ──────────────────────────────────────

    @Test
    fun `true branch runs and false branch is skipped`() = runTest {
        val calls = mutableListOf<String>()
        val start = start("s1")
        val cond = branch("c1", lhs = NodeValue.Literal("5"), cmp = Comparison.GT, rhs = NodeValue.Literal("3"))
        val yes = action("yes", "yes_tool")
        val no = action("no", "no_tool")
        val wf = workflow(
            nodes = listOf(start, cond, yes, no),
            edges = listOf(
                edge(start, cond),
                edge(cond, yes, EdgeGuard.Bool(true)),
                edge(cond, no, EdgeGuard.Bool(false))
            )
        )
        val result = engine.execute(wf, TriggerSource.Manual, toolRunner = recordingRunner(calls))
        assertTrue(result.success)
        assertEquals(listOf("yes_tool"), calls)   // 5 > 3 → only the true branch ran
    }

    // ── Engine: failure routing via OnFailure ───────────────────────────────

    @Test
    fun `failing action with OnFailure handler is treated as handled`() = runTest {
        val start = start("s1")
        val failing = action("f", "explode")
        val handler = action("h", "cleanup")
        val wf = workflow(
            nodes = listOf(start, failing, handler),
            edges = listOf(
                edge(start, failing),
                edge(failing, handler, EdgeGuard.OnFailure)
            )
        )
        val result = engine.execute(wf, TriggerSource.Manual, toolRunner = { name, _ ->
            if (name == "explode") throw RuntimeException("boom") else "cleaned"
        })
        // Handler ran → the failure was handled → overall run succeeds.
        assertTrue(result.message, result.success)
    }

    @Test
    fun `failing action without OnFailure handler fails the run`() = runTest {
        val start = start("s1")
        val failing = action("f", "explode")
        val wf = workflow(
            nodes = listOf(start, failing),
            edges = listOf(edge(start, failing))
        )
        val result = engine.execute(wf, TriggerSource.Manual, toolRunner = { _, _ ->
            throw RuntimeException("boom")
        })
        assertFalse(result.success)
    }

    // ── Trigger source matching ─────────────────────────────────────────────

    @Test
    fun `manual trigger fires only Manual start nodes`() = runTest {
        val manualStart = start("m", trigger = TriggerSpec.Manual)
        val intentStart = start("i", trigger = TriggerSpec.IntentAction("com.example.X"))
        val wf = workflow(
            nodes = listOf(manualStart, intentStart),
            edges = emptyList()
        )
        val result = engine.execute(wf, TriggerSource.Manual, toolRunner = noOpRunner())
        // Both starts have no downstream → run completes; the key assertion is it didn't fail
        // with "no matching start node" (which it would if Manual matched nothing).
        assertTrue(result.success)
    }

    @Test
    fun `intent trigger fires only the start whose action matches`() = runTest {
        val start = StartNode(
            id = "i",
            trigger = TriggerSpec.IntentAction("com.example.X")
        )
        val downstream = action("d", "web_search")
        val wf = workflow(
            nodes = listOf(start, downstream),
            edges = listOf(edge(start, downstream))
        )
        val result = engine.execute(
            wf,
            TriggerSource.Targeted.Node(kind = TriggerKind.INTENT, match = "com.example.X"),
            toolRunner = noOpRunner()
        )
        assertTrue(result.success)
    }

    @Test
    fun `intent trigger with non-matching action finds no entry and fails`() = runTest {
        val start = StartNode(id = "i", trigger = TriggerSpec.IntentAction("com.example.X"))
        val wf = workflow(nodes = listOf(start), edges = emptyList())
        val result = engine.execute(
            wf,
            TriggerSource.Targeted.Node(kind = TriggerKind.INTENT, match = "com.example.OTHER"),
            toolRunner = noOpRunner()
        )
        assertFalse(result.success)
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun start(id: String, trigger: TriggerSpec = TriggerSpec.Manual) =
        StartNode(id = id, label = id, trigger = trigger)

    private fun action(
        id: String,
        toolName: String,
        args: Map<String, NodeValue> = emptyMap()
    ) = ActionNode(id = id, label = id, toolName = toolName, args = args)

    private fun branch(
        id: String,
        lhs: NodeValue,
        cmp: Comparison,
        rhs: NodeValue
    ) = BranchNode(id = id, label = id, lhs = lhs, cmp = cmp, rhs = rhs)

    private fun edge(from: FlowNode, to: FlowNode, guard: EdgeGuard? = null) =
        FlowEdge(id = "e_${from.id}_${to.id}", from = from.id, to = to.id, guard = guard)

    private fun workflow(nodes: List<FlowNode>, edges: List<FlowEdge>) = Workflow(
        id = UUID.randomUUID().toString(),
        name = "test",
        nodes = nodes,
        edges = edges
    )

    private fun noOpRunner(): NodeExecutor.ToolRunner = NodeExecutor.ToolRunner { _, _ -> "ok" }

    private fun recordingRunner(sink: MutableList<String>): NodeExecutor.ToolRunner =
        NodeExecutor.ToolRunner { name, _ -> sink += name; "ok" }
}
