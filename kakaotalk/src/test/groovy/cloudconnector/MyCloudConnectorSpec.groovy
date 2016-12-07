package cloudconnector

import static java.net.HttpURLConnection.*

//import org.junit.Test
import spock.lang.*
import org.scalactic.*
import scala.Option
import cloud.artik.cloudconnector.api_v1.*
import utils.FakeContext
import static utils.Tools.*

class MyCloudConnectorSpec extends Specification {

    def sut = new MyCloudConnector()
    def ctx = new FakeContext()

    def "send action to cloud when receiving ARTIK Cloud action"() {
      when:
      def action = new ActionDef(Option.apply("sdid"), "ddid", System.currentTimeMillis(), "setValue", '{"value":"foo"}')
      def fakeDevice = new DeviceInfo(
        "ddid",
        Option.apply("extId"),
        new Credentials(AuthType.OAuth2, "", "", Empty.option(), Option.apply("bearer"), ctx.scope(), Empty.option()), ctx.cloudId(), Empty.option()
      )
      def actionRes = sut.onAction(ctx, action, fakeDevice)

      then:
      actionRes.isGood()
      actionRes.get() == new ActionResponse([
        new ActionRequest(
          [
            new RequestDef("${ctx.parameters().endpoint}/actions/extId/setValue")
              .withMethod(HttpMethod.Post)
              .withContent('{"value":"foo"}', "application/json")
          ]
        )
      ])
    }


}
