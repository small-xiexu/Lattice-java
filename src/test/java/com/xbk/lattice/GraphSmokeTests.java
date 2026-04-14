package com.xbk.lattice;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GraphSmokeTests {

    @Test
    void stateGraphCanCompileAndInvoke() throws Exception {
        StateGraph stateGraph = new StateGraph();
        stateGraph.addNode("step1", AsyncNodeAction.node_async(state -> Map.of("message", "hello-graph")));
        stateGraph.addEdge(StateGraph.START, "step1");
        stateGraph.addEdge("step1", StateGraph.END);

        CompiledGraph compiledGraph = stateGraph.compile();
        Optional<OverAllState> result = compiledGraph.invoke(Map.of("input", "ping"));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().value("message", String.class)).hasValue("hello-graph");
    }
}
