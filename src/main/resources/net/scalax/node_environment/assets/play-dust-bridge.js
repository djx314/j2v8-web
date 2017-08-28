"use strict";
var dust = require("dustjs-helpers");

//var propertyTemplate = dust.loadSource(dust.compile(propertyTemplateString));

/*var slickJsonOutAPI = function(contentKey, param, slickParam, isDebug, query, cb) {
    try {
        query.query(contentKey, JSON.stringify({ param: param, slickParam: slickParam, isDebug: isDebug }), function(s) {
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
            /!*try {
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
            }*!/
        });
    } catch (e) {
        cb(e, null);
    }
};*/

/*var remoteJsonRequest = function(serverKey, param, query, cb) {
    try {
        query.remoteJson(JSON.stringify({ serverKey: serverKey, data: param }), function(successStr, errorStr) {
            if (typeof errorStr === "string") {
                cb(null, errorStr);
            } else if (typeof successStr === "string") {
                //初始化为 undefined 类型，应对 json 解析失败的情况
                var result = undefined;
                try {
                    result = JSON.parse(successStr);
                } catch(e) {
                    console.log(e);
                }
                if (typeof result === "object") {
                    cb(result, null);
                } else {
                    cb(null, "响应的数据不能解析成 json")
                }
            } else {
                cb(null, "不可识别的响应类型和错误数据类型")
            }
        });
    } catch (e) {
        //cb(e, null);
        console.log(e);
    }
};*/

/*dust.helpers.slickJsonOut = function(chunk, context, bodies, params) {
    var slickParam = context.resolve(params.slickParam);
    var key = context.resolve(params.key);
    delete params.key;
    delete params.slickParam;

    var aa = chunk.map(function(inChunk) {
        slickJsonOutAPI(key, params, slickParam, context.get("scala-isDebug"), context.get("scala-query"), function(err, result, proInfo) {
            if ((err !== null && err !== undefined)/!*&& bodies["else"]*!/) {
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
};*/

dust.helpers.playRequest = function(chunk, context, bodies, params) {
    var key = params.key;
    if (! key) key = "request";
    var ctxObj = {};
    ctxObj[key] = context.get("scala-request");

    return chunk.map(function(inChunk) {
        inChunk.render(bodies.block, context.push(ctxObj)).end();
    });
};

for (var i = 0; i < helperNames.length; i++) {
    var helperName1 = helperNames[i];
    (function(helperName) {
        dust.helpers[helperName] = function(chunk, context, bodies, params) {
            var query = context.get("scala-query");

            return chunk.map(function(inChunk) {
                query(helperName, JSON.stringify(params), function(result, errStr) {
                    if (typeof errStr === "string") {
                        inChunk.end("渲染发生错误,错误信息:" + errStr);
                    //} else if ((typeof result === "object") || (typeof result === "string") || typeof result === "number") {
                    } else if (typeof result === "object") {
                        if ((typeof result._originalValue === "string")) {
                            inChunk.end(result._originalValue);
                        } else if (typeof result._originalJson === "string") {
                            var data = JSON.parse(result._originalJson);
                            inChunk.render(bodies.block, context.push({ content: data })).end();
                        } else if (typeof result._originalObject === "object") {

                        } else {
                            inChunk.end("渲染错误,不可识别的数据类型");
                        }
                    } else {
                        inChunk.end("渲染错误,错误信息和数据皆不合法");
                    }
                });
            });
        };
    })(helperName1);
}

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

var outPut = function(inPut, context, promise) {
    /*var requestParam = JSON.parse(param);

    requestParam["scala-isDebug"] = isDebug;
    requestParam["scala-query"] = query;

    requestParam["scala-request"] = query.request;*/
    dust.renderSource(inPut, context, function(error, result) {
        if (error) {
            promise.failure(error.message);
        } else {
            promise.success(result);
        }
    });
};

//添加模板方法，暴露给 java 使用
var addTemplate = function(templateName, templateContent) {
    //编译模板，使用 templateName 作为 key。
    var compiledTemplate = dust.compile(templateContent, templateName);
    //注册模板
    dust.loadSource(compiledTemplate);
};

exports.outPut = outPut;
exports.addTemplate = addTemplate;