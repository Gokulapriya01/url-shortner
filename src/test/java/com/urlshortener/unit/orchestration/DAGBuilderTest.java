package com.urlshortener.unit.orchestration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.urlshortener.domain.entity.Task;
import com.urlshortener.domain.enums.TaskStatus;
import com.urlshortener.orchestration.DAGBuilder;
import com.urlshortener.orchestration.DAGBuilder.TaskDAG;

@DisplayName("DAGBuilder Tests")
class DAGBuilderTest {

    private DAGBuilder dagBuilder;

    @BeforeEach
    void setUp() {
        dagBuilder = new DAGBuilder();
    }

    private Task createTask(String definitionId, int phase, int orderIndex, List<String> dependencies) {
        return Task.builder()
            .id(UUID.randomUUID())
            .definitionId(definitionId)
            .name("Task " + definitionId)
            .phase(phase)
            .orderIndex(orderIndex)
            .status(TaskStatus.PENDING)
            .dependencies(dependencies != null ? dependencies : new ArrayList<>())
            .build();
    }

    @Nested
    @DisplayName("build() - Basic DAG Construction")
    class BasicConstruction {

        @Test
        @DisplayName("should build DAG from single task")
        void shouldBuildDagFromSingleTask() {
            // Given
            List<Task> tasks = List.of(
                createTask("T1", 1, 1, null)
            );

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            assertNotNull(dag);
            assertEquals(1, dag.getNodes().size());
            assertTrue(dag.getNodes().containsKey("T1"));
            assertEquals(1, dag.getExecutionOrder().size());
        }

        @Test
        @DisplayName("should build DAG from multiple independent tasks")
        void shouldBuildDagFromMultipleIndependentTasks() {
            // Given
            List<Task> tasks = List.of(
                createTask("T1", 1, 1, null),
                createTask("T2", 1, 2, null),
                createTask("T3", 1, 3, null)
            );

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            assertEquals(3, dag.getNodes().size());
            assertEquals(3, dag.getExecutionOrder().size());
        }

        @Test
        @DisplayName("should build DAG with linear dependencies")
        void shouldBuildDagWithLinearDependencies() {
            // Given: T1 -> T2 -> T3
            List<Task> tasks = List.of(
                createTask("T1", 1, 1, null),
                createTask("T2", 1, 2, List.of("T1")),
                createTask("T3", 1, 3, List.of("T2"))
            );

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            List<String> order = dag.getExecutionOrder();
            assertTrue(order.indexOf("T1") < order.indexOf("T2"));
            assertTrue(order.indexOf("T2") < order.indexOf("T3"));
        }

        @Test
        @DisplayName("should build DAG with diamond dependencies")
        void shouldBuildDagWithDiamondDependencies() {
            // Given: T1 -> T2, T1 -> T3, T2 -> T4, T3 -> T4
            List<Task> tasks = List.of(
                createTask("T1", 1, 1, null),
                createTask("T2", 1, 2, List.of("T1")),
                createTask("T3", 1, 3, List.of("T1")),
                createTask("T4", 1, 4, List.of("T2", "T3"))
            );

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            List<String> order = dag.getExecutionOrder();
            assertEquals(4, order.size());

            // T1 must come before T2 and T3
            assertTrue(order.indexOf("T1") < order.indexOf("T2"));
            assertTrue(order.indexOf("T1") < order.indexOf("T3"));

            // T2 and T3 must come before T4
            assertTrue(order.indexOf("T2") < order.indexOf("T4"));
            assertTrue(order.indexOf("T3") < order.indexOf("T4"));
        }
    }

    @Nested
    @DisplayName("build() - Phase Organization")
    class PhaseOrganization {

        @Test
        @DisplayName("should organize tasks by phase")
        void shouldOrganizeTasksByPhase() {
            // Given
            List<Task> tasks = List.of(
                createTask("T1-1", 1, 1, null),
                createTask("T1-2", 1, 2, null),
                createTask("T2-1", 2, 1, null),
                createTask("T3-1", 3, 1, null)
            );

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            assertEquals(3, dag.getPhases().size());
            assertEquals(2, dag.getPhases().get(1).size());
            assertEquals(1, dag.getPhases().get(2).size());
            assertEquals(1, dag.getPhases().get(3).size());
        }

        @Test
        @DisplayName("should include task IDs in correct phases")
        void shouldIncludeTaskIdsInCorrectPhases() {
            // Given
            List<Task> tasks = List.of(
                createTask("setup", 1, 1, null),
                createTask("build", 2, 1, List.of("setup")),
                createTask("test", 2, 2, List.of("setup")),
                createTask("deploy", 3, 1, List.of("build", "test"))
            );

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            assertTrue(dag.getPhases().get(1).contains("setup"));
            assertTrue(dag.getPhases().get(2).contains("build"));
            assertTrue(dag.getPhases().get(2).contains("test"));
            assertTrue(dag.getPhases().get(3).contains("deploy"));
        }
    }

    @Nested
    @DisplayName("build() - Topological Sort (Kahn's Algorithm)")
    class TopologicalSort {

        @Test
        @DisplayName("should produce valid topological order")
        void shouldProduceValidTopologicalOrder() {
            // Given: Complex dependency graph
            List<Task> tasks = List.of(
                createTask("A", 1, 1, null),
                createTask("B", 1, 2, List.of("A")),
                createTask("C", 1, 3, List.of("A")),
                createTask("D", 2, 1, List.of("B", "C")),
                createTask("E", 2, 2, List.of("B")),
                createTask("F", 3, 1, List.of("D", "E"))
            );

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            List<String> order = dag.getExecutionOrder();

            // Verify all dependencies come before dependents
            for (Task task : tasks) {
                String taskId = task.getDefinitionId();
                int taskIndex = order.indexOf(taskId);

                for (String dep : task.getDependencies()) {
                    int depIndex = order.indexOf(dep);
                    assertTrue(depIndex < taskIndex,
                        "Dependency " + dep + " should come before " + taskId);
                }
            }
        }

        @Test
        @DisplayName("should handle tasks with no dependencies first")
        void shouldHandleTasksWithNoDependenciesFirst() {
            // Given
            List<Task> tasks = List.of(
                createTask("dependent", 1, 2, List.of("root1", "root2")),
                createTask("root1", 1, 1, null),
                createTask("root2", 1, 1, null)
            );

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            List<String> order = dag.getExecutionOrder();
            int dependentIndex = order.indexOf("dependent");

            // Both roots should come before dependent
            assertTrue(order.indexOf("root1") < dependentIndex);
            assertTrue(order.indexOf("root2") < dependentIndex);
        }
    }

    @Nested
    @DisplayName("build() - Cycle Detection")
    class CycleDetection {

        @Test
        @DisplayName("should detect simple cycle")
        void shouldDetectSimpleCycle() {
            // Given: A -> B -> A (cycle)
            List<Task> tasks = List.of(
                createTask("A", 1, 1, List.of("B")),
                createTask("B", 1, 2, List.of("A"))
            );

            // When/Then
            assertThrows(IllegalArgumentException.class, () -> dagBuilder.build(tasks));
        }

        @Test
        @DisplayName("should detect complex cycle")
        void shouldDetectComplexCycle() {
            // Given: A -> B -> C -> A (cycle)
            List<Task> tasks = List.of(
                createTask("A", 1, 1, List.of("C")),
                createTask("B", 1, 2, List.of("A")),
                createTask("C", 1, 3, List.of("B"))
            );

            // When/Then
            assertThrows(IllegalArgumentException.class, () -> dagBuilder.build(tasks));
        }

        @Test
        @DisplayName("should detect self-referencing cycle")
        void shouldDetectSelfReferencingCycle() {
            // Given: A -> A (self-cycle)
            List<Task> tasks = List.of(
                createTask("A", 1, 1, List.of("A"))
            );

            // When/Then
            assertThrows(IllegalArgumentException.class, () -> dagBuilder.build(tasks));
        }
    }

    @Nested
    @DisplayName("build() - Node Properties")
    class NodeProperties {

        @Test
        @DisplayName("should track dependencies correctly")
        void shouldTrackDependenciesCorrectly() {
            // Given
            List<Task> tasks = List.of(
                createTask("A", 1, 1, null),
                createTask("B", 1, 2, List.of("A"))
            );

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            assertEquals(0, dag.getNodes().get("A").getDependencies().size());
            assertEquals(1, dag.getNodes().get("B").getDependencies().size());
            assertTrue(dag.getNodes().get("B").getDependencies().contains("A"));
        }

        @Test
        @DisplayName("should track dependents correctly")
        void shouldTrackDependentsCorrectly() {
            // Given
            List<Task> tasks = List.of(
                createTask("A", 1, 1, null),
                createTask("B", 1, 2, List.of("A")),
                createTask("C", 1, 3, List.of("A"))
            );

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            assertEquals(2, dag.getNodes().get("A").getDependents().size());
            assertTrue(dag.getNodes().get("A").getDependents().contains("B"));
            assertTrue(dag.getNodes().get("A").getDependents().contains("C"));
        }

        @Test
        @DisplayName("should calculate in-degree correctly")
        void shouldCalculateInDegreeCorrectly() {
            // Given
            List<Task> tasks = List.of(
                createTask("A", 1, 1, null),
                createTask("B", 1, 2, List.of("A")),
                createTask("C", 1, 3, List.of("A", "B"))
            );

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            assertEquals(0, dag.getNodes().get("A").getInDegree());
            assertEquals(1, dag.getNodes().get("B").getInDegree());
            assertEquals(2, dag.getNodes().get("C").getInDegree());
        }

        @Test
        @DisplayName("should preserve task status")
        void shouldPreserveTaskStatus() {
            // Given
            Task completedTask = createTask("done", 1, 1, null);
            completedTask.setStatus(TaskStatus.COMPLETED);

            List<Task> tasks = List.of(completedTask);

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            assertEquals(TaskStatus.COMPLETED, dag.getNodes().get("done").getStatus());
        }
    }

    @Nested
    @DisplayName("build() - Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty task list")
        void shouldHandleEmptyTaskList() {
            // Given
            List<Task> tasks = new ArrayList<>();

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            assertNotNull(dag);
            assertTrue(dag.getNodes().isEmpty());
            assertTrue(dag.getExecutionOrder().isEmpty());
        }

        @Test
        @DisplayName("should use task ID when definitionId is null")
        void shouldUseTaskIdWhenDefinitionIdIsNull() {
            // Given
            Task task = Task.builder()
                .id(UUID.randomUUID())
                .definitionId(null)
                .name("Task without definition ID")
                .phase(1)
                .orderIndex(1)
                .status(TaskStatus.PENDING)
                .dependencies(new ArrayList<>())
                .build();

            List<Task> tasks = List.of(task);

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            assertEquals(1, dag.getNodes().size());
            assertTrue(dag.getNodes().containsKey(task.getId().toString()));
        }

        @Test
        @DisplayName("should handle tasks with null dependencies list")
        void shouldHandleTasksWithNullDependenciesList() {
            // Given
            Task task = Task.builder()
                .id(UUID.randomUUID())
                .definitionId("T1")
                .name("Task")
                .phase(1)
                .orderIndex(1)
                .status(TaskStatus.PENDING)
                .dependencies(null)
                .build();

            List<Task> tasks = List.of(task);

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            assertNotNull(dag);
            assertEquals(0, dag.getNodes().get("T1").getInDegree());
        }
    }

    @Nested
    @DisplayName("Real-World Scenarios")
    class RealWorldScenarios {

        @Test
        @DisplayName("should handle CI/CD pipeline dependency graph")
        void shouldHandleCiCdPipelineDependencyGraph() {
            // Given: Typical CI/CD pipeline
            // checkout -> build -> [unit-test, integration-test] -> deploy
            List<Task> tasks = List.of(
                createTask("checkout", 1, 1, null),
                createTask("build", 2, 1, List.of("checkout")),
                createTask("unit-test", 3, 1, List.of("build")),
                createTask("integration-test", 3, 2, List.of("build")),
                createTask("deploy", 4, 1, List.of("unit-test", "integration-test"))
            );

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            List<String> order = dag.getExecutionOrder();
            assertEquals(5, order.size());

            // Verify order constraints
            assertTrue(order.indexOf("checkout") < order.indexOf("build"));
            assertTrue(order.indexOf("build") < order.indexOf("unit-test"));
            assertTrue(order.indexOf("build") < order.indexOf("integration-test"));
            assertTrue(order.indexOf("unit-test") < order.indexOf("deploy"));
            assertTrue(order.indexOf("integration-test") < order.indexOf("deploy"));
        }

        @Test
        @DisplayName("should handle multi-phase project workflow")
        void shouldHandleMultiPhaseProjectWorkflow() {
            // Given: Multi-phase workflow from URL shortener project
            List<Task> tasks = List.of(
                // Phase 1: Setup
                createTask("init-project", 1, 1, null),
                createTask("setup-db", 1, 2, List.of("init-project")),

                // Phase 2: Core
                createTask("create-entities", 2, 1, List.of("setup-db")),
                createTask("create-services", 2, 2, List.of("create-entities")),
                createTask("create-controllers", 2, 3, List.of("create-services")),

                // Phase 3: Testing
                createTask("write-unit-tests", 3, 1, List.of("create-services")),
                createTask("write-integration-tests", 3, 2, List.of("create-controllers")),

                // Phase 4: Delivery
                createTask("documentation", 4, 1, List.of("write-unit-tests", "write-integration-tests")),
                createTask("deploy", 4, 2, List.of("documentation"))
            );

            // When
            TaskDAG dag = dagBuilder.build(tasks);

            // Then
            assertEquals(9, dag.getExecutionOrder().size());
            assertEquals(4, dag.getPhases().size());
            assertEquals(2, dag.getPhases().get(1).size());
            assertEquals(3, dag.getPhases().get(2).size());
            assertEquals(2, dag.getPhases().get(3).size());
            assertEquals(2, dag.getPhases().get(4).size());
        }
    }
}
