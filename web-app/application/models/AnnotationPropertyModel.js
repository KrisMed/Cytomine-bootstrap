var AnnotationPropertyModel = Backbone.Model.extend({
    initialize: function (options) {
        this.idAnnotation = options.idAnnotation;
    },
    url: function () {
        var base = 'api/annotation/' + this.idAnnotation + '/property';
        var format = '.json';
        if (this.isNew()) {
            return base + format;
        }
        return base + (base.charAt(base.length - 1) == '/' ? '' : '/') + this.id + format;
    },
    parse : function (response, options) {
        console.log("parse " + response.property);
        if (response.property) return response.property;
        else return response;
    }
});


var AnnotationPropertyCollection = PaginatedCollection.extend({
    model: AnnotationPropertyModel,
    initialize: function (options) {
        this.initPaginator(options);
        this.idAnnotation = options.idAnnotation;
    },
    url: function () {
        return "api/annotation/" + this.idAnnotation + "/property.json";
    }
});

var AnnotationPropertyKeysCollection = PaginatedCollection.extend({
    initialize: function (options) {
        this.initPaginator(options);
        this.idProject = options.idProject;
        this.idImage = options.idImage;
    },
    url: function () {
        if (this.idProject != undefined) {
            return "/api/annotation/property/key.json?idProject="+this.idProject;
        } else {
            if (this.idImage != undefined) {
                return "/api/annotation/property/key.json?idImage="+this.idImage;
            }
        }
    }
});

var AnnotationPropertyTextCollection = PaginatedCollection.extend({
    initialize: function (options) {
        this.initPaginator(options);
        this.idUser = options.idUser;
        this.idImage = options.idImage;
        this.key = options.key;
    },
    url: function () {
        if (this.idUser != undefined && this.idImage != undefined) {
            var offset = "?";
            if (this.key != undefined) {
                offset = offset + "key=" + this.key;
            }
            return "/api/user/"+this.idUser+"/imageinstance/"+this.idImage+"/annotationposition.json" + offset;
        }
    }
});

