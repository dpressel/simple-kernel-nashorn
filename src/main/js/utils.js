System = Java.type('java.lang.System')
ObjectMapper = Java.type('com.fasterxml.jackson.databind.ObjectMapper');
JACKSON = new ObjectMapper();


var utils;
if (!utils) {
    utils = {};
}

var console;
if (!console) {
    console = {}
}

(function () {
    'use strict';


    utils.java2str = function (obj)
    {
        return JACKSON.writeValueAsString(obj);
    };

    console.log = function (s) {

        var str = null;
        if (s instanceof Object) {
            str = JSON.stringify(s, null, '  ');
        } else {
            str = utils.java2str(s);
        }
        System.out.println(str);
    };

}());
