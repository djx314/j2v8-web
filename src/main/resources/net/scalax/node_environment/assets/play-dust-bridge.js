"use strict";
var dust = require("dustjs-helpers");

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
    if (typeof context.param === "string") {
        context.param = JSON.parse(context.param);
    }
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