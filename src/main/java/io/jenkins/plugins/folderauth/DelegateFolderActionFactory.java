package io.jenkins.plugins.folderauth;

import java.util.Collection;
import java.util.Collections;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import jenkins.model.TransientActionFactory;

@Extension
public class DelegateFolderActionFactory extends TransientActionFactory<AbstractFolder> {

    @Override
    public Class<AbstractFolder> type() {
        return AbstractFolder.class; 
    }

    @NonNull
	@Override
	public Collection<? extends Action> createFor(@NonNull AbstractFolder folder) {
    	return Collections.singleton(new DelegateFolderAction(folder)); 
	}
}