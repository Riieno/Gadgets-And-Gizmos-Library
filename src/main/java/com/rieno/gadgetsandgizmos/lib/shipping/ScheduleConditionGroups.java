package com.rieno.gadgetsandgizmos.lib.shipping;

import java.util.ArrayList;
import java.util.List;

// Edit ordered AND conditions and OR branches without dropping their typed configuration
public final class ScheduleConditionGroups {
    private ScheduleConditionGroups(){}

    public static <T> Position setConjunction(List<List<T>> groups, int group, int idx, boolean and){
        if(group < 0 || group >= groups.size() || idx < 0 || idx >= groups.get(group).size()) return null;
        if(and){
            if(idx > 0 || group == 0) return new Position(group, idx);
            int previousSize = groups.get(group - 1).size();
            groups.get(group - 1).addAll(groups.remove(group));
            return new Position(group - 1, previousSize);
        }
        if(idx == 0) return new Position(group, idx);
        List<T> column = groups.get(group);
        List<T> branch = new ArrayList<>(column.subList(idx, column.size()));
        column.subList(idx, column.size()).clear();
        groups.add(group + 1, branch);
        return new Position(group + 1, 0);
    }

    public static <T> Position move(List<List<T>> groups, int group, int idx, int direction){
        if(group < 0 || group >= groups.size() || idx < 0 || idx >= groups.get(group).size()) return null;
        List<T> column = groups.get(group);
        int next = idx + Integer.signum(direction);
        if(next < 0 || next >= column.size()) return new Position(group, idx);
        T condition = column.remove(idx);
        column.add(next, condition);
        return new Position(group, next);
    }

    public record Position(int group, int index) {}
}
