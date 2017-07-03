"use strict";
var dust = require("dustjs-helpers");

var propertyTemplate = dust.loadSource(dust.compile(propertyTemplateString));

var slickJsonOutAPI = function(contentKey, param, slickParam, isDebug, query, cb) {
    try {
        query.query(contentKey, JSON.stringify({ param: param, slickParam: slickParam, isDebug: isDebug }), { callback: function(s) {
            var result = JSON.parse(s);
            try {
                if (isDebug) {
                    var stringParam = JSON.stringify(param);
                    dust.render(propertyTemplate, {
                        properties: result.properties,
                        param: stringParam,
                        contentKey: contentKey,
                        slickParam: slickParam
                    }, function (err, out) {
                        if (err) {
                            cb(err, null, out);
                        } else {
                            cb(null, result, out);
                        }
                    });
                } else {
                    cb(null, result, null);
                }
            } catch (e) {
                cb(e, null, null);
            }
            /*try {
                if (s.isLeft()) {
                    console.log(s.left().get());
                    var error = s.left().get().getMessage();
                    if (! error) {
                        error = "未知错误";
                    }
                    cb(error, null, null);
                } else {
                    if (isDebug) {
                        var result = JSON.parse(s.right().get());
                        var stringParam = JSON.stringify(param);
                        dust.render(propertyTemplate, {
                            properties: result.properties,
                            param: stringParam,
                            contentKey: contentKey,
                            slickParam: slickParam
                        }, function(err, out) {
                            if (err) {
                                cb(err, null, out);
                            } else {
                                cb(null, result, out);
                            }
                        });
                    } else {
                        var result = JSON.parse(s.right().get());
                        cb(null, result, null);
                    }
                }
            } catch (e) {
                cb(e, null, null);
            }*/
        } });
    } catch (e) {
        cb(e, null);
    }
};

dust.helpers.slickJsonOut = function(chunk, context, bodies, params) {
    var slickParam = context.resolve(params.slickParam);
    var key = context.resolve(params.key);
    delete params.key;
    delete params.slickParam;

    var aa = chunk.map(function(inChunk) {
        slickJsonOutAPI(key, params, slickParam, context.get("scala-isDebug"), context.get("scala-query"), function(err, result, proInfo) {
            if ((err !== null && err !== undefined)/*&& bodies["else"]*/) {
                if (bodies["else"]) {
                    inChunk.render(bodies["else"], context.push({ error: err })).end();
                } else {
                    inChunk.end(err + "有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有有错误");
                }
            } else if (bodies.block) {
                if (proInfo) {
                    inChunk.write("<div>").render(bodies.block, context.push({ content: result })).write(proInfo + "</div>").end();
                } else {
                    inChunk.render(bodies.block, context.push({ content: result })).end();
                }
            }
        })
    });
    return aa;
};

dust.helpers.playRequest = function(chunk, context, bodies, params) {
    var key = params.key;
    if (! key) key = "request";
    var ctxObj = {};
    ctxObj[key] = context.get("scala-request");

    return chunk.map(function(inChunk) {
        inChunk.render(bodies.block, context.push(ctxObj)).end();
    });
};

dust.filters.d = function(value) {
    var result = null;
    if (typeof value === "string") {
        result = decodeURI(value);
    }
    return result;
};

dust.filters.dc = function(value) {
    var result = null;
    if (typeof value === "string") {
        result = decodeURIComponent(value);
    }
    return result;
};

dust.helpers.property = function yell(chunk, context, bodies, params) {
    return chunk.map(function(inChunk) {
        inChunk.render(bodies.block, context.push({ data: params.data[params.key] })).end();
    });
};

var outPut = function(inPut, param, isDebug, query, promise) {

    var requestParam = JSON.parse(param);

    requestParam["scala-isDebug"] = isDebug;
    requestParam["scala-query"] = query;

    /*var request = new Proxy({}, {
        get: function (target, key, receiver) {
            console.log(`getting ${key}!`);
            return query.getParam(key);
        },
        set: function (target, key, value, receiver) {
            console.log(`setting ${key}!`);
            return null;
        }
    });*/

    requestParam["scala-request"] = query.request;

    dust.renderSource(inPut, requestParam, function(error, result) {
        if (error) {
            promise.failure(error);
        } else {
            promise.success(result);
        }
    });
};

//添加模板方法，暴露给 java 使用
var addTemplate = function(templateName, templateContent) {
    //编译模板，使用 templateName 作为 key。
    var aabb = dust.compile(templateContent, templateName);
    //注册模板
    dust.loadSource(aabb);
};

exports.outPut = outPut;
exports.addTemplate = addTemplate;