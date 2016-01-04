import controllers.api.Statistics
import play.api.ApplicationLoader.Context
import play.api.libs.ws.ning.NingWSComponents
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext}
import router.Routes

class AppLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application =
    new AppComponents(context).application
}

class AppComponents(context: Context) extends BuiltInComponentsFromContext(context) with NingWSComponents {

  lazy val applicationController = new controllers.Application(wsClient)
  lazy val statisticsController = new Statistics()
  lazy val assets = new controllers.Assets(httpErrorHandler)

  override def router: Router = new Routes(
    httpErrorHandler,
    applicationController,
    assets,
    statisticsController
  )
}