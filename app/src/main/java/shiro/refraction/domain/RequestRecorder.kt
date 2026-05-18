package shiro.refraction.domain

import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import shiro.refraction.data.model.NetworkRequest
import shiro.refraction.data.model.RequestSource

class RequestRecorder {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _requests = MutableStateFlow<List<NetworkRequest>>(emptyList())
    val requests: StateFlow<List<NetworkRequest>> = _requests.asStateFlow()

    private val requestsList = mutableListOf<NetworkRequest>()

    fun startRecording() {
        synchronized(this) {
            requestsList.clear()
            _requests.value = emptyList()
        }
        _isRecording.value = true
    }

    fun stopRecording() {
        _isRecording.value = false
    }

    fun record(request: NetworkRequest) {
        if (!_isRecording.value) return
        synchronized(this) {
            if (requestsList.size >= MAX_REQUESTS) requestsList.removeAt(0)
            requestsList.add(request)
            _requests.value = requestsList.toList()
        }
    }

    fun clear() {
        synchronized(this) {
            requestsList.clear()
            _requests.value = emptyList()
        }
    }

    class JsBridge(private val recorder: RequestRecorder) {
        @JavascriptInterface
        fun capture(json: String) {
            val obj = JSONObject(json)
            val src = obj.optString("s", "api")
            recorder.record(
                NetworkRequest(
                    method = obj.optString("m", "GET"),
                    url = obj.optString("u", ""),
                    headers = obj.optJSONObject("h")?.toStringMap() ?: emptyMap(),
                    requestBody = obj.optString("b", null).takeIf { it != "" && it != "null" },
                    responseCode = obj.optInt("rc", 0),
                    responseHeaders = obj.optJSONObject("rh")?.toStringMap() ?: emptyMap(),
                    responseBody = obj.optString("rb", null).takeIf { it != "" && it != "null" },
                    source = when (src) {
                        "ws" -> RequestSource.WEBSOCKET
                        "res" -> RequestSource.RESOURCE
                        else -> RequestSource.API
                    },
                    timestamp = obj.optLong("t", System.currentTimeMillis())
                )
            )
        }

        private fun JSONObject.toStringMap(): Map<String, String> {
            val map = mutableMapOf<String, String>()
            val keys = this.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = this.getString(key)
            }
            return map
        }
    }

    companion object {
        private const val MAX_REQUESTS = 500

        @Suppress("StringLiteralDuplication")
        const val INJECTION_SCRIPT = """(function(){
if(window.__rfRec)return;
window.__rfRec=true;
function send(d){try{window.__rfBridge.capture(JSON.stringify(d));}catch(e){}}
function trunc(s,n){if(s===null||s===undefined)return null;try{var t=typeof s==='string'?s:String(s);return t.length>n?t.substring(0,n):t;}catch(e){return null;}}
function parseHdr(raw){var h={};if(!raw)return h;try{var lines=raw.trim().split(/\r?\n/);for(var i=0;i<lines.length;i++){var idx=lines[i].indexOf(':');if(idx>0)h[lines[i].substring(0,idx).trim()]=lines[i].substring(idx+1).trim();}}catch(e){}return h;}
function bufHex(buf){try{var a=new Uint8Array(buf);var h='';for(var i=0;i<a.length;i++){h+=('0'+a[i].toString(16)).slice(-2);if(h.length>=20480)break;}return h;}catch(e){return null;}}
function dataStr(d){if(d===null||d===undefined)return null;if(typeof d==='string')return trunc(d,10240);if(d instanceof ArrayBuffer)return bufHex(d);if(d.buffer&&d.buffer instanceof ArrayBuffer)return bufHex(d.buffer);try{return '['+d.constructor.name+']';}catch(e){return '[unknown]';}}
var oOpen=XMLHttpRequest.prototype.open;
var oSend=XMLHttpRequest.prototype.send;
var oHdr=XMLHttpRequest.prototype.setRequestHeader;
XMLHttpRequest.prototype.open=function(m,u){this.__rf={m:m,u:u,h:{}};return oOpen.apply(this,arguments);};
XMLHttpRequest.prototype.setRequestHeader=function(k,v){if(this.__rf)this.__rf.h[k]=v;return oHdr.apply(this,arguments);};
XMLHttpRequest.prototype.send=function(b){var s=this;if(s.__rf){s.__rf.b=trunc(b,10240);var t=Date.now();var done=function(){if(s.readyState!==4)return;send({m:s.__rf.m,u:s.__rf.u,h:s.__rf.h,b:s.__rf.b,rc:s.status,rh:parseHdr(s.getAllResponseHeaders()),rb:trunc(s.responseText,10240),t:t,s:'api'});};s.addEventListener('readystatechange',done);}return oSend.apply(this,arguments);};
var oFetch=window.fetch;
window.fetch=function(inp,init){var u=typeof inp==='string'?inp:(inp&&inp.url?inp.url:String(inp));var m=(init&&init.method)||(inp&&inp.method?inp.method:'GET');var hd={};if(init&&init.headers){if(typeof init.headers.forEach==='function'){init.headers.forEach(function(v,k){hd[k]=v;});}else{for(var k in init.headers){if(init.headers.hasOwnProperty(k))hd[k]=init.headers[k];}}}var b=trunc(init&&init.body?init.body:null,10240);var t=Date.now();return oFetch.apply(this,arguments).then(function(r){var cl=r.clone();var rh={};if(r.headers&&typeof r.headers.forEach==='function'){r.headers.forEach(function(v,k){rh[k]=v;});}cl.text().then(function(txt){send({m:m,u:u,h:hd,b:b,rc:r.status,rh:rh,rb:trunc(txt,10240),t:t,s:'api'});}).catch(function(){send({m:m,u:u,h:hd,b:b,rc:r.status,rh:rh,rb:null,t:t,s:'api'});});return r;}).catch(function(e){send({m:m,u:u,h:hd,b:b,rc:0,rh:{},rb:null,t:t,s:'api'});throw e;});};
var OW=window.WebSocket;
window.WebSocket=function(url,proto){var ws=proto?new OW(url,proto):new OW(url);var ck={};try{document.cookie.split(';').forEach(function(c){var p=c.trim().split('=');if(p.length>=2)ck[p[0].trim()]=p.slice(1).join('=');});}catch(e){}send({m:'WS_UP',u:url,h:ck,rc:101,t:Date.now(),s:'ws'});
ws.addEventListener('message',function(e){var p=dataStr(e.data);send({m:'WS_IN',u:url,rb:p,t:Date.now(),s:'ws'});});
var oWsSend=ws.send.bind(ws);ws.send=function(d){var p=dataStr(d);send({m:'WS_OUT',u:url,b:p,t:Date.now(),s:'ws'});return oWsSend(d);};
ws.addEventListener('close',function(e){send({m:'WS_CLOSE',u:url,rc:e.code,rb:trunc(e.reason,1024),t:Date.now(),s:'ws'});});
ws.addEventListener('error',function(){send({m:'WS_ERR',u:url,rc:0,t:Date.now(),s:'ws'});});
return ws;};
window.WebSocket.prototype=OW.prototype;
window.WebSocket.CONNECTING=OW.CONNECTING;
window.WebSocket.OPEN=OW.OPEN;
window.WebSocket.CLOSING=OW.CLOSING;
window.WebSocket.CLOSED=OW.CLOSED;
})();"""
    }
}
