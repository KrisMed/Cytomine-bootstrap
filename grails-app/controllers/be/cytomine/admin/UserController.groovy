package be.cytomine.admin

import be.cytomine.security.User
import grails.plugins.springsecurity.Secured

@Secured(['ROLE_ADMIN','ROLE_SUPER_ADMIN'])
class UserController {

    def scaffold = User
}