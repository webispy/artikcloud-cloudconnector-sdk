// Sample CloudConnector, that can be used as a boostrap to write a new CloudConnector.
// Lot of code is commented, and everything is optional.
// The class can be named as you want no additional import allowed
// See the javadoc/scaladoc of cloud.artik.cloudconnector.api_v1.CloudConnector
package cloudconnector

import static java.net.HttpURLConnection.*

import org.scalactic.*
import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import cloud.artik.cloudconnector.api_v1.*
import scala.Option

//@CompileStatic
class MyCloudConnector extends CloudConnector {
    static final CT_JSON = 'application/json'
	def bk = new String()
    JsonSlurper slurper = new JsonSlurper()

    @Override
    Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase) {
		    ctx.debug("!!!! signAndPrepare: - Phase: " + phase)
        ctx.debug("!!!!                 - RequestDef:" + req)
        ctx.debug("!!!!                 - DeviceInfo:" + info)
        ctx.debug("!!!!                 - Context:" + ctx)
        switch (phase) {          
          case Phase.getOauth2Token:
            ctx.debug("!!!! remove query params")
            return new Good(req.withQueryParams([:]))
          case Phase.subscribe:
          case Phase.unsubscribe:
          case Phase.undef:
          case Phase.fetch:
            return new Good(req.addHeaders(["Authorization": "Bearer " + info.credentials.token]))
          default:
            return super.signAndPrepare(ctx, req, info, phase)
        }
    }

    @Override
    Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef req) {
        ctx.debug("!!!! onNotification: " + req)
        // nothing todo
        return new Good(new NotificationResponse([]))
    }

    @Override
    Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
        ctx.debug("!!!! onFetchResponse:" + res)
        switch (res.status) {
            case HTTP_OK:
                def content = res.content.trim()
                if (content == '' || content == 'OK') {
                    return new Good([])
                } else if (res.contentType.startsWith(CT_JSON)) {
                    if (req.url.startsWith("${ctx.parameters().endpoint}/notify")) {
                        def json = slurper.parseText(content)
                        ctx.debug("!!!!     result: " + json)
                        if (json.status == 200)
                          return new Good([])
                        else
                          return new Bad(new Failure(json.message))
                    }
                    return new Bad(new Failure("unsupported request ${req.url}"))
                }
                return new Bad(new Failure("unsupported response ${res} ... ${res.contentType} .. ${res.contentType.startsWith(CT_JSON)}"))
            default:
                return new Bad(new Failure("http status : ${res.status} on ${req.method} ${req.url}, with content : ${res.content}"))
        }
    }

    @Override
    Or<ActionResponse, Failure> onAction(Context ctx, ActionDef action, DeviceInfo info) {
        ctx.debug("!!!! onAction:" + action)
        switch (action.name) {
            case "notify":
                def did = info.did
                def paramsAsJson = slurper.parseText(action.params)
                def valueToSend = paramsAsJson.value

                if (valueToSend == null) {
                    return new Bad(new Failure("Missing field 'value' in action parameters ${paramsAsJson}"))
                }

                def req = new RequestDef("${ctx.parameters().endpoint}/notify")
                              .withMethod(HttpMethod.Post)
                              .withContentType("application/x-www-form-urlencoded")
                              .withBodyParams(["message":valueToSend])
                return new Good(new ActionResponse([new ActionRequest([req])]))
            default:
                return new Bad(new Failure("Unknown action: ${action.name}"))
        }
    }
}
