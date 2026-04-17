package com.xbk.lattice;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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

    /**
     * 验证条件边可按状态切换到不同分支。
     *
     * @throws Exception 测试异常
     */
    @Test
    void stateGraphCanRouteByConditionalEdges() throws Exception {
        StateGraph stateGraph = new StateGraph();
        stateGraph.addNode("plan_changes", AsyncNodeAction.node_async(state -> Map.of("nothingToDo", Boolean.TRUE)));
        stateGraph.addNode("finalize_job", AsyncNodeAction.node_async(state -> Map.of("route", "finalize_job")));
        stateGraph.addNode("compile_new_articles", AsyncNodeAction.node_async(state -> Map.of("route", "compile_new_articles")));
        stateGraph.addEdge(StateGraph.START, "plan_changes");
        stateGraph.addConditionalEdges(
                "plan_changes",
                AsyncEdgeAction.edge_async(state -> state.value("nothingToDo", Boolean.class).orElse(Boolean.FALSE)
                        ? "finalize_job"
                        : "compile_new_articles"),
                Map.of(
                        "finalize_job", "finalize_job",
                        "compile_new_articles", "compile_new_articles"
                )
        );
        stateGraph.addEdge("finalize_job", StateGraph.END);
        stateGraph.addEdge("compile_new_articles", StateGraph.END);

        CompiledGraph compiledGraph = stateGraph.compile();
        Optional<OverAllState> result = compiledGraph.invoke(Map.of());

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().value("route", String.class)).hasValue("finalize_job");
    }

    /**
     * 验证生命周期监听会稳定触发 before / after。
     *
     * @throws Exception 测试异常
     */
    @Test
    void stateGraphCanInvokeLifecycleListener() throws Exception {
        AtomicInteger beforeCounter = new AtomicInteger(0);
        AtomicInteger afterCounter = new AtomicInteger(0);
        StateGraph stateGraph = new StateGraph();
        stateGraph.addNode("step1", AsyncNodeAction.node_async(state -> Map.of("message", "hello-listener")));
        stateGraph.addEdge(StateGraph.START, "step1");
        stateGraph.addEdge("step1", StateGraph.END);

        CompiledGraph compiledGraph = stateGraph.compile(
                CompileConfig.builder()
                        .withLifecycleListener(new GraphLifecycleListener() {
                            @Override
                            public void before(String nodeId, Map<String, Object> state, com.alibaba.cloud.ai.graph.RunnableConfig config, Long curTime) {
                                beforeCounter.incrementAndGet();
                            }

                            @Override
                            public void after(String nodeId, Map<String, Object> state, com.alibaba.cloud.ai.graph.RunnableConfig config, Long curTime) {
                                afterCounter.incrementAndGet();
                            }
                        })
                        .build()
        );

        Optional<OverAllState> result = compiledGraph.invoke(Map.of());

        assertThat(result).isPresent();
        assertThat(beforeCounter.get()).isEqualTo(1);
        assertThat(afterCounter.get()).isEqualTo(1);
    }

    /**
     * 验证 OverAllState 可承载中等规模对象集合。
     *
     * @throws Exception 测试异常
     */
    @Test
    void overAllStateCanCarryMediumSizedObjectCollection() throws Exception {
        StateGraph stateGraph = new StateGraph();
        stateGraph.addNode("prepare", AsyncNodeAction.node_async(state -> {
            List<DemoConcept> demoConcepts = new ArrayList<DemoConcept>();
            for (int index = 0; index < 32; index++) {
                demoConcepts.add(new DemoConcept("concept-" + index, "title-" + index));
            }
            return Map.of("concepts", demoConcepts);
        }));
        stateGraph.addNode("summarize", AsyncNodeAction.node_async(state -> {
            @SuppressWarnings("unchecked")
            List<DemoConcept> demoConcepts = (List<DemoConcept>) state.value("concepts").orElse(List.of());
            return Map.of("conceptCount", demoConcepts.size(), "lastConceptId", demoConcepts.get(demoConcepts.size() - 1).getId());
        }));
        stateGraph.addEdge(StateGraph.START, "prepare");
        stateGraph.addEdge("prepare", "summarize");
        stateGraph.addEdge("summarize", StateGraph.END);

        CompiledGraph compiledGraph = stateGraph.compile();
        Optional<OverAllState> result = compiledGraph.invoke(Map.of());

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().value("conceptCount", Integer.class)).hasValue(32);
        assertThat(result.orElseThrow().value("lastConceptId", String.class)).hasValue("concept-31");
    }

    /**
     * Demo 概念对象。
     *
     * 职责：作为中等体量状态载荷的最小测试对象
     *
     * @author xiexu
     */
    private static class DemoConcept {

        private final String id;

        private final String title;

        /**
         * 创建 Demo 概念对象。
         *
         * @param id 概念标识
         * @param title 概念标题
         */
        private DemoConcept(String id, String title) {
            this.id = id;
            this.title = title;
        }

        /**
         * 获取概念标识。
         *
         * @return 概念标识
         */
        public String getId() {
            return id;
        }

        /**
         * 获取概念标题。
         *
         * @return 概念标题
         */
        public String getTitle() {
            return title;
        }
    }
}
