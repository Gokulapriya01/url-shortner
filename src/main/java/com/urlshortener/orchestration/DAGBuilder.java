package com.urlshortener.orchestration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.urlshortener.domain.entity.Task;
import com.urlshortener.domain.enums.TaskStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DAGBuilder {

    /** Build. */
    public TaskDAG build(List<Task> tasks) {
        Map<String, DAGNode> nodes = new HashMap<>();
        Map<Integer, List<String>> phases = new HashMap<>();

        // Create nodes
        for (Task task : tasks) {
            String taskId = task.getDefinitionId() != null ? task.getDefinitionId() : task.getId().toString();

            List<String> deps = task.getDependencies() != null ? task.getDependencies() : List.of();

            nodes.put(taskId, DAGNode.builder()
                .taskId(taskId)
                .dependencies(new HashSet<>(deps))
                .dependents(new HashSet<>())
                .status(task.getStatus())
                .inDegree(deps.size())
                .build());

            phases.computeIfAbsent(task.getPhase(), k -> new ArrayList<>()).add(taskId);
        }

        // Build reverse edges (dependents)
        for (Task task : tasks) {
            String taskId = task.getDefinitionId() != null ? task.getDefinitionId() : task.getId().toString();
            List<String> deps = task.getDependencies() != null ? task.getDependencies() : List.of();

            for (String depId : deps) {
                DAGNode depNode = nodes.get(depId);
                if (depNode != null) {
                    depNode.getDependents().add(taskId);
                }
            }
        }

        // Validate no cycles
        validateNoCycles(nodes);

        // Topological sort
        List<String> executionOrder = topologicalSort(nodes);

        log.info("DAG built: taskCount={}, phaseCount={}", tasks.size(), phases.size());

        return TaskDAG.builder()
            .nodes(nodes)
            .phases(phases)
            .executionOrder(executionOrder)
            .build();
    }

    private void validateNoCycles(Map<String, DAGNode> nodes) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String nodeId : nodes.keySet()) {
            if (!visited.contains(nodeId)) {
                if (hasCycle(nodeId, nodes, visited, recursionStack)) {
                    throw new IllegalArgumentException("Cycle detected in task DAG involving: " + nodeId);
                }
            }
        }
    }

    private boolean hasCycle(String nodeId, Map<String, DAGNode> nodes,
                             Set<String> visited, Set<String> recursionStack) {
        visited.add(nodeId);
        recursionStack.add(nodeId);

        DAGNode node = nodes.get(nodeId);
        if (node != null) {
            for (String dependentId : node.getDependents()) {
                if (!visited.contains(dependentId)) {
                    if (hasCycle(dependentId, nodes, visited, recursionStack)) {
                        return true;
                    }
                } else if (recursionStack.contains(dependentId)) {
                    return true;
                }
            }
        }

        recursionStack.remove(nodeId);
        return false;
    }

    private List<String> topologicalSort(Map<String, DAGNode> nodes) {
        List<String> result = new ArrayList<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        // Initialize in-degrees
        for (Map.Entry<String, DAGNode> entry : nodes.entrySet()) {
            inDegree.put(entry.getKey(), entry.getValue().getInDegree());
            if (entry.getValue().getInDegree() == 0) {
                queue.add(entry.getKey());
            }
        }

        // Kahn's algorithm
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            result.add(nodeId);

            DAGNode node = nodes.get(nodeId);
            for (String dependentId : node.getDependents()) {
                int newDegree = inDegree.get(dependentId) - 1;
                inDegree.put(dependentId, newDegree);
                if (newDegree == 0) {
                    queue.add(dependentId);
                }
            }
        }

        if (result.size() != nodes.size()) {
            throw new IllegalStateException("Cannot resolve all dependencies - possible missing nodes");
        }

        return result;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class DAGNode {
        private String taskId;
        private Set<String> dependencies;
        private Set<String> dependents;
        private TaskStatus status;
        private int inDegree;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class TaskDAG {
        private Map<String, DAGNode> nodes;
        private Map<Integer, List<String>> phases;
        private List<String> executionOrder;
    }
}
