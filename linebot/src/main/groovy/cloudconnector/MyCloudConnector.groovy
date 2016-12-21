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
  static final BOT_TOKEN = '<your-token>'

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
            return new Good(req.addHeaders(["Authorization": "Bearer " + BOT_TOKEN]))
          default:
            return super.signAndPrepare(ctx, req, info, phase)
        }
    }

    @Override
    def Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
        ctx.debug("!!!! subscribe: - ctx: " + ctx)
        ctx.debug("!!!!            - info: " + info)

        new Good([new RequestDef("${ctx.parameters().endpoint}/profile/" + info.credentials.token).withMethod(HttpMethod.Get)])
    }

    @Override
    def Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
        ctx.debug("!!!! onSubscribeResponse: - ctx: " + ctx)
        ctx.debug("!!!!                      - req: " + req)
        ctx.debug("!!!!                      - info: " + info)
        ctx.debug("!!!!                      - res: " + res)

        def json = slurper.parseText(res.content)
        ctx.debug("!!!!                      - userId: " + json.userId)

        new Good(Option.apply(info.withExtId(json.userId)))
    }

    @Override
    Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef req) {
        ctx.debug("!!!! onNotification: - req: " + req)
        ctx.debug("!!!!                 - ctx: " + ctx)

        def json = slurper.parseText(req.content)
        ctx.debug("!!!!                 - req.content: " + json)

        if (json.source) {
          def extid = json.source.userId

          ctx.debug("!!!!                 - userId: " + extid)
          extid += ""

          // ctx.debug("!!!!                 - byExtId: " + new ByExternalId(extid))
          new Good(new NotificationResponse([new ThirdPartyNotification(new ByExternalId(extid), [], [req.content])]))
        } else {
          // nothing todo
          return new Good(new NotificationResponse([]))
        }
    }

    @Override
    def Or<List<Event>, Failure> onNotificationData(Context ctx, DeviceInfo info, String data) {
        ctx.debug("!!!! onNotificationData: - ctx: " + ctx)
        ctx.debug("!!!!                     - info: " + info)
        ctx.debug("!!!!                     - data: " + data)

        def json = slurper.parseText(data)
        def msgsplit = json.message.text.split(' ')
        ctx.debug("!!!!                     - split: " + msgsplit)

        // def ts = (json.timestamp)? json.timestamp * 1000L: ctx.now()
        // def fields = slurper.parseText('{ \"deviceName\": \"' + msgsplit[0] + '\", \"deviceAction\": \"' + msgsplit[1] + '\" }')
        // def fields = JsonOutput.toJson('{ \"deviceName\": \"' + msgsplit[0] + '\", \"deviceAction\": \"' + msgsplit[1] + '\" }')
        def fields = '{ \"deviceName\": \"' + msgsplit[0] + '\", \"deviceAction\": \"' + msgsplit[1] + '\" }'
        return new Good([new Event(ctx.now(), fields)])
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
                    if (req.url.startsWith("${ctx.parameters().endpoint}/message/push")) {
                        return new Good([])
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
            case "sendMessage":
                def did = info.did
                def paramsAsJson = slurper.parseText(action.params)
                def valueToSend = paramsAsJson.value

                if (valueToSend == null) {
                    return new Bad(new Failure("Missing field 'value' in action parameters ${paramsAsJson}"))
                }

                def req = new RequestDef("${ctx.parameters().endpoint}/message/push")
                              .withMethod(HttpMethod.Post)
                              .withContent("{ \"to\": \"" + info.credentials.token + "\", \"messages\": [{\"type\": \"text\", \"text\": \"" + valueToSend + "\" }] }", "application/json")
                return new Good(new ActionResponse([new ActionRequest([req])]))
            default:
                return new Bad(new Failure("Unknown action: ${action.name}"))
        }
    }
}
