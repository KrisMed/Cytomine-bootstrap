package be.cytomine.ontology

import be.cytomine.CytomineDomain
import be.cytomine.Exception.AlreadyExistException
import be.cytomine.utils.JSONUtils
import grails.converters.JSON
import org.apache.log4j.Logger
import org.jsondoc.core.annotation.ApiObjectField

/**
 * Relation between a term 1 and a term 2
 */
//@ApiObject(name = "relationTerm", description = "Relation Term description", show = true)
class RelationTerm extends CytomineDomain implements Serializable {

    static names = [PARENT: "parent", SYNONYM: "synonyme"]

    @ApiObjectField(
            description = "The relation associated",
            allowedType = "integer",
            apiFieldName = "relation",
            apiValueAccessor = "relationID")
    Relation relation

    private static Integer relationID(RelationTerm relationTerm) {
        return relationTerm.id
    }

    @ApiObjectField(
            description = "The first term associated",
            allowedType = "integer",
            apiFieldName = "term1",
            apiValueAccessor = "term1ID")
    Term term1

    private static Integer term1ID(RelationTerm relationTerm) {
        return relationTerm.term1?.id
    }

    @ApiObjectField(
            description = "The second term associated",
            allowedType = "integer",
            apiFieldName = "term2",
            apiValueAccessor = "term2ID")
    Term term2

    private static Integer term2ID(RelationTerm relationTerm) {
        return relationTerm.term2?.id
    }

    static mapping = {
        id(generator: 'assigned', unique: true)
        sort "id"
    }

    String toString() {
        "[" + this.id + " <" + relation + '(' + relation?.name + ')' + ":[" + term1 + '(' + term1?.name + ')' + "," + term2 + '(' + term2?.name + ')' + "]>]"
    }

    /**
     * Insert JSON data into domain in param
     * @param domain Domain that must be filled
     * @param json JSON containing data
     * @return Domain with json data filled
     */
    static RelationTerm insertDataIntoDomain(def json, def domain = new RelationTerm()) {
        domain.id = JSONUtils.getJSONAttrLong(json,'id',null)
        domain.relation = JSONUtils.getJSONAttrDomain(json, "relation", new Relation(), true)
        domain.term1 = JSONUtils.getJSONAttrDomain(json, "term1", new Term(), true)
        domain.term2 = JSONUtils.getJSONAttrDomain(json, "term2", new Term(), true)
        return domain;
    }

    static void registerMarshaller() {
        Logger.getLogger(this).info("Register custom JSON renderer for " + RelationTerm.class)
        JSON.registerObjectMarshaller(RelationTerm) {
            def returnArray = [:]
            returnArray['class'] = it.class
            returnArray['id'] = it.id
            returnArray['relation'] = it.relation.id
            returnArray['term1'] = it.term1.id
            returnArray['term2'] = it.term2.id

            return returnArray
        }
    }
//
//    /**
//     * Define fields available for JSON response
//     * This Method is called during application start
//     */
//    static void registerMarshaller() {
//        Logger.getLogger(this).info("Register custom JSON renderer for " + this.class)
//        println "<<< mapping from Term <<< " + getMappingFromAnnotation(RelationTerm)
//        JSON.registerObjectMarshaller(RelationTerm) { domain ->
//            return getDataFromDomain(domain)
//        }
//    }


    /**
     * Get the container domain for this domain (usefull for security)
     * @return Container of this domain
     */
    public CytomineDomain container() {
        return term1.container();
    }

    void checkAlreadyExist() {
        RelationTerm.withNewSession {
            if(relation && term1 && term2) {
                RelationTerm rt = RelationTerm.findByRelationAndTerm1AndTerm2(relation,term1,term2)
                if(rt!=null && (rt.id!=id))  {
                    throw new AlreadyExistException("RelationTerm with relation=${relation.id} and term1 ${term1.id} and term2 ${term2.id} already exist!")
                }
            }
        }
    }


}
