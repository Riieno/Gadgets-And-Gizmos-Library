package com.rieno.gadgetsandgizmos.lib.worker;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;

// Describe an ordered set of recipe operations that produces one requested resource
public record WorkerRecipeChain(List<Step> steps) {
    // Initialize an immutable dependency-first recipe chain
    public WorkerRecipeChain {
        List<Step> valid = new ArrayList<>();
        if (steps != null) {
            for (Step step : steps) if (step != null) valid.add(step);
        }
        steps = List.copyOf(valid);
    }

    // Check whether this chain contains a result-producing operation
    public boolean executable() {
        return !steps.isEmpty();
    }

    // Combine identical prerequisites and run every producer before its consumers
    public WorkerRecipeChain grouped(){
        if(steps.size() < 3 || steps.stream().anyMatch(step -> step.plan().stage() >= 0)) return this;
        Map<WorkerRecipePlan, Long> totals = new LinkedHashMap<>();
        for(int idx = 0; idx < steps.size() - 1; idx++){
            Step step = steps.get(idx);
            totals.merge(step.plan(), step.requestedAmount(), WorkerRecipeChain::add);
        }
        List<Step> ordered = new ArrayList<>();
        Set<WorkerRecipePlan> remaining = new HashSet<>(totals.keySet());
        while(!remaining.isEmpty()){
            WorkerRecipePlan ready = null;
            for(WorkerRecipePlan candidate : totals.keySet()){
                if(!remaining.contains(candidate)) continue;
                boolean needsProducer = remaining.stream().anyMatch(producer -> producer != candidate
                        && candidate.inputs().stream().anyMatch(input -> input.resource().equals(producer.result())));
                if(!needsProducer){
                    ready = candidate;
                    break;
                }
            }
            if(ready == null) return this;
            ordered.add(new Step(ready, totals.get(ready)));
            remaining.remove(ready);
        }
        ordered.add(steps.getLast());
        return new WorkerRecipeChain(ordered);
    }

    private static long add(long first, long second){
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    // Compile all prerequisites before the requested result and route only that result to its recipient
    public List<WorkerWorkOrder> orders(UUID requestId, String name, int priority,
                                        UUID destinationId, UUID returnStationId){
        return orders(requestId, name, priority, destinationId, returnStationId, null);
    }

    // Retain an explicit raw-material source while staged prerequisites stay in the worker inventory
    public List<WorkerWorkOrder> orders(UUID requestId, String name, int priority,
                                        UUID destinationId, UUID returnStationId, UUID sourceId){
        List<WorkerWorkOrder> orders = new ArrayList<>();
        for(int idx = 0; idx < steps.size(); idx++){
            Step step = steps.get(idx);
            WorkerRecipePlan plan = step.plan();
            boolean finalStep = idx == steps.size() - 1;
            boolean stageDelivery = finalStep && destinationId != null && step.requestedAmount() > plan.resultAmount();
            UUID id = finalStep && !stageDelivery ? requestId : UUID.nameUUIDFromBytes((requestId + ":recipe:" + idx
                    + ":" + plan.recipeId()).getBytes(StandardCharsets.UTF_8));
            WorkerResourceKey resource = plan.inputs().isEmpty() ? plan.result() : plan.inputs().getFirst().resource();
            WorkerTask task = new WorkerTask(id, name, resource, step.requestedAmount(), 0L, priority, true);
            WorkerWorkOrder.Mode mode = plan.operation() == WorkerRecipePlan.Operation.PROCESSING
                    ? WorkerWorkOrder.Mode.PROCESS : WorkerWorkOrder.Mode.AUTO_CRAFT;
            orders.add(new WorkerWorkOrder(id, task, mode, sourceId, finalStep && !stageDelivery ? destinationId : null,
                    null, plan.result(), plan.resultAmount(), finalStep && !stageDelivery ? returnStationId : null, plan));
            if(stageDelivery){
                WorkerTask delivery = new WorkerTask(requestId, name, plan.result(), step.requestedAmount(), 0L,
                        priority, true);
                orders.add(new WorkerWorkOrder(requestId, delivery, WorkerWorkOrder.Mode.TRANSFER, null,
                        destinationId, null, plan.result(), step.requestedAmount(), returnStationId));
            }
        }
        return List.copyOf(orders);
    }

    // Bind the final recipe visit while leaving its later delivery order unchanged
    public List<WorkerWorkOrder> orders(UUID requestId, String name, int priority, UUID destinationId,
                                        UUID returnStationId, UUID sourceId, UUID processorId){
        List<WorkerWorkOrder> orders = new ArrayList<>(orders(requestId, name, priority,
                destinationId, returnStationId, sourceId));
        if(processorId != null){
            for(int idx = orders.size() - 1; idx >= 0; idx--){
                if(orders.get(idx).recipePlan() == null) continue;
                orders.set(idx, orders.get(idx).withProcessor(processorId));
                break;
            }
        }
        return List.copyOf(orders);
    }

    // Describe one recipe operation and the requested amount of its result
    public record Step(WorkerRecipePlan plan, long requestedAmount) {
        // Initialize one positive recipe operation request
        public Step {
            plan = plan == null ? new WorkerRecipePlan(null, null, null, List.of(), null, 1L) : plan;
            requestedAmount = Math.max(1L, requestedAmount);
        }
    }
}
