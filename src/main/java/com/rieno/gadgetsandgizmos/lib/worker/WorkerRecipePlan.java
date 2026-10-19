package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Describe the exact resources and processor type required for one worker recipe operation
public record WorkerRecipePlan(ResourceLocation recipeId, ResourceLocation processorType,
                               Operation operation, List<Input> inputs,
                               WorkerResourceKey result, long resultAmount, int stage) {
    // Preserve ordinary recipe callers while allowing adapters to persist ordered machine stages
    public WorkerRecipePlan(ResourceLocation recipeId, ResourceLocation processorType, Operation operation,
                             List<Input> inputs, WorkerResourceKey result, long resultAmount){
        this(recipeId, processorType, operation, inputs, result, resultAmount, -1);
    }
    // Initialize one immutable worker recipe plan
    public WorkerRecipePlan {
        recipeId = recipeId == null ? ResourceLocation.fromNamespaceAndPath("minecraft", "empty") : recipeId;
        processorType = processorType == null ? ResourceLocation.fromNamespaceAndPath("minecraft", "crafting")
                : processorType;
        operation = operation == null ? Operation.CRAFTING : operation;
        inputs = normalizeInputs(inputs);
        result = result == null ? WorkerResourceKey.energy() : result;
        resultAmount = Math.max(1L, resultAmount);
    }

    // Check whether this plan requires a physical processing endpoint.
    public boolean requiresProcessor() {
        return operation != Operation.WORKER_CRAFTING;
    }

    // Get the total amount of one resource consumed by this recipe operation.
    public long inputAmount(WorkerResourceKey resource) {
        if (resource == null) return 0L;
        return inputs.stream().filter(input -> resource.equals(input.resource()))
                .mapToLong(Input::amount).sum();
    }

    // Count the undelivered ingredient across the selected production batches
    public long inputRemaining(int idx, long batches, long delivered){
        if(idx < 0 || idx >= inputs.size() || batches <= 0L) return 0L;
        long amount = inputs.get(idx).amount();
        long required = batches > Long.MAX_VALUE / amount ? Long.MAX_VALUE : amount * batches;
        return Math.max(0L, required - Math.max(0L, delivered));
    }

    // Bound a production visit by every ingredient route and the worker's output capacity
    public long batchesFor(long remaining, Map<WorkerResourceKey, Long> inputLimits, long outputLimit){
        if(remaining <= 0L || outputLimit < resultAmount || inputs.isEmpty() || inputLimits == null) return 0L;
        long batches = Math.min((remaining - 1L) / resultAmount + 1L, outputLimit / resultAmount);
        if(stage >= 0) batches = Math.min(batches, 1L);
        for(Input input : inputs){
            batches = Math.min(batches, Math.max(0L, inputLimits.getOrDefault(input.resource(), 0L)) / input.amount());
        }
        return batches;
    }

    // Write one recipe plan for persistent worker queues.
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Recipe", recipeId.toString());
        tag.putString("ProcessorType", processorType.toString());
        tag.putString("Operation", operation.id());
        ListTag inputTags = new ListTag();
        for (Input input : inputs) inputTags.add(input.toTag());
        tag.put("Inputs", inputTags);
        tag.put("Result", result.toTag());
        tag.putLong("ResultAmount", resultAmount);
        if(stage >= 0) tag.putInt("Stage", stage);
        return tag;
    }

    // Read one persisted recipe plan.
    public static WorkerRecipePlan fromTag(CompoundTag tag) {
        CompoundTag safe = tag == null ? new CompoundTag() : tag;
        List<Input> inputs = new ArrayList<>();
        ListTag inputTags = safe.getList("Inputs", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < inputTags.size(); idx++) inputs.add(Input.fromTag(inputTags.getCompound(idx)));
        return new WorkerRecipePlan(ResourceLocation.tryParse(safe.getString("Recipe")),
                ResourceLocation.tryParse(safe.getString("ProcessorType")),
                Operation.byId(safe.getString("Operation")), inputs,
                safe.contains("Result", Tag.TAG_COMPOUND)
                        ? WorkerResourceKey.fromTag(safe.getCompound("Result")) : WorkerResourceKey.energy(),
                safe.getLong("ResultAmount"), safe.contains("Stage") ? safe.getInt("Stage") : -1);
    }

    // Collapse only inputs with the same selected resource and recipe alternatives.
    private static List<Input> normalizeInputs(List<Input> inputs) {
        Map<InputSelection, Long> combined = new LinkedHashMap<>();
        if (inputs != null) {
            for (Input input : inputs) {
                if (input == null || input.amount() <= 0L) continue;
                combined.merge(new InputSelection(input.resource(), input.alternatives()), input.amount(),
                        WorkerRecipePlan::saturatedAdd);
            }
        }
        return combined.entrySet().stream().map(entry -> new Input(entry.getKey().resource(), entry.getValue(),
                entry.getKey().alternatives())).toList();
    }

    private record InputSelection(WorkerResourceKey resource, List<WorkerResourceKey> alternatives){}

    // Add amounts without wrapping a persistent plan.
    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    // Store one selected resource and the alternatives accepted by its recipe ingredient.
    public record Input(WorkerResourceKey resource, long amount, List<WorkerResourceKey> alternatives) {
        public Input(WorkerResourceKey resource, long amount){
            this(resource, amount, List.of(resource == null ? WorkerResourceKey.energy() : resource));
        }

        // Initialize one recipe ingredient requirement
        public Input {
            resource = resource == null ? WorkerResourceKey.energy() : resource;
            amount = Math.max(1L, amount);
            WorkerResourceType type = resource.type();
            alternatives = alternatives == null ? List.of(resource) : alternatives.stream()
                    .filter(candidate -> candidate != null && candidate.type() == type).distinct().toList();
            if(!alternatives.contains(resource)){
                List<WorkerResourceKey> accepted = new ArrayList<>();
                accepted.add(resource);
                accepted.addAll(alternatives);
                alternatives = List.copyOf(accepted);
            }
        }

        // Write one recipe ingredient requirement.
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.put("Resource", resource.toTag());
            tag.putLong("Amount", amount);
            ListTag accepted = new ListTag();
            for(WorkerResourceKey alternative : alternatives) accepted.add(alternative.toTag());
            tag.put("Alternatives", accepted);
            return tag;
        }

        // Read one recipe ingredient requirement.
        public static Input fromTag(CompoundTag tag) {
            CompoundTag safe = tag == null ? new CompoundTag() : tag;
            WorkerResourceKey resource = safe.contains("Resource", Tag.TAG_COMPOUND)
                    ? WorkerResourceKey.fromTag(safe.getCompound("Resource")) : WorkerResourceKey.energy();
            List<WorkerResourceKey> alternatives = new ArrayList<>();
            ListTag accepted = safe.getList("Alternatives", Tag.TAG_COMPOUND);
            for(int idx = 0; idx < accepted.size(); idx++) alternatives.add(WorkerResourceKey.fromTag(accepted.getCompound(idx)));
            return new Input(resource, safe.getLong("Amount"), alternatives.isEmpty() ? List.of(resource) : alternatives);
        }
    }

    // Identify whether a plan runs in a worker grid or an SCM-linked processor.
    public enum Operation {
        WORKER_CRAFTING("worker_crafting"),
        CRAFTING("crafting"),
        PROCESSING("processing");

        private final String id;

        // Initialize one operation id
        Operation(String id) {
            this.id = id;
        }

        // Get the saved operation id
        public String id() {
            return id;
        }

        // Resolve one saved operation id
        public static Operation byId(String id) {
            for (Operation operation : values()) if (operation.id.equalsIgnoreCase(id)) return operation;
            return PROCESSING;
        }
    }
}
