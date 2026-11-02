package io.github.tjhgibbs.shadermaker;
import java.util.*;
/** Bounded history. Call commit at gesture end, so a slider drag is a single edit. */
public final class History {
    private final List<Project> states = new ArrayList<>();
    private int index;
    public History(Project initial) { states.add(initial); }
    public void commit(Project project) {
        if (states.get(index).encode().equals(project.encode())) return;
        states.subList(index+1,states.size()).clear(); states.add(project); index++;
        if (states.size()>101) { states.removeFirst(); index--; }
    }
    public boolean canUndo() { return index>0; }
    public boolean canRedo() { return index<states.size()-1; }
    public Project undo() { if(canUndo()) index--; return states.get(index); }
    public Project redo() { if(canRedo()) index++; return states.get(index); }
}
