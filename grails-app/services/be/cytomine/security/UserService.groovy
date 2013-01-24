package be.cytomine.security

import be.cytomine.Exception.ForbiddenException
import be.cytomine.Exception.ObjectNotFoundException
import be.cytomine.utils.ModelService
import be.cytomine.command.AddCommand
import be.cytomine.command.DeleteCommand
import be.cytomine.command.EditCommand
import be.cytomine.ontology.Ontology
import be.cytomine.project.Project
import be.cytomine.social.LastConnection
import be.cytomine.utils.Utils
import org.apache.commons.collections.ListUtils
import org.codehaus.groovy.grails.plugins.springsecurity.acl.AclSid
import org.codehaus.groovy.grails.web.json.JSONObject
import org.springframework.security.access.prepost.PreAuthorize

import static org.springframework.security.acls.domain.BasePermission.ADMINISTRATION
import static org.springframework.security.acls.domain.BasePermission.READ

import org.springframework.security.acls.model.Permission
import org.springframework.transaction.annotation.Transactional
import be.cytomine.SecurityCheck
import be.cytomine.CytomineDomain
import org.springframework.security.acls.model.ObjectIdentity
import org.springframework.security.acls.domain.ObjectIdentityImpl
import org.springframework.security.acls.model.MutableAcl
import org.springframework.security.acls.model.NotFoundException
import org.springframework.security.core.context.SecurityContextHolder as SCH
import groovy.sql.Sql
import be.cytomine.command.CommandHistory
import be.cytomine.command.Command
import be.cytomine.command.UndoStackItem
import be.cytomine.command.RedoStackItem

class UserService extends ModelService {

    static transactional = true

    def springSecurityService
    def transactionService
    def cytomineService
    def commandService
    def modelService
    def userGroupService
    def dataSource
    def permissionService

    @PreAuthorize("hasRole('ROLE_USER')")
    def get(def id) {
        SecUser.get(id)
    }

    @PreAuthorize("hasRole('ROLE_USER')")
    SecUser getByPublicKey(String key) {
        SecUser.findByPublicKey(key)
    }

    @PreAuthorize("hasRole('ROLE_USER')")
    def read(def id) {
        SecUser.read(id)
    }

    @PreAuthorize("hasRole('ROLE_USER')")
    def readCurrentUser() {
        cytomineService.getCurrentUser()
    }

    @PreAuthorize("hasRole('ROLE_USER')")
    def list() {
        User.list(sort: "username", order: "asc")
    }

    @PreAuthorize("#project.hasPermission('READ') or hasRole('ROLE_ADMIN')")
    def list(Project project, List ids) {
        SecUser.findAllByIdInList(ids)
    }

    @PreAuthorize("#project.hasPermission('READ') or hasRole('ROLE_ADMIN')")
    def listUsers(Project project) {
        List<SecUser> users = SecUser.executeQuery("select distinct secUser from AclObjectIdentity as aclObjectId, AclEntry as aclEntry, AclSid as aclSid, SecUser as secUser "+
            "where aclObjectId.objectId = "+project.id+" and aclEntry.aclObjectIdentity = aclObjectId.id and aclEntry.sid = aclSid.id and aclSid.sid = secUser.username and secUser.class = 'be.cytomine.security.User'")
        return users
    }

    @PreAuthorize("#project.hasPermission('READ') or hasRole('ROLE_ADMIN')")
    def listCreator(Project project) {
        List<User> users = SecUser.executeQuery("select secUser from AclObjectIdentity as aclObjectId, AclSid as aclSid, SecUser as secUser where aclObjectId.objectId = "+project.id+" and aclObjectId.owner = aclSid.id and aclSid.sid = secUser.username and secUser.class = 'be.cytomine.security.User'")
        User user = users.isEmpty() ? null : users.first()
        return user
    }

    @PreAuthorize("#project.hasPermission('READ') or hasRole('ROLE_ADMIN')")
    def listAdmins(Project project) {
        def users = SecUser.executeQuery("select distinct secUser from AclObjectIdentity as aclObjectId, AclEntry as aclEntry, AclSid as aclSid, SecUser as secUser "+
            "where aclObjectId.objectId = "+project.id+" and aclEntry.aclObjectIdentity = aclObjectId.id and aclEntry.mask = 16 and aclEntry.sid = aclSid.id and aclSid.sid = secUser.username and secUser.class = 'be.cytomine.security.User'")
        return users
    }

    @PreAuthorize("#ontology.hasPermission('READ') or hasRole('ROLE_ADMIN')")
    def listUsers(Ontology ontology) {
        //TODO:: Not optim code a single SQL request will be very faster
        def users = []
        def projects = Project.findAllByOntology(ontology)
        projects.each { project ->
            users.addAll(listUsers(project))
        }
        users.unique()
    }

    /**
     * Get all allowed user id for a specific domain instance
     * E.g; get all user id for a project
     */
    List<Long> getAllowedUserIdList(CytomineDomain domain) {
        String request = "SELECT DISTINCT sec_user.id \n" +
                " FROM acl_object_identity, acl_entry,acl_sid, sec_user \n" +
                " WHERE acl_object_identity.object_id_identity = $domain.id\n" +
                " AND acl_entry.acl_object_identity=acl_object_identity.id\n" +
                " AND acl_entry.sid = acl_sid.id " +
                " AND acl_sid.sid = sec_user.username " +
                " AND sec_user.class = 'be.cytomine.security.User' "
        def data = []
        new Sql(dataSource).eachRow(request) {
            data << it[0]
        }
        return data
    }

    /**
     * List all layers from a project
     * Each user has its own layer
     * If project has private layer, just get current user layer
     */
    @PreAuthorize("#project.hasPermission('READ') or hasRole('ROLE_ADMIN')")
    def listLayers(Project project) {
        Collection<SecUser> users = listUsers(project)
        SecUser currentUser = cytomineService.getCurrentUser()
        if (project.privateLayer && users.contains(currentUser)) {
            return [currentUser]
        } else if (!project.privateLayer) {
            return  users
        } else { //should no arrive but possible if user is admin and not in project
            []
        }
    }

    /**
     * Get all online user
     */
    @PreAuthorize("hasRole('ROLE_USER')")
    List<SecUser> getAllOnlineUsers() {
        //get date with -X secondes
        def xSecondAgo = Utils.getDatePlusSecond(-20000)
        def results = LastConnection.withCriteria {
            ge('date', xSecondAgo)
            projections {
                groupProperty("user")
            }
        }
        return results
    }

    /**
     * Get all online user for a project
     */
    @PreAuthorize("#project.hasPermission('READ') or hasRole('ROLE_ADMIN')")
    List<SecUser> getAllOnlineUsers(Project project) {
        if(!project) return getAllOnlineUsers()
        def xSecondAgo = Utils.getDatePlusSecond(-20)
        def results = LastConnection.withCriteria {
            ge('date', xSecondAgo)
            eq('project',project)
            projections {
                groupProperty("user")
            }
        }
        return results
    }

    /**
     * Get all user that share at least a same project as user from argument
     */
    @PreAuthorize("#user.id == principal.id or hasRole('ROLE_ADMIN')")
    List<SecUser> getAllFriendsUsers(SecUser user) {
        AclSid sid = AclSid.findBySid(user.username)
        List<SecUser> users = SecUser.executeQuery(
            "select distinct secUser from AclSid as aclSid, AclEntry as aclEntry, SecUser as secUser "+
            "where aclEntry.aclObjectIdentity in (select  aclEntry.aclObjectIdentity from aclEntry where sid = ${sid.id}) and aclEntry.sid = aclSid.id and aclSid.sid = secUser.username and aclSid.id!=${sid.id}")

        return users
    }

    /**
     * Get all online user that share at least a same project as user from argument
     */
    @PreAuthorize("#user.id == principal.id or hasRole('ROLE_ADMIN')")
    List<SecUser> getAllFriendsUsersOnline(SecUser user) {
       return ListUtils.intersection(getAllFriendsUsers(user),getAllOnlineUsers())
    }

    /**
     * Get all user that share at least a same project as user from argument and
     */
    @PreAuthorize("#project.hasPermission('READ') or hasRole('ROLE_ADMIN')")
    List<SecUser> getAllFriendsUsersOnline(SecUser user, Project project) {
        //no need to make insterect because getAllOnlineUsers(project) contains only friends users
       return getAllOnlineUsers(project)
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    def add(def json,SecurityCheck security) {
        User currentUser = cytomineService.getCurrentUser()
        return executeCommand(new AddCommand(user: currentUser), json)
    }

    @PreAuthorize("#security.checkCurrentUserCreator(principal.id) or hasRole('ROLE_ADMIN')")
    def update(def json, SecurityCheck security) {
        User currentUser = cytomineService.getCurrentUser()
        return executeCommand(new EditCommand(user: currentUser), json)
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    def delete(def json, SecurityCheck security) {
        User currentUser = cytomineService.getCurrentUser()
        if (json.id == springSecurityService.principal.id) {
            throw new ForbiddenException("A user can't delete herself")
        }
        return executeCommand(new DeleteCommand(user: currentUser), json)
    }

    /**
     * Add a user in project user or admin list
     * @param user User to add in project
     * @param project Project that will be accessed by user
     * @param admin Flaf if user will become a simple user or a project admin
     * @return Response structure
     */
    @PreAuthorize("#project.hasPermission('ADMIN') or hasRole('ROLE_ADMIN')")
    def addUserFromProject(SecUser user, Project project, boolean admin) {
            if (project) {
                log.debug "addUserFromProject project=" + project + " username=" + user?.username + " ADMIN=" + admin
                if(admin) {
                    permissionService.addPermission(project,user.username,ADMINISTRATION)
                    permissionService.addPermission(project.ontology,user.username,READ)
                }
                else {
                    permissionService.addPermission(project,user.username,READ)
                    permissionService.addPermission(project.ontology,user.username,READ)
                }
            }
        [data: [message: "OK"], status: 201]
    }

    /**
     * Delete a user from a project user or admin list
     * @param user User to remove from project
     * @param project Project that will not longer be accessed by user
     * @param admin Flaf if user will become a simple user or a project admin
     * @return Response structure
     */
    @PreAuthorize("#project.hasPermission('ADMIN') or #user.id == principal.id or hasRole('ROLE_ADMIN')")
    def deleteUserFromProject(SecUser user, Project project, boolean admin) {
            if (project) {
                log.info "deleteUserFromProject project=" + project?.id + " username=" + user?.username + " ADMIN=" + admin
                if(admin) {
                    //TODO:: a user admin can remove another admin user?
                    permissionService.deletePermission(project,user.username,ADMINISTRATION)
                    //TODO:: bug code: if user x has access to ontology o thx to project p1 & p2, if x is removed from p1, it loose right from o... => it should keep this right thx to p2!
                    permissionService.deletePermission(project.ontology,user.username,READ)
                }
                else {
                    permissionService.deletePermission(project,user.username,READ)
                    //TODO:: bug code: if user x has access to ontology o thx to project p1 & p2, if x is removed from p1, it loose right from o... => it should keep this right thx to p2!
                    permissionService.deletePermission(project.ontology,user.username,READ)
                }
            }
       [data: [message: "OK"], status: 201]
    }


    /**
     * Restore domain which was previously deleted
     * @param json domain info
     * @param printMessage print message or not
     * @return response
     */
    def create(JSONObject json, boolean printMessage) {
        create(User.createFromDataWithId(json), printMessage)
    }

    def create(User domain, boolean printMessage) {
        //Save new object
        saveDomain(domain)
        //Build response message
        return responseService.createResponseMessage(domain, [domain.id, domain.username], printMessage, "Add", domain.getCallBack())
    }
    /**
     * Destroy domain which was previously added
     * @param json domain info
     * @param printMessage print message or not
     * @return response
     */
    def destroy(JSONObject json, boolean printMessage) {
        //Get object to delete
        destroy(User.get(json.id), printMessage)
    }

    def destroy(User domain, boolean printMessage) {
        //Build response message
        def response = responseService.createResponseMessage(domain, [domain.id, domain.username], printMessage, "Delete", domain.getCallBack())

        SecUserSecRole.findAllBySecUser(domain).each{
            it.delete(flush:true)
        }

        Command.findAllByUser(domain).each {
            UndoStackItem.findAllByCommand(it).each { it.delete()}
            RedoStackItem.findAllByCommand(it).each { it.delete()}
            CommandHistory.findAllByCommand(it).each {it.delete()}
            it.delete()
        }

        //Delete object
        deleteDomain(domain)
        return response
    }

    /**
     * Edit domain which was previously edited
     * @param json domain info
     * @param printMessage print message or not
     * @return response
     */
    def edit(JSONObject json, boolean printMessage) {
        //Rebuilt previous state of object that was previoulsy edited
        edit(fillDomainWithData(new User(), json), printMessage)
    }

    def edit(User domain, boolean printMessage) {
        //Build response message
        def response = responseService.createResponseMessage(domain, [domain.id, domain.username], printMessage, "Edit", domain.getCallBack())
        //Save update
        saveDomain(domain)
        return response
    }

    /**
     * Create domain from JSON object
     * @param json JSON with new domain info
     * @return new domain
     */
    User createFromJSON(def json) {
        return User.createFromData(json)
    }

    /**
     * Retrieve domain thanks to a JSON object
     * @param json JSON with new domain info
     * @return domain retrieve thanks to json
     */
    def retrieve(JSONObject json) {
        User user = User.get(json.id)
        if (!user) throw new ObjectNotFoundException("User " + json.id + " not found")
        return user
    }
}
