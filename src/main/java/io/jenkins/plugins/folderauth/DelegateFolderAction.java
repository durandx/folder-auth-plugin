package io.jenkins.plugins.folderauth;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.json.JsonBody;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;

import hudson.Functions;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import io.jenkins.plugins.folderauth.misc.FolderRoleCreationRequest;
import io.jenkins.plugins.folderauth.misc.PermissionWrapper;
import io.jenkins.plugins.folderauth.roles.FolderRole;
import jenkins.model.Jenkins;

public class DelegateFolderAction implements Action, StaplerProxy {
	
	private static final Logger LOGGER = Logger.getLogger(DelegateFolderAction.class.getName());

	private AbstractFolder folder;

	public DelegateFolderAction(AbstractFolder folder) {
		this.folder = folder;
	}

	public String getFolderName() {
		return folder.getFullName();
	}

	@Override
	public String getIconFileName() {
		return this.folder.hasPermission(FolderBasedAuthorizationStrategy.DELEGATE_FOLDER) ? "lock.png" : null; 
	}

	@Override
	public String getDisplayName() {
		return "Gestion des droits";
	}

	@Override
	public String getUrlName() {
		return "delegate";
	}
	
    @Override
    public Object getTarget() {
        this.folder.checkPermission(FolderBasedAuthorizationStrategy.DELEGATE_FOLDER); 
        return this;
    }

    public String getItemUrl() {
    	this.folder.checkPermission(FolderBasedAuthorizationStrategy.DELEGATE_FOLDER);
    	return Functions.getActionUrl(folder.getUrl(), this);
    }
	
    @Nonnull
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused") // used by index.jelly
    public Set<Permission> getFolderPermissions() {
        HashSet<PermissionGroup> groups = new HashSet<>(PermissionGroup.getAll());
        groups.remove(PermissionGroup.get(Hudson.class));
        groups.remove(PermissionGroup.get(Computer.class));
        groups.remove(PermissionGroup.get(Permission.class));
        return getDelegateSafePermissions(groups);
    }
    
    
    @Nonnull
    static Set<Permission> getDelegateSafePermissions(Set<PermissionGroup> groups) {
        TreeSet<Permission> safePermissions = new TreeSet<>(Permission.ID_COMPARATOR);
        groups.stream().map(PermissionGroup::getPermissions).forEach(safePermissions::addAll);
        safePermissions.removeAll(PermissionWrapper.DANGEROUS_PERMISSIONS);
        safePermissions.remove(FolderBasedAuthorizationStrategy.DELEGATE_FOLDER);
        return safePermissions;
    }

    
    /**
     * Adds a {@link FolderRole} to {@link FolderBasedAuthorizationStrategy}.
     *
     * @param request the request to create the role
     * @throws IllegalStateException when {@link Jenkins#getAuthorizationStrategy()} is
     *                               not {@link FolderBasedAuthorizationStrategy}
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doAddFolderRole(@JsonBody FolderRoleCreationRequest request) {
    	this.folder.checkPermission(FolderBasedAuthorizationStrategy.DELEGATE_FOLDER);
        request.folderNames = new HashSet<>(Arrays.asList(folder.getFullName()));
        FolderAuthorizationStrategyAPI.addFolderRole(request.getFolderRole());
    }

    
    /**
     * Assigns {@code sid} to the folder role identified by {@code roleName}.
     * <p>
     *
     * @param roleName the name of the global to which {@code sid} will be assigned to.
     * @param sid      the sid of the user/group to be assigned.
     * @throws IllegalStateException            when {@link Jenkins#getAuthorizationStrategy()} is
     *                                          not {@link FolderBasedAuthorizationStrategy}
     * @throws java.util.NoSuchElementException when no role with name equal to {@code roleName} exists.
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doAssignSidToFolderRole(@QueryParameter(required = true) String roleName,
                                        @QueryParameter(required = true) String sid) {
    	this.folder.checkPermission(FolderBasedAuthorizationStrategy.DELEGATE_FOLDER);
        
        Jenkins jenkins = Jenkins.get();
        AuthorizationStrategy strategy = jenkins.getAuthorizationStrategy();
        
        if (strategy instanceof FolderBasedAuthorizationStrategy) {
        	Set<FolderRole> folderRoles = ((FolderBasedAuthorizationStrategy) strategy).getFolderRoles();
        	Set<FolderRole> filteredFolderRole = removeMultiFolderAndFolderDelegation(folderRoles);
            
        	if (filteredFolderRole.stream().anyMatch(ffr -> ffr.getName().equals(roleName))) {
            	FolderAuthorizationStrategyAPI.assignSidToFolderRole(sid, roleName);
            } else {
            	throw new IllegalArgumentException("No folder role with name = \"" + roleName + "\" exists");
            }
            
        } else {
            throw new IllegalStateException("FolderBasedAuthorizationStrategy is not the" + " current authorization strategy");
        }

        redirect();
    }

    /**
     * Redirects to the same page that initiated the request.
     */
    private void redirect() {
        try {
            Stapler.getCurrentResponse().forwardToPreviousPage(Stapler.getCurrentRequest());
        } catch (ServletException | IOException e) {
            LOGGER.log(Level.WARNING, "Unable to redirect to previous page.");
        }
    }
    
    private Set<FolderRole> removeMultiFolderAndFolderDelegation(Set<FolderRole> folderRoles) {
    	//Filtering Role with multiple Forlders, and prevent the DelegateFolder role to be delegate to another user.
    	Set<FolderRole> filteredFolderRole = folderRoles.stream()
    													.filter(fr -> (fr.getFolderNames().contains(folder.getFullName()) && fr.getFolderNames().size() == 1)
    																  && (fr.getPermissionsUnsorted().contains(PermissionWrapper.wrapPermission(FolderBasedAuthorizationStrategy.DELEGATE_FOLDER)) == false))
    													.collect(Collectors.toSet());
    	return filteredFolderRole;
    	
    }
    
    /**
     * Returns the {@link FolderRole}s used by the {@link FolderBasedAuthorizationStrategy}.
     *
     * @return the {@link FolderRole}s used by the {@link FolderBasedAuthorizationStrategy}
     * @throws IllegalStateException when {@link Jenkins#getAuthorizationStrategy()} is
     *                               not {@link FolderBasedAuthorizationStrategy}
     */
    @Nonnull
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused") // used by index.jelly
    public SortedSet<FolderRole> getFolderRoles() {
        AuthorizationStrategy strategy = Jenkins.get().getAuthorizationStrategy();
        if (strategy instanceof FolderBasedAuthorizationStrategy) {
        	
        	Set<FolderRole> folderRoles = ((FolderBasedAuthorizationStrategy) strategy).getFolderRoles();
        	
            return new TreeSet<>(removeMultiFolderAndFolderDelegation(folderRoles));
        } else {
            throw new IllegalStateException(Messages.FolderBasedAuthorizationStrategy_NotCurrentStrategy());
        }
    }
    
    /**
     * Deletes a folder role.
     *
     * @param roleName the name of the role to be deleted
     * @throws IllegalStateException    when {@link Jenkins#getAuthorizationStrategy()} is
     *                                  not {@link FolderBasedAuthorizationStrategy}
     * @throws IllegalArgumentException when no role with name equal to {@code roleName} exists.
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doDeleteFolderRole(@QueryParameter(required = true) String roleName) {
    	this.folder.checkPermission(FolderBasedAuthorizationStrategy.DELEGATE_FOLDER);
        FolderAuthorizationStrategyAPI.deleteFolderRole(roleName);
        redirect();
    }
    
    /**
     * Removes {@code sid} from the folder role identified by {@code roleName}.
     *
     * @param roleName the name of the folder role from which {@code sid} will be removed.
     * @param sid      the sid of the user/group to be assigned.
     * @throws IllegalStateException    when {@link Jenkins#getAuthorizationStrategy()} is
     *                                  not {@link FolderBasedAuthorizationStrategy}
     * @throws IllegalArgumentException when no role with name equal to {@code roleName} exists.
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doRemoveSidFromFolderRole(@QueryParameter(required = true) String roleName,
                                          @QueryParameter(required = true) String sid) {
    	this.folder.checkPermission(FolderBasedAuthorizationStrategy.DELEGATE_FOLDER);
        FolderAuthorizationStrategyAPI.removeSidFromFolderRole(sid, roleName);
        redirect();
    }

}