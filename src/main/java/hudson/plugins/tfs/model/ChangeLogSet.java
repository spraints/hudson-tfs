package hudson.plugins.tfs.model;

import hudson.model.AbstractBuild;

import java.util.Iterator;
import java.util.List;

/**
 * ChangeLogSet for the Team Foundation Server SCM
 * The log set will set the parent of the log entries in the constructor.
 * 
 * @author Erik Ramfelt
 */
public class ChangeLogSet extends hudson.scm.ChangeLogSet<ChangeSet> {

    private final List<ChangeSet> changesets;
    
    public ChangeLogSet(AbstractBuild<?, ?> build, List<ChangeSet> changesets) {
        super(build);
        this.changesets = changesets;
        for (ChangeSet changeset : changesets) {
            changeset.setParent(this);
        }
    }

    @Override
    public boolean isEmptySet() {
        return changesets.isEmpty();
    }

    public Iterator<ChangeSet> iterator() {
        return changesets.iterator();
    }
}
